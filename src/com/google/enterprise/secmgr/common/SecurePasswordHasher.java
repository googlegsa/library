// Copyright 2009 Google Inc.
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


package com.google.enterprise.secmgr.common;

import com.google.common.base.Charsets;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * This class implements two password hashers.  One is based on the
 * PBKDF2 specification using HMAC-SHA256 as the underlying
 * primitive.  PBKDF is short for Password-Based Key Derivation
 * Function.  Requires JCE provider that implements the
 * "PBKDF2WithHmacSHA1" algorithm.  (Briefly, this algorithm and parameter
 * selection provides some protection against certain types of offline
 * password dictionary attacks.)  This algorithm is not deterministic,
 * but it allows the possibility of "verifying" a fingerprint.
 *
 * To generate fingerprints, use SecurePasswordHasher.getFingerprint().
 *
 * The other hasher is a simple MAC.  It is deterministic, but
 * the key is not saved, which means that it is not possible to
 * verify the hash later.  It is the more secure option of the two
 * because an attacker may only try to guess username/passwords by
 * querying the service while it is running.
 *
 * To generate MAC tags, user SecurePasswordHasher.getMac().
 *
 * #################################################################
 * WARNING:
 * This does not safely obfuscate weak passwords.  Only use if it
 * is absolutely necessary to store/log information about passwords,
 * and ensure that files with stored fingerprints have appropriate
 * permissions.
 * #################################################################
 *
 * Please see the RFC for more information on password hashing
 * security: http://tools.ietf.org/html/rfc2898.
 */
public class SecurePasswordHasher {

  private static final Logger LOGGER =
      Logger.getLogger(SecurePasswordHasher.class.getName());

  private static final SecureRandom prng = new SecureRandom();

  private static final int kNumSeedBytes = 16;
  private static final int kNumIterations = 1000;
  private static final int kNumOutputBits = 128;
  private static final String kHashAlgorithm = "PBKDF2WithHmacSHA1";
  private static Mac mac;

  static {
    // Initialize the MAC key.
    try {
      KeyGenerator kg = KeyGenerator.getInstance("HmacSHA1");
      mac = Mac.getInstance("HmacSHA1");
      mac.init(kg.generateKey());
    } catch (NoSuchAlgorithmException e) {
      LOGGER.log(Level.SEVERE, "Could not initialize MAC", e);
      mac = null;
    } catch (InvalidKeyException e) {
      LOGGER.log(Level.SEVERE, "Could not initialize MAC", e);
      mac = null;
    }
  }

  /**
   * We don't want this class to be instantiated.
   */
  private SecurePasswordHasher() {
  }

  /**
   * A container for a fingerprint specification.  From this fingerprint
   * specification, it should be easy to verify that a given password
   * generated the fingerprint.  Finding the password from the fingerprint
   * should be much more difficult as long as the password was "well-chosen".
   */
  public static class Fingerprint {
    private final String hash;
    private final String seed;
    private final String algorithm;
    private final int iterations;

    Fingerprint(String hash, String seed, String algorithm, int iterations) {
      this.hash = hash;
      this.seed = seed;
      this.algorithm = algorithm;
      this.iterations = iterations;
    }

    public String hash() {
      return hash;
    }

    public String seed() {
      return seed;
    }

    public String algorithm() {
      return algorithm;
    }

    public int iterations() {
      return iterations;
    }

    /**
     * Parses a Fingerprint object from a string.
     * @param fingerprint the output of toString() from a
     * Fingerprint object
     */
    public static Fingerprint parseFingerprint(String fingerprint)
        throws IllegalArgumentException {
      String[] parts = fingerprint.split(":");
      if (parts.length != 4) {
        throw new IllegalArgumentException(
            "Incorrectly formatted fingerprint: " + fingerprint);
      }

      int iterations;
      try {
        iterations = Integer.parseInt(parts[3]);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Could not parse number of " +
            "iterations from fingerprint: " + fingerprint);
      }

      return new Fingerprint(parts[0], parts[1], parts[2], iterations);
    }

