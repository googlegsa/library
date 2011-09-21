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

import com.google.enterprise.apis.client.GsaClient;
import com.google.gdata.util.AuthenticationException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.logging.Logger;

/**
 * Require GSA-Administrator authentication before allowing access to wrapped
 * handler.
 */
public class AdministratorSecurityHandler extends AbstractHandler {
  private static final Logger log
      = Logger.getLogger(AdministratorSecurityHandler.class.getName());
  /** Key used to store the fact the user has been authenticated. */
  private static final String SESSION_ATTR_NAME = "dashboard-authned";
  /** Page to display when prompting for user credentials. */
  private static final String LOGIN_PAGE = "static/login.html";
  /** Page to display when the user credentials are invalid. */
  private static final String LOGIN_INVALID_PAGE = "static/login-invalid.html";

  /** Wrapped handler, for when the user is authenticated. */
  private final HttpHandler handler;
  /** Manager that handles keeping track of authenticated users. */
  private final SessionManager<HttpExchange> sessionManager;
  /** Trusted entity for performing authentication of user credentials. */
  private final AuthnClient authnClient;

  AdministratorSecurityHandler(String fallbackHostname, Charset defaultEncoding,
      HttpHandler handler, SessionManager<HttpExchange> sessionManager,
      AuthnClient authnClient) {
    super(fallbackHostname, defaultEncoding);
    this.handler = handler;
    this.sessionManager = sessionManager;
    this.authnClient = authnClient;
  }

  public AdministratorSecurityHandler(String fallbackHostname,
      Charset defaultEncoding, HttpHandler handler,
      SessionManager<HttpExchange> sessionManager, String gsaHostname,
      boolean useHttps) {
    this(fallbackHostname, defaultEncoding, handler, sessionManager,
        new GsaAuthnClient(gsaHostname, useHttps));
  }

  @Override
  public void meteredHandle(HttpExchange ex) throws IOException {
    String pageToDisplay = LOGIN_PAGE;

    if ("POST".equals(ex.getRequestMethod())) {
      AuthzStatus authn = validUsernameAndPassword(ex);
      if (authn == AuthzStatus.PERMIT) {
        // Need the client to access the page via GET since the only reason the
        // request method was POST was because of submitting our login form.
        sendRedirect(ex, getRequestUri(ex));
        return;
      } else if (authn == AuthzStatus.DENY) {
        pageToDisplay = LOGIN_INVALID_PAGE;
      }
    }

    // Send login page.
    InputStream is = this.getClass().getResourceAsStream(pageToDisplay);
    if (is == null) {
      cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR, "text/plain",
                    "Could not load login page");
      return;
    }
    byte[] page;
    try {
      page = IOHelper.readInputStreamToByteArray(is);
    } finally {
      is.close();
    }
    respond(ex, HttpURLConnection.HTTP_FORBIDDEN, "text/html", page);
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
      String request;
      {
        byte[] bytes
            = IOHelper.readInputStreamToByteArray(ex.getRequestBody());
        request = new String(bytes, "US-ASCII");
      }
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
      log.fine("Processing POST caused exception");
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
    if (result != AuthzStatus.PERMIT) {
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
    // Perform fast-path checking here to prevent double-logging most requests.
    Session session = sessionManager.getSession(ex, false);
    if (session != null && session.getAttribute(SESSION_ATTR_NAME) != null) {
      handler.handle(ex);
      return;
    }
    super.handle(ex);
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
        return AuthzStatus.DENY;
      }
      return AuthzStatus.PERMIT;
    }
  }
}
