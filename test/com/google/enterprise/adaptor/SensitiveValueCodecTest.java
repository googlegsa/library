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

import static com.google.enterprise.adaptor.SensitiveValueCodec.SecurityLevel;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.security.*;

import javax.crypto.Cipher;

/**
 * Test cases for {@link SensitiveValueCodec}.
 */
public class SensitiveValueCodecTest {
  private static final KeyPair key;
  private static final Cipher cipher;

  private SensitiveValueCodec codec = new SensitiveValueCodec(key);

  static {
    try {
      key = KeyPairGenerator.getInstance("RSA").generateKeyPair();
      cipher = Cipher.getInstance("RSA");
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testIntCasting() {
    assertEquals(-1, (byte) 0xff);
  }

  @Test
  public void testEnsureAllPrefixesContainColon() {
    for (SecurityLevel security : SecurityLevel.values()) {
      // Plain-text logic uses this assumption.
      assertTrue(security.getPrefix().contains(":"));
    }
  }

  @Test
  public void testEncodeDecode() {
    final String golden = "Testing: !@#$%^&*()?+\"',.<>ë";
    for (SecurityLevel security : SecurityLevel.values()) {
      String encoded = codec.encodeValue(golden, security);
      assertFalse(golden.equals(encoded));
      assertEquals(golden, codec.decodeValue(encoded));
    }
  }

  @Test
  public void testEncodeDecodeEmpty() {
    final String golden = "";
    for (SecurityLevel security : SecurityLevel.values()) {
      String encoded = codec.encodeValue(golden, security);
      if (security == SecurityLevel.PLAIN_TEXT) {
        assertEquals(golden, encoded);
      } else {
        assertFalse(golden.equals(encoded));
      }
      assertEquals(golden, codec.decodeValue(encoded));
    }
  }

  @Test
  public void testDecodePlainTextWithColon() {
    final String golden = "notARealPrefix:testing";
    assertEquals(golden, codec.decodeValue(golden));
  }

  @Test
  public void testPadRandomDelimiterGeneration() {
    // This is only a probabilistic test.
    final int length = 1024;
    String longString;
    {
      StringBuilder sb = new StringBuilder(length);
      for (int i = 0; i < length; i++) {
        sb.append("a");
      }
      longString = sb.toString();
    }
    // Try long input to gain confidence that the code never uses random padding
    // that includes the delimiter.
    assertEquals(longString, codec.decodeValue(codec.encodeValue(
        longString, SecurityLevel.OBFUSCATED)));
  }

  @Test
  public void testDetermineSecurityLevelUsed() {
    final SecurityLevel golden = SecurityLevel.PLAIN_TEXT;
    String encoded = codec.encodeValue("", golden);
    assertEquals(golden, codec.determineSecurityLevelUsed(encoded));
  }

  @Test
  public void testDetermineSecurityLevelUsedNoPrefix() {
    assertEquals(SecurityLevel.PLAIN_TEXT,
        codec.determineSecurityLevelUsed(""));
  }

  @Test
  public void testNullKeyPair() {
    codec = new SensitiveValueCodec(null);
    codec.encodeValue("", SecurityLevel.PLAIN_TEXT);
    thrown.expect(IllegalStateException.class);
    codec.encodeValue("", SecurityLevel.ENCRYPTED);
  }

  @Test
  public void testNullKeyPairDecrypt() {
    String encrypted = codec.encodeValue("", SecurityLevel.ENCRYPTED);
    codec = new SensitiveValueCodec(null);
    thrown.expect(IllegalArgumentException.class);
    codec.decodeValue(encrypted);
  }
}
