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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Test cases for {@link AbortImmediatelyFilter}. */
public class AbortImmediatelyFilterTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Filter filter = new AbortImmediatelyFilter();
  private List<Filter> filters = Arrays.asList(filter);
  private MockHttpExchange ex = new MockHttpExchange("GET", "/",
      new MockHttpContext(null, "/"));

  @Test
  public void testDescription() {
    assertNotNull(filter.description());
  }

  @Test
  public void testNormal() throws Exception {
    new Filter.Chain(filters, new SuccessHandler()).doFilter(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testOverloaded() throws Exception {
    Filter.Chain chain = new Filter.Chain(filters, new SuccessHandler());
    HttpExchanges.abortImmediately.set(new Object());
    thrown.expect(IOException.class);
    try {
      chain.doFilter(ex);
    } finally {
      HttpExchanges.abortImmediately.set(null);
    }
  }

  private static class SuccessHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
      // Translation used in garbage.
      HttpExchanges.cannedRespond(ex, 200, Translation.HTTP_NOT_FOUND);
    }
  }
}
