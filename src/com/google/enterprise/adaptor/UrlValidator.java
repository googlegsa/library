// Copyright 2017 Google Inc. All Rights Reserved.
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates URLs by syntax checking and validating the host is reachable.
 * No attempts to fetch the URL are made, so a mocked up URL with
 * placeholder values in format substitutions may be used.
 */
public class UrlValidator {
  /** The logger for this class. */
  private static final Logger log =
      Logger.getLogger(UrlValidator.class.getName());

  /** The connect timeout, in milliseconds. */
  private static final int TIMEOUT = 30 * 1000;

  /**
   * Attempts to validate the given URL syntax and host reachability.
   * In this case, we're mostly trying to catch typos.
   *
   * @param urlString the URL to test
   * @return {@code true} if the URL's host is reachable, {@code false}
   *         if the host is not reachable.
   * @throws MalformedURLException if the URL can not be parsed
   */
  public boolean validate(String urlString) throws MalformedURLException {
    URL url = new URL(urlString);
    String host = url.getHost();

    // We won't accept URLs implicitly pointing to localhost.
    if (host.isEmpty()) {
      throw new MalformedURLException("no host: " + urlString);
    }

    // Try to determine if the host is reachable at this time.
    try {
      if (InetAddress.getByName(host).isReachable(TIMEOUT)) {
        log.log(Level.CONFIG, "Host {0} from URL {1} is reachable.",
            new Object[] { host, urlString });
        return true;
      } else {
        log.log(Level.WARNING, "Host {0} from URL {1} is not reachable.",
            new Object[] { host, urlString });
      }
    } catch (IOException e) {
      log.log(Level.WARNING, "Host " + host + " from URL " + urlString
          + " is not reachable.", e);
    }
    return false;
  }
}
