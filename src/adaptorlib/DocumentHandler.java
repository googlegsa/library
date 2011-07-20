package adaptorlib;

import com.sun.net.httpserver.HttpExchange;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

class DocumentHandler extends AbstractHandler {
  private static final Logger log
      = Logger.getLogger(AbstractHandler.class.getName());

  private GsaCommunicationHandler commHandler;
  private Adaptor adaptor;

  public DocumentHandler(String defaultHostname, Charset defaultCharset,
                         GsaCommunicationHandler commHandler, Adaptor adaptor) {
    super(defaultHostname, defaultCharset);
    this.commHandler = commHandler;
    this.adaptor = adaptor;
  }
  
  private static boolean requestIsFromGsa(HttpExchange ex) {
     String userAgent = ex.getRequestHeaders().getFirst("User-Agent");
     return (null != userAgent) && userAgent.startsWith("gsa-crawler");
  }

  public void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
      /* Call into adaptor developer code to get document bytes. */
      // TODO(ejona): Need to namespace all docids to allow random support URLs
      DocId docId = commHandler.decodeDocId(getRequestUri(ex));
      log.fine("id: " + docId.getUniqueId());

      Journal journal = commHandler.getJournal();
      if (requestIsFromGsa(ex)) {
        journal.recordGsaContentRequest(docId);
      } else {
        journal.recordNonGsaContentRequest(docId);
      }

      // TODO(ejona): support different mime types of content
      // TODO(ejona): if text, support providing encoding
      // TODO(ejona): don't retrieve the document contents for HEAD request
      byte content[];
      try {
        content = adaptor.getDocContent(docId);
        if (content == null) {
          throw new IOException("Adaptor did not provide content");
        }
      } catch (FileNotFoundException e) {
        cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND, "text/plain",
                      "Unknown document: " + e.getMessage());
        return;
      } catch (IOException e) {
        cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR, "text/plain",
                      "IO Exception: " + e.getMessage());
        return;
      } catch (Exception e) {
        log.log(Level.WARNING, "Unexpected exception from getDocContent", e);
        cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR, "text/plain",
                      "Exception (" + e.getClass().getName() + "): "
                      + e.getMessage());
        return;
      }
      // String contentType = "text/plain"; // "application/octet-stream"
      log.finer("processed request; response is size=" + content.length);
      // TODO(ejona): decide when to use compression based on mime-type
      enableCompressionIfSupported(ex);
      if ("GET".equals(requestMethod)) {
        respond(ex, HttpURLConnection.HTTP_OK, "text/plain", content);
      } else {
        respondToHead(ex, HttpURLConnection.HTTP_OK, "text/plain");
      }
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                    "Unsupported request method");
    }
  }
}
