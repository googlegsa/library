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
package com.google.enterprise.adaptor.experimental;

import com.google.enterprise.adaptor.IOHelper;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Accepts adaptor feeds and issues requests for documents. */
public class Sim implements Runnable {
  private static Logger log
      = Logger.getLogger(Sim.class.getName());
  static final Charset UTF8 = Charset.forName("UTF-8");

  private static class Index {
    Set<URL> urls = new HashSet<URL>();
    Map<URL, byte[]> content = new HashMap<URL, byte[]>();
    Map<URL, String> type = new HashMap<URL, String>(); // could be null
    Map<URL, Map<String, String>> meta
        = new HashMap<URL, Map<String, String>>();
  }

  private Index index = new Index(); // contains contents and metadata

  private void startFeedAcceptor() throws IOException {
    log.info("starting feed acceptor");
    HttpServer server = HttpServer.create();
    int useDefaultBacklog = -1;
    server.bind(new InetSocketAddress(19900), useDefaultBacklog);
    server.createContext("/xmlfeed", new FeedAcceptor());
    server.start();
    log.info("started feed acceptor");
  }

  private void startCrawler() {
    log.info("starting crawler");
    new Thread(new Crawler()).start();
    log.info("started crawler");
  }

  public void run() {
    try {
      startFeedAcceptor();
    } catch (IOException ie) {
      throw new RuntimeException("failed to start feed acceptor", ie);
    }
    startCrawler();
    // TODO: add serving
    // TODO: add stops to exit
  }

  public static void main(String args[]) {
    new Sim().run(); 
  }



