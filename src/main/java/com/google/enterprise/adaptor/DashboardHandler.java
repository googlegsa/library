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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Date;
import java.util.logging.Logger;

/** Serves class' resources like dashboard's html and jquery js. */
class DashboardHandler implements HttpHandler {
  private static final Logger log
      = Logger.getLogger(DashboardHandler.class.getName());
  /** Subpackage to look for static resources within. */
  private static final String STATIC_PACKAGE = "resources";

  @Override
  public void handle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod)) {
      URI req = HttpExchanges.getRequestUri(ex);
      final String basePath = ex.getHttpContext().getPath();
      final String pathPrefix = basePath + "/";
      if (basePath.equals(req.getPath())) {
        URI redirect;
        try {
          redirect = new URI(req.getScheme(), req.getAuthority(),
                             pathPrefix, req.getQuery(),
                             req.getFragment());
        } catch (java.net.URISyntaxException e) {
          throw new IllegalStateException(e);
        }
        ex.getResponseHeaders().set("Location", redirect.toString());
        HttpExchanges.respond(
            ex, HttpURLConnection.HTTP_MOVED_PERM, null, null);
        return;
      }
      String path = req.getPath();
      path = path.substring(pathPrefix.length());
      if ("".equals(path)) {
        path = "index.html";
      }
      path = STATIC_PACKAGE + "/" + path;
      java.net.URL url = DashboardHandler.class.getResource(path);
      if (url == null) {
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
            Translation.HTTP_NOT_FOUND);
        return;
      }
      Date lastModified = new Date(url.openConnection().getLastModified());
      if (lastModified.getTime() == 0) {
        log.info("Resource didn't have a lastModified time");
      } else {
        Date since = HttpExchanges.getIfModifiedSince(ex);
        if (since != null && !lastModified.after(since)) {
          HttpExchanges.respond(
              ex, HttpURLConnection.HTTP_NOT_MODIFIED, null, null);
          return;
        }
        HttpExchanges.setLastModified(ex, lastModified);
      }
      byte contents[] = loadPage(path);
      String contentType = "application/octet-stream";
      if (path.endsWith(".html")) {
        contentType = "text/html";
      } else if (path.endsWith(".css")) {
        contentType = "text/css";
      } else if (path.endsWith(".js")) {
        contentType = "text/javascript";
      }
      HttpExchanges.enableCompressionIfSupported(ex);
      HttpExchanges.respond(
          ex, HttpURLConnection.HTTP_OK, contentType, contents);
    } else {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
          Translation.HTTP_BAD_METHOD);
    }
  }

  /**
   * Provides static files that are resources.
   */
  private byte[] loadPage(String path) throws IOException {  
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
