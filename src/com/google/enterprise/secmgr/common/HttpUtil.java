// Copyright 2009 Google Inc.
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

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import org.springframework.mock.web.MockHttpServletRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Static methods for fetching pages via HTTP.  See RFCs 2616 and 2617 for
 * details.
 */
public final class HttpUtil {

  public static final String HTTP_METHOD_GET = "GET";
  public static final String HTTP_METHOD_POST = "POST";
  public static final String HTTP_METHOD_HEAD = "HEAD";

  // HTTP header names.
  public static final String HTTP_HEADER_ACCEPT = "Accept";
  public static final String HTTP_HEADER_ACCEPT_CHARSET = "Accept-Charset";
  public static final String HTTP_HEADER_ACCEPT_ENCODING = "Accept-Encoding";
  public static final String HTTP_HEADER_ACCEPT_LANGUAGE = "Accept-Language";
  public static final String HTTP_HEADER_AUTHORIZATION = "Authorization";
  public static final String HTTP_HEADER_CONNECTION = "Connection";
  public static final String HTTP_HEADER_CONTENT_LENGTH = "Content-Length";
  public static final String HTTP_HEADER_COOKIE = "Cookie";
  public static final String HTTP_HEADER_DATE = "Date";
  public static final String HTTP_HEADER_LOCATION = "Location";
  public static final String HTTP_HEADER_PROXY_AUTHENTICATE = "Proxy-Authenticate";
  public static final String HTTP_HEADER_PROXY_AUTHORIZATION = "Proxy-Authorization";
  public static final String HTTP_HEADER_RANGE = "Range";
  public static final String HTTP_HEADER_SET_COOKIE = "Set-Cookie";
  public static final String HTTP_HEADER_SET_COOKIE2 = "Set-Cookie2";
  public static final String HTTP_HEADER_USER_AGENT = "User-Agent";
  public static final String HTTP_HEADER_WWW_AUTHENTICATE = "WWW-Authenticate";

  // TODO(kstillson): The rest of this file is general purpose http, but
  // this cookie-cracking code is quite security manager specific.  At some
  // point, it would be good to refactor (perhaps providing a caller
  // specified callback for custom header processing?)
  //
  // TODO(kstillson): make this extensible/configurable (at least from spring).
  // These are Google-specific headers set in a response, which can set the
  // username and groups list for a credentials group.
  public static final String COOKIE_CRACK_USERNAME_HEADER = "X-Username";
  public static final String COOKIE_CRACK_GROUPS_HEADER = "X-Groups";

  // Boilerplate HTTP header values.
  public static final String KEEP_ALIVE = "keep-alive";
  // TODO(michellez): make this configurable through spring.
  public static final String USER_AGENT = "SecMgr";
  public static final String ACCEPT =
      "text/html, text/xhtml;q=0.9, text/plain;q=0.5, text/*;q=0.1";
  public static final String ACCEPT_FOR_HEAD = "*/*";
  public static final String ACCEPT_CHARSET = "us-ascii, iso-8859-1, utf-8";
  public static final String ACCEPT_ENCODING = "identity";
  public static final String ACCEPT_LANGUAGE = "en-us, en;q=0.9";
  private static final String RANGE_FORMAT = "bytes=0-%d";

  public static final char PARAM_VALUE_SEPARATOR = '=';
  public static final char STRING_DELIMITER = '"';
  public static final char STRING_QUOTE = '\\';
  public static final char PARAM_SEPARATOR_CHAR = ';';
  public static final String PARAM_SEPARATOR = "; ";

  // Don't instantiate.
  private HttpUtil() {
    throw new UnsupportedOperationException();
  }

  public static boolean isHttpGetMethod(String method) {
    return HTTP_METHOD_GET.equalsIgnoreCase(method);
  }

  public static boolean isHttpPostMethod(String method) {
    return HTTP_METHOD_POST.equalsIgnoreCase(method);
  }

