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

package adaptorlib;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

/**
 * Test cases for {@link HttpExchangeOutTransportAdapter}.
 */
public class HttpExchangeOutTransportAdapterTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private HttpExchangeOutTransportAdapter outTransport
      = new HttpExchangeOutTransportAdapter(
          new MockHttpExchange("http", "GET", "/", null));

  @Test
  public void testGetAttribute() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getAttribute(null);
  }

  @Test
  public void testGetLocalCredential() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getLocalCredential();
  }

  @Test
  public void testGetPeerCredential() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getPeerCredential();
  }

  @Test
  public void testSetConfidential() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.setConfidential(true);
  }

  @Test
  public void testSetIntegrityProtected() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.setIntegrityProtected(true);
  }

  @Test
  public void testSetAttribute() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.setAttribute(null, null);
  }

  @Test
  public void testAddParameter() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.addParameter(null, null);
  }

  @Test
  public void testSetStatusCode() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.setStatusCode(0);
  }

  @Test
  public void testSetVersion() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.setVersion(null);
  }

  @Test
  public void testGetHeaderValue() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getHeaderValue(null);
  }

  @Test
  public void testGetHttpMethod() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getHTTPMethod();
  }

  @Test
  public void testGetParameterValue() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getParameterValue(null);
  }

  @Test
  public void testGetParameterValues() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getParameterValues(null);
  }

  @Test
  public void testGetStatusCode() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getStatusCode();
  }

  @Test
  public void testGetVersion() {
    thrown.expect(UnsupportedOperationException.class);
    outTransport.getVersion();
  }
}
