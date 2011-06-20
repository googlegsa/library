package adaptorlib;

import java.io.IOException;
import java.io.OutputStream;
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
  private static Logger LOG
      = Logger.getLogger(GsaCommunicationHandler.class.getName());

  // Numbers for logging incoming and completed communications.
  private static int numberConnectionStarted = 0;
  private static int numberConnectionFinished = 0;

  private int port;
  private DocContentRetriever contentProvider;
  public GsaCommunicationHandler(int portNumber, DocContentRetriever contentProvider) {
    this.port = portNumber;
    this.contentProvider = contentProvider;
  }

  /** Starts listening for communications from GSA. */
  public void beginListeningForConnections() throws IOException {
    InetSocketAddress addr = new InetSocketAddress(port);
    HttpServer server = HttpServer.create(addr, 0);
    server.createContext("/", new Handler());
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    LOG.info("server is listening on port #" + port);
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

  private class Handler extends AbstractHandler {
    private HttpHandler ssoHandler = new SsoHandler();
    private HttpHandler documentHandler
        = new SecurityHandler(new DocumentHandler());

    /**
     * Determines kind of request (GET, HEAD, etc.) and dispatches
     * appropriately.
     */
    private void meteredHandle(HttpExchange ex) throws IOException {
      namedLog("got exchange");
      logRequest(ex);
      if ("/sso".equals(ex.getRequestURI().getPath())) {
        ssoHandler.handle(ex);
        return;
      }

      documentHandler.handle(ex);
    }

    /**
     * Performs entry counting, calls meteredHandle, and performs exit counting.
     * Also logs.
     */
    public void handle(HttpExchange exchange) {
      try {
        synchronized(Handler.class) {
          name = "HttpHandler" + numberConnectionStarted;
          numberConnectionStarted++;
          String countsStr = "in=" + numberConnectionStarted
              + ",out=" + numberConnectionFinished;
          namedLog("begining " + countsStr);
        }
        meteredHandle(exchange);
      } catch (Exception e) {
        namedLog("handling failed: " + e);
      } finally {
        synchronized(Handler.class) {
          numberConnectionFinished++;
          String countsStr = "in=" + numberConnectionStarted
              + ",out=" + numberConnectionFinished;
          namedLog("ending " + countsStr);
        }
      }
    }
  }

  private class SecurityHandler extends AbstractHandler {
    private static final boolean useHttpBasic = true;

    private HttpHandler nestedHandler;

    private SecurityHandler(HttpHandler nestedHandler) {
      this.nestedHandler = nestedHandler;
    }

    public void handle(HttpExchange ex) throws IOException {
      String id = DocId.decode(getRequestUri(ex));
      if (useHttpBasic) {
        boolean isPublic = "1001".equals(id)
            || ex.getRequestHeaders().getFirst("Authorization") != null;

        if (!isPublic) {
          ex.getResponseHeaders().add("WWW-Authenticate",
                                      "Basic realm=\"Test\"");
          respond(ex, 401, "text/plain", "Not public");
          return;
        }
      } else {
        // Using HTTP SSO
        boolean isPublic = "1001".equals(id)
            || ex.getRequestHeaders().getFirst("Cookie") != null;

        if (!isPublic) {
          ex.getResponseHeaders().add("Location",
                                      Config.getUrlBeginning() + "/sso");
          respond(ex, 307, "text/plain", "Must sign in via SSO");
          return;
        }
      }

     nestedHandler.handle(ex);
    }
  }

  private class DocumentHandler extends AbstractHandler {
    public void handle(HttpExchange ex) throws IOException {
      String requestMethod = ex.getRequestMethod();
      if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
        boolean isGet = "GET".equals(requestMethod);
        /* Call into connector developer code to get document bytes. */
        // TODO(ejona): Need to namespace all docids to allow random support URLs
        String id = DocId.decode(getRequestUri(ex));
        namedLog("id: " + id);

        DocId docId = new DocId(id);

        // TODO(ejona): support different mime types of content
        // TODO(ejona): if text, support providing encoding
        byte content[] = contentProvider.getDocContent(docId);
        String contentType = "text/plain"; // "application/octet-stream"
        if (null == content) {
          if (isGet)
            respond(ex, 404, "text/plain", "Unknown document");
          else
            respondToHead(ex, 404, "text/plain");
        } else {
          int len = content.length;
          namedLog("processed request; response is size=" + len);
          if (isGet)
            respond(ex, 200, "text/plain", content);
          else
            respondToHead(ex, 200, "text/plain");
        }
      } else {
        respond(ex, 405, "text/plain", "Unsupported request method");
      }
    }
  }

  private class SsoHandler extends AbstractHandler {
    public void handle(HttpExchange ex) throws IOException {
      if ("GET".equals(ex.getRequestMethod())) {
        if (ex.getRequestHeaders().getFirst("Cookie") == null) {
          respond(ex, 200, "text/html",
                  "<html><body><form action='/sso' method='POST'>"
                  + "<input name='user'><input name='password'>"
                  + "<input type='submit'>"
                  + "</form></body></html>");
        } else {
          respond(ex, 200, "text/html",
                  "<html><body>You are logged in</body></html>");
        }
      } else if ("HEAD".equals(ex.getRequestMethod())) {
        respondToHead(ex, 200, "text/html");
      } else if ("POST".equals(ex.getRequestMethod())) {
        ex.getResponseHeaders().add("Set-Cookie", "user=something");
        respond(ex, 200, "text/plain", "You are logged in");
      } else {
        respond(ex, 405, "text/plain", "Unhandled request method");
      }
    }
  }

  private abstract class AbstractHandler implements HttpHandler {
    protected String name;
    protected void namedLog(String msg) {
      LOG.fine(name + ": " + msg);
    }

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
      if (LOG.isLoggable(Level.FINE)) {
        LOG.log(Level.FINE, "received {1} request to {0}. Headers: '{'{2}'}'",
                new Object[] {ex.getRequestURI(), ex.getRequestMethod(),
                              getLoggableRequestHeaders(ex)});
      }
    }

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
      LOG.log(Level.FINE, "Resolved original URI to: {0}", requestedUri);
      return requestedUri;
    }

    protected void respondToHead(HttpExchange ex, int code, String contentType)
        throws IOException {
      ex.getResponseHeaders().set("Transfer-Encoding", "chunked");
      respond(ex, code, contentType, (byte[])null);
    }

    /** Sends response to GSA. */
    protected void respond(HttpExchange ex, int code, String contentType,
                         String response) throws IOException {
      respond(ex, code, contentType,
              response.getBytes(Config.getGsaCharacterEncoding()));
    }

    /** Sends response to GSA. */
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
        namedLog("before writing response");
        responseBody.write(response);
        namedLog("after writing response");
      }
      ex.close();
      namedLog("after closing exchange");
    }
  }
}
