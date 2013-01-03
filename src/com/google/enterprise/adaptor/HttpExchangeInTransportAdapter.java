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

import org.opensaml.ws.transport.http.HTTPInTransport;
import org.opensaml.xml.security.credential.Credential;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An adaptor for implementing {@link HttpInTransport} with {@link
 * HttpExchange}.
 */
class HttpExchangeInTransportAdapter implements HTTPInTransport {
  protected final HttpExchange ex;
  private boolean isAuthenticated;
  private boolean isHttps;
  private final Map<String, List<String>> queryParameters;

  public HttpExchangeInTransportAdapter(HttpExchange ex) {
    this(ex, false);
  }

  public HttpExchangeInTransportAdapter(HttpExchange ex, boolean isHttps) {
    this.ex = ex;
    this.isHttps = isHttps;
    if (ex.getRequestMethod().equals("GET")) {
      this.queryParameters
          = HttpExchanges.parseQueryParameters(ex, Charset.forName("UTF-8"));
    } else {
      // Unsupported decoding.
      this.queryParameters = null;
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public Object getAttribute(String name) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public String getCharacterEncoding() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public Credential getLocalCredential() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public Credential getPeerCredential() {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAuthenticated() {
    return isAuthenticated;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfidential() {
    return isHttps;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIntegrityProtected() {
    return isHttps;
  }

  /** {@inheritDoc} */
  @Override
  public void setAuthenticated(boolean isAuthenticated) {
    this.isAuthenticated = isAuthenticated;
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void setConfidential(boolean isConfidential) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void setIntegrityProtected(boolean isIntegrityProtected) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public InputStream getIncomingStream() {
    return ex.getRequestBody();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public String getPeerAddress() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public String getPeerDomainName() {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public String getHeaderValue(String name) {
    return ex.getRequestHeaders().getFirst(name);
  }

  /** {@inheritDoc} */
  @Override
  public String getHTTPMethod() {
    return ex.getRequestMethod();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public String getParameterValue(String name) {
    List<String> values = getParameterValues(name);
    return values == null ? null : values.get(0);
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public List<String> getParameterValues(String name) {
    if (queryParameters == null) {
      throw new UnsupportedOperationException(
          "Parameter decoding only supported for GET requests");
    }
    List<String> values = queryParameters.get(name);
    return values == null ? null : Collections.unmodifiableList(values);
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public int getStatusCode() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public HTTP_VERSION getVersion() {
    throw new UnsupportedOperationException();
  }
}
