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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;

/**
 * Test cases for {@link GsaFeedFileSender}.
 */
public class GsaFeedFileSenderTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Config config = new Config();
  private GsaFeedFileSender sender = new GsaFeedFileSender(config);
  private int port;
  private URL messageAndUrlUrl;  // url of metadata-and-url acceptor
  private URL groupsUrl;  // url of group definitions acceptor
  private HttpServer server;
  private Charset charset = Charset.forName("UTF-8");

  private void startup() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();
    messageAndUrlUrl = new URL("http://localhost:" + port + "/xmlfeed");
    groupsUrl = new URL("http://localhost:" + port + "/xmlgroups");
    server.start();
  }

  @After
  public void shutdown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  public void testMetadataAndUrlSuccess() throws Exception {
    final String payload = "<someXmlString/>";
    final String datasource = "test-DataSource_09AZaz";
    final String goldenResponse
        = "--<<\r\n"
        + "Content-Disposition: form-data; name=\"datasource\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + datasource + "\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"feedtype\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "metadata-and-url\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"data\"\r\n"
        + "Content-Type: text/xml\r\n"
        + "\r\n"
        + payload + "\r\n"
        + "--<<--\r\n";

    startup();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    sender.sendMetadataAndUrl(messageAndUrlUrl, datasource, payload, false);
    assertEquals("POST", handler.getRequestMethod());
    assertEquals(URI.create("/xmlfeed"), handler.getRequestUri());
    assertEquals("multipart/form-data; boundary=<<",
        handler.getRequestHeaders().getFirst("Content-Type"));
    assertEquals(goldenResponse,
        new String(handler.getRequestBytes(), charset));
  }

  @Test
  public void testMetadataAndUrlHttpsSuccess() throws Exception {
    final String payload = "<someXmlString/>";
    final String datasource = "testDataSource";
    final String goldenResponse
        = "--<<\r\n"
        + "Content-Disposition: form-data; name=\"datasource\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + datasource + "\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"feedtype\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "metadata-and-url\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"data\"\r\n"
        + "Content-Type: text/xml\r\n"
        + "\r\n"
        + payload + "\r\n"
        + "--<<--\r\n";

    // Unfortunately this test requires a fixed port.
    server = HttpsServer.create(new InetSocketAddress(19902), 0);
    HttpsConfigurator httpsConf
        = new HttpsConfigurator(SSLContext.getDefault());
    ((HttpsServer) server).setHttpsConfigurator(httpsConf);
    config.setValue("server.secure", "true");
    server.start();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    sender.sendMetadataAndUrl("localhost", datasource, payload, false);
    assertEquals("POST", handler.getRequestMethod());
    assertEquals(URI.create("/xmlfeed"), handler.getRequestUri());
    assertEquals("multipart/form-data; boundary=<<",
        handler.getRequestHeaders().getFirst("Content-Type"));
    assertEquals(goldenResponse,
        new String(handler.getRequestBytes(), charset));
  }

  @Test
  public void testMetadataAndUrlSuccessGzipped() throws Exception {
    final String payload = "<someXmlString/>";
    final String datasource = "testDataSource";
    final String goldenResponse
        = "--<<\r\n"
        + "Content-Disposition: form-data; name=\"datasource\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + datasource + "\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"feedtype\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "metadata-and-url\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"data\"\r\n"
        + "Content-Type: text/xml\r\n"
        + "\r\n"
        + payload + "\r\n"
        + "--<<--\r\n";

    startup();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    sender.sendMetadataAndUrl(messageAndUrlUrl, datasource, payload, true);
    assertEquals("POST", handler.getRequestMethod());
    assertEquals(URI.create("/xmlfeed"), handler.getRequestUri());
    assertEquals("multipart/form-data; boundary=<<",
        handler.getRequestHeaders().getFirst("Content-Type"));
    assertEquals("gzip",
        handler.getRequestHeaders().getFirst("Content-Encoding"));
    InputStream uncompressed = new GZIPInputStream(
        new ByteArrayInputStream(handler.getRequestBytes()));
    String response = new String(
        IOHelper.readInputStreamToByteArray(uncompressed), charset);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testMetadataAndUrlInvalidDataSource() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    sender.sendMetadataAndUrl("localhost", "bad#source", "<payload/>", false);
  }

  @Test
  public void testMetadataAndUrlInvalidDataSource2() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    sender.sendMetadataAndUrl("localhost", "9badsource", "<payload/>", false);
  }

  @Test(expected = IOException.class)
  public void testMetadataAndUrlBadHost() throws Exception {
    sender.sendMetadataAndUrl("fakehost!@#", "datasource", "<payload/>", false);
  }

  @Test(expected = IOException.class)
  public void testMetadataAndUrlPortNotOpen() throws Exception {
    sender.sendMetadataAndUrl("localhost", "datasource", "<payload/>", false);
  }

  @Test(expected = IOException.class)
  public void testMetadataAndUrlInvalidUrl() throws Exception {
    sender.sendMetadataAndUrl("badname:", "datasource", "<payload/>", false);
  }

  @Test
  public void testMetadataAndUrlFailureWriting() throws Exception {
    startup();
    server.createContext("/xmlfeed", new HttpHandler() {
      @Override
      public void handle(HttpExchange ex) throws IOException {
        throw new IOException();
      }
    });
    String longMsg = "Some random really long string\n";
    int numRepeats = 1024 * 256;
    StringBuilder sb = new StringBuilder(longMsg.length() * numRepeats);
    for (int i = 0; i < numRepeats; i++) {
      sb.append(longMsg);
    }
    // This payload has to be enough to exhaust output buffers, otherwise the
    // exception will be noticed when reading the response.
    String longPayload = sb.toString();
    sb = null;
    thrown.expect(IOException.class);
    sender.sendMetadataAndUrl(messageAndUrlUrl, "datasource", longPayload, false);
  }

  @Test
  public void testMetadataAndUrlGsaReturnedFailure() throws Exception {
    startup();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Some failure".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    thrown.expect(IllegalStateException.class);
    sender.sendMetadataAndUrl(messageAndUrlUrl, "datasource", "<payload/>", false);
  }

  @Test
  public void testMetadataAndUrlCantReadResponse() throws Exception {
    startup();
    server.createContext("/xmlfeed", new HttpHandler() {
      @Override
      public void handle(HttpExchange ex) throws IOException {
        throw new IOException();
      }
    });

    thrown.expect(IOException.class);
    sender.sendMetadataAndUrl(messageAndUrlUrl, "datasource", "<payload/>", false);
  }

  @Test
  public void testGroupsSuccess() throws Exception {
    final String payload = "<someXmlString/>";
    final String groupsource = "docspot";
    final String goldenResponse
        = "--<<\r\n"
        + "Content-Disposition: form-data; name=\"groupsource\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + groupsource + "\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"data\"\r\n"
        + "Content-Type: text/xml\r\n"
        + "\r\n"
        + payload + "\r\n"
        + "--<<--\r\n";
    startup();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlgroups", handler);
    sender.sendGroups(groupsUrl, groupsource, payload, false);
    assertEquals("POST", handler.getRequestMethod());
    assertEquals(URI.create("/xmlgroups"), handler.getRequestUri());
    assertEquals("multipart/form-data; boundary=<<",
        handler.getRequestHeaders().getFirst("Content-Type"));
    assertEquals(goldenResponse,
        new String(handler.getRequestBytes(), charset));
  }

  @Test
  public void testGroupsInvalidGroupSource() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    sender.sendGroups("localhost", "bad#source", "<payload/>", false);
  }

  @Test
  public void testGroupsInvalidGroupSource2() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    sender.sendGroups("localhost", "iamtoolongnow", "<payload/>", false);
  }

  private static class MockHttpHandler implements HttpHandler {
    private final int responseCode;
    private final byte[] responseBytes;
    private String requestMethod;
    private URI requestUri;
    private Headers requestHeaders;
    private byte[] requestBytes;

    public MockHttpHandler(int responseCode, byte[] responseBytes) {
      this.responseCode = responseCode;
      this.responseBytes = responseBytes;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
      requestMethod = ex.getRequestMethod();
      requestUri = ex.getRequestURI();
      requestHeaders = new Headers();
      requestHeaders.putAll(ex.getRequestHeaders());
      requestBytes = IOHelper.readInputStreamToByteArray(ex.getRequestBody());
      ex.sendResponseHeaders(responseCode, responseBytes == null ? -1 : 0);
      if (responseBytes != null) {
        ex.getResponseBody().write(responseBytes);
        ex.getResponseBody().flush();
        ex.getResponseBody().close();
      }
      ex.close();
    }

    public String getRequestMethod() {
      return requestMethod;
    }

    public URI getRequestUri() {
      return requestUri;
    }

    public Headers getRequestHeaders() {
      return requestHeaders;
    }

    public byte[] getRequestBytes() {
      return requestBytes;
    }
  }
}
