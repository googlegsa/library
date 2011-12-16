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

import com.sun.net.httpserver.HttpExchange;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.*;

/**
 * JSON-RPC handler for communication with the dashboard.
 */
class RpcHandler extends AbstractHandler {
  /** Key used to store the XSRF-prevention token in the session. */
  private static final String XSRF_TOKEN_ATTR_NAME = "rpc-xsrf-token";
  /** Cookie name used to provide the XSRF token to client. */
  public static final String XSRF_TOKEN_HEADER_NAME = "X-Adaptor-XSRF-Token";

  private final Charset charset = Charset.forName("UTF-8");
  private final Map<String, RpcMethod> methods
      = new HashMap<String, RpcMethod>();
  private final SessionManager<HttpExchange> sessionManager;

  public RpcHandler(String defaultHostname, Charset defaultCharset,
                    SessionManager<HttpExchange> sessionManager) {
    super(defaultHostname, defaultCharset);
    this.sessionManager = sessionManager;
  }

  /**
   * Register new RPC method.
   *
   * @throws IllegalStateException if method by that name already registered
   */
  public void registerRpcMethod(String name, RpcMethod method) {
    if (methods.containsKey(name)) {
      throw new IllegalStateException("Method by that name already registered");
    }
    methods.put(name, method);
  }

  /**
   * Unregister a previously registered RPC method.
   *
   * @throws RuntimeException if method by that name not previously registered
   */
  public void unregisterRpcMethod(String name) {
    if (!methods.containsKey(name)) {
      throw new RuntimeException("No method by that name registered");
    }
    methods.remove(name);
  }

  @Override
  public void meteredHandle(HttpExchange ex) throws IOException {
    if (!"POST".equals(ex.getRequestMethod())) {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
                    Translation.HTTP_BAD_METHOD);
      return;
    }
    if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
      cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
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
      cannedRespond(ex, HttpURLConnection.HTTP_CONFLICT,
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
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
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
      if ("HEAD".equals(ex.getRequestMethod())) {
        respondToHead(ex, HttpURLConnection.HTTP_OK, "application/json");
      } else {
        respond(ex, HttpURLConnection.HTTP_OK, "application/json",
                JSONValue.toJSONString(response).getBytes(defaultEncoding));
      }
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
      }
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> response = new JSONObject();
    response.put("id", id);
    response.put("result", result);
    response.put("error", error);
    enableCompressionIfSupported(ex);
    respond(ex, HttpURLConnection.HTTP_OK, "application/json",
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
