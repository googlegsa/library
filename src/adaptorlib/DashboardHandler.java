package adaptorlib;

import com.sun.net.httpserver.HttpExchange;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Set;
import java.util.logging.LogManager;

/** Serves class' resources like dashboard's html and jquery js.  */
class DashboardHandler extends AbstractHandler {
  private Config config;
  private Journal journal;

  public DashboardHandler(Config configuration, Journal journal) {
    super(configuration.getServerHostname(),
        configuration.getGsaCharacterEncoding());
    this.config = configuration;
    this.journal = journal;
  }

  protected void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "POST".equals(requestMethod)) {
      URI requested = getRequestUri(ex);
      byte contents[] = loadPage(requested);
      // TODO: Figure out what content-type to send.
      respond(ex, HttpURLConnection.HTTP_OK, "text/html", contents);
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
          "Unsupported request method");
    }
  }

  /** Provides static files that are resources.  Rejects URIs whose
   *  path does not begin with "/dashboard". */
  private byte[] loadPage(URI requested) throws IOException {
    String path = requested.getPath();
    if ("/dashboard".equals(path) || "/dashboard/".equals(path)) {
      path = "index.html";
    } else {
      if (!path.startsWith("/dashboard/")) {
        throw new IllegalArgumentException("non-dashboard path: " + path);
      } else {
        path = path.substring("/dashboard/".length());
      }
    }
    InputStream in = DashboardHandler.class.getResourceAsStream(path);
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
