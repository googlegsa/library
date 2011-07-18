package adaptorlib;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class DashboardHandler extends AbstractHandler {

  private Config config;

  public DashboardHandler(Config configuration) {
    super(configuration.getServerHostname(),
        configuration.getGsaCharacterEncoding());
    this.config = configuration;
  }

  protected void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "POST".equals(requestMethod)) {
      String contents = makeHtmlPage();
      cannedRespond(ex, HttpURLConnection.HTTP_OK, "text/html", contents);
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
          "Unsupported request method");
    }
  }

  // TODO: Create using DOM.
  private String makeHtmlPage() {
    String page = "<html><title>" + config.getFeedName() + "</title><body>";
    page += "Stats:<br>\n";
    page += "# Document ids pushed: "
        + Journal.numUniqueDocIdsPushed() + "<br>\n";
    page += "# Document content requests: "
        + Journal.numUniqueDocContentRequests() + "<br>\n";
    page += "# Time per content request: "
        + "NA" + "<br>\n";
    page += "Program started at: " + Journal.whenStarted() + "<br>\n";
    page += "<hr>\n";
    page += "# Adaptor's configuration:<br>\n";
    page += config.toHtml();
    page += "</body></html>";
    return page;
  }
}
