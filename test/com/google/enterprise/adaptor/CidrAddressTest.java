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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Tests for {@link GsaVersion}. */
public class CidrAddressTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  void assertInRange(CidrAddress range, String address)
      throws UnknownHostException {
    InetAddress addr = InetAddress.getByName(address);
    assertTrue(range.isInRange(addr));
  }

  void assertNotInRange(CidrAddress range, String address)
      throws UnknownHostException {
    InetAddress addr = InetAddress.getByName(address);
    assertFalse(range.isInRange(addr));
  }
  
  void assertAddressEquals(InetAddress address, String addressStr)
      throws UnknownHostException {
    assertEquals(address, InetAddress.getByName(addressStr));
  }

  // IPv4 tests
  @Test
  public void testValidMostSignificantBitOne() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("192.168.0.1");
    CidrAddress addr = new CidrAddress(address, 16);
    assertEquals("192.168.0.1/16 [192.168.0.1 - 192.168.255.254]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "192.168.255.255");
    assertAddressEquals(addr.networkAddress(), "192.168.0.0");
    assertAddressEquals(addr.firstAddress(), "192.168.0.1");
    assertAddressEquals(addr.lastAddress(), "192.168.255.254");
    assertAddressEquals(addr.networkMask(), "255.255.0.0");
    assertInRange(addr, "192.168.0.1");
    assertInRange(addr, "192.168.255.254");
    assertInRange(addr, "192.168.127.127");
    assertNotInRange(addr, "192.167.255.255");  // just below the range
    assertNotInRange(addr, "192.169.0.0");  // just above the range
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "192.168.255.255"); // broadcast
    // not longer can test addr.isInRange("1.2.3.4.5");
  }

  @Test
  public void testValidMostSignificantBitZero() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("63.168.0.1");
    CidrAddress addr = new CidrAddress(address, 8);
    assertEquals("63.168.0.1/8 [63.0.0.1 - 63.255.255.254]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "63.255.255.255");
    assertAddressEquals(addr.networkAddress(), "63.0.0.0");
    assertAddressEquals(addr.firstAddress(), "63.0.0.1");
    assertAddressEquals(addr.lastAddress(), "63.255.255.254");
    assertAddressEquals(addr.networkMask(), "255.0.0.0");
    assertInRange(addr, "63.0.0.1");
    assertInRange(addr, "63.255.255.254");
    assertInRange(addr, "63.111.123.234");
    assertNotInRange(addr, "63.0.0.0");  // network
    assertNotInRange(addr, "63.255.255.255");  // broadcast
    assertNotInRange(addr, "62.255.255.255");  // just below the range
    assertNotInRange(addr, "64.0.0.0");  // just above the range
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "255.255.255.255");
  }

  @Test
  public void testValidMostSignificantByteZero() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("0.15.0.1");
    CidrAddress addr = new CidrAddress(address, 20);
    assertEquals("0.15.0.1/20 [0.15.0.1 - 0.15.15.254]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "0.15.15.255");
    assertAddressEquals(addr.networkAddress(), "0.15.0.0");
    assertAddressEquals(addr.firstAddress(), "0.15.0.1");
    assertAddressEquals(addr.lastAddress(), "0.15.15.254");
    assertAddressEquals(addr.networkMask(), "255.255.240.0");
    assertInRange(addr, "0.15.0.1");
    assertInRange(addr, "0.15.15.254");
    assertInRange(addr, "0.15.7.127");
    assertNotInRange(addr, "0.15.0.0"); // network
    assertNotInRange(addr, "0.15.15.255"); // broadcast
    assertNotInRange(addr, "0.14.255.255");  // just below the range
    assertNotInRange(addr, "0.16.0.0");  // just above the range
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "255.255.255.255");
  }

  @Test
  public void testValidSubnetWithinClassC() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("10.10.10.75");
    CidrAddress addr = new CidrAddress(address, 27);
    assertEquals("10.10.10.75/27 [10.10.10.65 - 10.10.10.94]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "10.10.10.95");
    assertAddressEquals(addr.networkAddress(), "10.10.10.64");
    assertAddressEquals(addr.firstAddress(), "10.10.10.65");
    assertAddressEquals(addr.lastAddress(), "10.10.10.94");
    assertAddressEquals(addr.networkMask(), "255.255.255.224");
    assertInRange(addr, "10.10.10.72");
    assertInRange(addr, "10.10.10.94");
    assertInRange(addr, "10.10.10.65");
    assertNotInRange(addr, "10.10.10.64"); // network
    assertNotInRange(addr, "10.10.10.95"); // broadcast
    assertNotInRange(addr, "10.10.10.63");  // just below the range
    assertNotInRange(addr, "10.10.10.96");  // just above the range
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "255.255.255.255");
  }

  @Test
  public void testValidAllBitsMasked() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("192.168.0.1");
    CidrAddress addr = new CidrAddress(address, 32);
    assertEquals("192.168.0.1/32 [192.168.0.1 - 192.168.0.1]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "192.168.0.1");
    assertAddressEquals(addr.networkAddress(), "192.168.0.1");
    assertAddressEquals(addr.firstAddress(), "192.168.0.1");
    assertAddressEquals(addr.lastAddress(), "192.168.0.1");
    assertAddressEquals(addr.networkMask(), "255.255.255.255");
    assertInRange(addr, "192.168.0.1");
    assertNotInRange(addr, "192.168.0.0");  // just below the range
    assertNotInRange(addr, "192.168.0.2");  // just above the range
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "255.255.255.255");
  }

  @Test
  public void testValidNoBitsMasked() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("192.168.0.1");
    CidrAddress addr = new CidrAddress(address, 0);
    assertEquals("192.168.0.1/0 [0.0.0.1 - 255.255.255.254]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "255.255.255.255");
    assertAddressEquals(addr.networkAddress(), "0.0.0.0");
    assertAddressEquals(addr.firstAddress(), "0.0.0.1");
    assertAddressEquals(addr.lastAddress(), "255.255.255.254");
    assertAddressEquals(addr.networkMask(), "0.0.0.0");
    assertInRange(addr, "0.0.0.1");
    assertInRange(addr, "127.127.127.127");
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "255.255.255.255");
  }

  @Test
  public void testInvalidNegativeBits() throws UnknownHostException {
    thrown.expect(IllegalArgumentException.class);
    InetAddress address = InetAddress.getByName("192.168.0.1");
    new CidrAddress(address, -1);
  }

  @Test
  public void testInvalidTooManyBits() throws UnknownHostException {
    thrown.expect(IllegalArgumentException.class);
    InetAddress address = InetAddress.getByName("192.168.0.1");
    new CidrAddress(address, 33);
  }

  // IPv6 tests
  @Test
  public void testValidV6MostSignificantBitOne() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("9876::");
    CidrAddress addr = new CidrAddress(address, 112);
    assertEquals("9876:0:0:0:0:0:0:0/112 [9876:0:0:0:0:0:0:1 - "
        + "9876:0:0:0:0:0:0:fffe]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "9876::ffff");
    assertAddressEquals(addr.networkAddress(), "9876::0");
    assertAddressEquals(addr.firstAddress(), "9876::1");
    assertAddressEquals(addr.lastAddress(), "9876::fffe");
    assertAddressEquals(addr.networkMask(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:0");
    assertInRange(addr, "9876::0001");
    assertInRange(addr, "9876::fffe");
    assertInRange(addr, "9876::7fff");
    // just below (and then just above) the range
    assertNotInRange(addr, "9876::0");
    assertNotInRange(addr, "9876::ffff");
    assertNotInRange(addr, "9875:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
    assertNotInRange(addr, "9876::1:0");
    assertNotInRange(addr, "0::");
    assertNotInRange(addr, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
  }

  @Test
  public void testValidV6MostSignificantBitZero() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("7654::");
    CidrAddress addr = new CidrAddress(address, 120);
    assertEquals("7654:0:0:0:0:0:0:0/120 [7654:0:0:0:0:0:0:1 - "
        + "7654:0:0:0:0:0:0:fe]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "7654::00ff");
    assertAddressEquals(addr.networkAddress(), "7654::0000");
    assertAddressEquals(addr.firstAddress(), "7654::0001");
    assertAddressEquals(addr.lastAddress(), "7654::00fe");
    assertAddressEquals(addr.networkMask(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ff00");
    assertInRange(addr, "7654::0001");
    assertInRange(addr, "7654::00fe");
    assertInRange(addr, "7654::007f");
    assertNotInRange(addr, "7654::0000"); // network
    assertNotInRange(addr, "7654::00ff"); // broadcast
    // just below (and then just above) the range
    assertNotInRange(addr, "7653:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
    assertNotInRange(addr, "7654::0:0100");
    assertNotInRange(addr, "0::");
    assertNotInRange(addr, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
  }

  @Test
  public void testValidV6MostSignificantByteZero() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("0:7654::");
    CidrAddress addr = new CidrAddress(address, 124);
    assertEquals("0:7654:0:0:0:0:0:0/124 [0:7654:0:0:0:0:0:1 - "
        + "0:7654:0:0:0:0:0:e]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "0:7654::000f");
    assertAddressEquals(addr.networkAddress(), "0:7654::0000");
    assertAddressEquals(addr.firstAddress(), "0:7654::0001");
    assertAddressEquals(addr.lastAddress(), "0:7654::000e");
    assertAddressEquals(addr.networkMask(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:fff0");
    assertInRange(addr, "0:7654::0001");
    assertInRange(addr, "0:7654::000e");
    assertInRange(addr, "0:7654::0007");
    assertNotInRange(addr, "0:7654::0000"); // network
    assertNotInRange(addr, "0:7654::000f"); // broadcast
    // just below (and then just above) the range
    assertNotInRange(addr, "0:7653:ffff:ffff:ffff:ffff:ffff:ffff");
    assertNotInRange(addr, "0:7654::0:0010");
    assertNotInRange(addr, "0::");
    assertNotInRange(addr, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
  }

  @Test
  public void testValidV6AllBitsMasked() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("0:7654::");
    CidrAddress addr = new CidrAddress(address, 128);
    assertEquals("0:7654:0:0:0:0:0:0/128 [0:7654:0:0:0:0:0:0 - "
        + "0:7654:0:0:0:0:0:0]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "0:7654::0");
    assertAddressEquals(addr.networkAddress(), "0:7654::0");
    assertAddressEquals(addr.firstAddress(), "0:7654::0");
    assertAddressEquals(addr.lastAddress(), "0:7654::0");
    assertAddressEquals(addr.networkMask(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
    assertInRange(addr, "0:7654::0000");
    // just below the range
    assertNotInRange(addr, "0:7653:ffff:ffff:ffff:ffff:ffff:ffff");
    assertNotInRange(addr, "0:7654::1"); // just above the range
    assertNotInRange(addr, "0::");
    assertNotInRange(addr, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
  }

  @Test
  public void testValidV6NoBitsMasked() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("0:7654::");
    CidrAddress addr = new CidrAddress(address, 0);
    assertEquals("0:7654:0:0:0:0:0:0/0 [0:0:0:0:0:0:0:1 - "
        + "ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe]", addr.toString());
    assertAddressEquals(addr.broadcastAddress(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
    assertAddressEquals(addr.networkAddress(), "0::0");
    assertAddressEquals(addr.firstAddress(), "0::1");
    assertAddressEquals(addr.lastAddress(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe");
    assertInRange(addr, "7fff:7fff:7fff:7fff:7fff:7fff:7fff:7fff");
    assertInRange(addr, "0::1");
    assertInRange(addr, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe");
    assertNotInRange(addr, "0::0");
    assertNotInRange(addr, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
  }

  @Test
  public void testInvalidV6NegativeBits() throws UnknownHostException {
    thrown.expect(IllegalArgumentException.class);
    InetAddress address = InetAddress.getByName("0:7654::");
    new CidrAddress(address, -1);
  }

  @Test
  public void testInvalidV6TooManyBits() throws UnknownHostException {
    thrown.expect(IllegalArgumentException.class);
    InetAddress address = InetAddress.getByName("0:7654::");
    new CidrAddress(address, 129);
  }
}
