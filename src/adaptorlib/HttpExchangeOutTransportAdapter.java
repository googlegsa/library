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

import org.opensaml.ws.transport.http.HTTPOutTransport;
import org.opensaml.xml.security.credential.Credential;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.List;

/**
 * An adaptor for implementing {@link HttpOutTransport} with {@link
 * HttpExchange}.
 */
class HttpExchangeOutTransportAdapter implements HTTPOutTransport {
  private final HttpExchange ex;
  private boolean isAuthenticated;
  private boolean isHttps;
  private String characterEncoding = "ISO-8859-1";

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
    return characterEncoding;
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
  public OutputStream getOutgoingStream() {
    // Unfortunate coupling with AbstractHandler, but there doesn't seem to be a
    // better alternative.
    if (ex.getAttribute(AbstractHandler.ATTR_HEADERS_SENT) == null) {
      ex.setAttribute(AbstractHandler.ATTR_HEADERS_SENT, true);
      try {
        ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
      } catch (java.io.IOException e) {
        throw new RuntimeException(e);
      }
    }
    return ex.getResponseBody();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void setAttribute(String name, Object value) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public void setCharacterEncoding(String encoding) {
    if (ex.getResponseHeaders().get("Content-Type") != null) {
      throw new UnsupportedOperationException(
          "This implementation requires setCharacterEncoding before setting "
          + "Content-Type");
    }
    characterEncoding = encoding;
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void addParameter(String name, String value) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public void sendRedirect(String location) {
    ex.getResponseHeaders().set("Location", location);
    ex.setAttribute(AbstractHandler.ATTR_HEADERS_SENT, true);
    try {
      ex.sendResponseHeaders(307, -1);
    } catch (java.io.IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setHeader(String name, String value) {
    if ("Content-Type".equalsIgnoreCase(name)) {
      value = value + "; charset=" + characterEncoding;
    }
    ex.getResponseHeaders().set(name, value);
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void setStatusCode(int code) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void setVersion(HTTP_VERSION version) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public String getHeaderValue(String name) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public String getHTTPMethod() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public String getParameterValue(String name) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public List<String> getParameterValues(String name) {
    throw new UnsupportedOperationException();
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
