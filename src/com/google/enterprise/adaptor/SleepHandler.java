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
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.logging.*;

/** Handler that simply sleeps for a given amount of time. */
class SleepHandler implements HttpHandler {
  private static final Logger log
      = Logger.getLogger(SleepHandler.class.getName());

  private final Charset charset = Charset.forName("UTF-8");
  private final long sleepDurationMillis;

  public SleepHandler(long sleepDurationMillis) {
    this.sleepDurationMillis = sleepDurationMillis;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    if (!"GET".equals(ex.getRequestMethod())) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
          Translation.HTTP_BAD_METHOD);
      return;
    }
    if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
          Translation.HTTP_NOT_FOUND);
      return;
    }
    try {
      Thread.sleep(sleepDurationMillis);
    } catch (InterruptedException ie) {
      log.log(Level.WARNING, "Request interrupted", ie);
      HttpExchanges.respond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR,
          "text/plain", "Interrupted".getBytes(charset));
      return;
    }
    HttpExchanges.respond(ex, HttpURLConnection.HTTP_OK, "text/plain",
        "Done".getBytes(charset));
  }
}
