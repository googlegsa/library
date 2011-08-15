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

import org.opensaml.ws.transport.http.HTTPOutTransport;
import org.opensaml.xml.security.credential.Credential;

import java.io.OutputStream;
import java.util.List;

/**
 * An adaptor for implementing {@link HttpOutTransport} with {@link
 * HttpExchange}.
 */
class HttpExchangeOutTransportAdapter implements HTTPOutTransport {
  private final HttpExchange ex;
  private boolean isAuthenticated;
  private boolean isHttps;

  public HttpExchangeOutTransportAdapter(HttpExchange ex) {
    this(ex, false);
  }

  public HttpExchangeOutTransportAdapter(HttpExchange ex, boolean isHttps) {
    this.ex = ex;
    this.isHttps = isHttps;
  }

  /**
   * {@inheritDoc}
   *
   * This method is not supported for this transport implementatino and always
   * returns null.
   */
  @Override
  public Object getAttribute(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String getCharacterEncoding() {
    // TODO(ejona): implement
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Credential getLocalCredential() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Credential getPeerCredential() {
    return null;
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
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public void setConfidential(boolean isConfidential) {
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public void setIntegrityProtected(boolean isIntegrityProtected) {
  }

  /** {@inheritDoc} */
  @Override
  public OutputStream getOutgoingStream() {
    return ex.getResponseBody();
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public void setAttribute(String name, Object value) {}

  /** {@inheritDoc} */
  @Override
  public void setCharacterEncoding(String encoding) {
    // TODO(ejona): implement
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public void addParameter(String name, String value) {}

  /** {@inheritDoc} */
  @Override
  public void sendRedirect(String location) {
    ex.getResponseHeaders().set("Location", location);
    try {
      ex.sendResponseHeaders(307, -1);
    } catch (java.io.IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setHeader(String name, String value) {
    ex.getResponseHeaders().set(name, value);
  }

  /** {@inheritDoc} */
  @Override
  public void setStatusCode(int code) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public void setVersion(HTTP_VERSION version) {
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public String getHeaderValue(String name) {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public String getHTTPMethod() {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public String getParameterValue(String name) {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public List<String> getParameterValues(String name) {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public int getStatusCode() {
    return -1;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is not supported for this transport implementation.
   */
  @Override
  public HTTP_VERSION getVersion() {
    return null;
  }
}
