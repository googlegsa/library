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

import org.opensaml.ws.transport.http.HTTPInTransport;
import org.opensaml.xml.security.credential.Credential;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * A converter that converts an {@link HttpExchange} object, making it look like
 * an OpenSAML {@link HTTPInTransport} object.  This allows response messages to
 * be processed using the OpenSAML library.
 */
public class HttpExchangeToInTransport implements HTTPInTransport {

  private final HttpExchange exchange;
  private final InputStream entityStream;

  public HttpExchangeToInTransport(HttpExchange exchange) throws IOException {
    this.exchange = exchange;
    entityStream = exchange.getResponseEntityAsStream();
  }

  public InputStream getIncomingStream() {
    return entityStream;
  }

  public String getHeaderValue(String name) {
    return exchange.getResponseHeaderValue(name);
  }

  public int getStatusCode() {
    return exchange.getStatusCode();
  }

  public String getHTTPMethod() {
    return exchange.getHttpMethod();
  }

  public String getPeerAddress() {
    throw new UnsupportedOperationException();
  }

  public String getPeerDomainName() {
    throw new UnsupportedOperationException();
  }

  public Object getAttribute(String name) {
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

  public HTTP_VERSION getVersion() {
    throw new UnsupportedOperationException();
  }

  public String getParameterValue(String name) {
    throw new UnsupportedOperationException();
  }

  public List<String> getParameterValues(String name) {
    throw new UnsupportedOperationException();
  }

  public String getCharacterEncoding() {
    throw new UnsupportedOperationException();
  }
}
