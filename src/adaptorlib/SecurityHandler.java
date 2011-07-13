package adaptorlib;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

// WARNING: the isPublic checking is only good for testing at the moment.
class SecurityHandler extends AbstractHandler {
  private static final Logger log
      = Logger.getLogger(SecurityHandler.class.getName());
  private static final boolean useHttpBasic = true;

  private GsaCommunicationHandler commHandler;
  private HttpHandler nestedHandler;

  private SecurityHandler(String defaultHostname,
                          Charset defaultCharset,
                          GsaCommunicationHandler commHandler,
                          HttpHandler nestedHandler) {
    super(defaultHostname, defaultCharset);
    this.commHandler = commHandler;
    this.nestedHandler = nestedHandler;
  }

  protected void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if (!"GET".equals(requestMethod) && !"HEAD".equals(requestMethod)) {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                    "Unsupported request method");
      return;
    }

    DocId docId = commHandler.decodeDocId(getRequestUri(ex));
    if (useHttpBasic) {
      // TODO(ejona): implement authorization and authentication.
      boolean isPublic = !"1002".equals(docId.getUniqueId())
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
      boolean isPublic = !"1002".equals(docId.getUniqueId())
          || ex.getRequestHeaders().getFirst("Cookie") != null;

      if (!isPublic) {
        URI uri = commHandler.formNamespacedUri("/sso");
        ex.getResponseHeaders().add("Location", uri.toString());
        cannedRespond(ex, HttpURLConnection.HTTP_SEE_OTHER, "text/plain",
                      "Must sign in via SSO");
        return;
      }
    }

    log.log(Level.FINE, "Security checks passed. Processing with nested {0}",
            nestedHandler.getClass().getName());
    nestedHandler.handle(ex);
  }
}
