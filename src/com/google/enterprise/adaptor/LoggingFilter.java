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

import com.google.common.annotations.VisibleForTesting;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Filter that logs requests and responses. */
class LoggingFilter extends Filter {
  private static final Logger log
      = Logger.getLogger(LoggingFilter.class.getName());

  @Override
  public String description() {
    return "Filter that logs requests and responses";
  }

  @Override
  public void doFilter(HttpExchange ex, Filter.Chain chain) throws IOException {
    try {
      log.fine("beginning");
      logRequest(ex);
      log.log(Level.FINE, "Processing context for request is {0}",
          ex.getHttpContext().getHandler().getClass().getName());
      chain.doFilter(ex);
    } catch (RuntimeException e) {
      log.log(Level.WARNING, "Unexpected exception during request", e);
      throw e;
    } catch (IOException e) {
      log.log(Level.WARNING, "Unexpected exception during request", e);
      throw e;
    } finally {
      logResponse(ex);
      log.fine("ending");
    }
  }

  private void logRequest(HttpExchange ex) {
    if (log.isLoggable(Level.FINER)) {
      log.log(Level.FINER, "Received {1} request to {0}. Headers: '{'{2}'}'",
              new Object[] {ex.getRequestURI(), ex.getRequestMethod(),
                            getLoggableHeaders(ex.getRequestHeaders())});
    }
  }

  private void logResponse(HttpExchange ex) {
    if (log.isLoggable(Level.FINER)) {
      log.log(Level.FINER, "Responded to {1} request {0}. Headers: '{'{2}'}'",
              new Object[] {ex.getRequestURI(), ex.getRequestMethod(),
                            getLoggableHeaders(ex.getResponseHeaders())});
    }
  }

  @VisibleForTesting
  String getLoggableHeaders(Headers headers) {
    if (headers.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> me : headers.entrySet()) {
      for (String value : me.getValue()) {
        sb.append(me.getKey());
        sb.append(": ");
        sb.append(value);
        sb.append(", ");
      }
    }
    // Cut off trailing ", "
    return sb.substring(0, sb.length() - 2);
  }
}
