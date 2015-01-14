// Copyright 2012 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

/**
 * Provides encoding and decoding of sensitive values. It supports plain text,
 * obfuscated, and encrypted formats.
 *
 * <p>This class is thread-safe.
 */
class SensitiveValueCodec implements SensitiveValueDecoder {
  private static final Logger log
      = Logger.getLogger(SensitiveValueCodec.class.getName());
  
  private static final SecretKey OBFUSCATING_KEY = new SecretKeySpec(
      new byte[] {
        (byte) 0x7d, (byte) 0xec, (byte) 0xbd, (byte) 0x31, (byte) 0x4e,
        (byte) 0xf3, (byte) 0x68, (byte) 0x69, (byte) 0x69, (byte) 0x78,
        (byte) 0x7a, (byte) 0xc9, (byte) 0xfc, (byte) 0x99, (byte) 0x07,
        (byte) 0x9c
      }, "AES");
  private static final Charset CHARSET = Charset.forName("UTF-8");

  private final KeyPair encryptingKey;
  private final Random random = new Random();

  /**
   * Construct a codec capable of encoding and decoding secrets. The provided
   * {@code encryptingKey} is used when encryption is requested, but may be
   * {@code null}. If {@code null}, then {@link SecurityLevel#ENCRYPTED} will be
   * unavailable for encryption and decryption.
   *
   * @param encryptingKey key used when encryption is requested, or {@code null}
   */
  public SensitiveValueCodec(KeyPair encryptingKey) {
    this.encryptingKey = encryptingKey;
  }

  private Cipher createObfuscatingCipher() {
    try {
      return Cipher.getInstance(OBFUSCATING_KEY.getAlgorithm());
    } catch (NoSuchAlgorithmException ex) {
      throw new AssertionError();
    } catch (NoSuchPaddingException ex) {
      throw new AssertionError();
    }
  }

