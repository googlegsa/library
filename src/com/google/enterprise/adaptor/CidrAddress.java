// Copyright 2016 Google Inc. All Rights Reserved.
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

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

/**
 * A class to specify a range of IP Addresses (IPv4 or IPv6) via CIDR notation.
 * For example, "192.168.0.1/24" is parsed as address 128.168.0.1, with
 * netmask of length 24 (bits).  This means that the most significant 24 bits
 * (the 192.168.0) must be kept, and the other bits (32-24 = 8 least significant
 * bits) may take on any value.  Thus, 192.168.0.0 - 192.168.0.255 are all
 * valid addresses for the given range.
 *
 * This class can determine if a designated IP address falls within the range.
 * It uses {@code BigInteger}s to hold the start and end address of the range,
 * because the {@code BigInteger.compareTo} method makes it each to verify
 * whether or not startAddress <= arbitrary address <= endAddress (by confirming
 * that {@code (start.compareTo(addr) < 1) && (addr.compareTo(end) < 1)}).
 */

class CidrAddress {
  private static final Logger log
      = Logger.getLogger(CidrAddress.class.getName());

  /** The lowest address in our range.  For the given example, calling
   * {@code toByteArray()} would give [0, 192, 168, 0, 0] -- the first zero byte
   * to hold the sign (so that it wouldn't be taken as a negative number. */
  private BigInteger startAddress;

  /** The highest address in our range.  For the given example, calling
   * {@code toByteArray()} would give [0, 192, 168, 0, 255]. */
  private BigInteger endAddress;

  /** The address specified in our constructor. 192.168.0.1 for our example. */
  private InetAddress inetAddress;

  /** The netmask length specified in our constructor. 16 for our example. */
  private final int netmaskLength;

  /** The size of the address (in bytes) - should be 4 (IPv4) or 16 (IPv6). */
  private final int addressSize;

  /**
   * Stores the range of IP addresses of the CIDR-specified address
   * (address/netmaskLength).
   *
   * @throws IllegalArgumentException if the netmaskLength is too large for
   * the given address.
   */
  public CidrAddress(InetAddress inetAddress, int netmaskLength) {
    this.inetAddress = inetAddress;
    this.netmaskLength = netmaskLength;
    int bufferSize = inetAddress.getAddress().length;  // 4 (IPv4) or 16 (IPv6)
    if (bufferSize != 4 && bufferSize != 16) {
      throw new IllegalArgumentException("wrong address size for "
          + inetAddress);
    }
    this.addressSize = bufferSize;
    if ((netmaskLength < 0) || (bufferSize == 4 && netmaskLength > 32)
        || (bufferSize == 16 && netmaskLength > 128)) {
      throw new IllegalArgumentException("invalid netmask length for address "
          + inetAddress + ": " + netmaskLength);
    }
    byte[] maskBytes = new byte[bufferSize];
    for (int i = 0; i < bufferSize; i++) {
      maskBytes[i] = (byte) 0xff;
    }
    // create a BigInteger representing 2^32 for IPv4, 2^128 for IPv6
    // (you can think of BigInteger being a base-256 number, as far as the
    // byte array parameter goes.  The "1" first parameter means "treat as a
    // positive number."  Otherwise, a number where the most significant bit of
    // the most significant byte is set, would be treated as a negative number).
    BigInteger mask = new BigInteger(1, maskBytes).not();
    // now shift it right by the number of bits that are fixed, leaving
    // 2^(degrees of freedom).  Shifting it right leads to leading "1" bits,
    // 1111 1111 1111 0000 for our /24 example.
    mask = mask.shiftRight(netmaskLength);
    // make a positive integer of our original address [0, 192, 168, 0, 1]
    BigInteger givenAddr = new BigInteger(1, inetAddress.getAddress());
    // gives [0, 198, 168, 0, 0] for our example - non-fixed bits forced to 0.
    startAddress = givenAddr.and(mask);
    // mask.not() toggles the 0 and 1 bits, 0000 0000 0000 1111 for our example.
    // so adding the resulting "number" to our start address gives the ending
    // address.
    endAddress = startAddress.add(mask.not());
  }

  /**
   * Use {@code BigArray.compareTo} to check whether or not a given
   * {@code address} falls in the range startAddress .. endAddress.
   */
  public boolean isInRange(InetAddress address) {
    BigInteger addr = new BigInteger(1, address.getAddress());

    // ensure startAddress <= ipAddress <= endAddress
    return (startAddress.compareTo(addr) < 1)
        && (addr.compareTo(endAddress) < 1);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(inetAddress.getHostAddress() + "/" + netmaskLength);
    sb.append(" [");
    /**
     * The complicated part about {@code BigInteger.toByteArray()} is that
     * the number of bytes you get back is not always the number of bytes you
     * put in.  When the most significant bit of a positive number is 1, you
     * get back an extra "0" byte in front (so that it's not treated as a
     * negative number.  And if enough significant bits are 0, you get back
     * fewer bytes than you put in.  We definitely want 4 bytes for an IPv4
     * address or 16 for an IPv6 address - anything else causes the
     * {@code InetAddress.getByAddress()} call to fail.  So we allocate 4 or 16
     * zero bytes into an array of bytes, then use {@code System.arrayCopy}
     * to copy from the array of bytes returned from
     * {@code BigInteger.toByteArray()} into that array.  We do this for both
     * addresses (first in our range, last in our range).
     */
    try {
      byte[] firstBytes = new byte[addressSize];
      byte[] startBytes = startAddress.toByteArray();
      if (startBytes.length < addressSize) {
        System.arraycopy(startBytes, 0, firstBytes,
            addressSize - startBytes.length, startBytes.length);
      } else {
        System.arraycopy(startBytes, startBytes.length - addressSize,
            firstBytes, 0, addressSize);
      }
      InetAddress first = InetAddress.getByAddress(firstBytes);
      sb.append(first.getHostAddress());
    } catch (UnknownHostException uhe) {
      sb.append(uhe);
    }
    sb.append(" - ");
    try {
      byte[] lastBytes = new byte[addressSize];
      byte[] endBytes = endAddress.toByteArray();
      if (endBytes.length < addressSize) {
        System.arraycopy(endBytes, 0, lastBytes, addressSize - endBytes.length,
            endBytes.length);
      } else {
        System.arraycopy(endBytes, endBytes.length - addressSize,
            lastBytes, 0, addressSize);
      }
      InetAddress last = InetAddress.getByAddress(lastBytes);
      sb.append(last.getHostAddress());
    } catch (UnknownHostException uhe) {
      sb.append(uhe);
    }
    sb.append("]");
    return sb.toString();
  }
}
