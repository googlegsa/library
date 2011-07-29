package adaptorlib;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

class DocumentHandler extends AbstractHandler {
  private static final Logger log
      = Logger.getLogger(AbstractHandler.class.getName());

  private GsaCommunicationHandler commHandler;
  private Adaptor adaptor;
  private ThreadLocal<DocumentRequest> localRequest
      = new ThreadLocal<DocumentRequest>() {
        @Override
        protected DocumentRequest initialValue() {
          return new DocumentRequest();
        }
      };

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

      DocumentRequest request = localRequest.get();
      request.setup(ex, docId);
      DocumentResponse response = new DocumentResponse(ex);
      // TODO(ejona): if text, support providing encoding
      byte[] content;
      String contentType;
      int httpResponseCode;
      try {
        adaptor.getDocContent(request, response);

        content = response.getWrittenContent();
        contentType = response.contentType;
        httpResponseCode = response.httpResponseCode;
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
      } finally {
        request.reset();
      }

      if (httpResponseCode != HttpURLConnection.HTTP_OK
          && httpResponseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
        log.log(Level.WARNING, "Unexpected response code (was any response "
                + "sent from the adaptor?): {0}", httpResponseCode);
        cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR, "text/plain",
                      "Tried to return unexpected response code");
        return;
      }

      log.finer("processed request; response is size=" + content.length);
      // TODO(ejona): decide when to use compression based on mime-type
      enableCompressionIfSupported(ex);
      if ("GET".equals(requestMethod)) {
        respond(ex, httpResponseCode, contentType, content);
      } else {
        respondToHead(ex, httpResponseCode, contentType);
      }
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                    "Unsupported request method");
    }
  }

  private static class DocumentRequest implements Adaptor.Request {
    // DateFormats are relatively expensive to create, and cannot be used from
    // multiple threads
    private final DateFormat dateFormat
       = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    private HttpExchange ex;
    private DocId docId;

    private void setup(HttpExchange ex, DocId docId) {
      this.ex = ex;
      this.docId = docId;
    }

    private void reset() {
      ex = null;
      docId = null;
    }

    @Override
    public boolean needDocumentContent() {
      return !"HEAD".equals(ex.getRequestMethod());
    }

    @Override
    public boolean hasChangedSinceLastAccess(Date lastModified) {
      Date date = getLastAccessTime();
      if (date == null) {
        return true;
      }
      return date.before(lastModified);
    }

    @Override
    public Date getLastAccessTime() {
      String ifModifiedSinceStr = ex.getRequestHeaders().getFirst(
          "If-Modified-Since");
      if (ifModifiedSinceStr == null) {
        return null;
      }

      Date ifModifiedSince = null;
      try {
        ifModifiedSince = dateFormat.parse(ifModifiedSinceStr);
      } catch (ParseException e) {
        log.log(Level.WARNING, "Exception when parsing ifModifiedSince", e);
        // Ignore and act like it wasn't present
        return null;
      }
      log.fine("date: " + ifModifiedSince);
      return ifModifiedSince;
    }

    @Override
    public DocId getDocId() {
      return docId;
    }
  }

  private static class DocumentResponse implements Adaptor.Response {
    private HttpExchange ex;
    private OutputStream os;
    String contentType;
    int httpResponseCode;

    public DocumentResponse(HttpExchange ex) {
      this.ex = ex;
      if ("HEAD".equals(ex.getRequestMethod())) {
        // There is no need for them to call getOutputStream
        httpResponseCode = HttpURLConnection.HTTP_OK;
      }
    }

    @Override
    public void respondNotModified() {
      if (os != null) {
        throw new IllegalStateException();
      }
      httpResponseCode = HttpURLConnection.HTTP_NOT_MODIFIED;
      os = new SinkOutputStream();
    }

    @Override
    public OutputStream getOutputStream() {
      if (os != null) {
        return os;
      }
      httpResponseCode = HttpURLConnection.HTTP_OK;
      if ("HEAD".equals(ex.getRequestMethod())) {
        os = new SinkOutputStream();
      } else {
        os = new ByteArrayOutputStream();
      }
      return os;
    }

    @Override
    public void setContentType(String contentType) {
      if (os != null) {
        throw new IllegalStateException();
      }
      this.contentType = contentType;
    }

    @Override
    public void setDocReadPermissions(DocReadPermissions acl) {
      if (os != null) {
        throw new IllegalStateException();
      }
      // TODO(ejona): transfer to GSA
    }

    private byte[] getWrittenContent() {
      if (os instanceof ByteArrayOutputStream) {
        return ((ByteArrayOutputStream) os).toByteArray();
      } else {
        return null;
      }
    }
  }


  /**
   * OutputStream that forgets all input. It is equivalent to using /dev/null.
   */
  private static class SinkOutputStream extends OutputStream {
    @Override
    public void write(byte[] b, int off, int len) throws IOException {}

    @Override
    public void write(int b) throws IOException {}
  }

}
