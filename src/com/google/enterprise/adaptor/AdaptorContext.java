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

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Methods for an Adaptor to communicate with the adaptor library.
 * Implementations of this class must be thread-safe.
 */
public interface AdaptorContext {
  /**
   * Configuration instance for the adaptor and all things within the adaptor
   * library.
   */
  public Config getConfig();

  /**
   * Callback object for pushing {@code DocId}s to the GSA at any time.
   */
  public DocIdPusher getDocIdPusher();

  /**
   * A way to construct URIs from DocIds.
   */
  public DocIdEncoder getDocIdEncoder();

  /**
   * Add a status source to the dashboard.
   */
  public void addStatusSource(StatusSource source);

  /**
   * Remove a previously added status source to the dashboard.
   */
  public void removeStatusSource(StatusSource source);

  /**
   * Override the default {@link GetDocIdsErrorHandler}.
   */
  public void setGetDocIdsErrorHandler(GetDocIdsErrorHandler handler);

  /**
   * Retrieve the current {@link GetDocIdsErrorHandler}.
   */
  public GetDocIdsErrorHandler getGetDocIdsErrorHandler();

  /**
   * Retrieve decoder for sensitive values, like passwords. To protect sensitive
   * values, the user should have previously encoded them using the Dashboard.
   * However, a user is still allowed to choose to keep sensitive values in
   * plain text.
   */
  public SensitiveValueDecoder getSensitiveValueDecoder();

  /**
   * Registers a handler with the library's {@link
   * com.sun.net.httpserver.HttpServer} in similar fashion to {@link
   * com.sun.net.httpserver.HttpServer#createContext}. Removal of the handler
   * can be acheived by calling {@code
   * httpContext.getServer().removeContext(httpContext)} on the returned
   * context. Handler registration should generally occur during {@link
   * Adaptor#init} and removal during {@link Adaptor#destroy}.
   *
   * <p>Although {@code path} may be passed directly to the underlying {@code
   * HttpServer}, that is not necessarily the case. Thus, implementations should
   * use the returned context's path when forming URLs to the handler. In
   * addition, the handler and context may be modified before being returned;
   * this is primarily to allow adding commonly-needed filters for error
   * handling and logging, but also available for implementation-specific needs.
   */
  public HttpContext createHttpContext(String path, HttpHandler handler);

  /**
   * Get the session for the user communicating via {@code ex}. If a session
   * does not already exist, then {@code create} determines if one should be
   * created.
   *
   * @param ex exchange which user issued request
   * @param create whether a new session should be created if one does not
   *     already exist for this user
   * @return user's session, or {@code null} if session did not already exist
   *     and {@code create = false}
   */
  public Session getUserSession(HttpExchange ex, boolean create);
}
