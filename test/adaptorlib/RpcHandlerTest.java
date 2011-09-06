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

import static org.junit.Assert.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;

/**
 * Tests for {@link RpcHandler}.
 */
public class RpcHandlerTest {
  private RpcHandler handler
      = new RpcHandler("localhost", Charset.forName("UTF-8"), null);
  private Charset charset = Charset.forName("UTF-8");

  @Test
  public void testGet() throws Exception {
    MockHttpExchange ex = makeExchange("http", "GET", "/r", "/r");
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testWrongPath() throws Exception {
    MockHttpExchange ex = makeExchange("http", "POST", "/rwrong", "/r");
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testUnknownMethod() throws Exception {
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    ex.setRequestBody(stringToStream(
        "{\"method\": \"wrong\",\"params\":null,\"id\":null}"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    JSONObject obj = (JSONObject) JSONValue.parse(response);
    assertTrue(obj.get("error") != null);
    assertTrue(obj.get("result") == null);
  }

  @Test
  public void testInvalidJson() throws Exception {
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    ex.setRequestBody(stringToStream("{"));
    handler.handle(ex);
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testNoInput() throws Exception {
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    handler.handle(ex);
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testInvalidInput() throws Exception {
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    ex.setRequestBody(stringToStream("[]"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    JSONObject obj = (JSONObject) JSONValue.parse(response);
    assertTrue(obj.get("error") != null);
    assertTrue(obj.get("result") == null);
  }

  private MockHttpExchange makeExchange(String protocol, String method,
        String path, String contextPath) throws Exception {
    return new MockHttpExchange(protocol, method, path,
                                new MockHttpContext(handler, contextPath));
  }

  private InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(charset));
  }
}