  public static boolean isHttpHeadMethod(String method) {
    return HTTP_METHOD_HEAD.equalsIgnoreCase(method);
  }

  /*
  // Commented out to prevent pulling in ServletBase.
  public static List<StringPair> getBoilerplateHeaders() {
    return getBoilerplateHeaders(false);
  }

  public static List<StringPair> getBoilerplateHeaders(boolean isHeadRequest) {
    String accept = isHeadRequest ? ACCEPT_FOR_HEAD : ACCEPT;
    return ImmutableList.of(
        new StringPair(HTTP_HEADER_ACCEPT, accept),
        new StringPair(HTTP_HEADER_ACCEPT_CHARSET, ACCEPT_CHARSET),
        new StringPair(HTTP_HEADER_ACCEPT_ENCODING, ACCEPT_ENCODING),
        new StringPair(HTTP_HEADER_ACCEPT_LANGUAGE, ACCEPT_LANGUAGE),
        new StringPair(HTTP_HEADER_DATE, ServletBase.httpDateString()));
  }*/

  /**
   * Does the given HTTP status code indicate a valid response?
   *
   * @param status The status code to test.
   * @return True only if it indicates a valid response.
   */
  public static boolean isGoodHttpStatus(int status) {
    return status == HttpServletResponse.SC_OK
        || status == HttpServletResponse.SC_PARTIAL_CONTENT;
  }

