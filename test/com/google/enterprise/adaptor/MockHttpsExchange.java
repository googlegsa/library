// Copyright 2012 Google Inc. All Rights Reserved.
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

import javax.net.ssl.SSLSession;

/**
 * Mock {@link HttpsExchange} for testing.
 */
public class MockHttpsExchange extends HttpsExchange {
  private MockHttpExchange ex;
  private SSLSession sslSession;

  public MockHttpsExchange(MockHttpExchange ex, SSLSession sslSession) {
    this.ex = ex;
    this.sslSession = sslSession;
  }

  public MockHttpsExchange(String method, String path,
                           HttpContext context, SSLSession sslSession) {
    this.ex = new MockHttpExchange(method, path, context);
    this.sslSession = sslSession;
  }

  @Override
  public void close() {
    ex.close();
  }

  @Override
  public Object getAttribute(String name) {
    return ex.getAttribute(name);
  }

  @Override
  public HttpContext getHttpContext() {
    return ex.getHttpContext();
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    return ex.getLocalAddress();
  }

  @Override
  public HttpPrincipal getPrincipal() {
    return ex.getPrincipal();
  }

  @Override
  public String getProtocol() {
    return ex.getProtocol();
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return ex.getRemoteAddress();
  }

  @Override
  public InputStream getRequestBody() {
    return ex.getRequestBody();
  }

  @Override
  public Headers getRequestHeaders() {
    return ex.getRequestHeaders();
  }

  @Override
  public String getRequestMethod() {
    return ex.getRequestMethod();
  }

  @Override
  public URI getRequestURI() {
    return ex.getRequestURI();
  }

  @Override
  public OutputStream getResponseBody() {
    return ex.getResponseBody();
  }

  @Override
  public int getResponseCode() {
    return ex.getResponseCode();
  }

  @Override
  public Headers getResponseHeaders() {
    return ex.getResponseHeaders();
  }

  @Override
  public void sendResponseHeaders(int rCode, long responseLength) {
    ex.sendResponseHeaders(rCode, responseLength);
  }

  @Override
  public void setAttribute(String name, Object value) {
    ex.setAttribute(name, value);
  }

  @Override
  public void setStreams(InputStream i, OutputStream o) {
    ex.setStreams(i, o);
  }

  @Override
  public SSLSession getSSLSession() {
    return sslSession;
  }

  /* ** Additional Methods for Mocking ** */

  public void setRequestBody(byte[] bytes) {
    ex.setRequestBody(bytes);
  }

  public void setRequestBody(InputStream i) {
    ex.setRequestBody(i);
  }

  public byte[] getResponseBytes() {
    return ex.getResponseBytes();
  }
}
