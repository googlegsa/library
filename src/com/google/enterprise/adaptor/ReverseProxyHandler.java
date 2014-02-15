// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An HTTP reverse proxy. This class uses HttpURLConnection internally, so the
 * default {@link Authenticator} must be {@code null} for proper operation. The
 * implementation is known to drop the response body for 401 responses to POST
 * requests.
 */
class ReverseProxyHandler implements HttpHandler {
  // The Hop-by-hop headers that are connection-local, as defined by RFC 2616
  // Section 13.5.1. As mentioned in the section, additional headers can be
  // specified in the Connection header.
  private static final Set<String> PREDEFINED_HOP_BY_HOP_HEADERS
      = ImmutableSortedSet.orderedBy(String.CASE_INSENSITIVE_ORDER).add(
          "Connection",
          "Host", // Because this is a reverse proxy.
          "Keep-Alive",
          "Proxy-Authenticate",
          "Proxy-Authorization",
          "TE",
          "Trailers",
          "Transfer-Encoding",
          "Upgrade").build();

  private static final Logger log
      = Logger.getLogger(HttpExchanges.class.getName());

  private final URI destinationBase;

  public ReverseProxyHandler(URI destinationBase) {
    if (destinationBase == null) {
      throw new NullPointerException();
    }
    this.destinationBase = destinationBase;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    URI dest = computeProxyDestination(ex);

    // Set up request
    HttpURLConnection conn = (HttpURLConnection) dest.toURL().openConnection();
    conn.setRequestMethod(ex.getRequestMethod());
    conn.setAllowUserInteraction(false);
    conn.setInstanceFollowRedirects(false);
    // This adds Cache-Control: no-cache and Pragma: no-cache if those headers
    // keys aren't already there. It is unfortunate that it modifies the
    // request, however, the presense of these headers shouldn't have an impact
    // because they are targeted to proxy servers, not the application server.
    // See RFC 2616 Section 14.9.1 and 14.32.
    conn.setUseCaches(false);
    // HttpUrlConnection will add Accept and User-Agent headers if they aren't
    // already there. Luckily they are almost always there.
    copyRequestHeaders(ex, conn);
    conn.addRequestProperty("X-Forwarded-For", getClientIp(ex));

    // As defined in RFC 2616 Section 4.3
    boolean hasRequestBody
        = ex.getRequestHeaders().containsKey("Content-Length")
        || ex.getRequestHeaders().containsKey("Transfer-Encoding");

    conn.setDoOutput(hasRequestBody);
    // Input is required for getResponseCode()
    conn.setDoInput(true);

    if (hasRequestBody) {
      String strContentLength
          = ex.getRequestHeaders().getFirst("Content-Length");
      long contentLength = -1;
      if (strContentLength != null) {
        try {
          contentLength = Long.parseLong(strContentLength);
        } catch (NumberFormatException e) {
          // Keep contentLength = -1
        }
      }
      // Enable streaming mode on the connection to prevent entire request body
      // from being buffered. Streaming mode also disables automatic
      // authentication.
      if (contentLength < 0) {
        conn.setChunkedStreamingMode(-1);
      } else {
        // TODO(ejona): remove cast once we depend on Java 7.
        conn.setFixedLengthStreamingMode((int) contentLength);
      }
    } else {
      // It is essential that no authentication happens. Unfortunately for
      // requests with no body (like GET), it seems impossible to detect if
      // authentication could be or was performed and impossible to prevent it
      // for a particular request.
      //
      // Thus, we disable authentication globally, which could easily break
      // other code's assumptions. However, the hope is that the other code will
      // begin breaking and we have a chance at discovering the incompatibility
      // between the two pieces of code, instead of silently offering content to
      // requesters that they are not authenticated for.
      Authenticator.setDefault(null);
    }

    // Actually issue the request, and copy the data back
    conn.connect();
    if (hasRequestBody) {
      try {
        IOHelper.copyStream(ex.getRequestBody(), conn.getOutputStream());
        ex.getRequestBody().close();
        conn.getOutputStream().close();
      } catch (IOException e) {
        // TODO(ejona): determine if there is a more graceful way to clean up
        conn.disconnect();
        throw e;
      }
    }

    InputStream inputStream = conn.getResponseCode() >= 400
        ? conn.getErrorStream() : conn.getInputStream();
    // InputStream is null when responseCode == 401 and doing streaming, because
    // HttpURLConnection does not consider its state "connected". Unfortunately,
    // that means that we will lose the content for 401 responses for POST.
    if (conn.getResponseCode() == 401 && inputStream == null) {
      inputStream = new ByteArrayInputStream(new byte[0]);
    }

    // As defined in RFC 2616 Section 4.3
    boolean hasResponseBody = !("HEAD".equalsIgnoreCase(ex.getRequestMethod())
        || conn.getResponseCode() / 100 == 1 // 1xx Informational
        || conn.getResponseCode() == 204 // No Content
        || conn.getResponseCode() == 304); // Not Modified

    try {
      copyResponseHeaders(conn, ex);

      if (!hasResponseBody) {
        ex.sendResponseHeaders(conn.getResponseCode(), -1);
      } else {
        int contentLength = conn.getContentLength();
        if (contentLength <= 0) {
          // If the content length was unknown (-1) or if it was zero, then we
          // are forced to use chunked transfer encoding.
          contentLength = 0;
        }
        ex.sendResponseHeaders(conn.getResponseCode(), contentLength);
        IOHelper.copyStream(inputStream, ex.getResponseBody());
      }
      // Don't close in a finally because that would be a successful response.
      // If there is an error we want the server to kill the connection, which
      // informs the client that something went wrong.
      ex.close();
    } finally {
      inputStream.close();
    }
  }