  public static URL urlFromString(String urlString) {
    try {
      return new URL(urlString);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static URL urlFromString(URL baseUrl, String urlString) {
    try {
      return new URL(baseUrl, urlString);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static URL urlFromParts(String protocol, String host, int port, String file) {
    try {
      return new URL(protocol, host, port, file);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static URL parseUrlString(String urlString) {
    try {
      return new URL(urlString);
    } catch (MalformedURLException e) {
      return null;
    }
  }

  public static URL parseUrlString(URL baseUrl, String urlString) {
    try {
      return new URL(baseUrl, urlString);
    } catch (MalformedURLException e) {
      return null;
    }
  }

  public static URL parentUrl(URL url) {
    String path = url.getPath();
    int slash = path.lastIndexOf('/');
    if (slash <= 0) {
      return null;
    }
    return urlFromParts(url.getProtocol(), url.getHost(), url.getPort(), path.substring(0, slash));
  }

  public static URL stripQueryFromUrl(URL url) {
    return mergeQueryIntoUrl(url, null);
  }

  public static URL mergeQueryIntoUrl(URL url, String query) {
    return urlFromParts(url.getProtocol(), url.getHost(), url.getPort(),
        newQuery(url.getPath(), query));
  }

  private static String newQuery(String path, String query) {
    return Strings.isNullOrEmpty(query) ? path : path + "?" + query;
  }

  /**
   * Converts a {@link URL} to a {@link URI}.
   *
   * @param url The URL to convert.
   * @return The corresponding URI.
   * @throws IllegalArgumentException if there are any parse errors in the
   *     conversion.
   */
  public static URI toUri(URL url) {
    return URI.create(url.toString());
  }

  /**
   * Takes a given URI and returns a new one in which the query component has
   * been replaced with a given query string.
   *
   * @param uri The base URI.
   * @param query The new query component; may be {@code null} to delete the
   *     query component.
   * @return A suitably modified URI.
   */
  public static URI replaceUriQuery(URI uri, String query) {
    try {
      return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
          uri.getPath(), query, uri.getFragment());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Decodes an application/x-www-form-urlencoded format query string into its
   * component parameters.
   *
   * @param uri A URI to decode the query string of.
   * @return A multimap containing the decoded parameters from the uri.
   * @throws IllegalArgumentException if the URI's query isn't correctly formatted.
   */
  public static ListMultimap<String, String> decodeQueryString(URI uri) {
    return decodeQueryString(uri.getQuery());
  }

  /**
   * Decodes an application/x-www-form-urlencoded format query string into its
   * component parameters.
   *
   * @param string The query string to decode.
   * @return A multimap containing the decoded parameters from the string.
   * @throws IllegalArgumentException if the string isn't correctly formatted.
   */
  public static ListMultimap<String, String> decodeQueryString(String string) {
    ListMultimap<String, String> result = ArrayListMultimap.create();
    if (!Strings.isNullOrEmpty(string)) {
      for (String element : QUERY_SPLITTER.split(string)) {
        int index = element.indexOf('=');
        if (index < 0) {
          result.put(element, null);
        } else {
          result.put(element.substring(0, index), element.substring(index + 1));
        }
      }
    }
    return result;
  }

  /**
   * Encodes a multimap of query parameters in application/x-www-form-urlencoded
   * format.
   *
   * @param parameters The query parameters to be encoded.
   * @return The encoded string.
   */
  public static String encodeQueryString(Multimap<String, String> parameters) {
    StringBuilder builder = new StringBuilder();
    boolean needSeparator = false;
    for (Map.Entry<String, String> entry : parameters.entries()) {
      if (needSeparator) {
        builder.append('&');
      } else {
        needSeparator = true;
      }
      builder.append(entry.getKey());
      if (entry.getValue() != null) {
        builder.append('=');
        builder.append(entry.getValue());
      }
    }
    return builder.toString();
  }

  private static final Splitter QUERY_SPLITTER = Splitter.on('&');

  /**
   * Gets the URI for an HTTP request.
   *
   * @param request The HTTP request to get the URI from.
   * @param includeQuery If true, include the query part of the URI.
   * @return The request URI.
   * @throws IllegalArgumentException if the request's URI can't be parsed.
   */
  public static URI getRequestUri(HttpServletRequest request, boolean includeQuery) {
    URI uri = (request instanceof MockHttpServletRequest)
        ? getMockRequestUri((MockHttpServletRequest) request)
        : URI.create(request.getRequestURL().toString());
    return includeQuery
        ? uri
        : replaceUriQuery(uri, null);
  }

  private static URI getMockRequestUri(MockHttpServletRequest request) {
    // Note that it's not OK to call request.getRequestURL() because the mock
    // implementation is broken and will include ":-1" if there's no port
    // specified.
    try {
      return new URI(request.getScheme(), null, request.getServerName(), request.getServerPort(),
          request.getRequestURI(), request.getQueryString(), null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Gets the URL for an HTTP request.
   *
   * @param request The HTTP request to get the URL from.
   * @param includeQuery If true, include the query part of the URL.
   * @return The request URL.
   * @throws IllegalArgumentException if the request's URL can't be parsed.
   */
  public static URL getRequestUrl(HttpServletRequest request, boolean includeQuery) {
    try {
      return getRequestUri(request, includeQuery).toURL();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Given a URL, gets a string suitable for logging.  This string omits the URL
   * query, as it might contain sensitive parameters that shouldn't be logged
   * (e.g. a password).  It also omits the fragment identifier, since that isn't
   * usually needed in the log.
   *
   * @param url The URL to get a log string for.
   * @return An appropriate string representation of the URL.
   */
  public static String getUrlLogString(URL url) {
    return getUriLogString(toUri(url));
  }

  /**
   * Given a URL string, gets a string suitable for logging.  This string omits
   * the URL query, as it might contain sensitive parameters that shouldn't be
   * logged (e.g. a password).  It also omits the fragment identifier, since
   * that isn't usually needed in the log.
   *
   * @param urlString The URL string to get a log string for.
   * @return An appropriate string representation of the URL.
   */
  public static String getUrlLogString(String urlString) {
    URI uri;
    try {
      uri = new URI(urlString);
    } catch (URISyntaxException e) {
      // Dumb, but in the unlikely event we get the exception, it should serve.
      int index = urlString.indexOf('?');
      return (index >= 0)
          ? urlString.substring(0, index)
          : urlString;
    }
    return getUriLogString(uri);
  }

  /**
   * Given a URI, gets a string suitable for logging.  This string omits the URI
   * query, as it might contain sensitive parameters that shouldn't be logged
   * (e.g. a password).  It also omits the fragment identifier, since that isn't
   * usually needed in the log.
   *
   * @param uri The URI to get a log string for.
   * @return An appropriate string representation of the URL.
   */
  public static String getUriLogString(URI uri) {
    try {
      return (new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null))
          .toASCIIString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Gets the header value strings for any headers matching a given name.
   *
   * @param name The header name to look for.
   * @param request The request to look in.
   * @return The header values as an immutable list.
   */
  public static ImmutableList<String> getRequestHeaderValues(String name,
      HttpServletRequest request) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    Enumeration<?> e = request.getHeaders(name);
    while (e.hasMoreElements()) {
      builder.add(String.class.cast(e.nextElement()));
    }
    return builder.build();
  }

  /**
   * Parse an HTTP header parameter.  Parameters come in two forms:
   *
   * token PARAM_VALUE_SEPARATOR token
   * token PARAM_VALUE_SEPARATOR quoted-string
   *
   * The character set for a "token" is restricted.  A "quoted-string" is
   * surrounded by double quotes and can contain nearly all characters, plus
   * escaped characters.
   *
   * @param string The raw parameter string, assumed to have been trimmed of whitespace.
   * @return A list of two strings, the name and the value.
   * @throws IllegalArgumentException if the string can't be parsed.
   */
  public static List<String> parseHttpParameter(String string) {
    int equals = string.indexOf(PARAM_VALUE_SEPARATOR);
    checkParameterArgument(equals >= 0, string);
    String name = string.substring(0, equals);
    checkParameterArgument(isHttpToken(name), string);
    String rawValue = string.substring(equals + 1, string.length());
    return ImmutableList.of(name,
        isHttpToken(rawValue) ? rawValue : parseHttpQuotedString(rawValue));
  }

  private static void checkParameterArgument(boolean succeed, String argument) {
    Preconditions.checkArgument(succeed, "Incorrectly formatted HTTP parameter: %s", argument);
  }

  /**
   * Is the given string an HTTP token?
   *
   * @param string The string to test.
   * @return True if the string is a valid HTTP token.
   */
  public static boolean isHttpToken(String string) {
    return !Strings.isNullOrEmpty(string) && TOKEN.matchesAllOf(string);
  }

  /**
   * Is the given string something that can be encoded as an HTTP quoted-string?
   *
   * @param string The string to test.
   * @return True if the string can be encoded using the quoted-string format.
   */
  public static boolean isQuotedStringEncodable(String string) {
    return string != null && TEXT.matchesAllOf(string);
  }

  /**
   * Encodes a string so that it's suitable as an HTTP parameter value.  In
   * other words, if the string is an HTTP token, it's self encoding.
   * Otherwise, it is converted to the quoted-string format.
   *
   * @param string The string to be encoded.
   * @return The same string encoded as an HTTP parameter value.
   * @throws IllegalArgumentException if the given string can't be encoded.
   */
  public static String makeHttpParameterValueString(String string) {
    if (isHttpToken(string)) {
      return string;
    }
    StringBuilder builder = new StringBuilder();
    writeQuotedString(string, builder);
    return builder.toString();
  }

  /**
   * Writes a string-valued HTTP parameter to a given string builder.  The
   * parameter is prefixed by {@link #PARAM_SEPARATOR}.
   *
   * @param name The parameter name, which must satisfy {@link #isHttpToken}.
   * @param value The parameter value, which must satisfy
   *     {@link #isQuotedStringEncodable}.
   * @param builder A string builder to write the parameter to.
   * @throws IllegalArgumentException if {@code name} or {@code value} can't be
   *     encoded.
   */
  public static void writeParameter(String name, String value, StringBuilder builder) {
    writeParameterName(name, builder);
    builder.append(PARAM_VALUE_SEPARATOR);
    writeParameterValue(value, builder);
  }

  /**
   * Writes a boolean-valued HTTP parameter to a given string builder.  The
   * parameter is prefixed by {@link #PARAM_SEPARATOR}.
   *
   * @param name The parameter name, which must satisfy {@link #isHttpToken}.
   * @param value The parameter value.
   * @param builder A string builder to write the parameter to.
   * @throws IllegalArgumentException if {@code name} can't be encoded.
   */
  public static void writeParameter(String name, boolean value, StringBuilder builder) {
    if (value) {
      writeParameterName(name, builder);
    }
  }

  /**
   * Writes an HTTP parameter name to a given string builder.  The name is
   * prefixed by {@link #PARAM_SEPARATOR}.
   *
   * @param name The parameter name, which must satisfy {@link #isHttpToken}.
   * @param builder A string builder to write the name to.
   * @throws IllegalArgumentException if {@code name} can't be encoded.
   */
  public static void writeParameterName(String name, StringBuilder builder) {
    Preconditions.checkArgument(isHttpToken(name));
    builder.append(PARAM_SEPARATOR);
    builder.append(name);
  }

  /**
   * Writes an HTTP parameter value to a given string builder.
   *
   * @param value The parameter value, which must satisfy
   *     {@link #isQuotedStringEncodable}.
   * @param builder A string builder to write the value to.
   * @throws IllegalArgumentException if {@code value} can't be encoded.
   */
  public static void writeParameterValue(String value, StringBuilder builder) {
    if (isHttpToken(value)) {
      builder.append(value);
    } else {
      writeQuotedString(value, builder);
    }
  }

  /**
   * Writes a string to a string builder in HTTP quoted-string format.
   *
   * @param string The string to be written.
   * @param builder A string builder to write the string to.
   * @throws IllegalArgumentException if {@code string} can't be encoded.
   */
  public static void writeQuotedString(String string, StringBuilder builder) {
    Preconditions.checkArgument(isQuotedStringEncodable(string),
        "String can't be encoded as an HTTP parameter value: %s", string);
    builder.append(STRING_DELIMITER);
    for (char c : string.toCharArray()) {
      if (c == STRING_QUOTE || c == STRING_DELIMITER) {
        builder.append(STRING_QUOTE);
      }
      builder.append(c);
    }
    builder.append(STRING_DELIMITER);
  }

  /**
   * Parses an HTTP quoted-string.
   *
   * @param string The string to parse.
   * @return The parsed value of the quoted string.
   * @throws IllegalArgumentException if the string isn't a valid quoted-string.
   */
  public static String parseHttpQuotedString(String string) {
    int end = string.length();
    checkQuotedStringArgument(
        (end >= 2
            && string.charAt(0) == STRING_DELIMITER
            && string.charAt(end - 1) == STRING_DELIMITER),
        string);
    StringBuilder builder = new StringBuilder();
    boolean pendingQuote = false;
    for (char c : string.substring(1, end - 1).toCharArray()) {
      if (pendingQuote) {
        pendingQuote = false;
        checkQuotedStringArgument(CHAR.matches(c), string);
        builder.append(c);
      } else if (c == STRING_QUOTE) {
        pendingQuote = true;
      } else {
        checkQuotedStringArgument(QDTEXT.matches(c), string);
        builder.append(c);
      }
    }
    checkQuotedStringArgument(!pendingQuote, string);
    return builder.toString();
  }

  /**
   * Gets the http range header value for requesting the first number of bytes.
   *
   * @param bytes The number of bytes to request.
   * @return The range header value
   */
  public static String getRangeString(int bytes) {
    return String.format(RANGE_FORMAT, bytes);
  }

  private static void checkQuotedStringArgument(boolean succeed, String argument) {
    Preconditions.checkArgument(succeed, "Incorrectly formatted quoted-string: %s", argument);
  }

  // These names are taken directly from RFC 2616.

  private static final CharMatcher OCTET = CharMatcher.inRange('\u0000', '\u00ff');

  // Not strictly correct: CHAR technically includes CR and LF, but only for
  // line folding.  Since we're looking at a post-line-folding string, they
  // shouldn't be present.
  private static final CharMatcher CHAR = difference(CharMatcher.ASCII, CharMatcher.anyOf("\n\r"));

  /** ASCII control characters. */
  public static final CharMatcher CTLS =
      CharMatcher.inRange('\u0000', '\u001f').or(CharMatcher.is('\u007f'));

  /** ASCII alphabetic characters. */
  public static final CharMatcher ALPHA =
      CharMatcher.anyOf("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");

  /** ASCII digit characters. */
  public static final CharMatcher DIGIT = CharMatcher.anyOf("0123456789");

  /** Linear white space. */
  public static final CharMatcher LWS = CharMatcher.anyOf(" \t");

  /** Plain text. */
  public static final CharMatcher TEXT = union(difference(OCTET, CTLS), LWS);

  // Text that can be included in a quoted-string without backquotes.  Note that
  // RFC 2616 specifies only '"' as an exception, but clearly '\\' needs to be
  // excepted as well.
  private static final CharMatcher QDTEXT = difference(TEXT, CharMatcher.anyOf("\"\\"));

  // Separator characters that aren't allowed in most places except inside
  // quoted-strings.
  private static final CharMatcher SEPARATORS = CharMatcher.anyOf("()<>@,;:\\\"/[]?={} \t");

  // The constituent characters of a token.
  private static final CharMatcher TOKEN = difference(CharMatcher.ASCII, union(CTLS, SEPARATORS));

  private static CharMatcher union(CharMatcher m1, CharMatcher m2) {
    return m1.or(m2);
  }

  private static CharMatcher difference(CharMatcher m1, CharMatcher m2) {
    return m1.and(m2.negate());
  }

  // HTTP date formats (from RFC 2616):
  //
  // HTTP-date    = rfc1123-date | rfc850-date | asctime-date
  // rfc1123-date = wkday "," SP date1 SP time SP "GMT"
  // rfc850-date  = weekday "," SP date2 SP time SP "GMT"
  // asctime-date = wkday SP date3 SP time SP 4DIGIT
  // date1        = 2DIGIT SP month SP 4DIGIT
  //                ; day month year (e.g., 02 Jun 1982)
  // date2        = 2DIGIT "-" month "-" 2DIGIT
  //                ; day-month-year (e.g., 02-Jun-82)
  // date3        = month SP ( 2DIGIT | ( SP 1DIGIT ))
  //                ; month day (e.g., Jun  2)
  // time         = 2DIGIT ":" 2DIGIT ":" 2DIGIT
  //                ; 00:00:00 - 23:59:59
  // wkday        = "Mon" | "Tue" | "Wed"
  //              | "Thu" | "Fri" | "Sat" | "Sun"
  // weekday      = "Monday" | "Tuesday" | "Wednesday"
  //              | "Thursday" | "Friday" | "Saturday" | "Sunday"
  // month        = "Jan" | "Feb" | "Mar" | "Apr"
  //              | "May" | "Jun" | "Jul" | "Aug"
  //              | "Sep" | "Oct" | "Nov" | "Dec"

  private static final String DATE_FORMAT_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";
  private static final String DATE_FORMAT_RFC850 = "EEEE, dd-MMM-yy HH:mm:ss zzz";
  private static final String DATE_FORMAT_ASCTIME = "EEE MMM dd HH:mm:ss yyyy";

  /**
   * Generates an HTTP date string.
   *
   * @param date A date value specified as a non-negative difference from the
   *     epoch in milliseconds.
   * @return An HTTP date string representing that date.
   */
  public static String generateHttpDate(long date) {
    return getDateFormat(DATE_FORMAT_RFC1123).format(new Date(date));
  }

  /**
   * Parses an HTTP date string.
   *
   * @param dateString The string to parse.
   * @return The difference, measured in milliseconds, between the specified
   *     date and 1970-01-01T00:00:00Z.
   * @throws IllegalArgumentException if the date string can't be parsed.
   */
  public static long parseHttpDate(String dateString) {
    try {
      return parseDate(DATE_FORMAT_RFC1123, dateString);
    } catch (ParseException e) {
      // Fall through to next format.
    }
    try {
      return parseDate(DATE_FORMAT_RFC850, dateString);
    } catch (ParseException e) {
      // Fall through to next format.
    }
    try {
      return parseDate(DATE_FORMAT_ASCTIME, dateString);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Can't parse as HTTP date string: " + dateString);
    }
  }

  private static long parseDate(String formatString, String dateString)
      throws ParseException {
    return getDateFormat(formatString).parse(dateString).getTime();
  }

  private static DateFormat getDateFormat(String formatString) {
    DateFormat format = new SimpleDateFormat(formatString);
    format.setCalendar(Calendar.getInstance(GMT, Locale.US));
    return format;
  }

  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  /**
   * Is the given string a valid domain name?  Uses a fairly restrictive
   * definition, corresponding to the "preferred syntax" of RFC 1034 as updated
   * by RFC 1123.
   *
   * @param string The string to be tested.
   * @return True only if the string is a valid domain name.
   */
  public static boolean isValidDomainName(String string) {
    return parseDomainName(string) != null;
  }

  /**
   * Converts a given domain name to its canonical form.  This should eventually
   * handle IDNA names, but for now we just canonicalize case.
   *
   * @param domainName The domain name to convert.
   * @return The canonical form for {@code domainName}.
   * @throws IllegalArgumentException if {@code domainName} doesn't satisfy
   *     {@code #isValidDomainName}.
   */
  public static String canonicalizeDomainName(String domainName) {
    List<String> labels = parseDomainName(domainName);
    Preconditions.checkArgument(labels != null, "Not a valid domain name: %s", domainName);
    return labelsToDomanName(labels);
  }

  /**
   * Gets the "parent domain" name of a domain name.
   *
   * @param domainName The domain name to get the parent domain name of.
   * @return The parent domain name, or {@code null} if there isn't one.
   * @throws IllegalArgumentException if {@code domainName} doesn't satisfy
   *     {@code #isValidDomainName}.
   */
  public static String domainNameParent(String domainName) {
    List<String> labels = parseDomainName(domainName);
    Preconditions.checkArgument(labels != null, "Not a valid domain name: %s", domainName);
    if (labels.size() < 2) {
      return null;
    }
    labels.remove(0);
    return labelsToDomanName(labels);
  }

  private static List<String> parseDomainName(String domainName) {
    if (!(domainName.length() >= 1 && domainName.length() <= 255)) {
      return null;
    }
    List<String> labels = Lists.newArrayList(DOMAIN_NAME_SPLITTER.split(domainName));
    if (!(labels.size() >= 1 && labels.size() <= 127)) {
      return null;
    }
    for (String label : labels) {
      if (!isValidDomainLabel(label)) {
        return null;
      }
    }
    // Eliminates IPv4 addresses:
    if (DIGIT.matchesAllOf(labels.get(labels.size() - 1))) {
      return null;
    }
    return labels;
  }

  private static boolean isValidDomainLabel(String label) {
    return label.length() >= 1
        && label.length() <= 63
        && DOMAIN_LABEL_CHAR.matchesAllOf(label)
        && label.charAt(0) != '-'
        && label.charAt(label.length() - 1) != '-';
  }

  private static String labelsToDomanName(List<String> labels) {
    return DOMAIN_NAME_JOINER.join(
        Iterables.transform(labels,
            new Function<String, String>() {
              @Override
              public String apply(String label) {
                return label.toLowerCase(Locale.US);
              }
            }));
  }

  private static final CharMatcher DOMAIN_LABEL_CHAR = ALPHA.or(DIGIT).or(CharMatcher.is('-'));
  private static final Splitter DOMAIN_NAME_SPLITTER = Splitter.on('.');
  private static final Joiner DOMAIN_NAME_JOINER = Joiner.on('.');
}
