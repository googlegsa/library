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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * Mock {@link HttpHandler}.
 */
public class MockHttpHandler implements HttpHandler {
  private final int responseCode;
  private final byte[] responseBytes;
  private final Map<String, List<String>> responseHeaders;
  private String requestMethod;
  private URI requestUri;
  private Headers requestHeaders;
  private byte[] requestBytes;

  public MockHttpHandler(int responseCode, byte[] responseBytes) {
    this(responseCode, responseBytes,
        Collections.<String, List<String>>emptyMap());
  }

  public MockHttpHandler(int responseCode, byte[] responseBytes,
      Map<String, List<String>> responseHeaders) {
    this.responseCode = responseCode;
    this.responseBytes = responseBytes;
    this.responseHeaders = responseHeaders;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    requestMethod = ex.getRequestMethod();
    requestUri = ex.getRequestURI();
    requestHeaders = new Headers();
    requestHeaders.putAll(ex.getRequestHeaders());
    requestBytes = IOHelper.readInputStreamToByteArray(ex.getRequestBody());
    ex.getResponseHeaders().putAll(responseHeaders);
    ex.sendResponseHeaders(responseCode, responseBytes == null ? -1 : 0);
    if (responseBytes != null) {
      ex.getResponseBody().write(responseBytes);
      ex.getResponseBody().flush();
      ex.getResponseBody().close();
    }
    ex.close();
  }

  public String getRequestMethod() {
    return requestMethod;
  }

  public URI getRequestUri() {
    return requestUri;
  }

  public Headers getRequestHeaders() {
    return requestHeaders;
  }

  public byte[] getRequestBytes() {
    return requestBytes;
  }
}
