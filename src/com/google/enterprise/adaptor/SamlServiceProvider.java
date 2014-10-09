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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.adaptor.secmgr.authncontroller.ExportedState;
import com.google.enterprise.adaptor.secmgr.http.HttpClientInterface;
import com.google.enterprise.adaptor.secmgr.modules.SamlClient;
import com.google.enterprise.adaptor.secmgr.servlets.ResponseParser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.joda.time.DateTime;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.xml.security.SecurityHelper;
import org.opensaml.xml.security.credential.Credential;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.KeyPair;
import java.util.Date;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides the ability to send SAML authn requests and receive the responses.
 *
 * <p>This functions in the Service Provider (SP) role in SAML. A SP is simply
 * the site that the user was trying to use (it provides a service to the user).
 * The SP sends the user to an Identity Provider (IdP) to be authenticated.
 */
class SamlServiceProvider {
  @VisibleForTesting
  static final String SESSION_STATE_ATTR_NAME = "authnState";

  private static final Logger log = Logger.getLogger(
      SamlServiceProvider.class.getName());

  /**
   * Manager that handles keeping track of users attempting to authenticate.
   */
  private final SessionManager<HttpExchange> sessionManager;
  /** SAML configuration of endpoints. */
  private final SamlMetadata metadata;
  /** Credentials to use to sign messages. */
  private final Credential cred;
  /**
   * Http client implementation that {@code SamlClient} will use to send
   * requests directly to the GSA, for resolving SAML artifacts.
   */
  private final HttpClientInterface httpClient;
  private final AssertionConsumerHandler assertionConsumer
      = new AssertionConsumerHandler();

  /**
   * @param sessionManager manager for storing session state, like authn
   *   results
   * @param metadata SAML configuration of endpoints
   * @param keyAlias alias in keystore that contains the key for signing
   *   messages
   */
  public SamlServiceProvider(SessionManager<HttpExchange> sessionManager,
      SamlMetadata metadata, KeyPair key) {
    this(sessionManager, metadata, key, new HttpClientAdapter());
  }

  @VisibleForTesting
  SamlServiceProvider(SessionManager<HttpExchange> sessionManager,
      SamlMetadata metadata, KeyPair key, HttpClientInterface httpClient) {
    if (metadata == null || sessionManager == null || httpClient == null) {
      throw new NullPointerException();
    }
    this.sessionManager = sessionManager;
    this.metadata = metadata;
    this.cred = (key == null) ? null
        : SecurityHelper.getSimpleCredential(key.getPublic(),
            key.getPrivate());
    this.httpClient = httpClient;
  }

  /**
   * Consumer of authentication responses. It must be registered as a handler
   * with the HTTP server with the same path as defined in the SamlMetadata.
   */
  public HttpHandler getAssertionConsumer() {
    return assertionConsumer;
  }

  /**
   * Get the identity of the current user. This uses {@code ex} to identify the
   * user, but does not do any modification of the exchange.
   *
   * @return the identity of the user, or {@code null} if the user is not
   *     currently authenticated
   */
  public AuthnIdentity getUserIdentity(HttpExchange ex) {
    Session session = sessionManager.getSession(ex, false);
    if (session == null) {
      return null;
    }
    AuthnState authnState
        = (AuthnState) session.getAttribute(SESSION_STATE_ATTR_NAME);
    if (authnState != null && authnState.isAuthenticated()) {
      return authnState.getIdentity();
    }
    return null;
  }