  /**
   * Compute the URI the proxy should send a request to, to proxy the provided
   * request.
   */
  private URI computeProxyDestination(HttpExchange ex) {
    URI req = HttpExchanges.getRequestUri(ex);
    final String basePath = ex.getHttpContext().getPath();
    if (!req.getPath().startsWith(basePath)) {
      throw new AssertionError();
    }
    String lastPartOfPath = req.getPath().substring(basePath.length());
    try {
      return new URI(destinationBase.getScheme(),
          destinationBase.getAuthority(),
          destinationBase.getPath() + lastPartOfPath, req.getQuery(),
          req.getFragment());
    } catch (URISyntaxException e) {
      throw new AssertionError(e);
    }
  }

  private void copyRequestHeaders(HttpExchange from, HttpURLConnection to) {
    Set<String> requestHopByHopHeaders
        = getHopByHopHeaders(from.getRequestHeaders().get("Connection"));
    for (Map.Entry<String, List<String>> me
        : from.getRequestHeaders().entrySet()) {
      if (requestHopByHopHeaders.contains(me.getKey())) {
        continue;
      }
      for (String value : me.getValue()) {
        to.addRequestProperty(me.getKey(), value);
      }
    }
  }

  private void copyResponseHeaders(HttpURLConnection from, HttpExchange to) {
    Set<String> responseHopByHopHeaders
        = getHopByHopHeaders(from.getHeaderFields().get("Connection"));
    // HttpURLConnection.getHeaderFields() reverses the order of repeated
    // headers' values, so we avoid it. Specifically,
    // sun.net.www.MessageHeader.filterAndAddHeaders() loops through the
    // headers in reverse order while building the map.
    for (int i = 0;; i++) {
      String key = from.getHeaderFieldKey(i);
      String value = from.getHeaderField(i);
      if (value == null) {
        break; // Reached end
      }
      if (key == null) {
        continue; // Value is the HTTP status line, which we want to skip
      }
      if (responseHopByHopHeaders.contains(key)) {
        continue;
      }
      to.getResponseHeaders().add(key, value);
    }
  }

  private String getClientIp(HttpExchange ex) {
    InetSocketAddress clientAddr = ex.getRemoteAddress();
    if (clientAddr.isUnresolved()) {
      log.log(Level.WARNING, "Could not determine client's IP");
      return "unknown";
    }
    return clientAddr.getAddress().getHostAddress();
  }

  private Set<String> getHopByHopHeaders(List<String> connectionValues) {
    List<String> rawConnectionHeaders
        = HttpExchanges.splitHeaderValues(connectionValues);
    if (rawConnectionHeaders == null) {
      rawConnectionHeaders = Collections.emptyList();
    }
    Set<String> connectionHeaders
        = ImmutableSortedSet.orderedBy(String.CASE_INSENSITIVE_ORDER)
        .addAll(rawConnectionHeaders).build();
    return Sets.union(PREDEFINED_HOP_BY_HOP_HEADERS, connectionHeaders);
  }
}
