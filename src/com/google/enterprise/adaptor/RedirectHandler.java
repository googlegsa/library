// Copyright 2011 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.*;

/**
 * HTTP Handler that responds with a fixed redirect. The redirect only occurs if
 * this handler's path is identical to the requested path, otherwise the
 * response is 404.
 */
class RedirectHandler implements HttpHandler {
  private final String redirectPath;

  /**
   * @param redirectPath relative or absolute path to redirect clients to
   */
  public RedirectHandler(String redirectPath) {
    this.redirectPath = redirectPath;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
                    Translation.HTTP_NOT_FOUND);
      return;
    }

    URI base = HttpExchanges.getRequestUri(ex);
    URI path;
    try {
      path = new URI(redirectPath);
    } catch (URISyntaxException e) {
      throw new IOException("Could not construct URI");
    }
    HttpExchanges.sendRedirect(ex, base.resolve(path));
  }
}
