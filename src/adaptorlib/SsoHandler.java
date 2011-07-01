package adaptorlib;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SsoHandler extends AbstractHandler {
  public SsoHandler(String defaultHostname, Charset defaultCharset) {
    super(defaultHostname, defaultCharset);
  }

  protected void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
      if (ex.getRequestHeaders().getFirst("Cookie") == null) {
        cannedRespond(ex, HttpURLConnection.HTTP_OK, "text/html",
                      "<html><body><form action='/sso' method='POST'>"
                      + "<input name='user'><input name='password'>"
                      + "<input type='submit'>"
                      + "</form></body></html>");
      } else {
        cannedRespond(ex, HttpURLConnection.HTTP_OK, "text/html",
                      "<html><body>You are logged in</body></html>");
      }
    } else if ("POST".equals(ex.getRequestMethod())) {
      ex.getResponseHeaders().add("Set-Cookie", "user=something; Path=/");
      cannedRespond(ex, HttpURLConnection.HTTP_OK, "text/plain",
                    "You are logged in");
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                    "Unsupported request method");
    }
  }
}
