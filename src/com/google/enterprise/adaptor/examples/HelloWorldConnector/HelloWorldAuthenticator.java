package com.google.enterprise.adaptor.examples.HelloWorldConnector;

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

//import com.google.enterprise.adaptor.*;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.AuthnAuthority;
import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.AuthzAuthority;
import com.google.enterprise.adaptor.AuthzStatus;
import com.google.enterprise.adaptor.DocId;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Simple AuthN/AuthZ implementation
 */
public class HelloWorldAuthenticator implements AuthnAuthority, AuthzAuthority {

  private static final Logger log =
      Logger.getLogger(HelloWorldAuthenticator.class.getName());

  AdaptorContext context;

  public HelloWorldAuthenticator(AdaptorContext adaptorContext) {
    if (adaptorContext == null) {
      throw new NullPointerException();
    } else {
      context = adaptorContext;
    }
  }

  @Override
  public void authenticateUser(HttpExchange exchange, Callback callback)
      throws IOException {

    log.info("redirect");
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
}
