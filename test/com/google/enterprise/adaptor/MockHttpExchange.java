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
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock {@link HttpExchange} for testing.
 */
public class MockHttpExchange extends HttpExchange {
  public static final String HEADER_DATE_VALUE
      = "Sun, 06 Nov 1994 08:49:37 GMT";

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
  private ByteArrayOutputStream responseBodyOrig = new ByteArrayOutputStream();
  /** Overridable response body that hopefully wraps requestBodyOrig */
  private OutputStream responseBody
      = new ClosingFilterOutputStream(responseBodyOrig);
  private int responseCode = -1;
  private HttpContext httpContext;
  private InetSocketAddress remoteAddress;

  public MockHttpExchange(String method, String path,
                          HttpContext context) {
    this(method, "localhost", path, context);
  }

  public MockHttpExchange(String method, String host, String path,
      HttpContext context) {
    if (method == null || host == null || path == null) {
      throw new NullPointerException();
    }
    if (!("GET".equals(method) || "POST".equals(method)
          || "HEAD".equals(method))) {
      throw new IllegalArgumentException("invalid method");
    }
    this.method = method;
    try {
      this.uri = new URI(path);
    } catch (URISyntaxException ex) {
      throw new IllegalStateException(ex);
    }
    this.httpContext = context;
    getRequestHeaders().add("Host", host);

    try {
      remoteAddress = new InetSocketAddress(
          InetAddress.getByAddress("remotehost", new byte[] {127, 0, 0, 3}),
          65000);
    } catch (UnknownHostException ex) {
      throw new AssertionError(ex);
    }
  }

  @Override
  public void close() {
    try {
      requestBody.close();
      requestBodyOrig.close();
      if (responseBody != null) {
        responseBody.close();
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
    return "HTTP/1.1";
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  public void setRemoteAddress(InetSocketAddress remoteAddress) {
    this.remoteAddress = remoteAddress;
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
    // Although the documentation specifies that getResponseBody() may only be
    // called after sendResponseHeaders(), this is not actually the case. The
    // restriction is not in affect to allow filters to function.
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
    if (responseCode != -1) {
      throw new IllegalStateException();
    }
    // The handler gets no choice of the date.
    getResponseHeaders().set("Date", HEADER_DATE_VALUE);
    responseCode = rCode;
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
    getRequestHeaders().add("Transfer-Encoding", "chunked");
  }

  public byte[] getResponseBytes() {
    return responseBodyOrig.toByteArray();
  }

  private static class ClosingFilterOutputStream
      extends FastFilterOutputStream {
    private boolean closed;

    public ClosingFilterOutputStream(OutputStream os) {
      super(os);
    }

    @Override
    public void close() throws IOException {
      // Permit multiple closes.
      closed = true;
      super.close();
    }

    @Override
    public void flush() throws IOException {
      if (closed) {
        throw new IOException("Stream closed");
      }
      super.flush();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (closed) {
        throw new IOException("Stream closed");
      }
      super.write(b, off, len);
    }
  }
}
