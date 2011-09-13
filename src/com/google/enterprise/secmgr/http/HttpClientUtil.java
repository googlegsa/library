// Copyright 2009 Google Inc.
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

package com.google.enterprise.secmgr.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.enterprise.secmgr.common.CookieStore;
import com.google.enterprise.secmgr.common.GCookie;
import com.google.enterprise.secmgr.common.HttpUtil;
import com.google.enterprise.secmgr.config.ConfigSingleton;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Utilities useful throughout the security manager.
 */
@ThreadSafe
public class HttpClientUtil {

  // don't instantiate
  private HttpClientUtil() {
    throw new UnsupportedOperationException();
  }

  @GuardedBy("class") private static HttpClientInterface clientOverride = null;

  /**
   * Get an HTTP client to use when communicating with client servers.
   *
   * @return An HTTP client.
   */
  public static HttpClientInterface getHttpClient() {
    synchronized (HttpClientUtil.class) {
      if (clientOverride != null) {
        return clientOverride;
      }
    }
    return ConfigSingleton.getInstance(HttpClientInterface.class);
  }

  /**
   * Set the HTTP client to use when communicating with client servers.  To be
   * used by unit tests to override the default transport mechanism.
   *
   * @param client An HTTP client.
   */
  @VisibleForTesting
  public static void setHttpClient(HttpClientInterface client) {
    Preconditions.checkNotNull(client);
    synchronized (HttpClientUtil.class) {
      clientOverride = client;
    }
  }

  /**
   * Return the redirect location of an HTTP response.
   *
   * @param exchange The HTTP exchange object containing the response.
   * @return The URL from a <code>Refresh</code> or <code>Location</code>
   *     header, or null if none such.
   */
  public static String getRedirectUrl(HttpExchange exchange) {
    int status = exchange.getStatusCode();
    if (HttpUtil.isGoodHttpStatus(status)) {
      return HttpClientUtil.getRefreshUrl(exchange);
    }
    if (status >= 300 && status < 400) {
      return exchange.getResponseHeaderValue("Location");
    }
    return null;
  }

  /**
   * Get the relative URL string in Refresh header if exists.
   * @param exchange The HTTP exchange object.
   * @return The relative URL string of Refresh header or null
   *   if none exists
   */
  private static String getRefreshUrl(HttpExchange exchange) {
    String refresh = exchange.getResponseHeaderValue("Refresh");
    if (refresh != null) {
      int pos = refresh.indexOf(';');
      if (pos != -1) {
        // found a semicolon
        String timeToRefresh = refresh.substring(0, pos);
        if ("0".equals(timeToRefresh)) {
          // only follow this if its an immediate refresh (0 seconds)
          pos = refresh.indexOf('=');
          if (pos != -1 && (pos + 1) < refresh.length()) {
            return refresh.substring(pos + 1);
          }
        }
      }
    }
    return null;
  }

  /**
   * Parses cookies from the headers of an HTTP response.
   *
   * @param exchange The exchange to get the response headers from.
   * @param sessionId A session ID to add to log messages.
   * @param store A cookie store to which the parsed cookies will be added.
   */
  public static void parseHttpResponseCookies(HttpExchange exchange, String sessionId,
      CookieStore store) {
    GCookie.parseResponseHeaders(
        exchange.getResponseHeaderValues(HttpUtil.HTTP_HEADER_SET_COOKIE),
        HttpUtil.toUri(exchange.getUrl()),
        sessionId,
        store);
  }
}
