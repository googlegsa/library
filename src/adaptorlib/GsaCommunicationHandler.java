package adaptorlib;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/** This class handles the communications with GSA. */
public class GsaCommunicationHandler {
  private static final Logger LOG
      = Logger.getLogger(GsaCommunicationHandler.class.getName());

  // Numbers for logging incoming and completed communications.
  private static int numberConnectionStarted = 0;
  private static int numberConnectionFinished = 0;

  private final int port;
  private final Adaptor adaptor;

  public GsaCommunicationHandler(Adaptor adaptor) {
    this.port = Config.getLocalPort();
    this.adaptor = adaptor;
  }

  /** Starts listening for communications from GSA. */
  public void beginListeningForContentRequests() throws IOException {
    InetSocketAddress addr = new InetSocketAddress(port);
    HttpServer server = HttpServer.create(addr, 0);
    server.createContext("/sso", new SsoHandler());
    // Disable SecurityHandler until it can query adapter for configuration
    server.createContext(Config.getBaseUri().getPath() + Config.getDocIdPath(),
                         /*new SecurityHandler(*/new DocumentHandler()/*)*/);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    LOG.info("server is listening on port #" + port);
  }

  public void beginPushingDocIds(ScheduleIterator schedule) {
    Scheduler pushScheduler = new Scheduler();
    pushScheduler.schedule(new Scheduler.Task() {
      public void run() {
        // TODO: Prevent two simultenous calls.
        LOG.info("about to get doc ids");
        List<DocId> handles = adaptor.getDocIds();
        LOG.info("about to push " + handles.size() + " doc ids");
        GsaCommunicationHandler.pushDocIds("testfeed", handles);
        LOG.info("done pushing doc ids");
      }
    }, schedule);
  }

  private static void pushSizedBatchOfDocIds(String feedSourceName,
      List<DocId> handles) {
    String xmlFeedFile = GsaFeedFileMaker.makeMetadataAndUrlXml(
        feedSourceName, handles);
    boolean keepGoing = true;
    for (int ntries = 0; keepGoing; ntries++) {
      try {
        GsaFeedFileSender.sendMetadataAndUrl(feedSourceName, xmlFeedFile);
        keepGoing = false;  // Sent.
      } catch (GsaFeedFileSender.FailedToConnect ftc) {
        LOG.warning("" + ftc);
        keepGoing = Config.handleFailedToConnect(ftc, ntries);
      } catch (GsaFeedFileSender.FailedWriting fw) {
        LOG.warning("" + fw);
        keepGoing = Config.handleFailedToConnect(fw, ntries);
      } catch (GsaFeedFileSender.FailedReadingReply fr) {
        LOG.warning("" + fr);
        keepGoing = Config.handleFailedToConnect(fr, ntries);
      }
    }
  }

  /** Makes and sends metadata-and-url feed files to GSA. */
  public static void pushDocIds(String feedSourceName, List<DocId> handles) {
    final int MAX = Config.getUrlsPerFeedFile();
    int totalPushed = 0;
    for (int i = 0; i < handles.size(); i += MAX) {
      int endIndex = i + MAX;
      if (endIndex > handles.size()) {
        endIndex = handles.size();
      }
      List<DocId> batch = handles.subList(i, endIndex);
      pushSizedBatchOfDocIds(feedSourceName, batch);
      totalPushed += batch.size();
    }
    if (handles.size() != totalPushed) {
      throw new IllegalStateException();
    }
  }

  private class SecurityHandler extends AbstractHandler {
    private static final boolean useHttpBasic = true;

    private HttpHandler nestedHandler;

    private SecurityHandler(HttpHandler nestedHandler) {
      this.nestedHandler = nestedHandler;
    }

    protected void meteredHandle(HttpExchange ex) throws IOException {
      String requestMethod = ex.getRequestMethod();
      if (!"GET".equals(requestMethod) && !"HEAD".equals(requestMethod)) {
        cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                      "Unsupported request method");
        return;
      }

