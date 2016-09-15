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
import static org.junit.Assert.fail;

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

  // IPv4 tests
  @Test
  public void testValidMostSignificantBitOne() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("192.168.0.1");
    CidrAddress addr = new CidrAddress(address, 16);
    assertEquals("192.168.0.1/16 [192.168.0.0 - 192.168.255.255]",
        addr.toString());
    assertInRange(addr, "192.168.0.0");
    assertInRange(addr, "192.168.255.255");
    assertInRange(addr, "192.168.127.127");
    assertNotInRange(addr, "192.167.255.255");  // just below the range
    assertNotInRange(addr, "192.169.0.0");  // just above the range
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "255.255.255.255");
    // not longer can test addr.isInRange("1.2.3.4.5");
  }

  @Test
  public void testValidMostSignificantBitZero() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("63.168.0.1");
    CidrAddress addr = new CidrAddress(address, 8);
    assertEquals("63.168.0.1/8 [63.0.0.0 - 63.255.255.255]", addr.toString());
    assertInRange(addr, "63.0.0.0");
    assertInRange(addr, "63.255.255.255");
    assertInRange(addr, "63.111.123.234");
    assertNotInRange(addr, "62.255.255.255");  // just below the range
    assertNotInRange(addr, "64.0.0.0");  // just above the range
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "255.255.255.255");
  }

  @Test
  public void testValidMostSignificantByteZero() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("0.15.0.1");
    CidrAddress addr = new CidrAddress(address, 20);
    assertEquals("0.15.0.1/20 [0.15.0.0 - 0.15.15.255]", addr.toString());
    assertInRange(addr, "0.15.0.0");
    assertInRange(addr, "0.15.15.255");
    assertInRange(addr, "0.15.7.127");
    assertNotInRange(addr, "0.14.255.255");  // just below the range
    assertNotInRange(addr, "0.16.0.0");  // just above the range
    assertNotInRange(addr, "0.0.0.0");
    assertNotInRange(addr, "255.255.255.255");
  }

  @Test
  public void testValidAllBitsMasked() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("192.168.0.1");
    CidrAddress addr = new CidrAddress(address, 32);
    assertEquals("192.168.0.1/32 [192.168.0.1 - 192.168.0.1]", addr.toString());
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
    assertEquals("192.168.0.1/0 [0.0.0.0 - 255.255.255.255]", addr.toString());
    assertInRange(addr, "0.0.0.0");
    assertInRange(addr, "255.255.255.255");
    assertInRange(addr, "127.127.127.127");
  }

  @Test
  public void testInvalidNegativeBits() throws UnknownHostException {
    thrown.expect(IllegalArgumentException.class);
    InetAddress address = InetAddress.getByName("192.168.0.1");
    CidrAddress addr = new CidrAddress(address, -1);
  }

  @Test
  public void testInvalidTooManyBits() throws UnknownHostException {
    thrown.expect(IllegalArgumentException.class);
    InetAddress address = InetAddress.getByName("192.168.0.1");
    CidrAddress addr = new CidrAddress(address, 33);
  }

  // IPv6 tests
  @Test
  public void testValidV6MostSignificantBitOne() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("9876::");
    CidrAddress addr = new CidrAddress(address, 112);
    assertEquals("9876:0:0:0:0:0:0:0/112 [9876:0:0:0:0:0:0:0 - "
        + "9876:0:0:0:0:0:0:ffff]", addr.toString());
    assertInRange(addr, "9876::0000");
    assertInRange(addr, "9876::ffff");
    assertInRange(addr, "9876::7fff");
    // just below (and then just above) the range
    assertNotInRange(addr, "9875:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
    assertNotInRange(addr, "9876::1:0");
    assertNotInRange(addr, "0::");
    assertNotInRange(addr, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
  }

  @Test
  public void testValidV6MostSignificantBitZero() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("7654::");
    CidrAddress addr = new CidrAddress(address, 120);
    assertEquals("7654:0:0:0:0:0:0:0/120 [7654:0:0:0:0:0:0:0 - "
        + "7654:0:0:0:0:0:0:ff]", addr.toString());
    assertInRange(addr, "7654::0000");
    assertInRange(addr, "7654::00ff");
    assertInRange(addr, "7654::007f");
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
    assertEquals("0:7654:0:0:0:0:0:0/124 [0:7654:0:0:0:0:0:0 - "
        + "0:7654:0:0:0:0:0:f]", addr.toString());
    assertInRange(addr, "0:7654::0000");
    assertInRange(addr, "0:7654::000f");
    assertInRange(addr, "0:7654::0007");
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
    assertEquals("0:7654:0:0:0:0:0:0/0 [0:0:0:0:0:0:0:0 - "
        + "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff]", addr.toString());
    assertInRange(addr, "7fff:7fff:7fff:7fff:7fff:7fff:7fff:7fff");
    assertInRange(addr, "0::0");
    assertInRange(addr, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
  }

  @Test
  public void testInvalidV6NegativeBits() throws UnknownHostException {
    thrown.expect(IllegalArgumentException.class);
    InetAddress address = InetAddress.getByName("0:7654::");
    CidrAddress addr = new CidrAddress(address, -1);
  }

  @Test
  public void testInvalidV6TooManyBits() throws UnknownHostException {
    thrown.expect(IllegalArgumentException.class);
    InetAddress address = InetAddress.getByName("0:7654::");
    CidrAddress addr = new CidrAddress(address, 129);
  }
}
