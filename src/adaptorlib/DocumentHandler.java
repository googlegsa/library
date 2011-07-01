package adaptorlib;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class DocumentHandler extends AbstractHandler {
  private static final Logger LOG
      = Logger.getLogger(AbstractHandler.class.getName());

  private GsaCommunicationHandler commHandler;
  private Adaptor adaptor;

  public DocumentHandler(String defaultHostname, Charset defaultCharset,
                         GsaCommunicationHandler commHandler, Adaptor adaptor) {
    super(defaultHostname, defaultCharset);
    this.commHandler = commHandler;
    this.adaptor = adaptor;
  }

  protected void meteredHandle(HttpExchange ex) {
    throw new UnsupportedOperationException();
  }

  public void handle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
      /* Call into adaptor developer code to get document bytes. */
      // TODO(ejona): Need to namespace all docids to allow random support URLs
      DocId docId = commHandler.decodeDocId(getRequestUri(ex));
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
