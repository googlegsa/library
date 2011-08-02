package adaptorlib;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.HttpURLConnection;

/** Provides performance data when responding
 *  to ajax calls from dashboard. */
class StatHandler extends AbstractHandler {
  private Config config;
  private Journal journal;
  private CircularBufferHandler circularLog = new CircularBufferHandler();

  public StatHandler(Config configuration, Journal journal) {
    super(configuration.getServerHostname(),
        configuration.getGsaCharacterEncoding());
    this.config = configuration;
    this.journal = journal;
    LogManager.getLogManager().getLogger("").addHandler(circularLog);
  }

  protected void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "POST".equals(requestMethod)) {
      // TODO: Tie in real stats.
      String contents = "Di N0. u aksed 4 is 17.1, jes.";
      cannedRespond(ex, HttpURLConnection.HTTP_OK, "text/html", contents);
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
          "Unsupported request method");
    }
  }
}

/* 
-  private String makeHtmlPage() {
-    StringBuilder page = new StringBuilder();
-    page.append("<html><title>" + config.getFeedName() + "</title><body>");
-    page.append("Stats:<br>\n");
-    synchronized (journal) {
-      page.append("# total document ids pushed: "
-          + journal.numTotalDocIdsPushed() + "<br>\n");
-      page.append("# unique document ids pushed: "
-          + journal.numUniqueDocIdsPushed() + "<br>\n");
-      page.append("# total document requests by GSA: "
-          + journal.numTotalGsaRequests() + "<br>\n");
-      page.append("# unique document requests by GSA: "
-          + journal.numUniqueGsaRequests() + "<br>\n");
-      page.append("# total document requests not by GSA: "
-          + journal.numTotalNonGsaRequests() + "<br>\n");
-      page.append("# unique document requests not by GSA: "
-          + journal.numUniqueNonGsaRequests() + "<br>\n");
-      page.append("Program started at: " + journal.whenStarted() + "<br>\n");

-    page.append("<hr>\n");
-    page.append("# Adaptor's configuration:<br>\n");
-    page.append(makeConfigHtml());
-    page.append("<pre style='height: 20em; overflow: auto'>");
-    String log = circularLog.writeOut();
-    log = log.replace("&", "&amp;").replace("<", "&lt;");
-    page.append(log);
-    page.append("</pre>");
-    page.append("</body></html>");
-    return "" + page;
-  }
-
-  private String makeConfigHtml() {
-    StringBuilder table = new StringBuilder();
-    table.append("<table border=2>\n");
-    Set<String> configKeys = config.getAllKeys();
-    for (String key : configKeys) {
-      String value = config.getValue(key);
-      String row = "<tr><td>" + key + "</td><td>" + value + "</td></tr>\n";
-      table.append(row);
-    table.append("</table>\n");
-    return "" + table;
*/