      DocId docId = DocId.decode(getRequestUri(ex));
      if (useHttpBasic) {
        // TODO(ejona): implement authorization and authentication
        boolean isPublic = "1001".equals(docId.getUniqueId())
            || ex.getRequestHeaders().getFirst("Authorization") != null;

        if (!isPublic) {
          ex.getResponseHeaders().add("WWW-Authenticate",
                                      "Basic realm=\"Test\"");
          cannedRespond(ex, HttpURLConnection.HTTP_UNAUTHORIZED, "text/plain",
                        "Not public");
          return;
        }
      } else {
        // Using HTTP SSO
        // TODO(ejona): implement authorization
        boolean isPublic = "1001".equals(docId.getUniqueId())
            || ex.getRequestHeaders().getFirst("Cookie") != null;

        if (!isPublic) {
          URI uri;
          try {
            uri = new URI(null, null, Config.getBaseUri().getPath() + "/sso",
                          null);
          } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
          }
          uri = Config.getBaseUri().resolve(uri);
          ex.getResponseHeaders().add("Location", uri.toString());
          cannedRespond(ex, HttpURLConnection.HTTP_SEE_OTHER, "text/plain",
                        "Must sign in via SSO");
          return;
        }
      }

      LOG.log(Level.FINE, "Security checks passed. Processing with nested {0}",
              nestedHandler.getClass().getName());
      nestedHandler.handle(ex);
    }
  }

  private class DocumentHandler extends AbstractHandler {
    protected void meteredHandle(HttpExchange ex) {
      throw new UnsupportedOperationException();
    }

    public void handle(HttpExchange ex) throws IOException {
      String requestMethod = ex.getRequestMethod();
      if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
        /* Call into connector developer code to get document bytes. */
        // TODO(ejona): Need to namespace all docids to allow random support URLs
        DocId docId = DocId.decode(getRequestUri(ex));
        LOG.fine("id: " + docId.getUniqueId());

        // TODO(ejona): support different mime types of content
        // TODO(ejona): if text, support providing encoding
        // TODO(ejona): don't retrieve the document contents for HEAD request
        byte content[] = adaptor.getDocContent(docId);
        String contentType = "text/plain"; // "application/octet-stream"
        if (null == content) {
          cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND, "text/plain",
                        "Unknown document");
        } else {
          LOG.finer("processed request; response is size=" + content.length);
          if ("GET".equals(requestMethod))
            respond(ex, HttpURLConnection.HTTP_OK, "text/plain", content);
          else
            respondToHead(ex, HttpURLConnection.HTTP_OK, "text/plain");
        }
      } else {
        cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                      "Unsupported request method");
      }
    }
  }

  private class SsoHandler extends AbstractHandler {
    protected void meteredHandle(HttpExchange ex) throws IOException {
      String requestMethod = ex.getRequestMethod();
      if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
        if (ex.getRequestHeaders().getFirst("Cookie") == null) {
          cannedRespond(ex, HttpURLConnection.HTTP_OK, "text/html",
                        "<html><body><form action='/sso' method='POST'>"
                        + "<input name='user'><input name='password'>"
                        + "<input type='submit'>"
                        + "</form></body></html>");
        } else {
          cannedRespond(ex, HttpURLConnection.HTTP_OK, "text/html",
                        "<html><body>You are logged in</body></html>");
        }
      } else if ("POST".equals(ex.getRequestMethod())) {
        ex.getResponseHeaders().add("Set-Cookie", "user=something; Path=/");
        cannedRespond(ex, HttpURLConnection.HTTP_OK, "text/plain",
                      "You are logged in");
      } else {
        cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                      "Unsupported request method");
      }
    }
  }

  private abstract class AbstractHandler implements HttpHandler {
    // Numbers for logging incoming and completed communications.
    private int numberConnectionStarted = 0;
    private int numberConnectionFinished = 0;

    protected String getLoggableRequestHeaders(HttpExchange ex) {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (Map.Entry<String,List<String>> me
           : ex.getRequestHeaders().entrySet()) {
        for (String value : me.getValue()) {
          if (!first)
            sb.append(", ");
          else
            first = false;

          sb.append(me.getKey());
          sb.append(": ");
          sb.append(value);
        }
      }
      return sb.toString();
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
        host = Config.getBaseUri().getAuthority();
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
      if ("HEAD".equals(ex.getRequestMethod()))
        respondToHead(ex, code, contentType);
      else
        respond(ex, code, contentType,
                response.getBytes(Config.getGsaCharacterEncoding()));
    }

    /**
     * Sends response to GSA. Should not be used directly if the request method
     * is HEAD.
     */
    protected void respond(HttpExchange ex, int code, String contentType,
                           byte response[]) throws IOException {
      Headers responseHeaders = ex.getResponseHeaders();
      responseHeaders.set("Content-Type", contentType);
      if (response == null) {
        // No body. Required for HEAD requests
        ex.sendResponseHeaders(code, -1);
      } else {
        // Chuncked encoding
        ex.sendResponseHeaders(code, 0);
        OutputStream responseBody = ex.getResponseBody();
        LOG.finest("before writing response");
        responseBody.write(response);
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
}
