// Copyright 2008 Google Inc.
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

package com.google.enterprise.secmgr.saml;

import com.google.enterprise.secmgr.http.HttpExchange;

import org.opensaml.ws.transport.http.HTTPOutTransport;
import org.opensaml.xml.security.credential.Credential;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * A converter that converts an {@link HttpExchange} object, making it look like
 * an OpenSAML {@link HTTPOutTransport} object.  This allows request messages to
 * be generated using the OpenSAML library.
 */
public class HttpExchangeToOutTransport implements HTTPOutTransport {

  private final HttpExchange exchange;
  private final ByteArrayOutputStream entityStream;
  private String encoding;

  public HttpExchangeToOutTransport(HttpExchange exchange) {
    this.exchange = exchange;
    entityStream = new ByteArrayOutputStream();
  }

  public void finish() {
    exchange.setRequestBody(entityStream.toByteArray());
  }

  public void addParameter(String name, String value) {
    exchange.addParameter(name, value);
  }

  public void setHeader(String name, String value) {
    exchange.setRequestHeader(name, value);
  }

  public OutputStream getOutgoingStream() {
    return entityStream;
  }

  public void setCharacterEncoding(String encoding) {
    this.encoding = encoding;
  }

  public String getCharacterEncoding() {
    return encoding;
  }

  public void sendRedirect(String location) {
    throw new UnsupportedOperationException();
  }

  public void setStatusCode(int status) {
    throw new UnsupportedOperationException();
  }

  public void setVersion(HTTP_VERSION version) {
    throw new UnsupportedOperationException();
  }

  public void setAttribute(String name, Object value) {
    throw new UnsupportedOperationException();
  }

  public Object getAttribute(String name) {
    throw new UnsupportedOperationException();
  }

  public String getParameterValue(String name) {
    throw new UnsupportedOperationException();
  }

  public List<String> getParameterValues(String name) {
    throw new UnsupportedOperationException();
  }

  public String getHeaderValue(String name) {
    throw new UnsupportedOperationException();
  }

  public Credential getLocalCredential() {
    throw new UnsupportedOperationException();
  }

  public Credential getPeerCredential() {
    throw new UnsupportedOperationException();
  }

  public boolean isAuthenticated() {
    throw new UnsupportedOperationException();
  }

  public boolean isConfidential() {
    throw new UnsupportedOperationException();
  }

  public boolean isIntegrityProtected() {
    throw new UnsupportedOperationException();
  }

  public void setAuthenticated(boolean value) {
    throw new UnsupportedOperationException();
  }

  public void setConfidential(boolean value) {
    throw new UnsupportedOperationException();
  }

  public void setIntegrityProtected(boolean value) {
    throw new UnsupportedOperationException();
  }

  public String getHTTPMethod() {
    throw new UnsupportedOperationException();
  }

  public int getStatusCode() {
    throw new UnsupportedOperationException();
  }

  public HTTP_VERSION getVersion() {
    throw new UnsupportedOperationException();
  }
}
