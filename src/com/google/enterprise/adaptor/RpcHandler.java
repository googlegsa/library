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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.*;

/**
 * JSON-RPC handler for communication with the dashboard.
 */
class RpcHandler implements HttpHandler {
  /** Key used to store the XSRF-prevention token in the session. */
  private static final String XSRF_TOKEN_ATTR_NAME = "rpc-xsrf-token";
  /** Cookie name used to provide the XSRF token to client. */
  public static final String XSRF_TOKEN_HEADER_NAME = "X-Adaptor-XSRF-Token";

  private static final Logger log
      = Logger.getLogger(RpcHandler.class.getName());

  private final Charset charset = Charset.forName("UTF-8");
  private final Map<String, RpcMethod> methods
      = new HashMap<String, RpcMethod>();
  private final SessionManager<HttpExchange> sessionManager;

  public RpcHandler(SessionManager<HttpExchange> sessionManager) {
    this.sessionManager = sessionManager;
  }

  /**
   * Register new RPC method.
   *
   * @throws IllegalStateException if method by that name already registered
   */
  public void registerRpcMethod(String name, RpcMethod method) {
    if (methods.containsKey(name)) {
      throw new IllegalStateException(
          "Method by that name already registered");
    }
    methods.put(name, method);
  }

  /**
   * Unregister a previously registered RPC method.
   *
   * @throws IllegalStateException if method by that name not previously
   *     registered
   */
  public void unregisterRpcMethod(String name) {
    if (!methods.containsKey(name)) {
      throw new IllegalStateException("No method by that name registered");
    }
    methods.remove(name);
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    if (!"POST".equals(ex.getRequestMethod())) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
          Translation.HTTP_BAD_METHOD);
      return;
    }
    if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
          Translation.HTTP_NOT_FOUND);
      return;
    }
    // Make sure the session has a XSRF token.
    Session session = sessionManager.getSession(ex);
    String xsrfToken = (String) session.getAttribute(XSRF_TOKEN_ATTR_NAME);
    if (xsrfToken == null) {
      xsrfToken = sessionManager.generateRandomIdentifier();
      session.setAttribute(XSRF_TOKEN_ATTR_NAME, xsrfToken);
    }
    // Make sure the client provided the correct XSRF token.
    String providedXsrfToken
        = ex.getRequestHeaders().getFirst(XSRF_TOKEN_HEADER_NAME);
    if (!xsrfToken.equals(providedXsrfToken)) {
      ex.getResponseHeaders().set(XSRF_TOKEN_HEADER_NAME, xsrfToken);
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_CONFLICT,
          Translation.HTTP_CONFLICT_INVALID_HEADER, XSRF_TOKEN_HEADER_NAME);
      return;
    }
    Object requestObj;
    {
      String request = IOHelper.readInputStreamToString(
          ex.getRequestBody(), charset);
      requestObj = JSONValue.parse(request);
    }
    if (requestObj == null) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
          Translation.HTTP_BAD_REQUEST_INVALID_JSON);
      return;
    }
    String method;
    List params;
    Object id;
    try {
      Map request = (Map) requestObj;
      method = (String) request.get("method");
      params = (List) request.get("params");
      id = request.get("id");
    } catch (ClassCastException e) {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = new JSONObject();
      response.put("id", null);
      response.put("result", null);
      response.put("error", "Invalid request format: " + e.getMessage());
      HttpExchanges.respond(ex, HttpURLConnection.HTTP_OK, "application/json",
          JSONValue.toJSONString(response).getBytes(charset));
      return;
    }

    // You must set one and only one of result and error.
    Object result = null;
    Object error = null;
    try {
      RpcMethod methodObj = methods.get(method);
      if (methodObj != null) {
        result = methodObj.run(params);
        if (result == null) {
          error = "Null response from method";
        }
      } else {
        error = "Unknown method";
      }
    } catch (Exception e) {
      error = e.getMessage();
      if (error == null) {
        error = "Unknown exception";
        log.log(Level.WARNING, "Exception during RPC", e);
      } else {
        log.log(Level.FINE, "Exception during RPC", e);
      }
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> response = new JSONObject();
    response.put("id", id);
    response.put("result", result);
    response.put("error", error);
    HttpExchanges.enableCompressionIfSupported(ex);
    HttpExchanges.respond(ex, HttpURLConnection.HTTP_OK, "application/json",
        response.toString().getBytes(charset));
  }

  public interface RpcMethod {
    /**
     * Execute expected task for the class. Should not return {@code null}, as
     * that can't be disambiguated from a misformed response. If an exception
     * is thrown, the message will be sent in the response as the error.
     *
     * @throws Exception when something goes wrong
     */
    public Object run(List request) throws Exception;
  }
}