  /**
   * Authenticate the user, consuming {@code ex} in the process. This creates a
   * SAML Authn Request to the IdP and redirects the user to begin the
   * authentication process. At the end of the process, the user will be
   * redirected back to the current URL.
   *
   * <p>{@link #getAssertionConsumer} must have been properly registered with
   * the HTTP server for the operation to complete successfully.
   */
  public void handleAuthentication(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if (!"GET".equals(requestMethod) && !"HEAD".equals(requestMethod)) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
          Translation.HTTP_BAD_METHOD);
      return;
    }

    Session session = sessionManager.getSession(ex);
    AuthnState authnState = (AuthnState) session.getAttribute(
        SESSION_STATE_ATTR_NAME);
    if (authnState == null) {
      authnState = new AuthnState();
      session.setAttribute(SESSION_STATE_ATTR_NAME, authnState);
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

  /**
   * A servlet that implements the SAML "assertion consumer" role for SAML
   * credentials gathering. Once the client has been authenticated with the GSA,
   * the SSO service will redirect the client to us and include a SAML response.
   * This {@code Handler} interprets the response and updates our knowledge
   * about the client, followed by redirecting the client back to the original
   * page they tried to visit.
   */
  private class AssertionConsumerHandler implements HttpHandler {
    public AssertionConsumerHandler() {
      SecurityManagerConfig.load();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
      String requestMethod = ex.getRequestMethod();
      if (!"GET".equals(requestMethod)) {
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
            Translation.HTTP_BAD_METHOD);
        return;
      }
      Session session = sessionManager.getSession(ex);
      AuthnState authnState = (AuthnState) session.getAttribute(
          SESSION_STATE_ATTR_NAME);
      if (authnState == null) {
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_CONFLICT,
                      Translation.AUTHN_UNKNOWN_SESSION);
        return;
      }
      if (authnState.isAuthenticated()) {
        // TODO(ejona): keep track of each request, so that we can redirect here
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_CONFLICT,
                      Translation.AUTHN_RETRY);
        return;
      }
      SamlClient client = authnState.getSamlClient();
      URI origUri = authnState.getOriginalUri();
      if (client == null || origUri == null) {
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR,
                      Translation.AUTHN_NOT_STARTED);
        return;
      }
      boolean authnSuccess;
      // GET implies the assertion is being sent with the artifact binding.
      log.info("Received assertion via artifact binding");
      Response samlResponse = client.decodeArtifactResponse(
          HttpExchanges.getRequestUri(ex),
          new HttpExchangeInTransportAdapter(ex));
      authnSuccess = consumeAssertion(client, samlResponse,
          client.getArtifactAssertionConsumerService().getLocation(),
          authnState);

      if (authnSuccess) {
        HttpExchanges.sendRedirect(ex, origUri); 
      } else {
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_FORBIDDEN,
                      Translation.HTTP_FORBIDDEN_AUTHN_FAILURE);
      }
    }

    private boolean consumeAssertion(SamlClient client, Response samlResponse,
                                     String recipient, AuthnState authnState) {
      if (samlResponse == null) {
        log.warning("SAML response is missing");
        authnState.failAttempt();
        return false;
      }

      // "some session id" is trash that is used for logging in the
      // ResponseParser. We have no need for it, but we need to provide a value.
      ResponseParser parser = ResponseParser.make(
          client, recipient, samlResponse, "some session id");
      if (!parser.isResponseValid()) {
        log.warning("SAML response is invalid");
        authnState.failAttempt();
        return false;
      }

      String code = parser.getResponseStatus();
      log.log(Level.INFO, "status code = {0}", new Object[] {code});
      if (!code.equals(StatusCode.SUCCESS_URI)) {
        log.log(Level.WARNING, "SAML IdP failed to resolve: {0}",
                new Object[] {code});
        authnState.failAttempt();
        return false;
      }

      if (!parser.areAssertionsValid()) {
        log.warning("One or more SAML assertions are invalid");
        authnState.failAttempt();
        return false;
      }
      String subjectName = parser.getSubject();
      Set<String> groups = null;
      String password = null;

      ExportedState state = parser.getExportedState();
      if (state != null) {
        // Groups is also available via parser.getGroups, but this handles the
        // state == null case more appropriately for our usage.
        groups = state.getPviCredentials().getGroupsNames();
        password = state.getPviCredentials().getPassword();
      }
      DateTime expirationDateTime = parser.getExpirationTime();
      long expirationTime = (expirationDateTime == null)
          ? Long.MAX_VALUE : expirationDateTime.getMillis();
      log.log(Level.INFO, "SAML subject {0} verified {1}",
              new Object[] {subjectName, new Date(expirationTime)});
      AuthnIdentity identity = new AuthnIdentityImpl
          .Builder(new UserPrincipal(subjectName))
          .setGroups(GroupPrincipal.makeSet(groups))
          .setPassword(password).build();
      authnState.authenticated(identity, expirationTime);
      return true;
    }
  }

  /**
   * Current state of an authentication attempt. It can represent a pending
   * authentication attempt or a completed authentication.
   */
  @VisibleForTesting
  static class AuthnState {
    /** Client used for pending authn attempt. */
    private SamlClient client;
    /** Original URL that was accessed that caused this authn attempt. */
    private URI originalUri;
    /** Successfully authned identity for the user. */
    private AuthnIdentity identity;
    /** Time in milliseconds that authentication information expires */
    private long expirationTimeMillis;

    public void startAttempt(SamlClient client, URI originalUri) {
      expirationTimeMillis = 0;
      this.client = client;
      this.originalUri = originalUri;
    }

    public void failAttempt() {
      clearAttempt();
    }

    private void clearAttempt() {
      client = null;
      originalUri = null;
    }

    public void authenticated(AuthnIdentity identity,
        long expirationTimeMillis) {
      clearAttempt();
      this.identity = identity;
      this.expirationTimeMillis = expirationTimeMillis;
    }

    public boolean isAuthenticated() {
      if (expirationTimeMillis == 0) {
        return false;
      }

      if (System.currentTimeMillis() > expirationTimeMillis) {
        expirationTimeMillis = 0;
        identity = null;
        return false;
      }

      return true;
    }

    public SamlClient getSamlClient() {
      return client;
    }

    public URI getOriginalUri() {
      return originalUri;
    }

    public AuthnIdentity getIdentity() {
      return identity;
    }
  }
}
