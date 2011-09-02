// Copyright 2011 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package adaptorlib;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;

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
