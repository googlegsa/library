// Copyright 2013 Google Inc. All Rights Reserved.
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

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Test cases for {@link LoggingFilter}. */
public class LoggingFilterTest {
  private LoggingFilter filter = new LoggingFilter();
  private List<Filter> filters = Arrays.<Filter>asList(filter);
  private MockHttpExchange ex = new MockHttpExchange("GET", "/",
      new MockHttpContext(new MockHttpHandler(200, null), "/"));

  @Test
  public void testDescription() {
    assertNotNull(filter.description());
  }

  @Test
  public void testNormal() throws Exception {
    new Filter.Chain(filters, new HttpHandler() {
      @Override
      public void handle(HttpExchange ex) throws IOException {
        // Translation used in garbage.
        HttpExchanges.cannedRespond(ex, 200, Translation.HTTP_NOT_FOUND);
      }
    }).doFilter(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testRuntimeException() throws Exception {
    final RuntimeException rtex = new RuntimeException();
    Filter.Chain chain = new Filter.Chain(filters, new HttpHandler() {
      @Override
      public void handle(HttpExchange ex) throws IOException {
        throw rtex;
      }
    });
    try {
      chain.doFilter(ex);
    } catch (RuntimeException ex) {
      assertSame(rtex, ex);
    }
  }

  @Test
  public void testIOException() {
    final IOException ioex = new IOException();
    Filter.Chain chain = new Filter.Chain(filters, new HttpHandler() {
      @Override
      public void handle(HttpExchange ex) throws IOException {
        throw ioex;
      }
    });
    try {
      chain.doFilter(ex);
    } catch (IOException ex) {
      assertSame(ioex, ex);
    }
  }

  @Test
  public void testGetLoggableHeaders() {
    Headers headers = new Headers();
    headers.set("a", "1");
    headers.set("B", "2");
    assertEquals("A: 1, B: 2", filter.getLoggableHeaders(headers));
  }
}
