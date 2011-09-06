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

import com.sun.net.httpserver.*;

import java.util.*;

/**
 * Mock {@link HttpContext}.
 */
public class MockHttpContext extends HttpContext {
  private final HttpHandler handler;
  private final String path;
  private final Map<String, Object> attributes = new HashMap<String, Object>();
  private Authenticator authenticator;

  public MockHttpContext(HttpHandler handler, String path) {
    this.handler = handler;
    this.path = path;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public Authenticator getAuthenticator() {
    return authenticator;
  }

  public List<Filter> getFilters() {
    throw new UnsupportedOperationException();
  }

  public HttpHandler getHandler() {
    return handler;
  }

  public String getPath() {
    return path;
  }

  public HttpServer getServer() {
    throw new UnsupportedOperationException();
  }

  public Authenticator setAuthenticator(Authenticator auth) {
    Authenticator old = authenticator;
    authenticator = auth;
    return old;
  }

  public void setHandler(HttpHandler h) {
    throw new IllegalArgumentException();
  }
}
