// Copyright 2011 Google Inc.
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON-RPC handler for communication with the dashboard.
 */
class RpcHandler extends AbstractHandler {
  private static final Logger log = Logger.getLogger(
      RpcHandler.class.getName());

  private final Charset charset = Charset.forName("UTF-8");
  private final GsaCommunicationHandler commHandler;

  public RpcHandler(String defaultHostname, Charset defaultCharset,
                    GsaCommunicationHandler commHandler) {
    super(defaultHostname, defaultCharset);
    this.commHandler = commHandler;
  }

  @Override
  public void meteredHandle(HttpExchange ex) throws IOException {
    if (!"POST".equals(ex.getRequestMethod())) {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                    "Unsupported request method");
      return;
    }
    if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
      cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND, "text/plain",
                    "Not found");
      return;
    }
    Object requestObj;
    {
      byte[] request = IOHelper.readInputStreamToByteArray(ex.getRequestBody());
      requestObj = JSONValue.parse(new String(request, charset));
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
      log.log(Level.WARNING, "Invalid request format", e);
      @SuppressWarnings("unchecked")
      Map<String, Object> response = new JSONObject();
      response.put("id", null);
      response.put("result", null);
      response.put("error", "Invalid request format");
      cannedRespond(ex, HttpURLConnection.HTTP_OK, "application/json",
                    JSONValue.toJSONString(response));
      return;
    }

    // You must set one and only one of result and error.
    Object result = null;
    Object error = null;
    try {
      if ("startFeedPush".equals(method)) {
        result = startFeedPush(params);
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

  private Object startFeedPush(List request) {
    boolean pushStarted = commHandler.checkAndBeginPushDocIdsImmediately(null);
    if (!pushStarted) {
      throw new RuntimeException("A push is already in progress");
    }
    return 1;
  }
}
