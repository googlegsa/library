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

import static org.junit.Assert.*;

import com.sun.net.httpserver.*;

import org.junit.Test;

import java.util.Date;
import java.util.Locale;

/**
 * Test cases for {@link AbstractHandler}.
 */
public class HttpExchangesTest {
  private MockHttpExchange ex = new MockHttpExchange("GET", "/",
      new MockHttpContext("/"));

  @Test
  public void testRequestUriHttp10() {
    ex = new MockHttpExchange("GET", "/test",
                              new MockHttpContext("/"));
    assertEquals("http://localhost/test",
        HttpExchanges.getRequestUri(ex).toString());
  }

  @Test
  public void testRequestUriHttp11() {
    ex.getRequestHeaders().set("Host", "abc");
    assertEquals("http://abc/", HttpExchanges.getRequestUri(ex).toString());
  }

  @Test
  public void testEnableCompression() throws Exception {
    ex.getRequestHeaders().set("Accept-Encoding", "gzip,deflate");
    HttpExchanges.enableCompressionIfSupported(ex);
    assertEquals("gzip", ex.getResponseHeaders().getFirst("Content-Encoding"));
    ex.sendResponseHeaders(200, 0);
    int bytesToWrite = 1000;
    ex.getResponseBody().write(new byte[bytesToWrite]);
    ex.getResponseBody().close();
    assertTrue(ex.getResponseBytes().length < bytesToWrite);
  }

  @Test
  public void testNotEnableCompression() throws Exception {
    HttpExchanges.enableCompressionIfSupported(ex);
    assertNull(ex.getResponseHeaders().getFirst("Content-Encoding"));
  }

  @Test
  public void testNotEnableCompressionUnsupported() throws Exception {
    ex.getRequestHeaders().set("Accept-Encoding", "deflate");
    HttpExchanges.enableCompressionIfSupported(ex);
    assertNull(ex.getResponseHeaders().getFirst("Content-Encoding"));
  }

  @Test
  public void testCannedHead() throws Exception {
    ex = new MockHttpExchange("HEAD", "/test",
                              new MockHttpContext("/"));
    // Translation.HTTP_NOT_FOUND was randomly chosen.
    HttpExchanges.cannedRespond(ex, 200, Translation.HTTP_NOT_FOUND);
    assertEquals(0, ex.getResponseBytes().length);
  }

  @Test
  public void testIfModifiedSince() throws Exception {
    final Date golden = new Date(784111777L * 1000);
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Sun, 06 Nov 1994 08:49:37 GMT");
    assertEquals(golden, HttpExchanges.getIfModifiedSince(ex));

    ex.getRequestHeaders().set("If-Modified-Since",
                               "Sunday, 06-Nov-94 08:49:37 GMT");
    assertEquals(golden, HttpExchanges.getIfModifiedSince(ex));

    ex.getRequestHeaders().set("If-Modified-Since",
                               "Sun Nov  6 08:49:37 1994");
    assertEquals(golden, HttpExchanges.getIfModifiedSince(ex));

    ex.getRequestHeaders().set("If-Modified-Since",
                               "Sun, 06 Nov 1994 08:49:37 GMT");
    // Java 7 added categories for Locales. The no-argument get() is DISPLAY.
    // TODO(ejona): use reflection to override CATEGORY as well for Java 7.
    Locale defaultLocalDisplay = Locale.getDefault();
    Locale.setDefault(Locale.GERMANY);
    HttpExchanges.resetThread();
    Date date;
    try {
      date = HttpExchanges.getIfModifiedSince(ex);
    } finally {
      Locale.setDefault(defaultLocalDisplay);
      HttpExchanges.resetThread();
    }
    assertEquals(golden, date);
  }

  @Test
  public void testIfModifiedSinceEmpty() throws Exception {
    assertNull(HttpExchanges.getIfModifiedSince(ex));
  }

  @Test
  public void testIfModifiedSinceInvalid() throws Exception {
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Thu, 1 Jan 1970 00:0001 GMT");
    assertNull(HttpExchanges.getIfModifiedSince(ex));
  }

  @Test
  public void testSetLastModified() throws Exception {
    String golden = "Sun, 06 Nov 1994 08:49:38 GMT";
    Date date = new Date(784111778L  * 1000);
    HttpExchanges.setLastModified(ex, date);
    assertEquals(golden, ex.getResponseHeaders().getFirst("Last-Modified"));

    // Java 7 added categories for Locales. The no-argument get() is DISPLAY.
    // TODO(ejona): use reflection to override CATEGORY as well for Java 7.
    Locale defaultLocalDisplay = Locale.getDefault();
    Locale.setDefault(Locale.GERMANY);
    HttpExchanges.resetThread();
    try {
      HttpExchanges.setLastModified(ex, date);
    } finally {
      Locale.setDefault(defaultLocalDisplay);
      HttpExchanges.resetThread();
    }
    assertEquals(golden, ex.getResponseHeaders().getFirst("Last-Modified"));
  }
}
