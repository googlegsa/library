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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Acquires and provides GSA's version. */
final class GsaVersion {
  private static final Logger log
      = Logger.getLogger(GsaVersion.class.getName());
  private static final Charset charset = Charset.forName("UTF-8");

  private String ver;  // example: 7.2.1-1
  private int parts[] = new int[4];

  private static final Pattern VERSION_FORMAT
      = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)\\-(\\d+)$");
 
  GsaVersion(String version) {
    ver = version;
    Matcher m = VERSION_FORMAT.matcher(ver);
    if (!m.matches()) {
      throw new IllegalArgumentException(
          "GSA version is incorrectly formated : " + ver);
    }
    parts[0] = Integer.parseInt(m.group(1));
    parts[1] = Integer.parseInt(m.group(2));
    parts[2] = Integer.parseInt(m.group(3));
    parts[3] = Integer.parseInt(m.group(4));
  }

  /* Requsts entire detailed version string and returns it. */
  static GsaVersion get(String host, boolean securely) throws IOException {
    String protocol = securely ? "https" : "http";
    URL url = new URL(protocol, host, "/sw_version.txt");
    log.log(Level.FINE, "about to ask GSA for {0}", url);
    URLConnection conn = url.openConnection();
    InputStream in = conn.getInputStream();
    String ver = IOHelper.readInputStreamToString(in, charset);
    ver = ver.replaceAll("\\s", "");
    return new GsaVersion(ver);
  }

  /** Provides entire version string gotten from GSA, eg. 7.2.1-1 */
  @Override
  public String toString() {
    return ver;
  } 

  public boolean isAtLeast(String minimum) {
    GsaVersion min = new GsaVersion(minimum);
    for (int i = 0; i < parts.length; i++) {
      if (parts[i] < min.parts[i]) {
        return false;
      }
      if (parts[i] > min.parts[i]) {
        return true;
      }
      // parts[i] == min.parts[i] are equal
    }
    return true;  // all parts were the same
  }
}
