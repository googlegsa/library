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

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Mock {@link HttpServer}.
 */
public class MockHttpServer extends HttpServer {
  private final InetSocketAddress addr;
  final List<HttpContext> contexts = new ArrayList<HttpContext>();

  public MockHttpServer() {
    this(new InetSocketAddress(80));
  }

  public MockHttpServer(InetSocketAddress addr) {
    if (addr == null) {
      throw new NullPointerException();
    }
    this.addr = addr;
  }

  private HttpContext instantiateContext(String path) {
    return new MockHttpContext(this, path);
  }

  @Override
  public void bind(InetSocketAddress addr, int backlog) {
    throw new UnsupportedOperationException();
  }

  @Override
  public synchronized HttpContext createContext(String path) {
    HttpContext context = instantiateContext(path);
    for (HttpContext trailContext : contexts) {
      if (path.equals(trailContext.getPath())) {
        throw new IllegalArgumentException("Handler already exists for path");
      }
    }
    contexts.add(context);
    return context;
  }

  @Override
  public synchronized HttpContext createContext(
      String path, HttpHandler handler) {
    HttpContext context = createContext(path);
    context.setHandler(handler);
    return context;
  }

  @Override
  public InetSocketAddress getAddress() {
    return addr;
  }

  @Override
  public Executor getExecutor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public synchronized void removeContext(HttpContext context) {
    if (context == null) {
      throw new NullPointerException();
    }
    contexts.remove(context);
  }

  @Override
  public synchronized void removeContext(String path) {
    if (path == null) {
      throw new NullPointerException();
    }
    Iterator<HttpContext> iter = contexts.iterator();
    while (iter.hasNext()) {
      HttpContext context = iter.next();
      if (context.getPath().equals(path)) {
        iter.remove();
        // Completed.
        return;
      }
    }
    // Not found.
    throw new IllegalArgumentException();
  }

  @Override
  public void setExecutor(Executor executor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void start() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stop(int delay) {
    throw new UnsupportedOperationException();
  }

  public synchronized MockHttpExchange createExchange(String method,
      String path) {
    HttpContext best = null;
    int bestLength = -1;
    for (HttpContext context : contexts) {
      if (path.startsWith(context.getPath())
          && context.getPath().length() > bestLength) {
        best = context;
        bestLength = context.getPath().length();
      }
    }
    if (best == null) {
      return null;
    }
    return new MockHttpExchange(method, path, best);
  }

  public void handle(HttpExchange ex) throws IOException {
    if (ex == null) {
      throw new NullPointerException();
    }
    HttpContext context = ex.getHttpContext();
    new Filter.Chain(context.getFilters(), context.getHandler()).doFilter(ex);
  }
}