  private Cipher createEncryptingCipher() {
    try {
      return Cipher.getInstance(encryptingKey.getPrivate().getAlgorithm());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    } catch (NoSuchPaddingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Encode {@code readable} using requested {@code security}.
   *
   * @return encoded non-readable of {@code readable}
   * @throws IllegalStateException if the encrypting key and encrypting cipher
   *     are misconfigured or not in agreement, or is requested when unavailable
   */
  public String encodeValue(String readable, SecurityLevel security) {
    String encoded;
    switch (security) {
      case PLAIN_TEXT:
        // We always apply the prefix, even if it isn't strictly necessary. This
        // is to make it obvious that the process actually did something to the
        // user and makes them more aware of the pl: prefix if they need to use
        // it.
        encoded = readable;
        break;

      case OBFUSCATED:
        encoded = encryptAndBase64(readable, createObfuscatingCipher(),
            OBFUSCATING_KEY);
        break;

      case ENCRYPTED:
        if (encryptingKey == null) {
          throw new IllegalStateException(
              "No key provided to encrypt value");
        }
        encoded = encryptAndBase64(readable, createEncryptingCipher(),
            encryptingKey.getPublic());
        break;

      default:
        throw new AssertionError();
    }
    return security.getPrefix() + encoded;
  }

  /**
   * Beware that the provided Cipher will be modified as part of encryption.
   */
  private String encryptAndBase64(String readable, Cipher cipher, Key key) {
    byte[] bytes = readable.getBytes(CHARSET);

    try {
      cipher.init(Cipher.ENCRYPT_MODE, key);
    } catch (InvalidKeyException ex) {
      throw new AssertionError();
    }
    try {
      bytes = cipher.doFinal(bytes);
    } catch (IllegalBlockSizeException ex) {
      // The algorithm does not seem suited for our use.
      throw new IllegalStateException(ex);
    } catch (BadPaddingException ex) {
      throw new AssertionError();
    }

    return DatatypeConverter.printBase64Binary(bytes);
  }

  /**
   * Determine what encode operation was used to produce {@code nonReadable}.
   *
   * @param nonReadable previously-encoded string
   * @return security used
   */
  public SecurityLevel determineSecurityLevelUsed(String nonReadable) {
    SecurityLevel security = SecurityLevel.PLAIN_TEXT;
    for (SecurityLevel trySecurityLevel : SecurityLevel.values()) {
      if (nonReadable.startsWith(trySecurityLevel.getPrefix())) {
        security = trySecurityLevel;
        break;
      }
    }
    return security;
  }

  /**
   * Reverse previous encode operation that produced {@code nonReadable}.
   *
   * @param nonReadable previously-encoded string
   * @return non-encoded string
   * @throws IllegalArgumentException if {@code nonReadable} is unable to be
   *     decoded
   */
  @Override
  public String decodeValue(String nonReadable) {
    SecurityLevel security = determineSecurityLevelUsed(nonReadable);
    if (nonReadable.startsWith(security.getPrefix())) {
      nonReadable = nonReadable.substring(security.getPrefix().length());
    }
    switch (security) {
      case PLAIN_TEXT:
        return nonReadable;

      case OBFUSCATED:
        return base64AndDecrypt(nonReadable, createObfuscatingCipher(),
            OBFUSCATING_KEY);

      case ENCRYPTED:
        if (encryptingKey == null) {
          throw new IllegalArgumentException(
              "No key provided to decrypt value");
        }
        return base64AndDecrypt(nonReadable, createEncryptingCipher(),
            encryptingKey.getPrivate());

      default:
        throw new AssertionError();
    }
  }

  /**
   * Beware that the provided Cipher will be modified as part of decryption.
   */
  private String base64AndDecrypt(String nonReadable, Cipher cipher, Key key) {
    byte[] bytes = DatatypeConverter.parseBase64Binary(nonReadable);

    try {
      cipher.init(Cipher.DECRYPT_MODE, key);
    } catch (InvalidKeyException ex) {
      throw new AssertionError();
    }
    try {
      bytes = cipher.doFinal(bytes);
    } catch (IllegalBlockSizeException ex) {
      throw new AssertionError();
    } catch (BadPaddingException ex) {
      throw new IllegalArgumentException(ex);
    }

    return new String(bytes, CHARSET);
  }

  /**
   * Possible levels of security for storing value.
   */
  public enum SecurityLevel {
    /**
     * The value is prefixed with "pl:", but is otherwise left as-is.
     */
    PLAIN_TEXT("pl:"),
    /**
     * The value is prefixed with "obf:" and is obfuscated, but no real security
     * is added. AES is used to encrypt the value, but the key is hard-coded.
     */
    OBFUSCATED("obf:"),
    /**
     * The value is prefixed with "pkc:" and is encrypted using the public key
     * cryptography provided to the constructor.
     */
    ENCRYPTED("pkc:"),
    ;

    private final String prefix;

    private SecurityLevel(String prefix) {
      this.prefix = prefix;
    }

    String getPrefix() {
      return prefix;
    }
  }
  
  /**
   * <p>This class allows adaptor administrators to get encoded sensitive value
   * from command line.
   * 
   * Example command line to run:
   * <pre>
   * java \
   * -Djavax.net.ssl.keyStore=keys.jks \
   * -Djavax.net.ssl.keyStoreType=jks \
   * -Djavax.net.ssl.keyStorePassword=changeit \
   * -classpath 'adaptor-20130612-withlib.jar' \
   * com.google.enterprise.adaptor.SensitiveValueCodec \
   * -DsecurityLevel=ENCRYPTED \
   * -Dserver.keyAlias=adaptor \
   * -Dserver.secure=true
   * </pre>
   */
  public static void main(String[] args) throws IOException {
    Config config = new Config();
    config.addKey("securityLevel", SecurityLevel.PLAIN_TEXT.toString());
    config.setValue("gsa.hostname", "not-used");
    Application.autoConfig(config, args, null);
    
    SecurityLevel securityLevel =
        SecurityLevel.valueOf(config.getValue("securityLevel"));
    log.config("securityLevel=" + securityLevel.toString());
    String serverKeyAlias = config.getServerKeyAlias();
    log.config("server.keyAlias=" + serverKeyAlias);
    boolean secure = config.isServerSecure();
    log.config("server.secure=" + secure);
    
    KeyPair keyPair = null;
    try {
      keyPair = GsaCommunicationHandler.getKeyPair(serverKeyAlias);
    } catch (IOException ex) {
      // The exception is only fatal if we are in secure mode.
      if (secure) {
        throw ex;
      }
    } catch (RuntimeException ex) {
      // The exception is only fatal if we are in secure mode.
      if (secure) {
        throw ex;
      }
    }
    SensitiveValueCodec codec = new SensitiveValueCodec(keyPair);
    
    Console console = System.console();
    if (console == null) {
      log.warning("Couldn't get Console instance");
      return;
    }
    char passwordArray[] = console.readPassword("Sensitive Value: ");
    String password = new String(passwordArray);
    String encodedValue = codec.encodeValue(password, securityLevel);
    console.printf("Encoded value is: %s%n", encodedValue);
  }
}