  /** Takes multipart POST with metadata-and-url xml feed. */
  private class FeedAcceptor implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
      log.info("in feed acceptor");
      String requestMethod = ex.getRequestMethod();
      if (!"POST".equals(requestMethod)) {
        log.info("received non-post method: " + requestMethod);
        respond(ex, HttpURLConnection.HTTP_BAD_METHOD,
            "text/plain", "server accepts POST only".getBytes(UTF8));
      } else {
        URI req = com.google.enterprise.adaptor.HttpExchanges.getRequestUri(ex);
        log.info("received post on path: " + req.getPath());
        processMultipartPost(ex); 
      }
    }
  }

  private static class NoXmlFound extends Exception {}
  private static class BadFeed extends Exception {
    BadFeed(String emsg) {
      super(emsg);
    }
  }

  /** Periodically acquires new content and metadata for each URL. */
  private class Crawler implements Runnable {
    private Set<URL> dupIndexUrls() {
      synchronized (index.urls) {
        return new HashSet<URL>(index.urls);  
      }
    }

    public void run() {
      for (;;) {
        log.info("crawler about to hibernate");
        try {
          Thread.sleep(1000 * 20);
        } catch (InterruptedException terup) {
          log.info("crawler awoken early");
        }
        log.info("crawler is awake");
        for (URL doc : dupIndexUrls()) {
          crawl(doc);
        }
      }
    }

    private void crawl(URL doc) {
      log.info("about to crawl: " + doc);
      try {
        URLConnection con = doc.openConnection();
        byte content[]
            = IOHelper.readInputStreamToByteArray(con.getInputStream());
        index.content.put(doc, content);
        Map<String, List<String>> headers = con.getHeaderFields();
        List<String> ct = headers.get("Content-type");
        index.type.put(doc, (null != ct && ct.size() > 0) ? ct.get(0) : null);
        index.meta.put(doc, parseMeta(headers.get("X-gsa-external-metadata")));
        for (String k : headers.keySet()) {
          log.info("header: " + k + ":" + headers.get(k));
        }
        log.info("crawled: " + doc);
      } catch (IOException ie) {
        log.info("failed getting: " + doc);
      }
    }

    private Map<String, String> parseMeta(List<String> metadata) {
      Map<String, String> map = new HashMap<String, String>();
      if (null != metadata) {
        for (String m : metadata) {
          if (null != m) {
            parseMeta(map, m);
          }
        } 
      }
      return map;
    }

    private void parseMeta(Map<String, String> map, String metadatum) {
      String metadatums[] = metadatum.split(",", 0);
      switch (metadatums.length) {
        case 0:
          break;
        case 1: // really have single metadatum
          String m = metadatums[0];
          int splitPoint = m.indexOf('=');
          if (-1 == splitPoint) {
            log.info("skipping metadatum: " + m);
            return;
          }
          String key = m.substring(0, splitPoint);
          String value = m.substring(splitPoint + 1);
          key = percentDecode(key);
          value = percentDecode(value);
          log.info("key: " + key);
          log.info("value: " + value);
          map.put(key, value);
          break;
        default: // have multiple pieces split by comma
          for (String p : metadatums) {
            parseMeta(map, p);
          }
      }
    }
  }

  private void processMultipartPost(HttpExchange ex) throws IOException {
    InputStream inStream = ex.getRequestBody();
    String encoding = ex.getRequestHeaders().getFirst("Content-encoding");
    if (null != encoding && "gzip".equals(encoding.toLowerCase())) {
      inStream = new GZIPInputStream(inStream);
    }
    String lens = ex.getRequestHeaders().getFirst("Content-Length");
    int len = (null != lens) ? Integer.parseInt(lens) : 0;
    String ct = ex.getRequestHeaders().getFirst("Content-Type");
    try {
      String xml = extractFeedFromMultipartPost(inStream, len, ct);
      processXml(xml);
      respond(ex, HttpURLConnection.HTTP_OK,
          "text/plain", "Success".getBytes(UTF8));
    } catch (NoXmlFound nox) {
      log.warning("failed to find xml");
      respond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
          "text/plain", "xml beginning not found".getBytes(UTF8));
    } catch (SAXException saxe) {
      log.warning("sax error: " + saxe.getMessage());
      respond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
          "text/plain", "sax not liking the xml".getBytes(UTF8));
    } catch (ParserConfigurationException confige) {
      log.warning("parser error: " + confige.getMessage());
      respond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
          "text/plain", "parser error".getBytes(UTF8));
    } catch (BadFeed bad) {
      log.warning("error in feed: " + bad.getMessage());
      respond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
          "text/plain", bad.getMessage().getBytes(UTF8));
    }
  }

  private void processXml(String xml) throws SAXException,
      ParserConfigurationException, BadFeed {
    Set<URL> tmpUrls = extractUrls(xml);
    synchronized (index.urls) {
      index.urls.addAll(tmpUrls);
    }
  }

  static String extractFeedFromMultipartPost(
      InputStream in, int len, String contentType) throws NoXmlFound {
    HttpExchangeUploadInfo uploadInfo
        = new HttpExchangeUploadInfo(in, len, contentType);
    try {
      Map<String, byte[]> parts = splitMultipartRequest(uploadInfo);
      if (!parts.containsKey("data")) {
        throw new NoXmlFound();
      }
      return new String(parts.get("data"), UTF8);
    } catch (IOException ie) {
      throw new NoXmlFound();
    }
  }

  /** Find all record urls in Adaptor created XML metadata-and-url feed file. */
  static Set<URL> extractUrls(String xml) throws SAXException,
      ParserConfigurationException, BadFeed {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    /* to avoid blowing up on doctype line:
     * http://stackoverflow.com/questions/155101/make-documentbuilder-parse-ignore-dtd-references */
    dbf.setValidating(false);
    dbf.setFeature("http://xml.org/sax/features/namespaces", false);
    dbf.setFeature("http://xml.org/sax/features/validation", false);
    dbf.setFeature(
        "http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
    dbf.setFeature(
        "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    DocumentBuilder db = dbf.newDocumentBuilder();
    InputStream xmlStream = new ByteArrayInputStream(xml.getBytes(UTF8));
    Document doc;
    try {
      doc = db.parse(xmlStream);
    } catch (IOException ie) {
      throw new BadFeed(ie.getMessage());
    }
    doc.getDocumentElement().normalize();
    NodeList nodes = doc.getElementsByTagName("record");
    Set<URL> tmpUrls = new HashSet<URL>();
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      String url = element.getAttribute("url");
      if (null == url || url.trim().isEmpty()) {
        throw new BadFeed("record without url attribute"); 
      } else {
        try {
          tmpUrls.add(new URL(url));
        } catch (MalformedURLException male) {
          throw new BadFeed("record with bad url attribute: " + url);
        }
      }
      log.info("accepting url: " + url);
    }
    return tmpUrls;
  }

  /** Send some response body. */
  private static void respond(HttpExchange ex, int code, String contentType,
      byte response[]) throws IOException {
    ex.getResponseHeaders().set("Content-Type", contentType);
    ex.sendResponseHeaders(code, 0);
    OutputStream responseBody = ex.getResponseBody();
    log.finest("before writing response");
    responseBody.write(response);
    responseBody.flush();
    responseBody.close();
    ex.close();
    log.finest("after closing exchange");
  }

  /** Intermediary from an HttpExchange to FileUpload input. */
  private static class HttpExchangeUploadInfo implements RequestContext {
    InputStream inStream;
    int length;
    String contentType;
    HttpExchangeUploadInfo(InputStream is, int len, String ct) {
      this.inStream = is;
      this.length = len;
      this.contentType = ct;
    } 

    public String getCharacterEncoding() {
      // TODO: get from exchange?
      return "UTF-8";
    } 

    public String getContentType() {
      return contentType;
    } 

    public int getContentLength() {
      return length;
    }

    public InputStream getInputStream() throws IOException {
      return inStream; 
    }
  }

  static Map<String, byte[]> splitMultipartRequest(RequestContext req)
      throws IOException {
    Map<String, byte[]> parts = new HashMap<String, byte[]>();
    try {
      FileUpload upload = new FileUpload();
      FileItemIterator iterator = upload.getItemIterator(req);
      while (iterator.hasNext()) {
        FileItemStream item = iterator.next();
        String field = item.getFieldName();
        byte value[] = 
            IOHelper.readInputStreamToByteArray(item.openStream());
        parts.put(field, value);
      }
      return parts;
    } catch (FileUploadException e) {
      throw new IOException("caught FileUploadException", e);
    }
  }

  private static int hexToInt(byte b) {
    if (b >= '0' && b <= '9') {
      return (byte) (b - '0');
    } else if (b >= 'a' && b <= 'f') {
      return (byte) (b - 'a') + 10;
    } else if (b >= 'A' && b <= 'F') {
      return (byte) (b - 'A') + 10;
    } else {
      throw new IllegalArgumentException("invalid hex byte: " + b);
    }
  }

  public static String percentDecode(String encoded) {
    try {
      byte bytes[] = encoded.getBytes("ASCII");
      ByteArrayOutputStream decoded = percentDecode(bytes);
      return decoded.toString("UTF-8");
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException(uee);
    }
  }

  static ByteArrayOutputStream percentDecode(byte encoded[]) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int i = 0;
    while (i < encoded.length) {
      byte b = encoded[i];
      if (b == '%') {
        int iNeeded = i + 2;  // need two more bytes
        if (iNeeded >= encoded.length) {
          throw new IllegalArgumentException("ends too early");
        }
        int highOrder = hexToInt(encoded[i + 1]);
        int lowOrder = hexToInt(encoded[i + 2]);
        int byteInInt = (highOrder << 4) | lowOrder;
        b = (byte) byteInInt;  // chops top bytes; could make negative
        i += 3;
      } else if ((b >= 'a' && b <= 'z')
          || (b >= 'A' && b <= 'Z')
          || (b >= '0' && b <= '9')
          || b == '-' || b == '_' || b == '.' || b == '~') {
        // pass through
        i++;
      } else {
        throw new IllegalArgumentException("not percent encoded");
      }
      out.write(b);
    }
    return out;
  }
}
