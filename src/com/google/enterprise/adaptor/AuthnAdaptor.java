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

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Interface for adaptors capable of authenticating users.
 */
public interface AuthnAdaptor extends Adaptor {
  /**
   * Authenticate the user connected via {@code ex}. After attempting to
   * authenticate the user the implementation should respond by calling {@link
   * Callback#userAuthenticated}.
   *
   * <p>The implementation is expected to provide a response to the user with
   * {@code ex}. Since authentication commonly requires redirects, forms, and
   * other general HTTP mechanisms, full control is given to the implementation.
   * The implementation will likely need to use its own {@link
   * com.sun.net.httpserver.HttpHandler}s that can be registered with {@link
   * AdaptorContext#createHttpContext}.
   *
   * <p>If an implementation places {@code callback} in a session object, the
   * implementation should also remove the instance when {@code
   * userAuthenticated()} is called. This is to release resources as well as
   * preventing re-use of the callback.
   *
   * @param ex exchange whose request body has been processed, but whose
   *     response body and headers have not been sent
   * @param callback object to receive and respond with authentication results
   */
  public void authenticateUser(HttpExchange ex, Callback callback)
      throws IOException;

  /**
   * Interface for replying to {@link #authenticateUser
   * AuthnAdaptor.authenticateUser(HttpExchange, Callback)}.
   */
  public interface Callback {
    /**
     * Respond with authentication information discovered during {@link
     * #authenticateUser AuthnAdaptor.authenticateUser(HttpExchange, Callback)}.
     * The exchange {@code ex} does not need to be the same instance provided to
     * {@code authenticateUser()}, but it does need to be a connection to the
     * same client. This method provides a response to the client using {@code
     * ex}, so the exchange should not have had its headers sent and should be
     * considered completed after the method returns.
     *
     * <p>If authentication failed, then identity should be {@code null}. If an
     * {@code identity} is provided, its username must not be {@code null} or
     * empty.
     *
     * @param ex exchange whose request body has been processed, but whose
     *     response body and headers have not been sent
     * @param identity authenticated user identity information, or {@code null}
     */
    public void userAuthenticated(HttpExchange ex, AuthnIdentity identity)
        throws IOException;
  }
}
