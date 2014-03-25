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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Objects;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Unit tests for {@link ReverseProxyHandler}. */
public class ReverseProxyHandlerTest {
  private static final Charset charset = Charset.forName("UTF-8");

  private HttpServer server;
  private int port;
  private HttpServerScope scope;
  private HttpHandler handler;
  private MockHttpContext context = new MockHttpContext("/proxy/");

  @Before
  public void startupServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();
    server.start();
    handler = new ReverseProxyHandler(
        URI.create("http://localhost:" + port + "/"));
  }

  @After
  public void shutdownServer() {
    server.stop(0);
  }

  @Test
  public void testGet() throws IOException {
    byte[] response = "test response".getBytes(charset);
    Headers goldenRequestHeaders = new Headers();
    goldenRequestHeaders.add("Host", "localhost:" + port);
    goldenRequestHeaders.add("X-Forwarded-For", "127.0.0.3");
    goldenRequestHeaders.add("Accept", "text/html,text/plain,application/*");
    goldenRequestHeaders.add("User-agent",
        "gsa-crawler (Enterprise; E3-SOMETHING; nobody@google.com)");
    // Added by HttpUrlConnection. Would prefer not to have them, but they are
    // there.
    goldenRequestHeaders.add("Cache-Control", "no-cache");
    goldenRequestHeaders.add("Pragma", "no-cache");
    // Connection-specific header by HttpUrlConnection. This is normal.
    goldenRequestHeaders.add("Connection", "keep-alive");
    Headers goldenResponseHeaders = new Headers();
    goldenResponseHeaders.add("Date", MockHttpExchange.HEADER_DATE_VALUE);
    goldenResponseHeaders.add("Example", "1");
    goldenResponseHeaders.add("Example", "something2");
    goldenResponseHeaders.add("Example", "3");
    goldenResponseHeaders.add("Best-Header", "best_value");

    Map<String, List<String>> responseHeaders
        = new HashMap<String, List<String>>();
    responseHeaders.put("Example", Arrays.asList("1", "something2", "3"));
    responseHeaders.put("Best-Header", Arrays.asList("best_value"));
    MockHttpHandler mockHandler
        = new MockHttpHandler(200, response, responseHeaders);
    server.createContext("/get", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("GET", "example.com",
        "/proxy/get", context);
    ex.getRequestHeaders().add("Accept", "text/html,text/plain,application/*");
    ex.getRequestHeaders().add("User-agent",
        "gsa-crawler (Enterprise; E3-SOMETHING; nobody@google.com)");
    handler.handle(ex);

    assertHeadersEquals(goldenRequestHeaders, mockHandler.getRequestHeaders());
    assertEquals(200, ex.getResponseCode());
    assertHeadersEquals(goldenResponseHeaders, ex.getResponseHeaders());
    assertArrayEquals(response, ex.getResponseBytes());
  }

  @Test
  public void testHead() throws IOException {
    MockHttpHandler mockHandler = new MockHttpHandler(200, null);
    server.createContext("/head", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("HEAD", "example.com",
        "/proxy/head", context);
    handler.handle(ex);

    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(new byte[0], ex.getResponseBytes());
  }

  @Test
  public void testPost() throws IOException {
    byte[] request = "Are you still there?".getBytes(charset);
    byte[] response = "Hello, world!".getBytes(charset);
    MockHttpHandler mockHandler = new MockHttpHandler(200, response);
    server.createContext("/post", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("POST", "example.com",
        "/proxy/post", context);
    ex.setRequestBody(request);
    handler.handle(ex);

    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(request, mockHandler.getRequestBytes());
    assertArrayEquals(response, ex.getResponseBytes());
  }

  @Test
  public void testProxyXForwardedFor() throws IOException {
    MockHttpHandler mockHandler = new MockHttpHandler(200, null);
    server.createContext("/get", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("HEAD", "example.com",
        "/proxy/get", context);
    ex.getRequestHeaders().add("X-Forwarded-For", "10.0.0.4");
    handler.handle(ex);

    assertEquals(200, ex.getResponseCode());
    assertEquals(Arrays.asList("10.0.0.4", "127.0.0.3"),
        mockHandler.getRequestHeaders().get("X-Forwarded-For"));
  }

  @Test
  public void testIpv6Host() throws IOException {
    MockHttpHandler mockHandler = new MockHttpHandler(200, null);
    server.createContext("/get", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("HEAD", "example.com",
        "/proxy/get", context);
    ex.setRemoteAddress(new InetSocketAddress(
        InetAddress.getByAddress("remotehost",
          new byte[] {1, 2, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 15, 16}),
        65000));
    handler.handle(ex);

    assertEquals(200, ex.getResponseCode());
    assertEquals(Arrays.asList("102:0:0:8:0:0:0:f10"),
        mockHandler.getRequestHeaders().get("X-Forwarded-For"));
  }

  @Test
  public void test500() throws IOException {
    byte[] response = "error!".getBytes(charset);
    MockHttpHandler mockHandler = new MockHttpHandler(500, response);
    server.createContext("/get", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("GET", "example.com",
        "/proxy/get", context);
    handler.handle(ex);

    assertEquals(500, ex.getResponseCode());
    assertArrayEquals(response, ex.getResponseBytes());
  }

  @Test
  public void test404() throws IOException {
    byte[] response = "not there".getBytes(charset);
    MockHttpHandler mockHandler = new MockHttpHandler(404, response);
    server.createContext("/get", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("GET", "example.com",
        "/proxy/get", context);
    handler.handle(ex);

    assertEquals(404, ex.getResponseCode());
    assertArrayEquals(response, ex.getResponseBytes());
  }

  @Test
  public void test401Post() throws IOException {
    byte[] request = "Are you still there?".getBytes(charset);
    byte[] response = "not authorized".getBytes(charset);
    byte[] response2 = "authorized".getBytes(charset);
    MockHttpHandler mockHandler = new MockHttpHandler(401, response);
    MockHttpHandler mockHandler2 = new MockHttpHandler(200, response2);
    HttpHandler authHandler = new AuthHandler(mockHandler, mockHandler2);
    server.createContext("/post", authHandler);
    MockHttpExchange ex = new MockHttpExchange("POST", "example.com",
        "/proxy/post", context);
    ex.setRequestBody(request);
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication("user", "pass".toCharArray());
      }
    });
    try {
      handler.handle(ex);
    } finally {
      Authenticator.setDefault(null);
    }

    assertEquals(401, ex.getResponseCode());
    // The response body is known-broken due to HttpURLConnection.
    assertArrayEquals(new byte[0], ex.getResponseBytes());
    // However, the headers are properly captured.
    assertTrue(ex.getResponseHeaders().containsKey("Www-Authenticate"));
  }

  @Test
  public void test401GetAuthenticator() throws IOException {
    byte[] response = "not authorized".getBytes(charset);
    byte[] response2 = "authorized".getBytes(charset);
    MockHttpHandler mockHandler = new MockHttpHandler(401, response);
    MockHttpHandler mockHandler2 = new MockHttpHandler(200, response2);
    HttpHandler authHandler = new AuthHandler(mockHandler, mockHandler2);
    server.createContext("/get", authHandler);
    MockHttpExchange ex = new MockHttpExchange("GET", "example.com",
        "/proxy/get", context);
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication("user", "pass".toCharArray());
      }
    });
    try {
      handler.handle(ex);
    } finally {
      Authenticator.setDefault(null);
    }

    assertEquals(401, ex.getResponseCode());
    assertArrayEquals(response, ex.getResponseBytes());
  }

  @Test
  public void test401GetNoAuthenticator() throws IOException {
    byte[] response = "not authorized".getBytes(charset);
    byte[] response2 = "authorized".getBytes(charset);
    MockHttpHandler mockHandler = new MockHttpHandler(401, response);
    MockHttpHandler mockHandler2 = new MockHttpHandler(200, response2);
    HttpHandler authHandler = new AuthHandler(mockHandler, mockHandler2);
    server.createContext("/get", authHandler);
    MockHttpExchange ex = new MockHttpExchange("GET", "example.com",
        "/proxy/get", context);
    Authenticator.setDefault(null);
    handler.handle(ex);

    assertEquals(401, ex.getResponseCode());
    assertArrayEquals(response, ex.getResponseBytes());
  }

  @Test
  public void testRedirect() throws IOException {
    byte[] response = "test response".getBytes(charset);
    Headers goldenResponseHeaders = new Headers();
    goldenResponseHeaders.add("Date", MockHttpExchange.HEADER_DATE_VALUE);
    goldenResponseHeaders.add("Location", "http://example.com");

    Map<String, List<String>> responseHeaders
        = new HashMap<String, List<String>>();
    responseHeaders.put("Location", Arrays.asList("http://example.com"));
    MockHttpHandler mockHandler
        = new MockHttpHandler(307, response, responseHeaders);
    server.createContext("/get", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("GET", "example.com",
        "/proxy/get", context);
    handler.handle(ex);

    assertEquals(307, ex.getResponseCode());
    assertHeadersEquals(goldenResponseHeaders, ex.getResponseHeaders());
    assertArrayEquals(response, ex.getResponseBytes());
  }

  @Test
  public void testReverseRedirect() throws IOException {
    byte[] response = "test response".getBytes(charset);
    Headers goldenResponseHeaders = new Headers();
    goldenResponseHeaders.add("Date", MockHttpExchange.HEADER_DATE_VALUE);
    goldenResponseHeaders.add("Location", "http://example.com/proxy/page");

    Map<String, List<String>> responseHeaders
        = new HashMap<String, List<String>>();
    responseHeaders.put("Location",
        Arrays.asList("http://localhost:" + port + "/page"));
    MockHttpHandler mockHandler
        = new MockHttpHandler(307, response, responseHeaders);
    server.createContext("/get", mockHandler);
    MockHttpExchange ex = new MockHttpExchange("GET", "example.com",
        "/proxy/get", context);
    handler.handle(ex);

    assertEquals(307, ex.getResponseCode());
    assertHeadersEquals(goldenResponseHeaders, ex.getResponseHeaders());
    assertArrayEquals(response, ex.getResponseBytes());
  }

  private static void assertHeadersEquals(Headers golden, Headers header) {
    if (!Objects.equal(golden, header)) {
      fail("expected:" + new TreeMap<String, List<String>>(golden)
          + " but was:" + new TreeMap<String, List<String>>(header));
    }
  }

  private static class AuthHandler implements HttpHandler {
    private final HttpHandler needToAuth;
    private final HttpHandler authed;

    public AuthHandler(HttpHandler needToAuth, HttpHandler authed) {
      this.needToAuth = needToAuth;
      this.authed = authed;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
      if (ex.getRequestHeaders().containsKey("Authorization")) {
        authed.handle(ex);
      } else {
        ex.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"test\"");
        needToAuth.handle(ex);
      }
    }
  }
}
