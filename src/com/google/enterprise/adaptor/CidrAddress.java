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
import java.util.Arrays;

/**
 * A class to specify a range of IP Addresses (IPv4 or IPv6) via CIDR notation.
 * For example, "192.168.0.1/24" is parsed as address 128.168.0.1, with
 * netmask of length 24 (bits).  This means that the most significant 24 bits
 * (the 192.168.0) must be kept, and the other bits (32-24 = 8 least significant
 * bits) may take on any value. 192.168.0.1 - 192.168.0.254 are valid IP addresses for the given 
 * range, while 192.168.0.0 is the network address and 192.168.0.255 is the broadcast address.
 *
 * This class can determine if a designated IP address falls within the IP subnet.
 * It uses {@code BigInteger}s to hold the start and end address of the range,
 * because the {@code BigInteger.compareTo} method makes it easy to verify
 * whether or not firstAddress <= arbitrary address <= lastAddress.
 */

class CidrAddress {

  /** The lowest address in our IP subnet.  For the given example, calling
   * {@code toByteArray()} would give [0, 192, 168, 0, 0] -- the first zero byte
   * to hold the sign (so that it wouldn't be taken as a negative number). */
  private BigInteger network;

  /** The highest address in our range.  For the given example, calling
   * {@code toByteArray()} would give [0, 192, 168, 0, 255]. */
  private BigInteger broadcast;

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
    BigInteger netMask = calculateNetmask(netmaskLength, addressSize);
    // make a positive integer of our original address [0, 192, 168, 0, 1]
    BigInteger givenAddr = new BigInteger(1, inetAddress.getAddress());
    // gives [0, 198, 168, 0, 0] for our example - non-fixed bits forced to 0.
    network = givenAddr.and(netMask);
    // mask.not() toggles the 0 and 1 bits, 0000 0000 0000 1111 for our example.
    // so adding the resulting "number" to our start address gives the ending
    // address.
    broadcast = network.add(netMask.not());
  }

  /**
   * Use {@code BigArray.compareTo} to check whether or not a given
   * {@code address} falls in the range firstAddress .. lastAddress.
   */
  public boolean isInRange(InetAddress address) {
    BigInteger addr = new BigInteger(1, address.getAddress());

    // ensure networkAddress < ipAddress < endAddress
    // for subnets w/o network and broadcast addresses, <= is permitted too
    int assertion = hasBroadcastAndNetworkAddresses() ? 0 : 1;
    return (network.compareTo(addr) < assertion)
        && (addr.compareTo(broadcast) < assertion);
  }

  public InetAddress networkAddress() {
    return toInetAddress(network);
  }
  
  public InetAddress networkMask() {
    return toNetmask(calculateNetmask(netmaskLength, addressSize));
  }

  public InetAddress firstAddress() {
    BigInteger firstAddress =
        hasBroadcastAndNetworkAddresses() ? network.add(BigInteger.ONE) : network;
    return toInetAddress(firstAddress);
  }

  public InetAddress lastAddress() {
    BigInteger lastAddress =
        hasBroadcastAndNetworkAddresses() ? broadcast.subtract(BigInteger.ONE) : broadcast;
    return toInetAddress(lastAddress);
  }

  public InetAddress broadcastAddress() {
    return toInetAddress(broadcast);
  }

  // For single-host and /31 (v4), /127 (v6) subnets there are no broadcast and network addresses
  public boolean hasBroadcastAndNetworkAddresses() {
    return (addressSize == 4 && netmaskLength <= 30)
        || (addressSize == 16 && netmaskLength <= 126);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(inetAddress.getHostAddress() + "/" + netmaskLength);
    sb.append(" [").append(firstAddress().getHostAddress());
    sb.append(" - ");
    sb.append(lastAddress().getHostAddress()).append("]");
    return sb.toString();
  }

  private BigInteger calculateNetmask(int netmaskLength, int addressSize) {
    byte[] maskBytes = new byte[addressSize];
    Arrays.fill(maskBytes, (byte) 0xFF);
    // create a BigInteger representing 2^32 for IPv4, 2^128 for IPv6
    // (you can think of BigInteger being a base-256 number, as far as the
    // byte array parameter goes.  The "1" first parameter means "treat as a
    // positive number."  Otherwise, a number where the most significant bit of
    // the most significant byte is set, would be treated as a negative number).
    BigInteger mask = new BigInteger(1, maskBytes).not();
    // now shift it right by the number of bits that are fixed, leaving
    // 2^(degrees of freedom).  Shifting it right leads to leading "1" bits,
    // 1111 1111 1111 0000 for our /24 example.
    return mask.shiftRight(netmaskLength);
  }

  private InetAddress toInetAddress(BigInteger address) {
    byte[] addressbytes = new byte[addressSize];
    toByteArray(address, addressbytes);
    return toInetAddress(addressbytes);
  }
  
  private InetAddress toNetmask(BigInteger address) {
    byte[] addressbytes = new byte[addressSize];
    Arrays.fill(addressbytes, (byte)0xff);
    toByteArray(address, addressbytes);
    return toInetAddress(addressbytes);
  }

  private InetAddress toInetAddress(byte[] addressbytes) {
    try {
      return InetAddress.getByAddress(addressbytes);
    } catch (UnknownHostException e) {
      // should not happen, as address and netmask are validated in constructor
      throw new IllegalArgumentException("Object initialized with invalid values: "
          + inetAddress + "/" + netmaskLength);
    }
  }

  private void toByteArray(BigInteger address, byte[] dst) {
    /**
     * The complicated part about {@code BigInteger.toByteArray()} is that the number of bytes you
     * get back is not always the number of bytes you put in. When the most significant bit of a
     * positive number is 1, you get back an extra "0" byte in front (so that it's not treated as a
     * negative number. And if enough significant bits are 0, you get back fewer bytes than you put
     * in. We definitely want 4 bytes for an IPv4 address or 16 for an IPv6 address - anything else
     * causes the {@code InetAddress.getByAddress()} call to fail. So we allocate 4 or 16 zero bytes
     * into an array of bytes, then use {@code System.arrayCopy} to copy from the array of bytes
     * returned from {@code BigInteger.toByteArray()} into that array. We do this for both addresses
     * (first in our range, last in our range).
     */
    byte[] inputBytes = address.toByteArray();
    if (inputBytes.length < dst.length) {
      System.arraycopy(
          inputBytes, 0, dst, addressSize - inputBytes.length, inputBytes.length);
    } else {
      System.arraycopy(inputBytes, inputBytes.length - addressSize, dst, 0, addressSize);
    }
  }
}
