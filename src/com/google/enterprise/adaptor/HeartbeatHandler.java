// Copyright 2016 Google Inc. All Rights Reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class HeartbeatHandler implements HttpHandler {
  private static final Logger log
      = Logger.getLogger(HeartbeatHandler.class.getName());
  private static final Charset ENCODING = Charset.forName("UTF-8");

  private final DocIdDecoder heartbeatDecoder;
  private final DocIdEncoder docIdEncoder;
  private final DocumentHandler docHandler;
  private final Watchdog watchdog;
  private final long timeoutMillis;
  private HeadHttpExchange myHttpExchange;

  /**
   * An {@code HttpHandler} that converts GET requests for /heartbeat/docId into
   * HEAD requests for /doc/docId (which it then passes on to the
   * {@code docHandler}.
   */
  public HeartbeatHandler(DocIdDecoder heartbeatDecoder,
      DocIdEncoder docIdEncoder, DocumentHandler docHandler, Watchdog watchdog,
      long timeoutMillis) {

    if (heartbeatDecoder == null || docIdEncoder == null || docHandler == null
        || watchdog == null) {
      throw new NullPointerException();
    }
    this.heartbeatDecoder = heartbeatDecoder;
    this.docIdEncoder = docIdEncoder;
    this.docHandler = docHandler;
    this.watchdog = watchdog;
    this.timeoutMillis = timeoutMillis;
  }

  /**
   * Replace a GET (or HEAD) request for /heartbeat/... with a call on
   * docHandler.handle() of a HEAD request for /doc/... .
   */
  @Override
  public void handle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if (!"GET".equals(requestMethod) && !"HEAD".equals(requestMethod)) {
      log.log(Level.WARNING, "Invalid HTTP Request Method: {0}", requestMethod);
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
          Translation.HTTP_BAD_METHOD);
      return;
    }
    DocId docId = heartbeatDecoder.decodeDocId(HttpExchanges.getRequestUri(ex));
    // Replace the HeartbeatHandler URI with the URI of the DocHandler.
    URI realDocUri = docIdEncoder.encodeDocId(docId);
    watchdog.processingStarting(timeoutMillis);
    try {
      myHttpExchange = new HeadHttpExchange(realDocUri, ex);
      docHandler.handle(myHttpExchange);
    } finally {
      watchdog.processingCompleted();
    }
  }

  /** Returns the HttpExchange used in the most-recent call to handle(). */
  @VisibleForTesting
  HttpExchange getHttpExchange() {
    return myHttpExchange;
  }

  /**
   * An implementation of HttpExchange that lets particular elements be set.
   */
  //TODO (myk): Consider moving this class to HttpExchanges.java.
  private static class HeadHttpExchange extends HttpExchange {
    private final URI requestURI;
    private final HttpExchange ex;
    private final XgsaSkippingHeaders responseHeaders;

    private HeadHttpExchange(URI requestURI, HttpExchange ex) {
      super();
      this.requestURI = requestURI;
      this.ex = ex;
      this.responseHeaders = new XgsaSkippingHeaders(ex.getResponseHeaders());
    }

    // Below methods typically return different values than the passed-in
    // HttpExchange instance would return.
    @Override
    public String getRequestMethod() {
      return "HEAD";
    }

    @Override
    public URI getRequestURI() {
      return requestURI;
    }

    @Override
    public Headers getResponseHeaders() {
      return responseHeaders;
    }

    // Below methods are called (directly or indirectly) by DocumentHandler.
    // They just delegate to the passed-in HttpExchange instance.
    @Override
    public void close() {
      ex.close();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
      return ex.getRemoteAddress();
    }

    @Override
    public Headers getRequestHeaders() {
      return ex.getRequestHeaders();
    }

    @Override
    public OutputStream getResponseBody() {
      return ex.getResponseBody();
    }

    // Below methods are not called by DocumentHandler, but are still needed to
    // fully implement HttpExchange.  Like the methods above, they also delegate
    // to the passed-in HttpExchange instance.
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
    public InputStream getRequestBody() {
      return ex.getRequestBody();
    }

    @Override
    public int getResponseCode() {
      return ex.getResponseCode();
    }

    @Override
    public void sendResponseHeaders(int rCode, long responseLength)
        throws IOException {
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
  }

  /**
   * An implementation of Headers that skips all headers that start with "X-Gsa"
   */
  private static class XgsaSkippingHeaders extends Headers {

    public XgsaSkippingHeaders(Headers originalHeaders) {
      super();
      for (Map.Entry<String, List<String>> e : originalHeaders.entrySet()) {
        this.put(e.getKey(), e.getValue());
      }
    }

    public void add(String key, String value) {
      if ((key == null) || ("X-gsa".equalsIgnoreCase(key.substring(0, 5)))) {
        return;
      }
      super.add(key, value);
    }
  }
}
