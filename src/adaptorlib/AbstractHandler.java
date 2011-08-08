package adaptorlib;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

abstract class AbstractHandler implements HttpHandler {
  private static final Logger log
      = Logger.getLogger(AbstractHandler.class.getName());
  // DateFormats are relatively expensive to create, and cannot be used from
  // multiple threads
  protected static ThreadLocal<DateFormat> dateFormat
      = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
          return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        }
      };

  // Numbers for logging incoming and completed communications.
  private int numberConnectionStarted = 0;
  private int numberConnectionFinished = 0;

  /**
   * The hostname is sometimes needed to generate the correct DocId; in the case
   * that it is needed and the host is an old HTTP/1.0 client, this value will
   * be used.
   */
  protected final String fallbackHostname;
  /**
   * Default encoding to encode simple response messages.
   */
  protected final Charset defaultEncoding;

  /**
   * @param fallbackHostname Fallback hostname in case we talk to an old HTTP
   *    client
   * @param defaultEncoding Encoding to use when sending simple text responses
   */
  protected AbstractHandler(String fallbackHostname, Charset defaultEncoding) {
    this.fallbackHostname = fallbackHostname;
    this.defaultEncoding = defaultEncoding;
  }

  protected String getLoggableRequestHeaders(HttpExchange ex) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> me
         : ex.getRequestHeaders().entrySet()) {
      for (String value : me.getValue()) {
        sb.append(me.getKey());
        sb.append(": ");
        sb.append(value);
        sb.append(", ");
      }
    }
    // Cut off trailing ", "
    return sb.substring(0, sb.length() - 2);
  }

  protected void logRequest(HttpExchange ex) {
    if (log.isLoggable(Level.FINER)) {
      log.log(Level.FINER, "Received {1} request to {0}. Headers: '{'{2}'}'",
              new Object[] {ex.getRequestURI(), ex.getRequestMethod(),
                            getLoggableRequestHeaders(ex)});
    }
  }

  /**
   * Best-effort attempt to reform the identical URI the client used to
   * contact the server.
   */
  protected URI getRequestUri(HttpExchange ex) {
    String host = ex.getRequestHeaders().getFirst("Host");
    if (host == null) {
      // Client must be using HTTP/1.0
      host = fallbackHostname;
    }
    URI base;
    try {
      base = new URI("http", host, "/", null, null);
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
  protected void respondToHead(HttpExchange ex, int code, String contentType)
      throws IOException {
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
  protected void cannedRespond(HttpExchange ex, int code, String contentType,
                               String response) throws IOException {
    if ("HEAD".equals(ex.getRequestMethod())) {
      respondToHead(ex, code, contentType);
    } else {
      respond(ex, code, contentType, response.getBytes(defaultEncoding));
    }
  }

  /**
   * Sends response to GSA. Should not be used directly if the request method
   * is HEAD.
   */
  protected void respond(HttpExchange ex, int code, String contentType,
                         byte response[]) throws IOException {
    if (contentType != null) {
      ex.getResponseHeaders().set("Content-Type", contentType);
    }
    if (response == null) {
      // No body. Required for HEAD requests
      ex.sendResponseHeaders(code, -1);
    } else {
      // Chuncked encoding
      ex.sendResponseHeaders(code, 0);
      // Check to see if enableCompressionIfSupported was called
      if ("gzip".equals(ex.getResponseHeaders().getFirst("Content-Encoding"))) {
        // Creating the GZIPOutputStream must happen after sendResponseHeaders
        // since the constructor writes data to the provided OutputStream
        ex.setStreams(null, new GZIPOutputStream(ex.getResponseBody()));
      }
      OutputStream responseBody = ex.getResponseBody();
      log.finest("before writing response");
      responseBody.write(response);
      // These shouldn't be needed, but without them one developer had trouble
      responseBody.flush();
      responseBody.close();
      log.finest("after writing response");
    }
    ex.close();
    log.finest("after closing exchange");
  }

  /**
   * If the client supports it, set the correct headers and make {@link
   * #respond} provide GZIPed response data to the client.
   */
  protected void enableCompressionIfSupported(HttpExchange ex)
      throws IOException {
    String encodingList = ex.getRequestHeaders().getFirst("Accept-Encoding");
    if (encodingList == null) {
      return;
    }
    Collection<String> encodings = Arrays.asList(encodingList.split(","));
    if (encodings.contains("gzip")) {
      log.finer("Enabling gzip compression for response");
      ex.getResponseHeaders().set("Content-Encoding", "gzip");
    }
  }

  /**
   * Retrieves and parses the If-Modified-Since from the request, returning null
   * if there was no such header or there was an error.
   */
  protected static Date getIfModifiedSince(HttpExchange ex) {
    String ifModifiedSince
        = ex.getRequestHeaders().getFirst("If-Modified-Since");
    if (ifModifiedSince == null) {
      return null;
    }
    try {
      return dateFormat.get().parse(ifModifiedSince);
    } catch (java.text.ParseException e) {
      log.log(Level.WARNING, "Exception when parsing ifModifiedSince", e);
      // Ignore and act like it wasn't present
      return null;
    }
  }

  protected void setLastModified(HttpExchange ex, Date lastModified) {
    ex.getResponseHeaders().set("Last-Modified",
                                dateFormat.get().format(lastModified));

  }

  protected abstract void meteredHandle(HttpExchange ex) throws IOException;

  /**
   * Performs entry counting, calls {@link #meteredHandle}, and performs exit
   * counting. Also logs.
   */
  public void handle(HttpExchange ex) throws IOException {
    try {
      synchronized (this) {
        numberConnectionStarted++;
        log.log(Level.FINE, "begining in={0},out={1}", new Object[] {
          numberConnectionStarted, numberConnectionFinished});
      }
      logRequest(ex);
      log.log(Level.FINE, "Processing request with {0}",
              this.getClass().getName());
      meteredHandle(ex);
    } finally {
      synchronized (this) {
        numberConnectionFinished++;
        log.log(Level.FINE, "ending in={0},out={1}", new Object[] {
          numberConnectionStarted, numberConnectionFinished});
      }
    }
  }
}
