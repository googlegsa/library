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

import com.google.common.annotations.VisibleForTesting;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/** Utility class for working with {@link HttpExchange}s. */
public final class HttpExchanges {
  private static final Logger log
      = Logger.getLogger(HttpExchanges.class.getName());
  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
  /**
   * Default encoding to encode simple response messages.
   */
  private static final Charset ENCODING = Charset.forName("UTF-8");

  // DateFormats are relatively expensive to create, and cannot be used from
  // multiple threads
  /** RFC 822 date format, as updated by RFC 1123. */
  private static final ThreadLocal<DateFormat> dateFormatRfc1123
      = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
          DateFormat df = new SimpleDateFormat(
              "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
          df.setTimeZone(GMT);
          return df;
        }
      };
  /** RFC 1036 date format. */
  private static final ThreadLocal<DateFormat> dateFormatRfc1036
      = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
          DateFormat df = new SimpleDateFormat(
              "EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.ENGLISH);
          df.setTimeZone(GMT);
          return df;
        }
      };
  /** ANSI C's {@code asctime()} format. */
  private static final ThreadLocal<DateFormat> dateFormatAsctime
      = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
          DateFormat df = new SimpleDateFormat(
              "EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);
          df.setTimeZone(GMT);
          return df;
        }
      };
  /** The various date formats as required by RFC 2616 3.3.1. */
  private static final List<ThreadLocal<DateFormat>> dateFormatsRfc2616;
  /**
   * When thread-local value is not {@code null}, signals that {@link #handle}
   * should abort immediately with an error. This is a hack required because the
   * HttpServer can't handle the Executor rejecting execution of a runnable.
   */
  static final ThreadLocal<Object> abortImmediately
      = new ThreadLocal<Object>();

  static {
    List<ThreadLocal<DateFormat>> tmpList
        = new ArrayList<ThreadLocal<DateFormat>>();
    tmpList.add(dateFormatRfc1123);
    tmpList.add(dateFormatRfc1036);
    tmpList.add(dateFormatAsctime);
    dateFormatsRfc2616 = Collections.unmodifiableList(tmpList);
  }

  // Prevent initialization.
  private HttpExchanges() {}

  /** Clear ThreadLocal state to test construction of those variables */
  @VisibleForTesting
  static void resetThread() {
    dateFormatRfc1123.remove();
    dateFormatRfc1036.remove();
    dateFormatAsctime.remove();
    abortImmediately.remove();
  }

  /**
   * Best-effort attempt to reform the identical URI the client used to
   * contact the server.
   */
  public static URI getRequestUri(HttpExchange ex) {
    String host = ex.getRequestHeaders().getFirst("Host");
    if (host == null) {
      // Client must be using HTTP/1.0
      log.warning(
          "Request did not provide Host header, using 'localhost' as hostname");
      int port = ex.getHttpContext().getServer().getAddress().getPort();
      host = "localhost:" + port;
    }
    String protocol = (ex.getHttpContext().getServer() instanceof HttpsServer)
        ? "https" : "http";
    URI base;
    try {
      base = new URI(protocol, host, "/", null, null);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
    URI requestedUri = ex.getRequestURI();
    // If uri is already absolute (e.g., a proxy is involved), then this
    // does nothing, otherwise it resolves the URI for us based on who we
    // think we are
    requestedUri = base.resolve(requestedUri);
    log.log(Level.FINER, "Resolved original URI to: {0}", requestedUri);
    return requestedUri;
  }

  /**
   * Sends response to GSA. Should only be used when the request method is
   * HEAD.
   */
  static void respondToHead(HttpExchange ex, int code,
      String contentType) throws IOException {
    ex.getResponseHeaders().set("Transfer-Encoding", "chunked");
    respond(ex, code, contentType, (byte[]) null);
  }

  /**
   * Sends cheaply-generated response message to GSA. This is intended for use
   * with pre-build, canned messages. It automatically handles not sending the
   * actual content when the request method is HEAD. If the content requires
   * a moderate amount of work to produce, then you should manually call
   * {@link #respond} or {@link #respondToHead} depending on the situation.
   */
  static void cannedRespond(HttpExchange ex, int code, Translation response)
      throws IOException {
    // TODO(ejona): use exchange to decide on response language
    cannedRespond(ex, code, "text/plain", response.toString());
  }

  /**
   * Sends cheaply-generated response message to GSA. This is intended for use
   * with pre-build, canned messages. It automatically handles not sending the
   * actual content when the request method is HEAD. If the content requires
   * a moderate amount of work to produce, then you should manually call
   * {@link #respond} or {@link #respondToHead} depending on the situation.
   */
  static void cannedRespond(HttpExchange ex, int code, Translation response,
      Object... params) throws IOException {
    // TODO(ejona): use exchange to decide on response language
    cannedRespond(ex, code, "text/plain", response.toString(params));
  }

  private static void cannedRespond(HttpExchange ex, int code,
      String contentType, String response) throws IOException {
    if ("HEAD".equals(ex.getRequestMethod())) {
      respondToHead(ex, code, contentType);
    } else {
      respond(ex, code, contentType, response.getBytes(ENCODING));
    }
  }

  /**
   * Sends headers and configures {@code ex} for (possibly) sending content.
   * Completing the request is the caller's responsibility.
   */
  static void startResponse(HttpExchange ex, int code,
      String contentType, boolean hasBody) throws IOException {
    log.finest("Starting response");
    if (contentType != null) {
      ex.getResponseHeaders().set("Content-Type", contentType);
    }
    if (!hasBody) {
      // No body. Required for HEAD requests
      ex.sendResponseHeaders(code, -1);
    } else {
      // Chuncked encoding
      ex.sendResponseHeaders(code, 0);
    }
  }

  /**
   * Sends response to GSA. Should not be used directly if the request method
   * is HEAD.
   */
  static void respond(HttpExchange ex, int code, String contentType,
      byte response[]) throws IOException {
    startResponse(ex, code, contentType, response != null);
    if (response != null) {
      OutputStream responseBody = ex.getResponseBody();
      log.finest("before writing response");
      responseBody.write(response);
      responseBody.flush();
      // This shouldn't be needed, but without it one developer had trouble
      responseBody.close();
      log.finest("after writing response");
    }
    ex.close();
    log.finest("after closing exchange");
  }

  /**
   * Redirect client to {@code location}. The client should retrieve the
   * referred location via GET, independent of the method of this request.
   */
  public static void sendRedirect(HttpExchange ex, URI location)
      throws IOException {
    ex.getResponseHeaders().set("Location", location.toString());
    respond(ex, HttpURLConnection.HTTP_SEE_OTHER, null, null);
  }

  /**
   * Parses multi-valued header fields that can also be represented as
   * comma-separated lists. Leading and trailing whitespace is removed from each
   * value. This is the required format for any repeated header, as specified in
   * RFC 2616 Section 4.2.
   *
   * @return list of split and cleaned values, or {@code null} if no such
   *   headers were specified
   */
  static List<String> splitHeaderValues(List<String> values) {
    if (values == null) {
      return null;
    }
    List<String> parsed = new ArrayList<String>(values.size());
    for (String value : values) {
      String[] parts = value.split(",", -1);
      for (String part : parts) {
        parsed.add(part.trim());
      }
    }
    return Collections.unmodifiableList(parsed);
  }

  /**
   * If the client supports it, set the correct headers and streams to provide
   * GZIPed response data to the client. Because the content may become
   * compressed, users of this method should generally use a {@code
   * responseLength} of {@code 0} when calling {@link
   * HttpExchange#sendResponseHeaders}. The exception is when responding to a
   * HEAD request, in which {@code -1} is required.
   */
  public static void enableCompressionIfSupported(HttpExchange ex)
      throws IOException {
    Collection<String> encodings
        = splitHeaderValues(ex.getRequestHeaders().get("Accept-Encoding"));
    if (encodings == null) {
      return;
    }
    if (encodings.contains("gzip")) {
      log.finer("Enabling gzip compression for response");
      ex.getResponseHeaders().set("Content-Encoding", "gzip");
      // Although the documentation states that getResponseBody() can only be
      // called after sendResponseHeaders(), this is not actually the case.
      // Being able to call getResponseBody() before sendResponseHeaders() is
      // the only way for filters to function and so is supported even though
      // the documentation says otherwise.
      final OutputStream os = ex.getResponseBody();
      ex.setStreams(null, new AbstractLazyOutputStream() {
        @Override
        protected OutputStream retrieveOs() throws IOException {
          // Creating the GZIPOutputStream must happen after sendResponseHeaders
          // since the constructor writes data to the provided OutputStream.
          return new GZIPOutputStream(os);
        }
      });
    }
  }

  /**
   * Retrieves and parses the If-Modified-Since from the request, returning null
   * if there was no such header or there was an error.
   */
  public static Date getIfModifiedSince(HttpExchange ex) {
    String ifModifiedSince
        = ex.getRequestHeaders().getFirst("If-Modified-Since");
    if (ifModifiedSince == null) {
      return null;
    }
    for (ThreadLocal<DateFormat> threadLocal : dateFormatsRfc2616) {
      try {
        return threadLocal.get().parse(ifModifiedSince);
      } catch (java.text.ParseException e) {
        // Ignore and try another format. We expect only to encounter the first
        // format, however (the other formats are pre-HTTP/1.1).
        log.log(Level.FINE, "Exception when parsing If-Modified-Since", e);
      }
    }
    log.log(Level.WARNING, "Could not parse If-Modified-Since: {0}",
        ifModifiedSince);
    return null;
  }

  /**
   * Determines if the headers have already been sent for the exchange.
   *
   * <p>This implementation currently uses an imperfect heuristic, but should
   * work well in most cases. It checks to see if the Date header is present,
   * which is added during {@link HttpExchange#sendResponseHeaders}.
   */
  public static boolean headersSent(HttpExchange ex) {
    return ex.getResponseHeaders().getFirst("Date") != null;
  }

  public static void setLastModified(HttpExchange ex, Date lastModified) {
    ex.getResponseHeaders().set("Last-Modified",
                                dateFormatRfc1123.get().format(lastModified));

  }

  /**
   * Parse request GET query parameters of {@code ex} into its parts, correctly
   * taking into account {@code charset}. The encoding of the GET parameters is
   * not specified in the request parameters, so it must be negotiated elsewhere
   * (i.e., via hard-coding). ISO 8859-1 (Latin-1) and UTF-8 are the only
   * commonly used encodings for query parameters.
   *
   * @param ex exchange whose request query string is to be parsed
   * @param charset character set used during encoding
   * @return fully-decoded parameter values
   */
  public static Map<String, List<String>> parseQueryParameters(HttpExchange ex,
      Charset charset) {
    String queryString = ex.getRequestURI().getRawQuery();
    if (queryString == null || queryString.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, List<String>> parsedParams
        = new TreeMap<String, List<String>>();
    for (String param : queryString.split("&")) {
      String[] parts = param.split("=", 2);
      String key = parts[0];
      String value = parts.length == 2 ? parts[1] : "";
      try {
        key = URLDecoder.decode(key, charset.name());
        value = URLDecoder.decode(value, charset.name());
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError(e);
      }
      List<String> values = parsedParams.get(key);
      if (values == null) {
        values = new LinkedList<String>();
        parsedParams.put(key, values);
      }
      values.add(value);
    }

    for (Map.Entry<String, List<String>> me : parsedParams.entrySet()) {
      me.setValue(Collections.unmodifiableList(me.getValue()));
    }
    return Collections.unmodifiableMap(parsedParams);
  }
}
