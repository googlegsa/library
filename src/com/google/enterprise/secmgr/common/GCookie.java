// Copyright 2011 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;

import org.joda.time.DateTimeUtils;

import java.net.URI;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * An immutable cookie implementation that complies with RFC 6265.  Note that
 * this implementation does not (yet) support IDNA (RFC 5890) even though that
 * is required by the specification.
 */
@Immutable
@ParametersAreNonnullByDefault
public final class GCookie {
  private static final Logger LOGGER = Logger.getLogger(GCookie.class.getName());

  // A cookie with its name in this list will always have its value logged in
  // cleartext.
  private static final ImmutableList<String> ALWAYS_SHOW_VALUE =
      ImmutableList.of("GSA_SESSION_ID", "JSESSIONID");
  private static final char OBFUSCATED_VALUE_PREFIX = '#';
  private static final char VALUE_SEPARATOR = '=';
  private static final char REQUEST_SEPARATOR = ';';
  private static final String REQUEST_SEPARATOR_STRING = "; ";
  private static final char ATTR_SEPARATOR = ';';
  private static final String ATTR_SEPARATOR_STRING = "; ";
  private static final char PATH_SEPARATOR = '/';
  public static final String UNIVERSAL_PATH = "/";

  private static final CharMatcher WSP = CharMatcher.anyOf(" \t");

  /**
   * Characters allowed in a cookie name.  Servers are supposed to restrict
   * names to the HTTP token syntax.
   */
  private static final CharMatcher COOKIE_NAME = CharMatcher.noneOf(";=");

  /**
   * Characters allowed in a cookie value.  Servers are supposed to restrict
   * values to octets between 0x21 and 0x7E inclusive, except for {@code '"'},
   * {@code '\\'}, {@code ','}, and {@code ';'}.  The value may optionally be
   * surrounded by double quotes.
   */
  private static final CharMatcher COOKIE_VALUE = CharMatcher.noneOf(";");

  /**
   * Cookies are distinguished by their name, their domain, and their path.  If
   * two cookies have the same values for those elements, they are considered to
   * be the same cookie.  This class formalizes that concept by providing an
   * object representing the distinguishing components.
   */
  @Immutable
  @ParametersAreNonnullByDefault
  public static final class Key {
    @Nonnull private final String name;
    @Nonnull private final String domain;
    @Nonnull private final String path;

