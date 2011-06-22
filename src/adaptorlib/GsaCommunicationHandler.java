package adaptorlib;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
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
    server.createContext("/", new Handler());
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    LOG.info("server is listening on port #" + port);
  }

  private class Handler implements HttpHandler {
    private String name;
    private void namedLog(String msg) {
      // LOG.info(name + ": " + msg);
    }

    /** Call into connector developer code to get document bytes. */
    private byte []processGet(HttpExchange ex) throws IOException {
      URI uri = ex.getRequestURI();
      namedLog("uri: " + uri);
      String prefix = Config.getUrlBeginning();
      URL url = new URL(prefix + uri);
      namedLog("url: " + url);
      String id = DocId.decode(url);
      namedLog("id: " + id);
      byte content[] = adaptor.getDocContent(new DocId(id));
      return content; 
    }

    /** Sends response to GSA. */
    private void respond(HttpExchange ex, int code, byte response[])
        throws IOException {
      Headers responseHeaders = ex.getResponseHeaders();
      responseHeaders.set("Content-Type", "text/plain");
      ex.sendResponseHeaders(code, 0);
      OutputStream responseBody = ex.getResponseBody();
      namedLog("before writing response");
      responseBody.write(response);
      namedLog("after writing response");
      responseBody.close();
      namedLog("after closing writer");
    }

    /** Determines kind of request (GET, HEAD, etc.) and dispatches
      appropriately. */
    private void meteredHandle(HttpExchange ex) throws IOException {
      namedLog("got exchange");
      String requestMethod = ex.getRequestMethod();
      if (!requestMethod.equalsIgnoreCase("GET")) {
        namedLog("got non-GET (" + requestMethod + ")");
        respond(ex, 400, new byte[0]);
      } else {
        namedLog("got GET");
        byte response[] = processGet(ex);
        if (null == response) {
          respond(ex, 404, new byte[0]);
        } else {
          int len = response.length;
          namedLog("processed GET; response is size=" + len);
          respond(ex, 200, response);
        }
        namedLog("responded to GET");
      }
      // TODO: Implement authorization handler.
    }

    /** Performs entry counting, calls meteredHandle, and 
      performs exit counting.  Also logs. */
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
}
