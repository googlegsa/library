package adaptorlib;

import com.sun.net.httpserver.HttpExchange;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Date;
import java.util.logging.Logger;

/** Serves class' resources like dashboard's html and jquery js. */
class DashboardHandler extends AbstractHandler {
  private static final Logger log
      = Logger.getLogger(DashboardHandler.class.getName());
  /** Requests for this path get redirected to {@link #pathPrefix}. */
  private static final String pathRedirect = "/dashboard";
  /** The base path of requests we expect to serve. */
  private static final String pathPrefix = pathRedirect + "/";

  private final Config config;
  private final Journal journal;

  public DashboardHandler(Config configuration, Journal journal) {
    super(configuration.getServerHostname(),
        configuration.getGsaCharacterEncoding());
    this.config = configuration;
    this.journal = journal;
  }

  protected void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod)) {
      URI req = getRequestUri(ex);
      if (pathRedirect.equals(req.getPath())) {
        URI redirect;
        try {
          redirect = new URI(req.getScheme(), req.getAuthority(),
                             pathPrefix, req.getQuery(),
                             req.getFragment());
        } catch (java.net.URISyntaxException e) {
          throw new IllegalStateException(e);
        }
        ex.getResponseHeaders().set("Location", redirect.toString());
        respond(ex, HttpURLConnection.HTTP_MOVED_PERM, null, null);
        return;
      }
      String path = req.getPath();
      if (!path.startsWith(pathPrefix)) {
        cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND, "text/plain",
                      "404: Not found");
        return;
      }
      path = path.substring(pathPrefix.length());
      if ("".equals(path)) {
        path = "index.html";
      }
      java.net.URL url = DashboardHandler.class.getClassLoader().getResource(
          path);
      Date lastModified = new Date(url.openConnection().getLastModified());
      if (lastModified.getTime() == 0) {
        log.info("Resource didn't have a lastModified time");
      } else {
        Date since = getIfModifiedSince(ex);
        if (since != null && !lastModified.after(since)) {
          respond(ex, HttpURLConnection.HTTP_NOT_MODIFIED, null, null);
          return;
        }
        setLastModified(ex, lastModified);
      }
      byte contents[] = loadPage(path);
      String contentType = null;
      if (path.endsWith(".html")) {
        contentType = "text/html";
      } else if (path.endsWith(".css")) {
        contentType = "text/css";
      } else if (path.endsWith(".js")) {
        contentType = "text/javascript";
      }
      enableCompressionIfSupported(ex);
      respond(ex, HttpURLConnection.HTTP_OK, contentType, contents);
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
          "Unsupported request method");
    }
  }

  /**
   * Provides static files that are resources.
   */
  private byte[] loadPage(String path) throws IOException {
    InputStream in = DashboardHandler.class.getClassLoader()
        .getResourceAsStream(path);
    if (null == in) {
      throw new FileNotFoundException(path);
    } else {
      try {
        byte page[] = IOHelper.readInputStreamToByteArray(in);
        return page;
      } finally {
        in.close();
      }
    }
  }
}
