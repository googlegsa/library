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

import com.sun.net.httpserver.HttpExchange;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Tests for {@link RpcHandler}.
 */
public class RpcHandlerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final String SESSION_COOKIE_NAME = "testSess";
  private SessionManager.ClientStore<HttpExchange> clientStore
      = new SessionManager.HttpExchangeClientStore(SESSION_COOKIE_NAME);
  private SessionManager<HttpExchange> sessionManager
      = new SessionManager<HttpExchange>(new MockTimeProvider(),
         clientStore, 10000, 1000);
  private RpcHandler handler = new RpcHandler(
      "localhost", Charset.forName("UTF-8"), sessionManager);
  private Charset charset = Charset.forName("UTF-8");
  private String sessionId;
  private String xsrfToken;

  @Before
  public void loadXsrfToken() throws Exception {
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    handler.handle(ex);
    assertEquals(409, ex.getResponseCode());
    xsrfToken = (String) ex.getResponseHeaders().getFirst(
        RpcHandler.XSRF_TOKEN_HEADER_NAME);
    assertNotNull(xsrfToken);
    sessionId = clientStore.retrieve(ex);
    assertNotNull(sessionId);
  }

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
  public void testInvalidXsrfToken() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("http", "POST", "/r",
        new MockHttpContext(handler, "/r"));
    ex.getRequestHeaders().set("Cookie", SESSION_COOKIE_NAME + "=" + sessionId);
    handler.handle(ex);
    assertEquals(409, ex.getResponseCode());
    assertNotNull(ex.getResponseHeaders().get(
        RpcHandler.XSRF_TOKEN_HEADER_NAME));
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

  @Test
  public void testValidCallWithResponse() throws Exception {
    handler.registerRpcMethod("someName", new RpcHandler.RpcMethod() {
      @Override
      public Object run(List request) throws Exception {
        if (request.size() == 1 && request.get(0).equals("input")) {
          return "some response";
        } else {
          throw new RuntimeException("Wrong input");
        }
      }
    });
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    ex.setRequestBody(stringToStream(
        "{\"id\": null, \"method\": \"someName\", \"params\": [\"input\"]}"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    JSONObject obj = (JSONObject) JSONValue.parse(response);
    assertNull(obj.get("error"));
    assertEquals("some response", obj.get("result"));

    // Make sure that the method can be unregistered.
    handler.unregisterRpcMethod("someName");
    ex = makeExchange("http", "POST", "/r", "/r");
    ex.setRequestBody(stringToStream(
        "{\"id\": null, \"method\": \"someName\", \"params\": [\"input\"]}"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    response = new String(ex.getResponseBytes(), charset);
    obj = (JSONObject) JSONValue.parse(response);
    assertNotNull(obj.get("error"));
    assertNull(obj.get("result"));
  }

  @Test
  public void testValidCallWithBrokenResponse() throws Exception {
    handler.registerRpcMethod("someName", new RpcHandler.RpcMethod() {
      @Override
      public Object run(List request) throws Exception {
        return null;
      }
    });
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    ex.setRequestBody(stringToStream(
        "{\"id\": null, \"method\": \"someName\", \"params\": null}"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    JSONObject obj = (JSONObject) JSONValue.parse(response);
    assertNotNull(obj.get("error"));
    assertNull(obj.get("result"));
  }

  @Test
  public void testExceptionWithMessage() throws Exception {
    handler.registerRpcMethod("someName", new RpcHandler.RpcMethod() {
      @Override
      public Object run(List request) throws Exception {
        throw new RuntimeException("some error");
      }
    });
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    ex.setRequestBody(stringToStream(
        "{\"id\": null, \"method\": \"someName\", \"params\": null}"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    JSONObject obj = (JSONObject) JSONValue.parse(response);
    assertEquals("some error", obj.get("error"));
    assertNull(obj.get("result"));
  }

  @Test
  public void testExceptionWithoutMessage() throws Exception {
    handler.registerRpcMethod("someName", new RpcHandler.RpcMethod() {
      @Override
      public Object run(List request) throws Exception {
        throw new RuntimeException();
      }
    });
    MockHttpExchange ex = makeExchange("http", "POST", "/r", "/r");
    ex.setRequestBody(stringToStream(
        "{\"id\": null, \"method\": \"someName\", \"params\": null}"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    JSONObject obj = (JSONObject) JSONValue.parse(response);
    assertNotNull(obj.get("error"));
    assertNull(obj.get("result"));
  }

  @Test
  public void testDoubleRegisterRpcMethod() {
    RpcHandler.RpcMethod method = new ErroringRpcMethod();
    handler.registerRpcMethod("someName", method);
    thrown.expect(IllegalStateException.class);
    handler.registerRpcMethod("someName", method);
  }

  @Test
  public void testDoubleUnregisterRpcMethod() {
    RpcHandler.RpcMethod method = new ErroringRpcMethod();
    handler.registerRpcMethod("someName", method);
    handler.unregisterRpcMethod("someName");
    thrown.expect(IllegalStateException.class);
    handler.unregisterRpcMethod("someName");
  }

  private MockHttpExchange makeExchange(String protocol, String method,
        String path, String contextPath) throws Exception {
    MockHttpExchange ex = new MockHttpExchange(protocol, method, path,
                                new MockHttpContext(handler, contextPath));
    ex.getRequestHeaders().set(RpcHandler.XSRF_TOKEN_HEADER_NAME, xsrfToken);
    ex.getRequestHeaders().set("Cookie", SESSION_COOKIE_NAME + "=" + sessionId);
    return ex;
  }

  private InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(charset));
  }

  private static class ErroringRpcMethod implements RpcHandler.RpcMethod {
    @Override
    public Object run(List request) throws Exception {
      throw new RuntimeException();
    }
  }
}
