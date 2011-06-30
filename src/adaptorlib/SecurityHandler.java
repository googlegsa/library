package adaptorlib;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

// WARNING: the isPublic checking is only good for testing at the moment.
class SecurityHandler extends AbstractHandler {
  private static final Logger LOG
      = Logger.getLogger(SecurityHandler.class.getName());
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
