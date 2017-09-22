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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Enum for all translation keys. All user-visible messages should exist in our
 * resource bundle and have its key here.
 */
enum Translation {
  AUTHN_RETRY,
  AUTHN_UNKNOWN_SESSION,
  AUTHN_NOT_STARTED,
  AUTHZ_BAD_QUERY_NO_RESOURCE,
  AUTHZ_BAD_QUERY_NO_SUBJECT,
  AUTHZ_BAD_QUERY_NOT_SAME_USER,
  HTTP_BAD_REQUEST_INVALID_JSON,
  HTTP_BAD_REQUEST_ERROR_DECODING,
  HTTP_BAD_REQUEST_SECURITY_ERROR,
  HTTP_FORBIDDEN,
  HTTP_FORBIDDEN_AUTHN_FAILURE,
  HTTP_FORBIDDEN_SECMGR,
  HTTP_NOT_FOUND,
  HTTP_BAD_METHOD,
  HTTP_CONFLICT_INVALID_HEADER,
  HTTP_INTERNAL_ERROR,
  STATS_CONFIG_NONE,
  STATS_VERSION_UNKNOWN,
  STATUS_CRAWLING,
  STATUS_CRAWLING_NO_ACCESSES_IN_PAST_DAY,
  STATUS_ERROR_RATE,
  STATUS_ERROR_RATE_RATE,
  STATUS_FEED,
  STATUS_FEED_INTERRUPTED,
  STATUS_JAVA_VERSION,
  STATUS_JAVA_VERSION_SUPPORTED,
  STATUS_JAVA_VERSION_UNKNOWN,
  STATUS_JAVA_VERSION_PARTIAL,
  STATUS_JAVA_VERSION_UNSUPPORTED,
  ;

  /**
   * @throws java.util.MissingResourceException if it could not find a string
   *   for the default locale
   */
  @Override
  public String toString() {
    return toString(Locale.getDefault());
  }

  /**
   * @throws java.util.MissingResourceException if it could not find a string
   *   for the provided {@code locale}
   */
  public String toString(Locale locale) {
    String localeClassStr = "com.google.enterprise.adaptor.TranslationStrings";
    return ResourceBundle.getBundle(localeClassStr, locale)
        .getString(name());
  }

  /**
   * @throws java.util.MissingResourceException if it could not find a string
   *   for the default locale
   */
  public String toString(Object... params) {
    return toString(Locale.getDefault(), params);
  }

  /**
   * @throws java.util.MissingResourceException if it could not find a string
   *   for the provided {@code locale}
   */
  public String toString(Locale locale, Object... params) {
    String translation = toString(locale);
    return new MessageFormat(translation, locale)
        .format(params, new StringBuffer(), null).toString();
  }
}
