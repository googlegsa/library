// Copyright 2011 Google Inc. All Rights Reserved.
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

import com.sun.net.httpserver.*;

import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;

/**
 * Tests for {@link AdministratorSecurityHandler}.
 */
public class AdministratorSecurityHandlerTest {
  private SessionManager<HttpExchange> sessionManager
      = new SessionManager<HttpExchange>(new MockTimeProvider(),
          new SessionManager.HttpExchangeClientStore(), 10000, 1000);
  private MockHttpHandler mockHandler = new MockHttpHandler();
  private AdministratorSecurityHandler handler
      = new AdministratorSecurityHandler("localhost", Charset.forName("UTF-8"),
          mockHandler, sessionManager, new MockAuthnClient());
  private MockHttpExchange ex = new MockHttpExchange("http", "POST", "/",
      new MockHttpContext(handler, "/"));

  @Test
  public void testGet() throws Exception {
    ex = new MockHttpExchange("http", "GET", "/",
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
  }

  @Test
  public void testInvalidRequest() throws Exception {
    ex.setRequestBody("not=expected&really-unexpected".getBytes("UTF-8"));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
  }

  @Test
  public void testIOException() throws Exception {
    ex.setRequestBody(new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException();
      }
    });
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
  }

  @Test
  public void testPasswordOnly() throws Exception {
    ex.setRequestBody("password=pass".getBytes("UTF-8"));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
  }

  @Test
  public void testUsernameOnly() throws Exception {
    ex.setRequestBody("username=user".getBytes("UTF-8"));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
  }

  @Test
  public void testInvalidCredentials() throws Exception {
    ex.setRequestBody("username=wrong&password=wrong".getBytes("UTF-8"));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
  }

  @Test
  public void testInvalidTestErrorCredentials() throws Exception {
    ex.setRequestBody("username=cause&password=error".getBytes("UTF-8"));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
  }

  @Test
  public void testValidCredentials() throws Exception {
    ex.setRequestBody("username=user&password=pass".getBytes("UTF-8"));
    handler.handle(ex);
    assertEquals(303, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
    String cookie = ex.getResponseHeaders().getFirst("Set-Cookie")
        .split(";")[0];

    ex = new MockHttpExchange("http", "GET", "/",
        new MockHttpContext(handler, "/"));
    ex.getRequestHeaders().set("Cookie", cookie);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals(1, mockHandler.getTimesExecuted());
  }

  @Test
  public void testPreexistingSession() throws Exception {
    HttpExchange tmpEx = new MockHttpExchange("http", "GET", "/",
      new MockHttpContext(handler, "/"));
    // Create session
    sessionManager.getSession(tmpEx, true);
    String cookie = tmpEx.getResponseHeaders().getFirst("Set-Cookie")
        .split(";")[0];

    ex.getRequestHeaders().set("Cookie", cookie);
    assertNotNull(sessionManager.getSession(ex, false));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
    assertEquals(0, mockHandler.getTimesExecuted());
  }

  private class MockAuthnClient implements
      AdministratorSecurityHandler.AuthnClient {
    @Override
    public AuthzStatus authn(String username, String password) {
      if ("user".equals(username) && "pass".equals(password)) {
        return AuthzStatus.PERMIT;
      } else if ("cause".equals(username) && "error".equals(password)) {
        return AuthzStatus.INDETERMINATE;
      } else {
        return AuthzStatus.DENY;
      }
    }
  }
}
