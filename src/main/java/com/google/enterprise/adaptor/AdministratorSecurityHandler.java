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

import com.google.enterprise.apis.client.GsaClient;
import com.google.gdata.util.AuthenticationException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

/**
 * Require GSA-Administrator authentication before allowing requests.
 */
class AdministratorSecurityHandler implements HttpHandler {
  private static final Logger log
      = Logger.getLogger(AdministratorSecurityHandler.class.getName());
  /** Key used to store the fact the user has been authenticated. */
  private static final String SESSION_ATTR_NAME = "dashboard-authned";
  /** Page to display when prompting for user credentials. */
  private static final String LOGIN_PAGE = "resources/login.html";
  /** Page to display when the user credentials are invalid. */
  private static final String LOGIN_FAILED_PAGE = "resources/login-failed.html";
  /** Page to display when the user credentials were not able to be verified. */
  private static final String LOGIN_INDETERMINATE_PAGE
      = "resources/login-indeterminate.html";

  /** Wrapped handler, for when the user is authenticated. */
  private final HttpHandler handler;
  /** Manager that handles keeping track of authenticated users. */
  private final SessionManager<HttpExchange> sessionManager;
  /** Trusted entity for performing authentication of user credentials. */
  private final AuthnClient authnClient;

  AdministratorSecurityHandler(HttpHandler handler,
      SessionManager<HttpExchange> sessionManager, AuthnClient authnClient) {
    this.handler = handler;
    this.sessionManager = sessionManager;
    this.authnClient = authnClient;
  }

  public AdministratorSecurityHandler(HttpHandler handler,
      SessionManager<HttpExchange> sessionManager, String gsaHostname,
      boolean useHttps) {
    this(handler, sessionManager, new GsaAuthnClient(gsaHostname, useHttps));
  }

  private void meteredHandle(HttpExchange ex) throws IOException {
    String pageToDisplay = LOGIN_PAGE;

    if ("POST".equals(ex.getRequestMethod())) {
      AuthzStatus authn = validUsernameAndPassword(ex);
      if (authn == AuthzStatus.PERMIT) {
        // Need the client to access the page via GET since the only reason the
        // request method was POST was because of submitting our login form.
        HttpExchanges.sendRedirect(ex, HttpExchanges.getRequestUri(ex));
        return;
      } else if (authn == AuthzStatus.INDETERMINATE) {
        pageToDisplay = LOGIN_INDETERMINATE_PAGE;
      } else {
        pageToDisplay = LOGIN_FAILED_PAGE;
      }
    }

    // Send login page.
    InputStream is = this.getClass().getResourceAsStream(pageToDisplay);
    if (is == null) {
      throw new IOException("Could not load login page");
    }
    byte[] page;
    try {
      page = IOHelper.readInputStreamToByteArray(is);
    } finally {
      is.close();
    }
    HttpExchanges.respond(
        ex, HttpURLConnection.HTTP_FORBIDDEN, "text/html", page);
  }

