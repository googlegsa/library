package adaptorlib;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

abstract class AbstractHandler implements HttpHandler {
  private static final Logger LOG
      = Logger.getLogger(AbstractHandler.class.getName());

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
    for (Map.Entry<String,List<String>> me
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
    if (LOG.isLoggable(Level.FINER)) {
      LOG.log(Level.FINER, "Received {1} request to {0}. Headers: '{'{2}'}'",
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
    LOG.log(Level.FINER, "Resolved original URI to: {0}", requestedUri);
    return requestedUri;
  }

  /**
   * Sends response to GSA. Should only be used when the request method is
   * HEAD.
   */
  protected void respondToHead(HttpExchange ex, int code, String contentType)
      throws IOException {
    ex.getResponseHeaders().set("Transfer-Encoding", "chunked");
    respond(ex, code, contentType, (byte[])null);
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
    // TODO(johnfelton) : For now, don't set the content type.
    // ex.getResponseHeaders().set("Content-Type", contentType);
    if (response == null) {
      // No body. Required for HEAD requests
      ex.sendResponseHeaders(code, -1);
    } else {
      // Chuncked encoding
      ex.sendResponseHeaders(code, 0);
      OutputStream responseBody = ex.getResponseBody();
      LOG.finest("before writing response");
      responseBody.write(response);
      responseBody.flush();
      responseBody.close();
      LOG.finest("after writing response");
    }
    ex.close();
    LOG.finest("after closing exchange");
  }

  protected abstract void meteredHandle(HttpExchange ex) throws IOException;

  /**
   * Performs entry counting, calls {@link #meteredHandle}, and performs exit
   * counting. Also logs.
   */
  public void handle(HttpExchange ex) throws IOException {
    try {
      synchronized(this) {
        numberConnectionStarted++;
        LOG.log(Level.FINE, "begining in={0},out={1}", new Object[] {
          numberConnectionStarted, numberConnectionFinished});
      }
      logRequest(ex);
      LOG.log(Level.FINE, "Processing request with {0}",
              this.getClass().getName());
      meteredHandle(ex);
    } catch (Exception e) {
      LOG.log(Level.WARNING,
              "Unexpected exception propagated to top-level request handling",
              e);
    } finally {
      synchronized(this) {
        numberConnectionFinished++;
        LOG.log(Level.FINE, "ending in={0},out={1}", new Object[] {
          numberConnectionStarted, numberConnectionFinished});
      }
    }
  }
}
