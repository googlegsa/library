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

import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.AuthnAuthority.Callback;
import com.google.enterprise.adaptor.Session;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 
 * This code does three things:
 * <ol>
 * <li>Retrieve the previously stored callback object.
 * <li>Retrieve the userid from the query parameters. 
 * Again, this is just to illustrate the point - it can be a userid from 
 * another service. 
 * It also skips the user authentication process and goes directly to step 
 * three.
 * <li>Constructs a AuthnIdentity object, 
 * and pass on to callback.userAuthenticated(). 
 * You can set both userid and groups on the identity object.
 */
public class AuthNResponseHandler implements HttpHandler {
  private static final Logger log =
      Logger.getLogger(AuthNResponseHandler.class.getName());
  private AdaptorContext context;
  private Callback callback;
  
  public AuthNResponseHandler(AdaptorContext adaptorContext) {
    context = adaptorContext; 
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    log.info("handle");
   
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
    MyAuthnIdentity identity = new MyAuthnIdentity(userid);
    callback.userAuthenticated(ex, identity);
  }
  
  private Callback getCallback(HttpExchange ex) {
    Session session = context.getUserSession(ex, false);
    Callback callback = (Callback)session.getAttribute("callback");
    if (callback == null) {
      log.warning("Something is wrong, callback object is misssing");
    }
    return callback;
  }

  public Map<String, String> extractQueryParams(String request) {
    String query = request.substring(request.lastIndexOf("?") + 1);
    String params[] = query.split("&");
    Map<String, String> paramMap = new HashMap<String, String>();
    try {
      for (int i = 0; i < params.length; ++i) {
        String param[] = params[i].split("%2F=");
        paramMap.put(URLDecoder.decode(param[0], "UTF-8"),
            URLDecoder.decode(param[1], "UTF-8"));
      }
    } catch (UnsupportedEncodingException e) {
      log.warning(e.getMessage());
    }
    return paramMap;
  }
}