  /**
   * Check POST data to see if the user can be authenticated by the GSA. This
   * abuses the {@code AuthzStatus} class, using it for Authn, but it was
   * convenient.
   *
   * @return {@code PERMIT} if user authenticated, {@code DENY} if invalid
   *   credentials, and {@code INDETERMINATE} otherwise
   */
  private AuthzStatus validUsernameAndPassword(HttpExchange ex)
      throws IOException {
    log.fine("Not already authenticated");
    // Check to see if they provided a username and password.
    String username = null;
    String password = null;
    try {
      String request = IOHelper.readInputStreamToString(
          ex.getRequestBody(), Charset.forName("US-ASCII"));
      for (String pair : request.split("&")) {
        String[] splitPair = pair.split("=", 2);
        if (splitPair.length != 2) {
          continue;
        }
        splitPair[0] = URLDecoder.decode(splitPair[0], "UTF-8");
        splitPair[1] = URLDecoder.decode(splitPair[1], "UTF-8");
        if ("username".equals(splitPair[0])) {
          username = splitPair[1];
        } else if ("password".equals(splitPair[0])) {
          password = splitPair[1];
        }
        if (username != null && password != null) {
          break;
        }
      }
    } catch (Exception e) {
      log.log(Level.FINE, "Processing POST caused exception", e);
      // Assume that they were POSTing to a different page, since they didn't
      // provide the expected input.
      username = null;
      password = null;
    }
    if (username == null || password == null) {
      log.fine("Username or password is null. Not authenticated");
      // Must not have been from our login page.
      return AuthzStatus.INDETERMINATE;
    }

    // Check to see if provided username and password are valid.
    AuthzStatus result = authnClient.authn(username, password);
    if (result == AuthzStatus.INDETERMINATE) {
      log.fine("Failed communicating with the GSA");
      return result;
    } else if (result != AuthzStatus.PERMIT) {
      log.fine("GSA login was not successful");
      return result;
    }

    // We have a winner. Store in the session that they are a valid user.
    log.fine("GSA login successful");
    Session session = sessionManager.getSession(ex);
    session.setAttribute(SESSION_ATTR_NAME, true);
    return AuthzStatus.PERMIT;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    // Clickjacking defence.
    ex.getResponseHeaders().set("X-Frame-Options", "deny");

    // This comment is no longer true and exists from before logging was done in
    // a filter. TODO(ejona): split into a separate filter and handler.
    // Perform fast-path checking here to prevent double-logging most requests.
    Session session = sessionManager.getSession(ex, false);
    if (session != null && session.getAttribute(SESSION_ATTR_NAME) != null) {
      handler.handle(ex);
      return;
    }
    meteredHandle(ex);
  }

  interface AuthnClient {
    public AuthzStatus authn(String username, String password);
  }

  static class GsaAuthnClient implements AuthnClient {
    private String gsaHostname;
    private boolean useHttps;

    public GsaAuthnClient(String gsaHostname, boolean useHttps) {
      this.gsaHostname = gsaHostname;
      this.useHttps = useHttps;
    }

    @Override
    public AuthzStatus authn(String username, String password) {
      String protocol = useHttps ? "https" : "http";
      int port = useHttps ? 8443 : 8000;
      try {
        new GsaClient(protocol, gsaHostname, port, username, password);
      } catch (AuthenticationException e) {
        if (e.getCause() instanceof ConnectException) {
          log.log(Level.WARNING, "Failed to connect to the GSA Administrative "
              + "Console at " + adminUrl() + " . If the GSA is online and the "
              + "GSA's dedicated administrative network interface is enabled, "
              + "please verify that the gsa.admin.hostname property is "
              + "properly configured.", e);
          return AuthzStatus.INDETERMINATE;
        } else if (e.getCause() instanceof UnknownHostException) {
          log.log(Level.WARNING, "Failed to locate the GSA Administrative "
              + "Console at " + adminUrl() + " . Please verify that the "
              + "gsa.hostname and/or the gsa.admin.hostname configuration "
              + "properties are correct.", e);
          return AuthzStatus.INDETERMINATE;
        } else if (e.getCause() instanceof SSLException && useHttps) {
          log.log(Level.WARNING, "Failed to connect to the GSA Administrative "
              + "Console at " + adminUrl() + " . Please verify that the your "
              + "SSL Certificates are properly configured for secure "
              + "communication with the GSA.", e);
        } else {
          log.log(Level.FINE, "AuthenticationException", e);
        }
        return AuthzStatus.DENY;
      }
      return AuthzStatus.PERMIT;
    }

    private String adminUrl() {
      try {
        String protocol = useHttps ? "https" : "http";
        int port = useHttps ? 8443 : 8000;
        return new URL(protocol, gsaHostname, port, "").toString();
      } catch (MalformedURLException e) {
        return e.toString();
      }
    }
  }
}
