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
   * Callback object for asynchronously pushing {@code DocId}s to the GSA
   * at any time.
   */
  public AsyncDocIdPusher getAsyncDocIdPusher();

  /**
   * A way to construct URIs from DocIds.
   */
  public DocIdEncoder getDocIdEncoder();

  /**
   * Add a status source to the dashboard. The source will automatically be
   * removed just before {@link Adaptor#destroy}. Source registration should
   * occur during {@link Adaptor#init}.
   */
  public void addStatusSource(StatusSource source);

  /**
   * Override the default {@link ExceptionHandler} for full push.
   */
  public void setGetDocIdsFullErrorHandler(ExceptionHandler handler);

  /**
   * Retrieve the current {@link ExceptionHandler} for full push.
   */
  public ExceptionHandler getGetDocIdsFullErrorHandler();

  /**
   * Override the default {@link ExceptionHandler} for incremental push.
   */
  public void setGetDocIdsIncrementalErrorHandler(
      ExceptionHandler handler);

  /**
   * Retrieve the current {@link ExceptionHandler} for incremental push.
   */
  public ExceptionHandler getGetDocIdsIncrementalErrorHandler();

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
   * com.sun.net.httpserver.HttpServer#createContext}. The handler will
   * automatically be removed during adaptor shutdown. Handler registration
   * should occur during {@link Adaptor#init}.
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

  /**
   * Register a polling incremental lister, so that it can be called when
   * appropriate. Registration may not occur after {@link Adaptor#init}.
   */
  public void setPollingIncrementalLister(PollingIncrementalLister lister);

  /**
   * Register an authentication provider, so it can authenticate users for the
   * GSA. Registration may not occur after {@link Adaptor#init}.
   */
  public void setAuthnAuthority(AuthnAuthority authnAuthority);

  /**
   * Register an authorization provider, so it can check authorization of users
   * for the GSA. Registration may not occur after {@link Adaptor#init}.
   */
  public void setAuthzAuthority(AuthzAuthority authzAuthority);

  /**
   * Find out if GSA we are talking to supports the "full" type
   * of group definition feed.  The "full" type of group definition
   * push replaces all group definitions from a particular source (e.g.
   * an adaptor instance). When GSA does not support "full" type
   * it still supports "incremental" type.
   */
  public boolean gsaSupportsFullGroupPush();
}
