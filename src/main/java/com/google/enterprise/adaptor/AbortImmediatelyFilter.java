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

/** Filter that aborts the request when server is under high load. */
class AbortImmediatelyFilter extends Filter {
  @Override
  public String description() {
    return "Filter that aborts the request when server is under high load";
  }

  @Override
  public void doFilter(HttpExchange ex, Filter.Chain chain) throws IOException {
    // Checking abortImmediately is part of a hack to immediately reject clients
    // when the work queue grows too long.
    if (HttpExchanges.abortImmediately.get() != null) {
      throw new IOException(
          "Aborting request because server is under high load");
    }
    chain.doFilter(ex);
  }
}
