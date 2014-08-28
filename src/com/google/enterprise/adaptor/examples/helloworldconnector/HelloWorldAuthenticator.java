package com.google.enterprise.adaptor.examples.helloworldconnector;

// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.AuthnAuthority;
import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.AuthzAuthority;
import com.google.enterprise.adaptor.AuthzStatus;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.Session;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Simple AuthN/AuthZ implementation
 */
class HelloWorldAuthenticator implements AuthnAuthority, AuthzAuthority, 
    HttpHandler {

  private static final Logger log =
      Logger.getLogger(HelloWorldAuthenticator.class.getName());

  private AdaptorContext context;
  private Callback callback;

  public HelloWorldAuthenticator(AdaptorContext adaptorContext) {
    if (adaptorContext == null) {
      throw new NullPointerException();
    }
    context = adaptorContext;
  }

  @Override
  public void authenticateUser(HttpExchange exchange, Callback callback)
      throws IOException {

    log.entering("HelloWorldAuthenticator", "authenticateUser");
    context.getUserSession(exchange, true).setAttribute("callback",
        callback);

    Headers responseHeaders = exchange.getResponseHeaders();
    responseHeaders.set("Content-Type", "text/html");
    exchange.sendResponseHeaders(200, 0);
    OutputStream os = exchange.getResponseBody();
    String str = "<html><body><form action=\"/google-response\" method=Get>"
        + "<input type=text name=userid/>"
        + "<input type=password name=password/>"
        + "<input type=submit value=submit></form></body></html>";
    os.write(str.getBytes());
    os.flush();
    os.close();
    exchange.close();
  }

  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity userIdentity,
      Collection<DocId> ids) throws IOException {

    HashMap<DocId, AuthzStatus> authorizedDocs =
        new HashMap<DocId, AuthzStatus>();

    for (Iterator<DocId> iterator = ids.iterator(); iterator.hasNext();) {
      DocId docId = iterator.next();
      // if authorized
      authorizedDocs.put(docId, AuthzStatus.PERMIT);
    }
    return authorizedDocs;
  }

  /**
   * Handle the form submit from /samlip<br>
   * If all goes well, this should result in an Authenticated user for the
   * session
   */
  @Override
  public void handle(HttpExchange ex) throws IOException {
    log.entering("HelloWorldAuthenticator", "handle");

    callback = getCallback(ex);
    if (callback == null) {
      return;
    }

    Map<String, String> parameters =
        extractQueryParams(ex.getRequestURI().toString());
    if (parameters.size() == 0 || null == parameters.get("userid")) {
      log.warning("missing userid");
      callback.userAuthenticated(ex, null);
      return;
    }
    String userid = parameters.get("userid");
    SimpleAuthnIdentity identity = new SimpleAuthnIdentity(userid);
    callback.userAuthenticated(ex, identity);
  }

  // Return a 200 with simple response in body
  private void sendResponseMessage(String message, HttpExchange ex)
      throws IOException {
    OutputStream os = ex.getResponseBody();
    ex.sendResponseHeaders(200, 0);
    os.write(message.getBytes());
    os.flush();
    os.close();
    ex.close();
  }

  // Return the Callback method,
  // or print error if the handler wasn't called correctly
  private Callback getCallback(HttpExchange ex) throws IOException {
    Session session = context.getUserSession(ex, false);
    if (session == null) {
      log.warning("No Session");
      sendResponseMessage("No Session", ex);
      return null;
    }
    Callback callback = (Callback) session.getAttribute("callback");
    if (callback == null) {
      log.warning("Something is wrong, callback object is missing");
      sendResponseMessage("No Callback Specified", ex);
    }
    return callback;
  }

  // Parse user/password/group params
  private Map<String, String> extractQueryParams(String request) {
    Map<String, String> paramMap = new HashMap<String, String>();
    int queryIndex = request.lastIndexOf("?");

    if (queryIndex == -1) {
      return paramMap;
    }
    String query = request.substring(queryIndex + 1);
    String params[] = query.split("&", 4);
    if (query.equals("")) {
      return paramMap;
    }
    try {
      for (int i = 0; i < params.length; ++i) {
        String param[] = params[i].split("%2F=", 2);
        paramMap.put(URLDecoder.decode(param[0], "UTF-8"),
            URLDecoder.decode(param[1], "UTF-8"));
      }
    } catch (UnsupportedEncodingException e) {
      log.warning("Request parameters may not have been properly encoded: "
          + e.getMessage());
    } catch (ArrayIndexOutOfBoundsException e) {
      log.warning("Wrong number of parameters specified: " + e.getMessage());
    }
    return paramMap;
  }
}
