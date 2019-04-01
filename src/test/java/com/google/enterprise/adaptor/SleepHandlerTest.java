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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link SleepHandler}.
 */
public class SleepHandlerTest {
  private SleepHandler handler = new SleepHandler(1);

  @Test
  public void testSuccess() throws Exception {
    MockHttpExchange ex = makeExchange("GET", "/r", "/r");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testPost() throws Exception {
    MockHttpExchange ex = makeExchange("POST", "/r", "/r");
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testWrongPath() throws Exception {
    MockHttpExchange ex = makeExchange("GET", "/rwrong", "/r");
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testInterrupted() throws Exception {
    MockHttpExchange ex = makeExchange("GET", "/r", "/r");
    Thread.currentThread().interrupt();
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
  }

  private MockHttpExchange makeExchange(String method, String path,
      String contextPath) throws Exception {
    MockHttpExchange ex = new MockHttpExchange(method, path,
        new MockHttpContext(handler, contextPath));
    return ex;
  }
}
