package adaptorlib;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.Set;
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

  private String makeHtmlPage() {
    StringBuilder page = new StringBuilder();
    page.append("<html><title>" + config.getFeedName() + "</title><body>");
    page.append("Stats:<br>\n");
    page.append("# total document requests: "
        + Journal.numTotalQueries() + "<br>\n");
    page.append("# total document requests by GSA: "
        + Journal.numTotalGsaQueries() + "<br>\n");
    page.append("# total document ids pushed: "
        + Journal.numTotalDocIdsPushed() + "<br>\n");
    page.append("# unique documents requested: "
        + Journal.numUniqueDocContentRequests() + "<br>\n");
    page.append("# unique documents requested by GSA: "
        + Journal.numUniqueGsaCrawled() + "<br>\n");
    page.append("# unique document ids pushed: "
        + Journal.numUniqueDocIdsPushed() + "<br>\n");
    page.append("Program started at: " + Journal.whenStarted() + "<br>\n");
    page.append("<hr>\n");
    page.append("# Adaptor's configuration:<br>\n");
    page.append(makeConfigHtml());
    page.append("</body></html>");
    return "" + page;
  }

  private String makeConfigHtml() {
    StringBuilder table = new StringBuilder();
    table.append("<table border=2>\n");
    Set<String> configKeys = config.getAllKeys();
    for (String key : configKeys) {
      String value = config.getValue(key);
      String row = "<tr><td>" + key + "</td><td>" + value + "</td></tr>\n";
      table.append(row);
    }
    table.append("</table>\n");
    return "" + table;
  }
}
