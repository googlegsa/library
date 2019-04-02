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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;

/**
 * Test cases for {@link HttpExchangeInTransportAdapter}.
 */
public class HttpExchangeInTransportAdapterTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private HttpExchangeInTransportAdapter inTransport
      = new HttpExchangeInTransportAdapter(
          new MockHttpExchange("GET", "/", null));

  @Test
  public void testGetAttribute() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getAttribute(null);
  }

  @Test
  public void testGetCharacterEncoding() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getCharacterEncoding();
  }

  @Test
  public void testGetLocalCredential() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getLocalCredential();
  }

  @Test
  public void testGetPeerCredential() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getPeerCredential();
  }

  @Test
  public void testIsHttps() {
    assertFalse(inTransport.isConfidential());
    assertFalse(inTransport.isIntegrityProtected());

    inTransport = new HttpExchangeInTransportAdapter(
          new MockHttpExchange("GET", "/", null), true);
    assertTrue(inTransport.isConfidential());
    assertTrue(inTransport.isIntegrityProtected());
  }

  @Test
  public void testSetAuthenticated() {
    assertFalse(inTransport.isAuthenticated());
    inTransport.setAuthenticated(true);
    assertTrue(inTransport.isAuthenticated());
    inTransport.setAuthenticated(false);
    assertFalse(inTransport.isAuthenticated());
  }

  @Test
  public void testSetConfidential() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.setConfidential(true);
  }

  @Test
  public void testSetIntegrityProtected() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.setIntegrityProtected(true);
  }

  @Test
  public void testGetPeerAddress() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getPeerAddress();
  }

  @Test
  public void testGetPeerDomainName() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getPeerDomainName();
  }

  @Test
  public void testGetParameterValuesNone() {
    inTransport = new HttpExchangeInTransportAdapter(
        new MockHttpExchange("GET", "/", null));
    assertEquals(null, inTransport.getParameterValues("p"));
    assertEquals(null, inTransport.getParameterValue("p"));
  }

  @Test
  public void testGetParameterValuesBasic() {
    inTransport = new HttpExchangeInTransportAdapter(
        new MockHttpExchange("GET", "/?p=1&abc=def&p=%3a%3B%26%C3%BC&p",
          null));
    assertEquals(Arrays.asList("1", ":;&ü", ""),
        inTransport.getParameterValues("p"));
    assertEquals("1", inTransport.getParameterValue("p"));
  }

  @Test
  public void testGetParameterValuesPost() {
    inTransport = new HttpExchangeInTransportAdapter(
        new MockHttpExchange("POST", "/", null));
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getParameterValues(null);
  }

  @Test
  public void testGetStatusCode() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getStatusCode();
  }

  @Test
  public void testGetVersion() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getVersion();
  }

}
