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

import com.google.enterprise.secmgr.http.HttpClientInterface;
import com.google.enterprise.secmgr.modules.SamlClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.opensaml.xml.security.SecurityHelper;
import org.opensaml.xml.security.credential.Credential;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyPair;

/**
 * A credentials gatherer that implements authentication by communicating with
 * the GSA's security manager via SAML. This class only sends the initial
 * request; the response is handled in {@link SamlAssertionConsumerHandler}.
 */
class AuthnHandler implements HttpHandler {
  /** Manager that handles keeping track of users attempting to authenticate. */
  private final SessionManager<HttpExchange> sessionManager;
  /**
   * Http client implementation that {@code SamlClient} will use to send
   * requests directly to the GSA, for resolving SAML artifacts.
   */
  private final HttpClientInterface httpClient;
  /** Credentials to use to sign messages. */
  private final Credential cred;
  /** SAML configuration of endpoints. */
  private final SamlMetadata metadata;

  /**
   * @param fallbackHostname fallback hostname in case we talk to an old HTTP
   *   client
   * @param defaultEncoding encoding to use when sending simple text responses
   * @param sessionManager manager for storing session state, like authn results
   * @param keyAlias alias in keystore that contains the key for signing
   *   messages
   * @param metadata SAML configuration of endpoints
   */
  AuthnHandler(SessionManager<HttpExchange> sessionManager,
               SamlMetadata metadata, KeyPair key) {
    this(sessionManager, metadata, new HttpClientAdapter(), key);
  }

  AuthnHandler(SessionManager<HttpExchange> sessionManager,
               SamlMetadata metadata, HttpClientInterface httpClient,
               KeyPair key) {
    if (sessionManager == null || metadata == null || httpClient == null) {
      throw new NullPointerException();
    }
    this.sessionManager = sessionManager;
    this.metadata = metadata;
    this.httpClient = httpClient;
    this.cred = (key == null) ? null
        : SecurityHelper.getSimpleCredential(key.getPublic(), key.getPrivate());
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if (!"GET".equals(requestMethod) && !"HEAD".equals(requestMethod)) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
          Translation.HTTP_BAD_METHOD);
      return;
    }

    Session session = sessionManager.getSession(ex);
    AuthnState authnState = (AuthnState) session.getAttribute(
        AuthnState.SESSION_ATTR_NAME);
    if (authnState == null) {
      authnState = new AuthnState();
      session.setAttribute(AuthnState.SESSION_ATTR_NAME, authnState);
    }
    SamlClient client =
        new SamlClient(
            metadata.getLocalEntity(),
            metadata.getPeerEntity(),
            "GSA Adaptor",
            cred,
            httpClient);
    authnState.startAttempt(client, HttpExchanges.getRequestUri(ex));
    client.sendAuthnRequest(new HttpExchangeOutTransportAdapter(ex, true));
  }
}
