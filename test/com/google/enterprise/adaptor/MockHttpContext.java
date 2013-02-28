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

package com.google.enterprise.adaptor;

import com.sun.net.httpserver.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock {@link HttpContext}.
 */
public class MockHttpContext extends HttpContext {
  private HttpHandler handler;
  private final String path;
  private final Map<String, Object> attributes
      = Collections.synchronizedMap(new HashMap<String, Object>());
  private final List<Filter> filters = new CopyOnWriteArrayList<Filter>();
  private final HttpServer httpServer;
  private Authenticator authenticator;

  public MockHttpContext(String path) {
    this(new MockHttpServer(), path);
  }

  public MockHttpContext(HttpHandler handler, String path) {
    this(new MockHttpServer(), path);
    this.handler = handler;
  }

  public MockHttpContext(HttpServer server, String path) {
    if (server == null || path == null) {
      throw new NullPointerException();
    }
    this.httpServer = server;
    this.path = path;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public synchronized Authenticator getAuthenticator() {
    return authenticator;
  }

  @Override
  public List<Filter> getFilters() {
    return filters;
  }

  @Override
  public synchronized HttpHandler getHandler() {
    return handler;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public HttpServer getServer() {
    return httpServer;
  }

  @Override
  public synchronized Authenticator setAuthenticator(Authenticator auth) {
    Authenticator old = authenticator;
    authenticator = auth;
    return old;
  }

  @Override
  public synchronized void setHandler(HttpHandler h) {
    if (h == null) {
      throw new NullPointerException();
    }
    if (handler != null) {
      throw new IllegalArgumentException();
    }
    handler = h;
  }
}
