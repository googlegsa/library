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

import static org.junit.Assert.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;

/**
 * Test cases for {@link GsaFeedFileSender}.
 */
public class GsaFeedFileSenderTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Charset charset = Charset.forName("UTF-8");
  private HttpServer server;
  private int port;
  private GsaFeedFileSender sender;

  @Before
  public void startup() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();
    server.start();
    URL metadataAndUrlUrl = new URL("http://localhost:" + port + "/xmlfeed");
    URL groupsUrl = new URL("http://localhost:" + port + "/xmlgroups");
    sender = new GsaFeedFileSender(metadataAndUrlUrl, groupsUrl, charset);
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

    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    sender.sendMetadataAndUrl(datasource, payload, false);
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
    server.start();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    URL metadataAndUrlUrl = new URL("https://localhost:19902/xmlfeed");
    URL groupsUrl = new URL("https://localhost:19902/xmlgroups");
    GsaFeedFileSender secureSender = new GsaFeedFileSender(metadataAndUrlUrl,
        groupsUrl, charset);
    secureSender.sendMetadataAndUrl(datasource, payload, false);
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

    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    sender.sendMetadataAndUrl(datasource, payload, true);
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
    sender.sendMetadataAndUrl("bad#source", "<payload/>", false);
  }

  @Test
  public void testMetadataAndUrlInvalidDataSource2() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    sender.sendMetadataAndUrl("9badsource", "<payload/>", false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMetadataAndUrlInvalidUrl() throws Exception {
    new GsaFeedFileSender("badname:", false, charset);
  }

  @Test
  public void testMetadataAndUrlFailureWriting() throws Exception {
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
    sender.sendMetadataAndUrl("datasource", longPayload, false);
  }

  @Test
  public void testMetadataAndUrlGsaReturnedFailure() throws Exception {
    MockHttpHandler handler
        = new MockHttpHandler(200, "Some failure".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    thrown.expect(IllegalStateException.class);
    sender.sendMetadataAndUrl("datasource", "<payload/>", false);
  }

  @Test
  public void testMetadataAndUrlCantReadResponse() throws Exception {
    server.createContext("/xmlfeed", new HttpHandler() {
      @Override
      public void handle(HttpExchange ex) throws IOException {
        throw new IOException();
      }
    });

    thrown.expect(IOException.class);
    sender.sendMetadataAndUrl("datasource", "<payload/>", false);
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
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlgroups", handler);
    sender.sendGroups(groupsource, payload, false);
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
    sender.sendGroups("bad#source", "<payload/>", false);
  }
}
