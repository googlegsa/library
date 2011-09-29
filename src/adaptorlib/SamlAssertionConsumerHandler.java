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

import com.google.enterprise.secmgr.modules.SamlClient;
import com.google.enterprise.secmgr.servlets.ResponseParser;

import com.sun.net.httpserver.HttpExchange;

import org.joda.time.DateTime;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.StatusCode;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A servlet that implements the SAML "assertion consumer" role for SAML
 * credentials gathering. Once the client has been authenticated with the GSA,
 * the SSO service will redirect the client to us and include a SAML response.
 * This {@code Handler} interprets the response and updates our knowledge about
 * the client, followed by redirecting the client back to the original page they
 * tried to visit.
 */
class SamlAssertionConsumerHandler extends AbstractHandler {
  private static final Logger log = Logger.getLogger(
      SamlAssertionConsumerHandler.class.getName());

  /**
   * Manager that contains the {@link AuthnState} populated by {@link
   * AuthnHandler}.
   */
  private final SessionManager<HttpExchange> sessionManager;

  /**
   * @param sessionManager manager to use to find authn attempts in progress
   */
  SamlAssertionConsumerHandler(String fallbackHostname, Charset defaultEncoding,
                               SessionManager<HttpExchange> sessionManager) {
    super(fallbackHostname, defaultEncoding);
    this.sessionManager = sessionManager;
  }

  @Override
  public void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if (!"GET".equals(requestMethod)) {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
          "Unsupported request method");
      return;
    }
    Session session = sessionManager.getSession(ex);
    AuthnState authnState = (AuthnState) session.getAttribute(
        AuthnState.SESSION_ATTR_NAME);
    if (authnState == null) {
      cannedRespond(ex, HttpURLConnection.HTTP_CONFLICT, "text/plain",
                    "Could not find session; please try again. If the problem "
                    + "continues, please enable session cookies in your "
                    + "browser.");
      return;
    }
    if (authnState.isAuthenticated()) {
      // TODO(ejona): keep track of each request, so that we can redirect here
      cannedRespond(ex, HttpURLConnection.HTTP_CONFLICT, "text/plain",
                    "You are already authenticated. Retry accessing the "
                    + "original document.");
      return;
    }
    SamlClient client = authnState.getSamlClient();
    URI origUri = authnState.getOriginalUri();
    if (client == null || origUri == null) {
      cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR, "text/plain",
                    "No authentication attempt started.");
      return;
    }
    boolean authnSuccess;
    // GET implies the assertion is being sent with the artifact binding.
    log.info("Received assertion via artifact binding");
    Response samlResponse = client.decodeArtifactResponse(getRequestUri(ex),
        new HttpExchangeInTransportAdapter(ex));
    authnSuccess = consumeAssertion(client, samlResponse,
        client.getArtifactAssertionConsumerService().getLocation(),
        authnState);

    if (authnSuccess) {
      sendRedirect(ex, origUri); 
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_FORBIDDEN, "text/plain",
                    "You were not authenticated.");
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
    Set<String> groups = new HashSet<String>(parser.getGroups());
    DateTime expirationDateTime = parser.getExpirationTime();
    long expirationTime = (expirationDateTime == null)
        ? Long.MAX_VALUE : expirationDateTime.getMillis();
    log.log(Level.INFO, "SAML subject {0} verified {1}",
            new Object[] {subjectName, new Date(expirationTime)});
    authnState.authenticated(subjectName, groups, expirationTime);
    return true;
  }
}
