// Copyright 2010 Google Inc.
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

package com.google.enterprise.secmgr.common;

import com.google.common.base.Preconditions;

import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

/**
 * A collection of session utilities.
 */
public final class SessionUtil {
  /**
   * The name of the GSA session ID cookie.
   */
  public static final String GSA_SESSION_ID_COOKIE_NAME = "GSA_SESSION_ID";

  /**
   * A regular expression that matches a valid session ID; basically alphanumeric.
   */
  // TODO(cph): might be useful to broaden this pattern to handle base64.
  private static final Pattern SESSION_ID_REGEXP = Pattern.compile("[0-9A-Za-z]*");

  /**
   * The smallest acceptable length for a session ID string.
   */
  private static final int MIN_ACCEPTABLE_SESSION_ID_LENGTH = 16;

  /**
   * The largest acceptable length for a session ID string.
   */
  private static final int MAX_ACCEPTABLE_SESSION_ID_LENGTH = 100;

  /**
   * The length of a generated session ID string.
   */
  private static final int GENERATED_SESSION_ID_LENGTH = MIN_ACCEPTABLE_SESSION_ID_LENGTH;

  // Don't instantiate.
  private SessionUtil() {
    throw new UnsupportedOperationException();
  }

  /**
   * Generate a session ID for a new session.
   */
  public static String generateId() {
    return SecurityManagerUtil.generateRandomNonceHex(GENERATED_SESSION_ID_LENGTH / 2);
  }

  /**
   * Is the given string a valid session ID?
   *
   * @param proposedId The string to test.
   * @return True only if the string is valid.
   */
  public static boolean isValidId(String proposedId) {
    return proposedId != null
        && proposedId.length() >= MIN_ACCEPTABLE_SESSION_ID_LENGTH
        && proposedId.length() <= MAX_ACCEPTABLE_SESSION_ID_LENGTH
        && SESSION_ID_REGEXP.matcher(proposedId).matches();
  }

  /**
   * Get the GSA session ID by examining the cookies in an incoming request.
   *
   * @param request The HTTP request to check the cookies of.
   * @return The GSA session ID, if a valid one is found; otherwise null.
   */
  public static String findGsaSessionId(HttpServletRequest request) {
    Preconditions.checkNotNull(request);
    CookieStore cookies = GCookie.parseHttpRequestCookies(request, null);
    for (GCookie c : cookies) {
      if (GSA_SESSION_ID_COOKIE_NAME.equalsIgnoreCase(c.getName())
          && isValidId(c.getValue())) {
        return c.getValue();
      }
    }
    return null;
  }
}
