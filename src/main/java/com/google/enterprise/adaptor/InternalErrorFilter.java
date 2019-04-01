// Copyright 2013 Google Inc. All Rights Reserved.
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

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Filter that sends 500 Internal Error instead of having the error close the
 * connection, when possible.
 */
class InternalErrorFilter extends Filter {
  private static final Logger log
      = Logger.getLogger(InternalErrorFilter.class.getName());

  @Override
  public String description() {
    return "Filter sends HTTP 500 when an exception occurs, when possible";
  }

  @Override
  public void doFilter(HttpExchange ex, Filter.Chain chain) throws IOException {
    try {
      chain.doFilter(ex);
    } catch (Exception e) {
      // We want to send 500 Internal Error if the response headers have not
      // been sent, but HttpExchange doesn't provide a way to learn if they have
      // been sent.
      if (!HttpExchanges.headersSent(ex)) {
        log.log(Level.WARNING, "Unexpected exception during request", e);
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR,
            Translation.HTTP_INTERNAL_ERROR);
      } else {
        // The headers have already been sent, so all we can do is throw the
        // exception up and allow the server to kill the connection.
        if (e instanceof RuntimeException) {
          throw (RuntimeException) e;
        } else {
          throw (IOException) e;
        }
      }
    }
  }
}
