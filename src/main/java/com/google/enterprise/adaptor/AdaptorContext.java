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

import com.google.enterprise.adaptor.testing.UnsupportedAdaptorContext;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Methods for an Adaptor to communicate with the adaptor library.
 * Implementations of this class must be thread-safe.
 *
 * <p>Avoid implementing this interface in adaptor unit tests because
 * new methods may be added in the future. Instead use
 * {@link UnsupportedAdaptorContext}, or use an automated mock
 * generator like Mockito or {@code java.lang.reflect.Proxy}.
 */
public interface AdaptorContext {
  /**
   * Configuration instance for the adaptor and all things within the adaptor
   * library.
   * @return Config of an instance
   */
  public Config getConfig();

  /**
   * Callback object for pushing {@code DocId}s to the GSA at any time.
   * @return DocIdPusher sends doc ids to GSA
   */
  public DocIdPusher getDocIdPusher();

  /**
   * Callback object for asynchronously pushing {@code DocId}s to the GSA
   * at any time.
   * @return AsyncDocIdPusher sends doc ids to GSA
   */
  public AsyncDocIdPusher getAsyncDocIdPusher();

  /**
   * A way to construct URIs from DocIds.
   * @return DocIdEncoder makes URLs to this adaptor per doc id
   */
  public DocIdEncoder getDocIdEncoder();

  /**
   * Add a status source to the dashboard. The source will automatically be
   * removed just before {@link Adaptor#destroy}. Source registration should
   * occur during {@link Adaptor#init}.
   * @param source gives data to dashboard
   */
  public void addStatusSource(StatusSource source);

  /**
   * Override the default {@link ExceptionHandler} for full push.
   * @param handler for dealing with errors sending doc ids to GSA
   */
  public void setGetDocIdsFullErrorHandler(ExceptionHandler handler);

  /**
   * Retrieve the current {@link ExceptionHandler} for full push.
   * @return ExceptionHandler deals with errors sending doc ids to GSA
   */
  public ExceptionHandler getGetDocIdsFullErrorHandler();

  /**
   * Override the default {@link ExceptionHandler} for incremental push.
   * @param handler for dealing with errors sending doc ids to GSA
   */
  public void setGetDocIdsIncrementalErrorHandler(
      ExceptionHandler handler);

  /**
   * Retrieve the current {@link ExceptionHandler} for incremental push.
   * @return ExceptionHandler deals with errors sending doc ids to GSA
   */
  public ExceptionHandler getGetDocIdsIncrementalErrorHandler();

  /**
   * Retrieve decoder for sensitive values, like passwords. To protect sensitive
   * values, the user should have previously encoded them using the Dashboard.
   * However, a user is still allowed to choose to keep sensitive values in
   * plain text.
   * @return SensistiveValueDecoder to decode config values
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
   * @param path on the server
   * @param handler of requests for path
   * @return HttpContext for path utility methods
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
   * @param lister provides updated ids
   */
  public void setPollingIncrementalLister(PollingIncrementalLister lister);

  /**
   * Register an authentication provider, so it can authenticate users for the
   * GSA. Registration may not occur after {@link Adaptor#init}.
   * @param authnAuthority identifies users
   */
  public void setAuthnAuthority(AuthnAuthority authnAuthority);

  /**
   * Register an authorization provider, so it can check authorization of users
   * for the GSA. Registration may not occur after {@link Adaptor#init}.
   * @param authzAuthority makes access decisions
   */
  public void setAuthzAuthority(AuthzAuthority authzAuthority);
}