    private Key(String name, String domain, String path) {
      this.name = name;
      this.domain = domain;
      this.path = path;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) { return true; }
      if (!(object instanceof Key)) { return false; }
      Key other = (Key) object;
      return getName().equalsIgnoreCase(other.getName())
          && getDomain().equalsIgnoreCase(other.getDomain())
          && getPath().equals(other.getPath());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name.toLowerCase(Locale.US), domain.toLowerCase(Locale.US), path);
    }

    // True only of cookies that didn't come from a set-cookie header.
    boolean isPartial() {
      return domain.isEmpty() && path.isEmpty();
    }

    /**
     * Gets the name for this key.
     *
     * @return This key's name.
     */
    @CheckReturnValue
    @Nonnull
    public String getName() {
      return name;
    }

    /**
     * Gets the domain for this key.
     *
     * @return This key's domain.
     */
    @CheckReturnValue
    @Nonnull
    public String getDomain() {
      return domain;
    }

    /**
     * Gets the path for this key.
     *
     * @return This key's path.
     */
    @CheckReturnValue
    @Nonnull
    public String getPath() {
      return path;
    }
  }

  @Nonnull private final Key key;
  @Nonnull private final String value;
  @Nonnegative private final long expires;
  @Nonnegative private final long creationTime;
  @Nonnegative private final long lastAccessTime;
  private final boolean persistent;
  private final boolean hostOnly;
  private final boolean secureOnly;
  private final boolean httpOnly;

  private GCookie(Key key, String value, @Nonnegative long expires, @Nonnegative long creationTime,
      @Nonnegative long lastAccessTime, boolean persistent, boolean hostOnly, boolean secureOnly,
      boolean httpOnly) {
    this.key = key;
    this.value = value;
    this.expires = expires;
    this.creationTime = creationTime;
    this.lastAccessTime = lastAccessTime;
    this.persistent = persistent;
    this.hostOnly = hostOnly;
    this.secureOnly = secureOnly;
    this.httpOnly = httpOnly;
  }

  // **************** Accessors ****************

  /**
   * Gets the cookie's key, an object that embodies what it means for two
   * cookies to have the "same name".
   *
   * @return The key.
   */
  @CheckReturnValue
  @Nonnull
  public Key getKey() {
    return key;
  }

  /**
   * Gets the cookie's name, which is a non-empty case-insensitve string.
   *
   * @return The name.
   */
  @CheckReturnValue
  @Nonnull
  public String getName() {
    return key.getName();
  }

  /**
   * Gets the cookie's value.
   *
   * @return The value.
   */
  @CheckReturnValue
  @Nonnull
  public String getValue() {
    return value;
  }

  /**
   * Gets the cookie's expiration time in milliseconds from the epoch.
   *
   * @return The expiration time.
   */
  @CheckReturnValue
  @Nonnegative
  public long getExpires() {
    return expires;
  }

  /**
   * Gets the cookie's domain.
   *
   * @return The domain.
   */
  @CheckReturnValue
  @Nonnull
  public String getDomain() {
    return key.getDomain();
  }

  /**
   * Gets the cookie's path.
   *
   * @return The path.
   */
  @CheckReturnValue
  @Nonnull
  public String getPath() {
    return key.getPath();
  }

  /**
   * Gets the cookie's creation time in milliseconds from the epoch.
   *
   * @return The creation time.
   */
  @CheckReturnValue
  @Nonnegative
  public long getCreationTime() {
    return creationTime;
  }

  /**
   * Gets the cookie's last-access time in milliseconds from the epoch.
   *
   * @return The last-access time.
   */
  @CheckReturnValue
  @Nonnegative
  public long getLastAccessTime() {
    return lastAccessTime;
  }

  /**
   * Is the cookie persistent?
   *
   * @return True if the cookie is persistent.
   */
  @CheckReturnValue
  public boolean getPersistent() {
    return persistent;
  }

  /**
   * Should this cookie be restricted to the host that exactly matches its domain?
   *
   * @return True if the cookie should be restricted.
   */
  @CheckReturnValue
  public boolean getHostOnly() {
    return hostOnly;
  }

  /**
   * Is the cookie valid only for "secure" connections?
   *
   * @return True if the cookie is usable only for HTTPS and other secure
   *     connections.
   */
  @CheckReturnValue
  public boolean getSecureOnly() {
    return secureOnly;
  }

  /**
   * Should the cookie be restricted to HTTP messages?  If true, Javascript
   * client programs can't access this cookie.
   *
   * @return True if the cookie should be restricted.
   */
  @CheckReturnValue
  public boolean getHttpOnly() {
    return httpOnly;
  }

  /**
   * Is this cookie expired?
   *
   * @param timeStamp The reference time at which to determine the answer.
   * @return True if the cookie is expired.
   */
  @CheckReturnValue
  public boolean isExpired(@Nonnegative long timeStamp) {
    Preconditions.checkArgument(timeStamp >= 0);
    return getExpires() <= timeStamp;
  }

  /**
   * Gets a predicate that computes {@link #isExpired}.
   *
   * @param timeStamp The reference time at which to determine the answer.
   * @return A predicate that's true if a given cookie is expired at the
   *     reference time.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<GCookie> isExpiredPredicate(@Nonnegative final long timeStamp) {
    Preconditions.checkArgument(timeStamp >= 0);
    return new Predicate<GCookie>() {
      @Override
      public boolean apply(GCookie cookie) {
        return cookie.getExpires() <= timeStamp;
      }
    };
  }

  /**
   * Gets the "maximum age" of this cookie.  Normally this isn't used; it's provided
   * for compatibility.  Instead use {@link #getExpires}.
   *
   * @return The "maximum age" of this cookie in seconds, or {@code -1} for a
   *     session cookie.
   */
  @CheckReturnValue
  public int getMaxAge() {
    if (!getPersistent()) {
      return -1;
    }
    if (getExpires() < getCreationTime()) {
      return 0;
    }
    long deltaSeconds = ((getExpires() - getCreationTime()) + 500) / 1000;
    return (deltaSeconds > Integer.MAX_VALUE)
        ? -1
        : (int) deltaSeconds;
  }

  // **************** Equality ****************

  /** Note that equality doesn't consider any of the cookie's times. */
  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof GCookie)) { return false; }
    GCookie other = (GCookie) object;
    return getKey().equals(other.getKey())
        && getValue().equals(other.getValue())
        && getPersistent() == other.getPersistent()
        && getSecureOnly() == other.getSecureOnly()
        && getHttpOnly() == other.getHttpOnly();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getKey(), getValue(), getPersistent(), getSecureOnly(), getHttpOnly());
  }

  /**
   * Checks whether a given cookie has the same name as this cookie.
   *
   * @param cookie The cookie to compare against.
   * @return True only if the names match.
   */
  @CheckReturnValue
  public boolean hasSameName(GCookie cookie) {
    return getName().equalsIgnoreCase(cookie.getName());
  }

  /**
   * Checks whether any of some given cookies have the same name as this cookie.
   *
   * @param cookies The cookies to compare against.
   * @return True only if the name of one of the cookies matches.
   */
  @CheckReturnValue
  public boolean hasSameName(Iterable<GCookie> cookies) {
    for (GCookie cookie : cookies) {
      if (hasSameName(cookie)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether a given cookie has the same key as this cookie.
   *
   * @param cookie The cookie to compare against.
   * @return True only if the keys match.
   */
  @CheckReturnValue
  public boolean hasSameKey(GCookie cookie) {
    return getKey().equals(cookie.getKey());
  }

  /**
   * Checks whether any of some given cookies have the same key as this cookie.
   *
   * @param cookies The cookies to compare against.
   * @return True only if the key of one of the cookies matches.
   */
  @CheckReturnValue
  public boolean hasSameKey(Iterable<GCookie> cookies) {
    for (GCookie cookie : cookies) {
      if (hasSameKey(cookie)) {
        return true;
      }
    }
    return false;
  }

  // **************** Logging support ****************

  @Override
  public String toString() {
    return responseHeaderString(false);
  }

  /**
   * Generates a log message containing a description of some given cookies in
   * request format.
   *
   * @param prefix A prefix for the log message.
   * @param cookies The cookies to be described.
   * @return A suitably formatted log message.
   */
  @CheckReturnValue
  @Nonnull
  public static String requestCookiesMessage(String prefix, Iterable<GCookie> cookies) {
    StringBuilder builder = new StringBuilder();
    builder.append(prefix);
    builder.append(": ");
    if (Iterables.isEmpty(cookies)) {
      builder.append("(none)");
    } else {
      writeRequest(cookies, false, builder);
    }
    return builder.toString();
  }

  /**
   * Generates a log message containing a description of some given cookies in
   * response format.
   *
   * @param prefix A prefix for the log message.
   * @param cookies The cookies to be described.
   * @return A suitably formatted log message.
   */
  @CheckReturnValue
  @Nonnull
  public static String responseCookiesMessage(String prefix, Iterable<GCookie> cookies) {
    StringBuilder builder = new StringBuilder();
    builder.append(prefix);
    builder.append(": ");
    if (Iterables.isEmpty(cookies)) {
      builder.append("(none)");
    } else {
      boolean needSeparator = false;
      for (GCookie cookie : cookies) {
        if (needSeparator) {
          builder.append(", ");
        } else {
          needSeparator = true;
        }
        cookie.writeResponse(false, builder);
      }
    }
    return builder.toString();
  }

  // **************** URI filtering ****************

  /**
   * Checks if this cookie is suitable for a given target URI.
   *
   * @param uri The URI object to test against.
   * @return True if the cookie is suitable to be sent to the URI.
   */
  @CheckReturnValue
  public boolean isGoodFor(URI uri) {
    return domainMatch(getDomain(), getHostOnly(), computeRequestHost(uri))
        && pathMatch(getPath(), uri.getPath())
        && secureOnlyMatch(getSecureOnly(), uri.getScheme());
  }

  private static boolean domainMatch(String cookieDomain, boolean hostOnly, String requestHost) {
    if (cookieDomain.isEmpty()) {
      return true;
    }
    requestHost = requestHost.toLowerCase(Locale.US);
    if (requestHost.equals(cookieDomain)) {
      return true;
    }
    if (hostOnly) {
      return false;
    }
    return requestHost.endsWith(cookieDomain)
        && requestHost.charAt(requestHost.length() - cookieDomain.length() - 1) == '.';
  }

  private static boolean pathMatch(String cookiePath, String requestPath) {
    return cookiePath.isEmpty()
        || requestPath.equals(cookiePath)
        || (requestPath.startsWith(cookiePath)
            && (lastChar(cookiePath) == PATH_SEPARATOR
                || requestPath.charAt(cookiePath.length()) == PATH_SEPARATOR));
  }

  private static char firstChar(String string) {
    return string.charAt(0);
  }

  private static char lastChar(String string) {
    return string.charAt(string.length() - 1);
  }

  private static boolean secureOnlyMatch(boolean secureOnly, String requestProtocol) {
    return !secureOnly || "https".equalsIgnoreCase(requestProtocol);
  }

  /**
   * Determine whether there are any cookies to send.
   *
   * @param uri The URI of the authority.
   * @param userAgentCookies The cookies received from the user agent.
   * @param authorityCookies The cookies previously received from the authority.
   * @return True if there are some cookies to be sent.
   */
  @CheckReturnValue
  public static boolean haveCookiesToSend(URI uri, Iterable<GCookie> userAgentCookies,
      Iterable<GCookie> authorityCookies) {
    return anyCookieGoodFor(userAgentCookies, uri)
        || anyCookieGoodFor(authorityCookies, uri);
  }

  private static boolean anyCookieGoodFor(Iterable<GCookie> cookies, URI uri) {
    for (GCookie cookie : cookies) {
      if (cookie.isGoodFor(uri)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Compute the cookies to send to a given authority.  Authority cookies have
   * priority over user-agent cookies.
   *
   * @param uri The URI of the authority.
   * @param userAgentCookies The cookies received from the user agent.
   * @param authorityCookies The cookies previously received from the authority.
   * @param store The cookie store to add the "to-send" cookies to.
   */
  public static void computeCookiesToSend(URI uri, Iterable<GCookie> userAgentCookies,
      Iterable<GCookie> authorityCookies, CookieStore store) {
    for (GCookie cookie : authorityCookies) {
      if (cookie.isGoodFor(uri)) {
        store.add(cookie);
      }
    }
    for (GCookie cookie : userAgentCookies) {
      if (cookie.isGoodFor(uri) && !store.contains(cookie.getName())) {
        store.add(cookie);
      }
    }
  }

  /**
   * Compute the cookies to send to a given authority.  Authority cookies have
   * priority over user-agent cookies.
   *
   * @param uri The URI of the authority.
   * @param userAgentCookies The cookies received from the user agent.
   * @param authorityCookies The cookies previously received from the authority.
   * @return A cookie store containing the "to-send" cookies.
   */
  @CheckReturnValue
  @Nonnull
  public static CookieStore computeCookiesToSend(URI uri,
      Iterable<GCookie> userAgentCookies, Iterable<GCookie> authorityCookies) {
    CookieStore store = makeStore();
    computeCookiesToSend(uri, userAgentCookies, authorityCookies, store);
    store.expireCookies();
    return store;
  }

  // **************** Cookie store ****************

  /**
   * Makes a new empty store.
   *
   * @return A new empty store.
   */
  @CheckReturnValue
  @Nonnull
  public static CookieStore makeStore() {
    return new CookieStoreImpl();
  }

  /**
   * Makes a new store and adds some cookies to it.
   *
   * @param cookies The cookies to be added.
   * @return A new store containing the given cookies.
   */
  @CheckReturnValue
  @Nonnull
  public static CookieStore makeStore(Iterable<GCookie> cookies) {
    CookieStore store = makeStore();
    Iterables.addAll(store, cookies);
    store.expireCookies();
    return store;
  }

  /**
   * Merge two collections of cookies together.
   *
   * @param cookies1 The first collection.
   * @param cookies2 The second collection, which overrides the first.
   * @return The merged collection.
   */
  @CheckReturnValue
  @Nonnull
  public static Iterable<GCookie> mergeCookies(Iterable<GCookie> cookies1,
      Iterable<GCookie> cookies2) {
    CookieStore store = makeStore();
    Iterables.addAll(store, cookies1);
    Iterables.addAll(store, cookies2);
    store.expireCookies();
    return store;
  }

  // **************** Conversions to/from Java cookies ****************

  /**
   * Converts a cookie to a {@link GCookie}.
   *
   * @param cookie A cookie to be converted.
   * @return The converted cookie.
   */
  @CheckReturnValue
  @Nonnull
  public static GCookie fromCookie(Cookie cookie) {
    return builder(cookie).build();
  }

  /**
   * Converts this {@link GCookie} to a {@link Cookie}.
   *
   * @return A newly created {@link Cookie}.
   * @throws RuntimeException if unable to perform the conversion.
   */
  @CheckReturnValue
  @Nonnull
  public Cookie toCookie() {
    Cookie cookie = new Cookie(getName(), getValue());
    cookie.setValue(getValue());
    if (getDomain() != null) {
      cookie.setDomain(getDomain());
    }
    cookie.setPath(getPath());
    cookie.setSecure(getSecureOnly());
    cookie.setMaxAge(getMaxAge());
    return cookie;
  }

  /**
   * Converts some {@link GCookie}s to {@link Cookie}s.
   *
   * @param cookies The cookies to be converted.
   * @return The converted cookies, except for those that fail to convert.
   */
  @CheckReturnValue
  @Nonnull
  public static ImmutableList<Cookie> toCookie(Iterable<GCookie> cookies) {
    ImmutableList.Builder<Cookie> builder = ImmutableList.builder();
    for (GCookie cookie : cookies) {
      Cookie c;
      try {
        c = cookie.toCookie();
      } catch (RuntimeException e) {
        continue;
      }
      builder.add(c);
    }
    return builder.build();
  }

  // **************** Generating HTTP headers ****************

  /**
   * Gets a string representing the given cookies in request format.
   *
   * @param cookies The cookies to convert.
   * @param showValues If false, the cookie's value will be obfuscated in the
   *     returned string.
   * @return The formatted string.
   */
  @CheckReturnValue
  @Nonnull
  public static String requestHeaderString(Iterable<GCookie> cookies,
      boolean showValues) {
    StringBuilder builder = new StringBuilder();
    writeRequest(cookies, showValues, builder);
    return builder.toString();
  }

  /**
   * Writes a representation of the given cookies in request format.
   *
   * @param cookies The cookies to write.
   * @param showValues If false, the cookie's value will be obfuscated in the
   *     returned string.
   * @param builder A string builder to write the representation to.
   */
  public static void writeRequest(Iterable<GCookie> cookies, boolean showValues,
      StringBuilder builder) {
    boolean needSeparator = false;
    for (GCookie cookie : cookies) {
      if (needSeparator) {
        builder.append(REQUEST_SEPARATOR_STRING);
      } else {
        needSeparator = true;
      }
      cookie.writeRequest(showValues, builder);
    }
  }

  /**
   * Gets a string representing this cookie in request format.
   *
   * @param showValues If false, the cookie's value will be obfuscated in the
   *     returned string.
   * @return The formatted string.
   */
  @CheckReturnValue
  @Nonnull
  public String requestHeaderString(boolean showValues) {
    StringBuilder builder = new StringBuilder();
    writeRequest(showValues, builder);
    return builder.toString();
  }

  /**
   * Writes a representation of this cookie in request format.
   *
   * @param showValues If false, the cookie's value will be obfuscated in the
   *     returned string.
   * @param builder A string builder to write the representation to.
   */
  public void writeRequest(boolean showValues, StringBuilder builder) {
    writeBinding(showValues, builder);
  }

  /**
   * Gets a string representing this cookie in response format.
   *
   * @param showValues If false, the cookie's value will be obfuscated in the
   *     returned string.
   * @return The formatted string.
   */
  @CheckReturnValue
  @Nonnull
  public String responseHeaderString(boolean showValues) {
    StringBuilder builder = new StringBuilder();
    writeResponse(showValues, builder);
    return builder.toString();
  }

  /**
   * Writes a representation of this cookie in response format.
   *
   * @param showValues If false, the cookie's value will be obfuscated in the
   *     returned string.
   * @param builder A string builder to write the representation to.
   */
  public void writeResponse(boolean showValues, StringBuilder builder) {
    writeBinding(showValues, builder);
    if (getExpires() < Long.MAX_VALUE) {
      writeAttr(AttrName.EXPIRES, HttpUtil.generateHttpDate(getExpires()), builder);
    }
    writeAttr(AttrName.MAX_AGE, getMaxAge(), builder);
    if (!(getHostOnly() || getDomain().isEmpty())) {
      String domain = getDomain();
      if (!domain.startsWith(".")) {
        domain = "." + domain;
      }
      writeAttr(AttrName.DOMAIN, domain, builder);
    }
    writeAttr(AttrName.PATH, getPath(), builder);
    writeAttr(AttrName.SECURE, getSecureOnly(), builder);
    writeAttr(AttrName.HTTP_ONLY, getHttpOnly(), builder);
  }

  private void writeBinding(boolean showValues, StringBuilder builder) {
    builder.append(getName());
    builder.append(VALUE_SEPARATOR);
    if (!getValue().isEmpty()) {
      if (showValues || alwaysShowValue(getName())) {
        builder.append(getValue());
      } else {
        builder.append(OBFUSCATED_VALUE_PREFIX);
        builder.append(
            Base64.encodeWebSafe(SecurePasswordHasher.macInput(getName(), getValue()), false));
      }
    }
  }

  private static void writeAttr(AttrName param, String value, StringBuilder builder) {
    if (!value.isEmpty()) {
      builder.append(ATTR_SEPARATOR_STRING);
      builder.append(param.toString());
      builder.append(VALUE_SEPARATOR);
      builder.append(value);
    }
  }

  private static void writeAttr(AttrName param, int value, StringBuilder builder) {
    if (value >= 0) {
      writeAttr(param, Integer.toString(value), builder);
    }
  }

  private static void writeAttr(AttrName param, boolean value, StringBuilder builder) {
    if (value) {
      builder.append(ATTR_SEPARATOR_STRING);
      builder.append(param.toString());
    }
  }

  private static boolean alwaysShowValue(String name) {
    for (String name2 : ALWAYS_SHOW_VALUE) {
      if (name2.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private enum AttrName {
    EXPIRES("Expires"),
    MAX_AGE("Max-Age"),
    DOMAIN("Domain"),
    PATH("Path"),
    SECURE("Secure"),
    HTTP_ONLY("HttpOnly");

    @Nonnull private final String name;

    private AttrName(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  // **************** Parsers ****************

  /**
   * Parses request header values to produce cookies.  Returns Java cookies for
   * use by legacy code.
   *
   * @param headers The header values to parse.
   * @return An immutable list of the parsed cookies.
   */
  @CheckReturnValue
  @Nonnull
  public static ImmutableList<Cookie> legacyParseRequestHeaders(Iterable<String> headers) {
    CookieStore store = makeStore();
    parseRequestHeaders(headers, null, null, store);
    return toCookie(store);
  }

  /**
   * Parses request header values to produce cookies.
   *
   * @param headers The header values to parse.
   * @param requestUri The request URI.
   * @param sessionId A session ID to add to log messages.
   * @param store A cookie store to which the parsed cookies will be added.
   */
  public static void parseRequestHeaders(Iterable<String> headers, URI requestUri, String sessionId,
      CookieStore store) {
    long now = DateTimeUtils.currentTimeMillis();
    for (String header : headers) {
      for (String cookiePair : REQUEST_SPLITTER.split(header)) {
        Builder builder;
        try {
          builder = parseCookiePair(cookiePair, now);
        } catch (ParseException e) {
          LOGGER.info(SecurityManagerUtil.sessionLogMessage(sessionId, e.getMessage()));
          continue;
        }
        store.add(builder.build());
      }
    }
    store.expireCookies(now);
  }

  /**
   * Parses response header values to produce cookies.
   *
   * @param headers The header values to parse.
   * @param requestUri The request URI corresponding to this response.
   * @param sessionId A session ID to add to log messages.
   * @param store A cookie store to which the parsed cookies will be added.
   */
  public static void parseResponseHeaders(Iterable<String> headers, URI requestUri,
      String sessionId, CookieStore store) {
    long now = DateTimeUtils.currentTimeMillis();
    for (String header : headers) {
      GCookie cookie;
      try {
        cookie = parseResponseHeader(header, requestUri, now);
      } catch (ParseException e) {
        LOGGER.info(SecurityManagerUtil.sessionLogMessage(sessionId, e.getMessage()));
        continue;
      }
      store.add(cookie);
    }
    store.expireCookies(now);
  }

  private static GCookie parseResponseHeader(String header, URI requestUri, long now)
      throws ParseException {
    List<String> parts = ImmutableList.copyOf(ATTR_SPLITTER.split(header));
    if (parts.isEmpty()) {
      parseError("No value separator in: %s", Stringify.object(header));
    }
    Builder builder = parseCookiePair(parts.get(0), now)
        .setCreationTime(now)
        .setLastAccessTime(now);
    Map<AttrName, Object> parsedAttrs = parseAttrs(parts.subList(1, parts.size()));

    Long maxAgeRaw = Long.class.cast(parsedAttrs.get(AttrName.MAX_AGE));
    if (maxAgeRaw != null) {
      long maxAge = maxAgeRaw.longValue();
      long expires;
      if (maxAge <= 0) {
        expires = 0;
      } else {
        expires = now + maxAge;
        if (expires < 0) {
          // Means we got an overflow.
          expires = Long.MAX_VALUE;
        }
      }
      builder.setPersistent(true);
      builder.setExpires(expires);
    } else {
      Long expiresRaw = Long.class.cast(parsedAttrs.get(AttrName.EXPIRES));
      if (expiresRaw != null) {
        long expires = expiresRaw.longValue();
        if (expires >= 0) {
          builder.setPersistent(true);
          builder.setExpires(expires);
        } else {
          builder.setPersistent(false);
          builder.setExpires(Long.MAX_VALUE);
        }
      }
    }

    if (requestUri != null) {
      String requestHost = computeRequestHost(requestUri);
      String domain = String.class.cast(parsedAttrs.get(AttrName.DOMAIN));
      if (domain == null) {
        domain = "";
      }
      if (isPublicSuffix(domain)) {
        if (!domain.equals(requestHost)) {
          parseError("Cookie domain %s is a public domain",
              Stringify.object(domain));
        }
        domain = "";
      }
      if (domain.isEmpty()) {
        builder.setHostOnly(true);
        builder.setDomain(requestHost);
      } else {
        if (!domainMatch(domain, false, requestHost)) {
          parseError("Cookie domain %s doesn't match request host %s",
              Stringify.object(domain), Stringify.object(requestHost));
        }
        builder.setHostOnly(false);
        builder.setDomain(domain);
      }

      String path = String.class.cast(parsedAttrs.get(AttrName.PATH));
      if (path == null || path.isEmpty()) {
        builder.setPath(computeDefaultPath(requestUri));
      } else {
        builder.setPath(path);
      }
    }

    builder.setSecureOnly(parsedAttrs.get(AttrName.SECURE) != null);
    builder.setHttpOnly(parsedAttrs.get(AttrName.HTTP_ONLY) != null);

    return builder.build();
  }

  private static Builder parseCookiePair(String cookiePair, long now)
      throws ParseException {
    int vsep = cookiePair.indexOf(VALUE_SEPARATOR);
    if (vsep < 0) {
      parseError("No value separator in: %s",
          Stringify.object(cookiePair));
    }
    String name = WSP.trimFrom(cookiePair.substring(0, vsep));
    if (!isCookieName(name)) {
      parseError("Invalid cookie name %s in: %s",
          Stringify.object(name), Stringify.object(cookiePair));
    }
    String value = WSP.trimFrom(cookiePair.substring(vsep + 1));
    if (!isCookieValue(value)) {
      parseError("Invalid cookie value %s in: %s",
          Stringify.object(value), Stringify.object(cookiePair));
    }
    return builder(name, now).setValue(value);
  }

  private static final Splitter REQUEST_SPLITTER =
      Splitter.on(REQUEST_SEPARATOR).trimResults().omitEmptyStrings();

  private static final Splitter ATTR_SPLITTER =
      Splitter.on(ATTR_SEPARATOR).trimResults().omitEmptyStrings();

  private static Map<AttrName, Object> parseAttrs(List<String> unparsedAttrs) {
    Map<AttrName, Object> parsedAttrs = Maps.newHashMap();
    for (String unparsed : unparsedAttrs) {
      String aname;
      String avalue;
      int vsep = unparsed.indexOf(VALUE_SEPARATOR);
      if (vsep < 0) {
        aname = WSP.trimFrom(unparsed);
        avalue = "";
      } else {
        aname = WSP.trimFrom(unparsed.substring(0, vsep));
        avalue = WSP.trimFrom(unparsed.substring(vsep + 1));
      }
      AttrName attrName = findAttrName(aname);
      if (attrName != null) {
        Object value = dispatchAttr(attrName, avalue);
        if (value != null) {
          parsedAttrs.put(attrName, value);
        }
      }
    }
    return parsedAttrs;
  }

  private static AttrName findAttrName(String aname) {
    for (AttrName attrName : EnumSet.allOf(AttrName.class)) {
      if (attrName.toString().equalsIgnoreCase(aname)) {
        return attrName;
      }
    }
    return null;
  }

  private static Object dispatchAttr(AttrName attrName, String avalue) {
    switch (attrName) {
      case EXPIRES: return parseExpires(avalue);
      case MAX_AGE: return parseMaxAge(avalue);
      case DOMAIN: return parseDomain(avalue);
      case PATH: return parsePath(avalue);
      case SECURE: return parseSecure(avalue);
      case HTTP_ONLY: return parseHttpOnly(avalue);
      default: throw new IllegalStateException("Unknown AttrName: " + attrName);
    }
  }

  private static Long parseExpires(String expires) {
    try {
      return parseDate(expires);
    } catch (IllegalArgumentException e) {
      LOGGER.info("Error parsing Expires attribute: " + e.getMessage());
      return null;
    }
  }

  private static Long parseMaxAge(String maxAge) {
    try {
      return Long.parseLong(maxAge);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String parseDomain(String domain) {
    return domain.isEmpty() ? null : canonicalizeDomain(domain);
  }

  private static String canonicalizeDomain(String domain) {
    if (domain.startsWith(".")) {
      domain = domain.substring(1);
    }
    try {
      // Do full canonicalization if possible.
      return HttpUtil.canonicalizeDomainName(domain);
    } catch (IllegalArgumentException e) {
      // Otherwise fall back to simple case folding.
      return domain.toLowerCase(Locale.US);
    }
  }

  private static boolean isPublicSuffix(String domain) {
    // TODO(cph): should check for "public suffix" here.  See
    // <http://publicsuffix.org/>.
    return false;
  }

  private static String parsePath(String path) {
    return (path.isEmpty() || firstChar(path) != PATH_SEPARATOR)
        ? ""
        : path;
  }

  private static boolean parseSecure(String avalue) {
    return true;
  }

  private static boolean parseHttpOnly(String avalue) {
    return true;
  }

  private static String computeRequestHost(URI requestUri) {
    String host = requestUri.getHost();
    try {
      return HttpUtil.canonicalizeDomainName(host);
    } catch (IllegalArgumentException e) {
      return host.toLowerCase(Locale.US);
    }
  }

  private static String computeDefaultPath(URI requestUri) {
    String path = requestUri.getPath();
    if (path.isEmpty() || firstChar(path) != PATH_SEPARATOR) {
      return UNIVERSAL_PATH;
    }
    int lastSeparator = path.lastIndexOf(PATH_SEPARATOR);
    return (lastSeparator > 0)
        ? path.substring(0, lastSeparator)
        : UNIVERSAL_PATH;
  }

  private static String parseError(String format, Object... args)
      throws ParseException {
    throw new ParseException(String.format(format, args));
  }

  /**
   * An exception that's thrown by a cookie parser when the input can't be
   * parsed.
   */
  public static final class ParseException extends Exception {
    ParseException(String message) { super(message); }
  }

  // **************** Element predicates and canonicalizers ****************

  /**
   * Is the given string a valid cookie name?
   *
   * @param name The string to test.
   * @return True only if the given string can be used as a cookie's name.
   */
  @CheckReturnValue
  public static boolean isCookieName(String name) {
    return !name.isEmpty()
        && COOKIE_NAME.matchesAllOf(name)
        && noLeadingOrTrailingWhitespace(name);
  }

  /**
   * Is the given string a valid cookie value?
   *
   * @param value The string to test.
   * @return True only if the given string can be used as a cookie's value.
   */
  @CheckReturnValue
  public static boolean isCookieValue(String value) {
    return value.isEmpty()
        || (COOKIE_VALUE.matchesAllOf(value)
            && noLeadingOrTrailingWhitespace(value));
  }

  private static boolean noLeadingOrTrailingWhitespace(String string) {
    return !WSP.matches(string.charAt(0))
        && !WSP.matches(string.charAt(string.length() - 1));
  }

  // **************** Date parser ****************

  @VisibleForTesting
  static long parseDate(String string) {
    int[] hms = null;
    int dayOfMonth = -1;
    int month = -1;
    int year = -1;
    for (String token : tokenizeDate(string)) {
      if (hms == null) {
        hms = parseTimeToken(token);
        if (hms != null) {
          continue;
        }
      }
      if (dayOfMonth < 0) {
        dayOfMonth = parseDayOfMonthToken(token);
        if (dayOfMonth >= 0) {
          continue;
        }
      }
      if (month < 0) {
        month = parseMonthToken(token);
        if (month >= 0) {
          continue;
        }
      }
      if (year < 0) {
        year = parseYearToken(token);
      }
    }
    if (year >= 0 && year < 70) {
      year += 2000;
    } else if (year >= 70 && year < 100) {
      year += 1900;
    }
    String message = null;
    if (hms == null) {
      message = "no time seen";
    } else if (hms[0] > 23) {
      message = String.format("hour too large: %d", hms[0]);
    } else if (hms[1] > 59) {
      message = String.format("minute too large: %d", hms[1]);
    } else if (hms[2] > 59) {
      message = String.format("second too large: %d", hms[2]);
    } else if (dayOfMonth < 0) {
      message = "no day of month seen";
    } else if (dayOfMonth == 0 || dayOfMonth > 31) {
      message = String.format("illegal day of month: %d", dayOfMonth);
    } else if (month < 0) {
      message = "no month seen";
    } else if (year < 0) {
      message = "no year seen";
    } else if (year < 1601) {
      message = String.format("year too small: %d", year);
    }
    if (message != null) {
      throw new IllegalArgumentException(parseDateMessage(message, string));
    }
    Calendar calendar = Calendar.getInstance(GMT, Locale.US);
    calendar.setLenient(false);
    calendar.set(year, month, dayOfMonth, hms[0], hms[1], hms[2]);
    try {
      return calendar.getTimeInMillis();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(parseDateMessage(e.getMessage(), string));
    }
  }

  private static List<String> tokenizeDate(String string) {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    StringBuilder tokenBuilder = null;
    for (int i = 0; i < string.length(); i += 1) {
      char c = string.charAt(i);
      if (DATE_DELIMITER.matches(c)) {
        if (tokenBuilder != null) {
          listBuilder.add(tokenBuilder.toString());
          tokenBuilder = null;
        }
      } else {
        if (tokenBuilder == null) {
          tokenBuilder = new StringBuilder();
        }
        tokenBuilder.append(c);
      }
    }
    if (tokenBuilder != null) {
      listBuilder.add(tokenBuilder.toString());
    }
    return listBuilder.build();
  }

  private static final CharMatcher DATE_DELIMITER =
      CharMatcher.is('\t')
      .or(CharMatcher.inRange('\u0020', '\u002F'))
      .or(CharMatcher.inRange('\u003B', '\u0040'))
      .or(CharMatcher.inRange('\u005B', '\u0060'))
      .or(CharMatcher.inRange('\u007B', '\u007E'));

  private static int[] parseTimeToken(String token) {
    Matcher m = applyPattern(TIME_PATTERN, token);
    if (m == null) {
      return null;
    }
    int[] hms = new int[3];
    hms[0] = Integer.valueOf(m.group(1));
    hms[1] = Integer.valueOf(m.group(2));
    hms[2] = Integer.valueOf(m.group(3));
    return hms;
  }

  private static final Pattern TIME_PATTERN =
      Pattern.compile("([0-9]{1,2}):([0-9]{1,2}):([0-9]{1,2})");

  private static int parseDayOfMonthToken(String token) {
    Matcher m = applyPattern(DAY_OF_MONTH_PATTERN, token);
    return (m != null) ? Integer.valueOf(m.group()) : -1;
  }

  private static final Pattern DAY_OF_MONTH_PATTERN = Pattern.compile("[0-9]{1,2}");

  private static int parseMonthToken(String token) {
    if (token.length() < 3) {
      return -1;
    }
    String p = token.substring(0, 3);
    for (Map.Entry<String, Integer> entry : MONTHS.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(p)) {
        return entry.getValue();
      }
    }
    return -1;
  }

  private static final Map<String, Integer> MONTHS;
  static {
    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
    builder.put("jan", Calendar.JANUARY);
    builder.put("feb", Calendar.FEBRUARY);
    builder.put("mar", Calendar.MARCH);
    builder.put("apr", Calendar.APRIL);
    builder.put("may", Calendar.MAY);
    builder.put("jun", Calendar.JUNE);
    builder.put("jul", Calendar.JULY);
    builder.put("aug", Calendar.AUGUST);
    builder.put("sep", Calendar.SEPTEMBER);
    builder.put("oct", Calendar.OCTOBER);
    builder.put("nov", Calendar.NOVEMBER);
    builder.put("dec", Calendar.DECEMBER);
    MONTHS = builder.build();
  }

  private static int parseYearToken(String token) {
    Matcher m = applyPattern(YEAR_PATTERN, token);
    return (m != null) ? Integer.valueOf(m.group()) : -1;
  }

  private static final Pattern YEAR_PATTERN = Pattern.compile("[0-9]{2,4}");

  private static Matcher applyPattern(Pattern pattern, String token) {
    Matcher m = pattern.matcher(token);
    if (m.lookingAt()) {
      int end = m.end();
      if (end == token.length() || !HttpUtil.DIGIT.matches(token.charAt(end))) {
        return m;
      }
    }
    return null;
  }

  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  private static String parseDateMessage(String message, String string) {
    return String.format("Can't parse date string because %s: %s",
        message, Stringify.object(string));
  }

  // **************** Constructor ****************

  /**
   * Gets a {@link GCookie} with just a name and a value.
   *
   * @param name The name of the cookie to build.
   * @param value The value of the cookie to build.
   * @return A {@link GCookie}.
   * @throws IllegalArgumentException if {@code name} doesn't satisfy
   *     {@link #isCookieName} or if {@code value} doesn't satisfy
   *     {@link #isCookieValue}.
   */
  @CheckReturnValue
  @Nonnull
  public static GCookie make(String name, String value) {
    return builder(name).setValue(value).build();
  }

  /**
   * Gets a builder for constructing a {@link GCookie}.
   *
   * @param name The name of the cookie to build.
   * @return A {@link GCookie} builder.
   * @throws IllegalArgumentException if {@code name} doesn't satisfy
   *     {@link #isCookieName}.
   */
  @CheckReturnValue
  @Nonnull
  public static Builder builder(String name) {
    return new Builder(name, DateTimeUtils.currentTimeMillis());
  }

  /**
   * Gets a builder for constructing a {@link GCookie}.
   *
   * @param name The name of the cookie to build.
   * @param now The current time in milliseconds since the epoch.
   * @return A {@link GCookie} builder.
   * @throws IllegalArgumentException if {@code name} doesn't satisfy
   *     {@link #isCookieName} or if {@code now} is negative.
   */
  @CheckReturnValue
  @Nonnull
  public static Builder builder(String name, long now) {
    return new Builder(name, now);
  }

  /**
   * Gets a builder for constructing a {@link GCookie}.
   *
   * @param key The key of the cookie to build.
   * @return A {@link GCookie} builder.
   */
  @CheckReturnValue
  @Nonnull
  public static Builder builder(Key key) {
    return builder(key.getName())
        .setDomain(key.getDomain())
        .setPath(key.getPath());
  }

  /**
   * Gets a builder for constructing a {@link GCookie}.
   *
   * @param cookie A cookie to use to pre-populate the builder.
   * @return A {@link GCookie} builder.
   */
  @CheckReturnValue
  @Nonnull
  public static Builder builder(Cookie cookie) {
    return builder(cookie.getName()).setFromCookie(cookie);
  }

  /**
   * Gets a builder for constructing a {@link GCookie}.
   *
   * @param cookie A cookie to use to pre-populate the builder.
   * @return A {@link GCookie} builder.
   */
  @CheckReturnValue
  @Nonnull
  public static Builder builder(GCookie cookie) {
    return builder(cookie.getName()).setFromCookie(cookie);
  }

  /**
   * A builder class for {@link GCookie} instances.
   */
  @NotThreadSafe
  @ParametersAreNonnullByDefault
  public static final class Builder {
    @Nonnull private final String name;
    @Nonnegative private final long now;
    @Nonnull private String value = "";
    private long expires = -1;
    @Nonnull private String domain = "";
    @Nonnull private String path = "";
    private long creationTime = -1;
    private long lastAccessTime = -1;
    private boolean persistent = false;
    private boolean hostOnly = false;
    private boolean secureOnly = false;
    private boolean httpOnly = false;
    private boolean maxAgeSet = false;
    private long maxAge;

    private Builder(String name, @Nonnegative long now) {
      Preconditions.checkArgument(isCookieName(name),
          "Illegal name: %s", name);
      Preconditions.checkArgument(now >= 0,
          "Illegal time: %s", now);
      this.name = name;
      this.now = now;
    }

    /**
     * Builds the cookie using the parameters accumulated by the builder.
     *
     * @return A newly created {@link GCookie}.
     */
    @CheckReturnValue
    @Nonnull
    public GCookie build() {
      long at = computeLastAccessTime();
      long ct = computeCreationTime(at);
      return new GCookie(new Key(name, domain, path), value, computeExpires(ct), ct, at,
          computePersistent(), hostOnly, secureOnly, httpOnly);
    }

    // The following computations assume that:
    // 1. If maxAgeSet is true, maxAge is preferred to expires and persistent.
    // 2. creationTime is either negative or <= now.
    // 3. lastAccessTime is either negative or <= now.
    // 4. When finished, creationTime <= lastAccessTime.

    private long computeLastAccessTime() {
      return (lastAccessTime < 0) ? now : lastAccessTime;
    }

    private long computeCreationTime(long lastAccessTime) {
      return (creationTime < 0 || lastAccessTime < creationTime)
          ? lastAccessTime
          : creationTime;
    }

    private long computeExpires(long creationTime) {
      return maxAgeSet
          ? ((maxAge < 0) ? Long.MAX_VALUE : creationTime + (maxAge * 1000))
          : ((expires < 0) ? Long.MAX_VALUE : expires);
    }

    private boolean computePersistent() {
      return maxAgeSet ? (maxAge >= 0) : persistent;
    }

    /**
     * Sets the value of the {@link GCookie} being built.
     *
     * @param value The value.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException if {@code value} doesn't satisfy
     *     {@link #isCookieValue}.
     */
    @Nonnull
    public Builder setValue(String value) {
      Preconditions.checkArgument(isCookieValue(value),
          "Illegal value: %s", value);
      this.value = value;
      return this;
    }

    /**
     * Sets the expiration time of the {@link GCookie} being built.
     *
     * @param expires The expiration time.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException if the argument is invalid.
     */
    @Nonnull
    public Builder setExpires(@Nonnegative long expires) {
      Preconditions.checkArgument(expires >= 0,
          "Illegal expires: %s", expires);
      this.expires = expires;
      return this;
    }

    /**
     * Sets the maximum age of the {@link GCookie} being built.  Translates this
     * age into the "expires" and "persistent" attributes of the resulting
     * cookie.
     *
     * @param maxAge The maximum age in seconds.
     * @return This builder, for convenience.
     */
    @Nonnull
    public Builder setMaxAge(long maxAge) {
      this.maxAge = maxAge;
      maxAgeSet = true;
      return this;
    }

    /**
     * Sets the domain of the {@link GCookie} being built.
     *
     * @param domain The domain.
     * @return This builder, for convenience.
     */
    @Nonnull
    public Builder setDomain(String domain) {
      this.domain = canonicalizeDomain(domain);
      return this;
    }

    /**
     * Sets the path of the {@link GCookie} being built.
     *
     * @param path The path.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException if the argument is invalid.
     */
    @Nonnull
    public Builder setPath(String path) {
      Preconditions.checkArgument(path.isEmpty() || firstChar(path) == PATH_SEPARATOR,
          "Illegal path: %s", path);
      this.path = path;
      return this;
    }

    /**
     * Sets the creation time of the {@link GCookie} being built.
     *
     * @param creationTime The creation time.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException if the argument is invalid.
     */
    @Nonnull
    public Builder setCreationTime(@Nonnegative long creationTime) {
      Preconditions.checkArgument(creationTime >= 0 && creationTime <= now,
          "Illegal creation time: %s", creationTime);
      this.creationTime = creationTime;
      return this;
    }

    /**
     * Sets the last-access time of the {@link GCookie} being built.
     *
     * @param lastAccessTime The last-access time.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException if the argument is invalid.
     */
    @Nonnull
    public Builder setLastAccessTime(@Nonnegative long lastAccessTime) {
      Preconditions.checkArgument(lastAccessTime >= 0 && lastAccessTime <= now,
          "Illegal last-access time: %s", lastAccessTime);
      this.lastAccessTime = lastAccessTime;
      return this;
    }

    /**
     * Sets whether the {@link GCookie} being built is persistent.
     *
     * @param persistent True if the cookie is persistent.
     * @return This builder, for convenience.
     */
    @Nonnull
    public Builder setPersistent(boolean persistent) {
      this.persistent = persistent;
      return this;
    }

    /**
     * Sets whether the {@link GCookie} being built is restricted to "secure"
     * connections.
     *
     * @param secureOnly True if the cookie is restricted.
     * @return This builder, for convenience.
     */
    @Nonnull
    public Builder setSecureOnly(boolean secureOnly) {
      this.secureOnly = secureOnly;
      return this;
    }

    /**
     * Sets whether the {@link GCookie} being built is restricted to the host
     * that exactly matches its domain.
     *
     * @param hostOnly True if the cookie is restricted.
     * @return This builder, for convenience.
     */
    @Nonnull
    public Builder setHostOnly(boolean hostOnly) {
      this.hostOnly = hostOnly;
      return this;
    }

    /**
     * Sets whether the {@link GCookie} being built should be restricted to the
     * HTTP messages.  In other words, if true, Javascript client programs can't
     * access this cookie.
     *
     * @param httpOnly If true, the cookie should be restricted.
     * @return This builder, for convenience.
     */
    @Nonnull
    public Builder setHttpOnly(boolean httpOnly) {
      this.httpOnly = httpOnly;
      return this;
    }

    /**
     * Sets the fields of the {@link GCookie} being built by copying them from a
     * given cookie.  All fields in the given cookie, other than the name and
     * value, are copied to the new {@link GCookie}.
     *
     * @param cookie The cookie to copy them from.
     * @return This builder, for convenience.
     */
    @Nonnull
    public Builder setFromCookie(Cookie cookie) {
      setValue(cookie.getValue());
      setMaxAge(cookie.getMaxAge());
      setDomain(cookie.getDomain());
      setPath(cookie.getPath());
      setSecureOnly(cookie.getSecure());
      return this;
    }

    /**
     * Sets the fields of the {@link GCookie} being built by copying them from a
     * given cookie.  All fields in the given cookie, other than the name and
     * value, are copied to the new {@link GCookie}.
     *
     * @param cookie The cookie to copy them from.
     * @return This builder, for convenience.
     */
    @Nonnull
    public Builder setFromCookie(GCookie cookie) {
      setValue(cookie.getValue());
      setExpires(cookie.getExpires());
      setDomain(cookie.getDomain());
      setPath(cookie.getPath());
      setCreationTime(cookie.getCreationTime());
      setLastAccessTime(cookie.getLastAccessTime());
      setPersistent(cookie.getPersistent());
      setHostOnly(cookie.getHostOnly());
      setSecureOnly(cookie.getSecureOnly());
      setHttpOnly(cookie.getHttpOnly());
      return this;
    }
  }

  // **************** Interface with HTTP request/response ****************

  /**
   * Parses cookies from the headers of an HTTP request.
   *
   * @param request The request to get the headers from.
   * @param sessionId A session ID to add to log messages.
   * @param store A cookie store to which the parsed cookies will be added.
   */
  public static void parseHttpRequestCookies(HttpServletRequest request, String sessionId,
      CookieStore store) {
    parseRequestHeaders(
        HttpUtil.getRequestHeaderValues(HttpUtil.HTTP_HEADER_COOKIE, request),
        HttpUtil.getRequestUri(request, false),
        sessionId,
        store);
  }

  /**
   * Parses cookies from the headers of an HTTP request.
   *
   * @param request The request to get the headers from.
   * @param sessionId A session ID to add to log messages.
   * @return A cookie store containing the parsed cookies.
   */
  public static CookieStore parseHttpRequestCookies(HttpServletRequest request, String sessionId) {
    CookieStore store = makeStore();
    parseHttpRequestCookies(request, sessionId, store);
    return store;
  }

  /**
   * Adds a cookie to an HTTP response.
   *
   * @param cookie The cookie to add.
   * @param response The response to save the cookie header in.
   */
  public static void addHttpResponseCookie(GCookie cookie, HttpServletResponse response) {
    response.addCookie(cookie.toCookie());
    response.addHeader(HttpUtil.HTTP_HEADER_SET_COOKIE, cookie.responseHeaderString(true));
  }

  /**
   * Adds cookies to an HTTP response.
   *
   * @param cookies The cookies to add.
   * @param response The response to save the cookie headers in.
   */
  public static void addHttpResponseCookies(Iterable<GCookie> cookies,
      HttpServletResponse response) {
    for (GCookie cookie : cookies) {
      addHttpResponseCookie(cookie, response);
    }
  }

  // **************** JSON ****************

  public static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(GCookie.class,
        ProxyTypeAdapter.make(GCookie.class, GCookie.LocalProxy.class));
  }

  private static final class LocalProxy implements TypeProxy<GCookie> {
    String name;
    String value;
    long expires;
    String domain;
    String path;
    long creationTime;
    long lastAccessTime;
    boolean persistent;
    boolean hostOnly;
    boolean secureOnly;
    boolean httpOnly;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(GCookie cookie) {
      name = cookie.getName();
      value = cookie.getValue();
      expires = cookie.getExpires();
      domain = cookie.getDomain();
      path = cookie.getPath();
      creationTime = cookie.getCreationTime();
      lastAccessTime = cookie.getLastAccessTime();
      persistent = cookie.getPersistent();
      hostOnly = cookie.getHostOnly();
      secureOnly = cookie.getSecureOnly();
      httpOnly = cookie.getHttpOnly();
    }

    @Override
    public GCookie build() {
      return builder(name)
          .setValue(value)
          .setExpires(expires)
          .setDomain(domain)
          .setPath(path)
          .setCreationTime(creationTime)
          .setLastAccessTime(lastAccessTime)
          .setPersistent(persistent)
          .setHostOnly(hostOnly)
          .setSecureOnly(secureOnly)
          .setHttpOnly(httpOnly)
          .build();
    }
  }
}
