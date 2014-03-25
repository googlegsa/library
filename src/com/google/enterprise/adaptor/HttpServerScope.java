// Copyright 2013 Google Inc. All Rights Reserved.
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

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a scope within an HttpServer. The current implementation simply
 * allows adding contexts and having them be cleaned up during {@link #close},
 * as well as namespacing the contexts with a prefix.
 *
 * <p>After {@link #close}d, instance may not be reused.
 */
class HttpServerScope implements Closeable {
  private final HttpServer server;
  private final String contextPrefix;
  private final List<HttpContext> contexts = new ArrayList<HttpContext>();
  private boolean closed;

  public HttpServerScope(HttpServer server, String contextPrefix) {
    this.server = server;
    this.contextPrefix = contextPrefix;
  }

  public synchronized HttpContext createContext(
      String path, HttpHandler handler) {
    if (closed) {
      throw new IllegalStateException("Closed");
    }
    HttpContext context = server.createContext(contextPrefix + path, handler);
    contexts.add(context);
    return context;
  }

  /**
   * Removes the registered contexts and prevents future requests from being
   * processed. Does not impact currently-running requests.
   */
  @Override
  public synchronized void close() {
    for (HttpContext context : contexts) {
      server.removeContext(context);
    }
    contexts.clear();
    closed = true;
  }

  public HttpServer getHttpServer() {
    return server;
  }

  public String getContextPrefix() {
    return contextPrefix;
  }
}
