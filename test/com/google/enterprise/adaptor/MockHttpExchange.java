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

import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Mock {@link HttpExchange} for testing.
 */
public class MockHttpExchange extends HttpExchange {
  private final String protocol;
  private final String method;
  private final URI uri;
  private final Map<String, Object> attributes = new HashMap<String, Object>();
  /** The request body that has the contents that would be sent to the UA */
  private InputStream requestBodyOrig = new ByteArrayInputStream(new byte[0]);
  /** Overridable request body that hopefully wraps requestBodyOrig */
  private InputStream requestBody = requestBodyOrig;
  private final Headers requestHeaders = new Headers();
  private final Headers responseHeaders = new Headers();
  /** The response body that has the contents that would be sent to the UA */
  private ByteArrayOutputStream responseBodyOrig;
  /** Overridable response body that hopefully wraps requestBodyOrig */
  private OutputStream responseBody;
  private int responseCode = -1;
  private HttpContext httpContext;

  public MockHttpExchange(String protocol, String method, String path,
                          HttpContext context) {
    if (protocol == null || method == null || path == null) {
      throw new NullPointerException();
    }
    if (!("GET".equals(method) || "POST".equals(method)
          || "HEAD".equals(method))) {
      throw new IllegalArgumentException("invalid method");
    }
    this.protocol = protocol;
    this.method = method;
    try {
      this.uri = new URI(path);
    } catch (URISyntaxException ex) {
      throw new IllegalStateException(ex);
    }
    this.httpContext = context;
  }

  @Override
  public void close() {
    try {
      requestBody.close();
      requestBodyOrig.close();
      if (responseBody != null) {
        responseBody.close();
      }
      if (responseBodyOrig != null) {
        responseBodyOrig.close();
      }
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public Object getAttribute(String name) {
    return attributes.get(name);
  }

  @Override
  public HttpContext getHttpContext() {
    return httpContext;
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    try {
      return new InetSocketAddress(
          InetAddress.getByAddress("localhost", new byte[] {127, 0, 0, 2}), 80);
    } catch (UnknownHostException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public HttpPrincipal getPrincipal() {
    return null;
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    try {
      return new InetSocketAddress(
          InetAddress.getByAddress("remotehost", new byte[] {127, 0, 0, 3}),
          65000);
    } catch (UnknownHostException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public InputStream getRequestBody() {
    return requestBody;
  }

  @Override
  public Headers getRequestHeaders() {
    return requestHeaders;
  }

  @Override
  public String getRequestMethod() {
    return method;
  }

  @Override
  public URI getRequestURI() {
    return uri;
  }

  @Override
  public OutputStream getResponseBody() {
    if (responseBody == null) {
      throw new IllegalStateException();
    }
    return responseBody;
  }

  @Override
  public int getResponseCode() {
    return responseCode;
  }

  @Override
  public Headers getResponseHeaders() {
    return responseHeaders;
  }

  @Override
  public void sendResponseHeaders(int rCode, long responseLength) {
    if (responseBody != null) {
      throw new IllegalStateException();
    }
    responseCode = rCode;
    responseBodyOrig = new ByteArrayOutputStream();
    responseBody = responseBodyOrig;
    // TODO(ejona): handle responseLengeth
  }

  @Override
  public void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }

  @Override
  public void setStreams(InputStream i, OutputStream o) {
    if (i != null) {
      requestBody = i;
    }
    if (o != null) {
      responseBody = o;
    }
  }

  /* ** Additional Methods for Mocking ** */

  public void setRequestBody(byte[] bytes) {
    setRequestBody(new ByteArrayInputStream(bytes));
  }

  public void setRequestBody(InputStream i) {
    requestBodyOrig = i;
    requestBody = requestBodyOrig;
  }

  public byte[] getResponseBytes() {
    return ((ByteArrayOutputStream) responseBodyOrig).toByteArray();
  }
}
