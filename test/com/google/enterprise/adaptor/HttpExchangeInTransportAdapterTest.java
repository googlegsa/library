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

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

/**
 * Test cases for {@link HttpExchangeInTransportAdapter}.
 */
public class HttpExchangeInTransportAdapterTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private HttpExchangeInTransportAdapter inTransport
      = new HttpExchangeInTransportAdapter(
          new MockHttpExchange("http", "GET", "/", null));

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
  public void testGetParameterValue() {
    thrown.expect(UnsupportedOperationException.class);
    inTransport.getParameterValue(null);
  }

  @Test
  public void testGetParameterValues() {
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
