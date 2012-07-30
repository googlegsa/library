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

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * Test cases for {@link AbstractHandler}.
 */
public class AbstractHandlerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private AbstractHandler handler = new MockImpl();
  private MockHttpExchange ex = new MockHttpExchange("http", "GET", "/",
      new MockHttpContext(null, "/"));

  @Test
  public void testLoggableRequestHeaders() {
    ex.getRequestHeaders().set("a", "1");
    ex.getRequestHeaders().set("B", "2");
    assertEquals("A: 1, B: 2",
        handler.getLoggableHeaders(ex.getRequestHeaders()));
  }

  @Test
  public void testRequestUriHttp10() {
    ex = new MockHttpExchange("http", "GET", "/test",
                              new MockHttpContext(null, "/"));
    assertEquals("http://localhost/test", handler.getRequestUri(ex).toString());
  }

  @Test
  public void testRequestUriHttp11() {
    ex.getRequestHeaders().set("Host", "abc");
    assertEquals("http://abc/", handler.getRequestUri(ex).toString());
  }

  @Test
  public void testEnableCompression() throws Exception {
    ex.getRequestHeaders().set("Accept-Encoding", "gzip,deflate");
    handler.enableCompressionIfSupported(ex);
    assertEquals("gzip", ex.getResponseHeaders().getFirst("Content-Encoding"));
  }

  @Test
  public void testNotEnableCompression() throws Exception {
    handler.enableCompressionIfSupported(ex);
    assertNull(ex.getResponseHeaders().getFirst("Content-Encoding"));
  }

  @Test
  public void testNotEnableCompressionUnsupported() throws Exception {
    ex.getRequestHeaders().set("Accept-Encoding", "deflate");
    handler.enableCompressionIfSupported(ex);
    assertNull(ex.getResponseHeaders().getFirst("Content-Encoding"));
  }

  @Test
  public void testCannedHead() throws Exception {
    ex = new MockHttpExchange("http", "HEAD", "/test",
                              new MockHttpContext(null, "/"));
    // Translation.HTTP_NOT_FOUND was randomly chosen.
    handler.cannedRespond(ex, 200, Translation.HTTP_NOT_FOUND);
    assertEquals(0, ex.getResponseBytes().length);
  }

  @Test
  public void testIfModifiedSince() throws Exception {
    final Date golden = new Date(784111777L * 1000);
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Sun, 06 Nov 1994 08:49:37 GMT");
    assertEquals(golden, handler.getIfModifiedSince(ex));

    ex.getRequestHeaders().set("If-Modified-Since",
                               "Sunday, 06-Nov-94 08:49:37 GMT");
    assertEquals(golden, handler.getIfModifiedSince(ex));

    ex.getRequestHeaders().set("If-Modified-Since",
                               "Sun Nov  6 08:49:37 1994");
    assertEquals(golden, handler.getIfModifiedSince(ex));
  }

  @Test
  public void testIfModifiedSinceEmpty() throws Exception {
    assertNull(handler.getIfModifiedSince(ex));
  }

  @Test
  public void testIfModifiedSinceInvalid() throws Exception {
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Thu, 1 Jan 1970 00:0001 GMT");
    assertNull(handler.getIfModifiedSince(ex));
  }

  @Test
  public void testHandleSuccess() throws Exception {
    MockTimeProvider timeProvider = new MockTimeProvider();
    timeProvider.time = 784111777L  * 1000;
    timeProvider.autoIncrement = false;
    handler = new MockImpl(timeProvider) {
      protected void meteredHandle(HttpExchange ex) throws IOException {
        setLastModified(ex, new Date(timeProvider.currentTimeMillis() + 1000));
        // Translation used in garbage.
        cannedRespond(ex, 200, Translation.HTTP_NOT_FOUND);
      }
    };
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("Sun, 06 Nov 1994 08:49:37 GMT",
        ex.getResponseHeaders().getFirst("Date"));
    assertEquals("Sun, 06 Nov 1994 08:49:38 GMT",
        ex.getResponseHeaders().getFirst("Last-Modified"));
  }

  @Test
  public void testHandleErrorPreResponse() throws Exception {
    handler = new MockImpl() {
      protected void meteredHandle(HttpExchange ex) {
        throw new RuntimeException("testing");
      }
    };
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
  }

  @Test
  public void testHandleErrorPostResponse() throws Exception {
    handler = new MockImpl() {
          @Override
          protected void meteredHandle(HttpExchange ex) throws IOException {
            // Translation.HTTP_NOT_FOUND was randomly chosen.
            cannedRespond(ex, 200, Translation.HTTP_NOT_FOUND);
            throw new RuntimeException("testing");
          }
        };
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  private static class MockImpl extends AbstractHandler {
    public MockImpl() {
      super("localhost", Charset.forName("UTF-8"));
    }

    public MockImpl(TimeProvider timeProvider) {
      super("localhost", Charset.forName("UTF-8"), timeProvider);
    }

    @Override
    protected void meteredHandle(HttpExchange ex) throws IOException{}
  }
}