    @Override
    public String toString() {
      return hash + ":" + seed + ":" + algorithm + ":" + iterations;
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Fingerprint)) {
        return false;
      }
      return toString().equals(((Fingerprint) obj).toString());
    }
  }

  /**
   * Produce a fingerprint from an input string.  This is non-deterministic
   * and will use safe defaults for the parameters (as of 05/2009).
   */
  public static synchronized Fingerprint getFingerprint(String input) {
    byte[] seed = new byte[kNumSeedBytes];

    // Randomly choose a seed.
    synchronized (prng) {
      prng.nextBytes(seed);
    }

    byte[] hash = hashString(input, kHashAlgorithm, kNumIterations, seed);
    return new Fingerprint(Base64.encode(hash), Base64.encode(seed),
        kHashAlgorithm, kNumIterations);
  }

  /**
   * Returns true iff the input is a valid producer of the given fingerprint.
   * @param input the candidate input string
   * @param fingerprint
   */
  public static boolean verifyFingerprint(String input, Fingerprint fingerprint) {
    byte[] seedBytes;
    try {
      seedBytes = Base64.decode(fingerprint.seed());
    } catch (Base64DecoderException e) {
      LOGGER.warning("Could not base64 decode input string: " + fingerprint.seed());
      return false;
    }

    byte[] hashBytes;
    try {
      hashBytes = Base64.decode(fingerprint.hash());
    } catch (Base64DecoderException e) {
      LOGGER.warning("Could not base64 decode input string: " + fingerprint.hash());
      return false;
    }
    return Arrays.equals(hashBytes, hashString(input, fingerprint.algorithm(),
        fingerprint.iterations(), seedBytes));
  }

  /**
   * Returns the MAC of the combination of the username and password.
   * The key used to MAC these messages is not saved, so this effectively
   * erases the threat of doing off-line brute force password-guessing.
   * If an attacker has access to the logs, they may mount an on-line
   * attack (querying many different known username/password combos to see
   * if they match unknown username/password MACs), but this is a much
   * more constrained environment.
   *
   * This function is deterministic, however, which enables debugging by
   * tracking that a particular username/password is being used in
   * multiple places or tracking how frequently a user logs in, etc.
   *
   * @param username required so an attacker cannot easily tell if
   * two users have the same password by looking at the logs
   * @param password password to mac
   */
  public static String getMac(String username, String password) {
    return Base64.encode(macInput(username, password));
  }

  /**
   * Run the input string through the PBKDF with the given parameters and
   * return the result.
   */
  private static byte[] hashString(String input, String algorithm,
                            int iterations, byte[] seed) {

    PBEKeySpec keySpec = new PBEKeySpec(input.toCharArray(), seed,
                                        iterations, kNumOutputBits);

    SecretKeyFactory factory;
    try {
      factory = SecretKeyFactory.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      LOGGER.log(Level.SEVERE, "Could not get key spec", e);
      return new byte[kNumSeedBytes];  // Don't reveal information about the password.
    }

    SecretKey hash;
    try {
      hash = factory.generateSecret(keySpec);
    } catch (InvalidKeySpecException e) {
      LOGGER.severe("Could not load hash algorithm: " + e);
      return new byte[kNumSeedBytes];  // Don't reveal information about the password.
    }
    return hash.getEncoded();
  }

  /**
   * Returns a one-way keyed function output for the given username and
   * password.
   *
   * @param username required so an attacker cannot easily tell if two users
   *     have the same password by looking at the logs
   * @param password password to mac
   */
  public static byte[] macInput(String username, String password) {
    if (mac == null) {
      LOGGER.severe("tried to MAC message when mac object uninitialized");
      return new byte[0];
    }
    return mac.doFinal((username + ":" + password).getBytes(Charsets.UTF_8));
  }

  /**
   * This provides a simple command-line tool to verify passwords.
   */
  public static void main(String[] args) throws Throwable {
    int numArgs = args.length;
    if (numArgs < 2 || numArgs > 3) {
      System.err.println("Usage: SecurePasswordHasher password [fingerprint]");
      System.exit(1);
    }
    if (numArgs == 2) {
      System.out.println(SecurePasswordHasher.getFingerprint(args[1])
                         .toString());
    } else {
      Fingerprint fingerprint = Fingerprint.parseFingerprint(args[2]);
      boolean verified =
          SecurePasswordHasher.verifyFingerprint(args[1], fingerprint);
      if (verified) {
        System.out.println("Password is correct");
      } else {
        System.out.println("Invalid password");
      }
    }

    System.exit(0);
  }
}
