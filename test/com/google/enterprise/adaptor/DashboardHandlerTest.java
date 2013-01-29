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

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Tests for {@link DashboardHandler}.
 */
public class DashboardHandlerTest {
  private DashboardHandler handler = new DashboardHandler();
  private String pathPrefix = "/dash";
  private MockHttpContext httpContext
      = new MockHttpContext(handler, pathPrefix);

  /** Returns entire static test file's contents. */
  private static byte[] readLocal(String basename) throws IOException {
    String dirname = "test/com/google/enterprise/adaptor/resources/";
    String filename = dirname + basename;
    RandomAccessFile f = new RandomAccessFile(filename, "r");
    byte b[] = new byte[(int) f.length()];
    f.readFully(b);
    f.close();
    return b;
  }

  @Test
  public void testIndex() throws Exception {
    MockHttpExchange ex = createExchange("");
    handler.handle(ex);
    assertEquals(301, ex.getResponseCode());
    assertEquals("http://localhost" + pathPrefix + "/",
        ex.getResponseHeaders().getFirst("Location"));
  }

  @Test
  public void testGetTestHtmlPage() throws Exception {
    String basename = "DashboardHandlerTest.test.html";
    MockHttpExchange ex = createExchange("/" + basename);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("text/html", ex.getResponseHeaders().getFirst("Content-Type"));
    assertArrayEquals(readLocal(basename), ex.getResponseBytes());
  }

  @Test
  public void testGetTestJavaScriptPage() throws Exception {
    String basename = "DashboardHandlerTest.test.js";
    MockHttpExchange ex = createExchange("/" + basename);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("text/javascript",
        ex.getResponseHeaders().getFirst("Content-Type"));
    assertArrayEquals(readLocal(basename), ex.getResponseBytes());
  }

  @Test
  public void testGetTestCssPage() throws Exception {
    String basename = "DashboardHandlerTest.test.css";
    MockHttpExchange ex = createExchange("/" + basename);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("text/css", ex.getResponseHeaders().getFirst("Content-Type"));
    assertArrayEquals(readLocal(basename), ex.getResponseBytes());
  }

  @Test
  public void testGetTestUnknownPage() throws Exception {
    String basename = "/DashboardHandlerTest.test.unknown";
    MockHttpExchange ex = createExchange("/" + basename);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("application/octet-stream",
        ex.getResponseHeaders().getFirst("Content-Type"));
    assertArrayEquals(readLocal(basename), ex.getResponseBytes());
  }

  @Test
  public void testGetDefaultPage() throws Exception {
    MockHttpExchange ex = createExchange("/");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("text/html", ex.getResponseHeaders().getFirst("Content-Type"));
  }

  @Test
  public void testGetDefaultPageCached() throws Exception {
    MockHttpExchange ex = createExchange("/");
    ex.getRequestHeaders()
      .add("If-Modified-Since", "Thu, 1 Jan 2037 10:15:30 GMT");
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());
  }

  @Test
  public void testGetDefaultPageCachedButOld() throws Exception {
    MockHttpExchange ex = createExchange("/");
    ex.getRequestHeaders()
      .add("If-Modified-Since", "Fri, 1 Jan 1971 10:15:30 GMT");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testNotFound() throws Exception {
    MockHttpExchange ex = createExchange("/notfound");
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testPost() throws Exception {
    MockHttpExchange ex
        = new MockHttpExchange("POST", pathPrefix + "/", httpContext);
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  private MockHttpExchange createExchange(String path) {
    return new MockHttpExchange("GET", pathPrefix + path, httpContext);
  }
}
