// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.common.labs.matcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;

/**
 * This class parses a Google URL pattern into an immutable representation that
 * provides equivalent Java regexes,
 * exact-match patterns and prefix patterns, as appropriate. For a description
 * of Google URL patterns, see the
 * documentation in <a
 * href="http://code.google.com/apis/searchappliance/documentation/50/admin/URL_patterns.html">
 * this document</a>.
 * <p>
 * All Google URL patterns can be translated into an equivalent Java regex (with
 * some exceptions and caveats, see below). This class provides access to an
 * equivalent Java regex through {@link #getUrlRegex()}.
 * <p>
 * In addition, the class provides further analysis and special kinds of
 * patterns, depending on these top-level predicates:
 * <ul>
 * <li> {@link #isHostPathType()}: Returns {@code true} if the parsed pattern
 * is a "host-path" pattern. A "host-path" pattern is a pattern that can be
 * parsed into two regexes, a host regex and a path regex, such that a subject
 * URL matches the original URL pattern iff the host portion matches the host
 * regex and the path portion matches the path regex. If
 * {@code isHostPathType()} is true, then {@link #getHostRegex()} and
 * {@link #getPathRegex()} return the corresponding regexes. </li>
 * <li> {@link #isPathPrefixMatch()}: Returns {@code true} if the parsed
 * pattern is a "host-path" pattern and the path portion of the pattern is
 * simply a fixed string that must appear at the beginning of the path. In this
 * case, {@link #getPathPrefixString()} returns a simple string (not a regex)
 * that can be matched against the start of the subject URL's path. </li>
 * <li> {@link #isPathExactMatch()}: Returns {@code true} if if the parsed
 * pattern is a "host-path" pattern and the path portion of the pattern is an
 * exact-match string. In this case, {@link #getPathExactString()} returns a
 * simple string (not a regex) that can be matched exactly against the subject
 * URL's path. </li>
 * </ul>
 * In summary:
 * <ul>
 * <li> {@code getUrlRegex()} provides an equivalent Java regex for the entire
 * pattern. </li>
 * <li> If {@code isHostPathType()} is true, then, {@code getHostRegex()} and
 * {@code getPathRegex()} return regexes for the two portions.</li>
 * <li> If {@code isPrefixPathMatch()} is true, then,
 * {@code getPrefixPathMatchPattern()} returns a simple string pattern for
 * prefix match.</li>
 * <li> If {@code isPathExactMatch()} is true, then, in addition,
 * {@code getPathExactMatchPattern()} returns a simple string pattern for exact
 * match.
 * </ul>
 * <p>
 * Note: the "path" portion is the hierarchical part, that is, everything
 * following the first slash (not the {@code ://}). The "host" portion is
 * everything before that. For example: for the URL
 * {@code http://www.example.com/foo/bar}, the protocol-authority portion is
 * {@code http://www.example.com/} and the file portion is {@code /foo/bar}.
 * Note the the middle slash appears in both portions.
 * <p>
 * A parser is provided to separate a URL string into host and path portions:
 * {@link AnalyzedUrl}. You can access the host and path portions through
 * {@link AnalyzedUrl#getHostPart()} and {@link AnalyzedUrl#getPathPart()}. It
 * is recommended that this parser be used rather than the standard
 * {@code getHost()} and {@code getPath()} functions of {@link java.net.URL},
 * because this class and {@code AnalyzedUrl} share parsing infrastructure and
 * at present, there is at least one significant difference:
 * {@code AnalyzedUrl.getPathPart()} includes the leading slash but
 * {@code java.net.URL.getPath()} does not. TODO: fix this.
 * <p>
 * Exceptions and caveats: not all forms of Google URL patterns are currently
 * supported. At present, these exceptions and special cases apply:
 * <ul>
 * <li> {@code www?:} patterns are not supported </li>
 * <li> {@code regexp:} and {@code regexpCase:} patterns are translated simply
 * by removing those two prefixes. Thus, the remaining pattern is assumed to be
 * a Java regex, not a GNU regex (as documented on the <a
 * href="http://code.google.com/apis/searchappliance/documentation/50/admin/URL_patterns.html">
 * reference site</a>). </li>
 * <li> {@code regexpIgnoreCase:} patterns are handled similarly. In this case,
 * the prefix is removed and the pattern is enclosed in {@code (?i:}...{@code )}</li>
 * <li> Exception patterns (patterns with leading {@code -} or {@code +-}) are
 * not supported.</li>
 * </ul>
 */
public class ParsedUrlPattern {

  private final String urlPattern;
  private final String urlRegex;

  private final boolean hostPathType;
  private final String hostRegex;

  private final String pathRegex;
  private final boolean pathExactMatch;
  private final String pathExactMatchPattern;

  private final boolean prefixPathMatch;
  private final String prefixPathMatchPattern;

  /**
   * Parses a Google URL pattern to Java regexes. Google URL patterns are
   * publicly documented <a
   * href="http://code.google.com/apis/searchappliance/documentation/50/admin/URL_patterns.html">
   * here </a>.
   * 
   * @param urlPattern A Google URL pattern
   * @throws IllegalArgumentException if the URL pattern is unsupported or can
   *         not be parsed
   */
  public ParsedUrlPattern(String urlPattern) {
    ParsedUrlPatternBuilder t = new ParsedUrlPatternBuilder(urlPattern);
    this.urlPattern = t.urlPattern;
    this.urlRegex = t.urlRegex;
    this.hostPathType = t.hostPathType;
    this.hostRegex = t.hostRegex;
    this.pathRegex = t.pathRegex;
    this.pathExactMatch = t.pathExactMatch;
    this.pathExactMatchPattern = t.pathExactMatchPattern;
    this.prefixPathMatch = t.prefixPathMatch;
    this.prefixPathMatchPattern = t.prefixPathMatchPattern;

  }

  /**
   * Returns a regex that matches the entire URL. A subject string matches the
   * URL pattern iff it matches this regex.
   * 
   * @return a regex that matches the entire URL
   */
  public String getUrlRegex() {
    return urlRegex;
  }

  /**
   * Returns {@code true} if the parsed pattern is a "host-path" pattern. A
   * "host-path" pattern is a pattern that can be parsed into two regexes, a
   * host regex and a path regex, such that a subject url matches the pattern
   * iff the host portion matches the host regex and the path portion matches
   * the path regex.
   * <p>
   * For example, the pattern {@code example.com/foo} might be parsed into two
   * regexes, host regex: {@code example.com/$} and path regex: {@code ^/foo}.
   */
  public boolean isHostPathType() {
    return hostPathType;
  }

  /**
   * Returns a regex that matches the host (protocol and authority) portion of
   * the URL. If this is a host-path regex then a subject string matches the url
   * pattern iff the host portion matches this regex and the the path portion
   * matches the corresponding path regex (obtained by {@link #getPathRegex()}).
   * <p>
   * This should be used against URLs that have been parsed using the
   * {@link AnalyzedUrl} class.
   * <p>
   * Note: this should only be used if {@code isHostPathType()} is true; if not,
   * then this method throws an {@code IllegalStateException}.
   * 
   * @return a regex that matches the host (protocol and authority) portion of
   *         the URL
   * @throws IllegalStateException if {@code isHostPathType()} is false
   */
  public String getHostRegex() {
    Preconditions.checkState(isHostPathType());
    return hostRegex;
  }

  /**
   * Returns a regex that matches the path (hierarchical) portion of the URL.
   * <p>
   * This should be used against URLs that have been parsed using the
   * {@link AnalyzedUrl} class.
   * <p>
   * Note: this should only be used if {@link #isHostPathType()} is true; if
   * not, then this method throws an {@code IllegalStateException}.
   * 
   * @return a regex that matches the path (hierarchical) portion of the URL
   * @throws IllegalStateException if {@code isHostPathType()} is false
   */
  public String getPathRegex() {
    Preconditions.checkState(isHostPathType());
    return pathRegex;
  }

  /**
   * Indicates whether the parsed pattern gives a prefix match pattern. If this
   * is true, then this pattern can be obtained using
   * {@link #getPathPrefixString()}.
   * 
   * @return {@code true} if the parsed pattern gives an prefix match pattern.
   */
  public boolean isPathPrefixMatch() {
    return prefixPathMatch;
  }

  /**
   * If {@link #isPathPrefixMatch()} is true, then this returns a simple string
   * that can be matched against the path portion of a subject string using
   * {@link String#startsWith(String)}.
   * <p>
   * Note: this should only be used if {@code isPrefixPathMatch()} is true; if
   * not, then this method throws an {@code IllegalStateException}.
   * 
   * @return a string that matches a prefix of the path portion of the URL
   * @throws IllegalStateException if {@code isPathPrefixMatch()} is false
   */
  public String getPathPrefixString() {
    Preconditions.checkState(isPathPrefixMatch());
    return prefixPathMatchPattern;
  }

  /**
   * Returns whether the parsed pattern gives an exact match pattern. If this is
   * true, then this pattern can be obtained using {@link #getPathExactString()}.
   * 
   * @return {@code true} if the parsed pattern gives an exact match pattern.
   */
  public boolean isPathExactMatch() {
    return pathExactMatch;
  }

  /**
   * If {@link #isPathExactMatch()} is true, then this returns a simple string
   * that can be matched against the path portion of a subject string using
   * {@link String#equals(Object)}. Note: this should only be used if
   * {@code isPathExactMatch()} is true; if not, then this method throws an
   * {@code IllegalStateException}.
   * 
   * @return a string that matches the entire path
   * @throws IllegalStateException if {@code isPathExactMatch()} is false
   */
  public String getPathExactString() {
    Preconditions.checkState(isPathExactMatch());
    return pathExactMatchPattern;
  }

  /**
   * Returns the original URL pattern.
   * 
   * @return the original URL pattern.
   */
  public String getUrlPattern() {
    return urlPattern;
  }

  // This is the master meta-regex. This is used both for parsing URL patterns
  // and for parsing URLs
  private static final String URL_METAPATTERN_STRING =
      "\\A(\\^)?((?:([^/:$<]*)((?:(?::|(?::/))?\\Z)|(?:://)))?" +
    // ___1_____2a__3_________4b__c____d____________e
      "(?:([^/:@]*)@)?([^/:<]*)?(?::([^/<]*))?)(/|(?:</>))?(?:(.*?)(\\Z|\\$)?)?\\Z"
    // f__5___________6_________g___7__________8__h________i__9____0
  ;

  // Groups: (capturing groups are numbered, non-capturing are lettered)
  // 1 anchor (^)
  // 2 protocol + authority (not including /)
  // a protocol + ((nothing or : or :/ followed by end of pattern) or ::/)
  // 3 protocol
  // 4 protocol separator ((nothing or : or :/ followed by end of pattern) or
  // ::/)
  // b nothing or : or :/ followed by end of pattern
  // c : or :/
  // d :/
  // e ::/
  // f userinfo + @
  // 5 userinfo
  // 6 host
  // g : + port
  // 7 port
  // 8 slash (after authority) (could be a slash or "</>")
  // h </>
  // i file + anchor
  // 9 file
  // 10 anchor ($)
  
  // This Pattern is package visible so it can be used by AnalyzedUrl
  static final Pattern URL_METAPATTERN = Pattern.compile(URL_METAPATTERN_STRING);

  // As above, the enum is package visible so it can be used by AnalyzedUrl
  // Note: if you change the master regex, you should change this enum to match
  static enum MetaRegexGroup {
    LEFT_ANCHOR(1), PROTOCOL_AUTHORITY(2), PROTOCOL(3), PROTOCOL_SEPARATOR(4), USERINFO(5),
    HOST(6), PORT(7), SLASH_AFTER_AUTHORITY(8), FILE(9), RIGHT_ANCHOR(10);
    private int n;

    MetaRegexGroup(int n) {
      this.n = n;
    }

    int intValue() {
      return n;
    }
  }

  // This static helper is also shared with the AnalyzedUrl
  static String getGroup(Matcher m, MetaRegexGroup g) {
    String s = m.group(g.intValue());
    return (s == null) ? "" : s;
  }

  private static class ParsedUrlPatternBuilder {

    public String urlPattern;
    public String urlRegex;

    public boolean hostPathType;
    public String hostRegex;

    public String pathRegex;
    public boolean pathExactMatch;
    public String pathExactMatchPattern;

    public boolean prefixPathMatch;
    public String prefixPathMatchPattern;

    ParsedUrlPatternBuilder(String urlPattern) {
      checkPatternValidity(urlPattern);
      this.urlPattern = urlPattern;
      analyze();
    }

    private void analyze() {
      if (urlPattern.startsWith(CONTAINS_PATTERNS_METAPATTERN_PREFIX)) {
        urlRegex =
            Pattern.quote(urlPattern.substring(CONTAINS_PATTERNS_METAPATTERN_PREFIX.length()));
        initNonHostPathPattern();
        return;
      }

      if (urlPattern.startsWith(REGEXP_PATTERNS_METAPATTERN_PREFIX)) {
        urlRegex = urlPattern.substring(REGEXP_PATTERNS_METAPATTERN_PREFIX.length());
        initNonHostPathPattern();
        return;
      }

      if (urlPattern.startsWith(REGEXPCASE_PATTERNS_METAPATTERN_PREFIX)) {
        urlRegex = urlPattern.substring(REGEXPCASE_PATTERNS_METAPATTERN_PREFIX.length());
        initNonHostPathPattern();
        return;
      }

      if (urlPattern.startsWith(REGEXPIGNORECASE_PATTERNS_METAPATTERN_PREFIX)) {
        urlRegex =
            "(?i:" + urlPattern.substring(REGEXPIGNORECASE_PATTERNS_METAPATTERN_PREFIX.length())
                + ")";
        initNonHostPathPattern();
        return;
      }

      initHostPathPattern();

      if (isNullOrEmpty(urlPattern)) {
        prefixPathMatch = true;
        return;
      }
      if (testForAndHandleNoSlashSuffixPattern()) {
        return;
      }
      Matcher m = URL_METAPATTERN.matcher(urlPattern);
      Preconditions.checkArgument(m.find(), "problem parsing urlpattern: " + urlPattern);
      urlRegex = buildUrlRegex(m);
      pathRegex = buildPathRegex(m);
      hostRegex = buildHostRegex(m);
    }

    private void initNonHostPathPattern() {
      hostPathType = false;
      pathRegex = null;
      hostRegex = null;
      pathExactMatch = false;
      pathExactMatchPattern = null;
      prefixPathMatch = false;
      prefixPathMatchPattern = null;
    }

    private void initHostPathPattern() {
      hostPathType = true;
      urlRegex = "";
      pathRegex = "";
      hostRegex = "";
      pathExactMatch = false;
      pathExactMatchPattern = null;
      prefixPathMatch = false;
      prefixPathMatchPattern = "/";
    }

    // A suffix pattern (ends in $) that has no slash just doesn't parse well
    // with
    // the metapattern. So we use a special pattern for this case.
    private boolean testForAndHandleNoSlashSuffixPattern() {
      Matcher m = NO_SLASH_SUFFIX_PATTERN.matcher(urlPattern);
      if (!m.find()) {
        return false;
      }
      urlRegex = Pattern.quote(m.group(1)) + OUTPUT_RIGHT_ANCHOR_PATTERN_STRING;
      pathRegex = urlRegex;
      hostRegex = "";
      pathExactMatch = false;
      pathExactMatchPattern = null;
      prefixPathMatch = false;
      prefixPathMatchPattern = null;
      return true;
    }

    // suffix patterns that contain no slash jam up my master meta-regex: the
    // string before the $ gets put in the wrong capturing group. I fought with
    // it
    // a while but then bailed and just made a special meta-regex for them
    private static final String NO_SLASH_SUFFIX_PATTERN_STRING = "\\A([^/]*)\\$\\Z";
    private static final Pattern NO_SLASH_SUFFIX_PATTERN =
        Pattern.compile(NO_SLASH_SUFFIX_PATTERN_STRING);

    private static final String CONTAINS_PATTERNS_METAPATTERN_PREFIX = "contains:";

    private static final String REGEXP_PATTERNS_METAPATTERN_PREFIX = "regexp:";

    private static final String REGEXPCASE_PATTERNS_METAPATTERN_PREFIX = "regexpCase:";

    private static final String REGEXPIGNORECASE_PATTERNS_METAPATTERN_PREFIX = "regexpIgnoreCase:";

    private static final String UNSUPPORTED_PATTERNS_METAPATTERN_STRING = "\\A(?:(www\\?:)|(-))";
    private static final Pattern UNSUPPORTED_PATTERNS_METAPATTERN =
        Pattern.compile(UNSUPPORTED_PATTERNS_METAPATTERN_STRING);

    private static final String OUTPUT_RIGHT_ANCHOR_PATTERN_STRING = "\\Z";
    private static final String OUTPUT_LEFT_ANCHOR_PATTERN_STRING = "\\A";

    private static final String OUTPUT_SLASH = "/";
    private static final String OUTPUT_ANY_OR_NO_PORT_PATTERN = "(\\:[^/]*)?";
    private static final String OUTPUT_ANY_PORT_PATTERN = "\\:[^/]*";

    private static boolean isNullOrEmpty(String s) {
      return (s == null || s.length() < 1);
    }

    // These helper functions whose names match buildSOMETHINGPattern build a
    // regex to match the SOMETHING in their names. They should be usable,
    // appropriately quoted regexes
    private static String buildProtocolUserinfoHostPattern(Matcher m) {
      StringBuilder sb = new StringBuilder();
      sb.append(getGroup(m, MetaRegexGroup.PROTOCOL));
      sb.append(getGroup(m, MetaRegexGroup.PROTOCOL_SEPARATOR));
      String userInfo = getGroup(m, MetaRegexGroup.USERINFO);
      if (!isNullOrEmpty(userInfo)) {
        sb.append(userInfo);
        sb.append("@");
      }
      sb.append(getGroup(m, MetaRegexGroup.HOST));
      String unquotedPattern = sb.toString();
      return isNullOrEmpty(unquotedPattern) ? "" : Pattern.quote(unquotedPattern);
    }

    // port is tricky because the absence of a port in a pattern should match
    // any
    // specific port in a target
    private static String buildPortPattern(Matcher m) {
      StringBuilder sb = new StringBuilder();
      String port = getGroup(m, MetaRegexGroup.PORT);
      if (isNullOrEmpty(port)) {
        // port was empty - match any port - default or explicit
        sb.append(OUTPUT_ANY_OR_NO_PORT_PATTERN);
      } else {
        if (port.equals("*")) {
          // port was explicitly "*" - match any explicitly specified port
          sb.append(OUTPUT_ANY_PORT_PATTERN);
        } else {
          // port was explicit and not "*" - match only that port
          sb.append("\\:");
          sb.append(Pattern.quote(port));
        }
      }
      return sb.toString();
    }

    private static String buildUnquotedFilePattern(Matcher m) {
      return getGroup(m, MetaRegexGroup.FILE);
    }

    private static String buildQuotedFilePattern(Matcher m) {
      String unquotedPattern = buildUnquotedFilePattern(m);
      return isNullOrEmpty(unquotedPattern) ? "" : Pattern.quote(unquotedPattern);
    }

    // the helper functions whose names match buildSOMETHINGRegex each build one
    // of the three public regexes: the urlRegex, the protocolAuthorityRegex and
    // the fileRegex.

    // The main reason that the urlRegex is not simply the concatenation of the
    // protocolAuthorityRegex and the fileRegex is the anchors. Both for
    // correctness and efficiency, we want to use anchors only where
    // appropriate:
    // using ^A.*foo is considerably slower than just using foo.
    private String buildUrlRegex(Matcher m) {
      StringBuilder sb = new StringBuilder();
      String leftAnchor = getGroup(m, MetaRegexGroup.LEFT_ANCHOR);
      String protocolUserinfoHostPattern = buildProtocolUserinfoHostPattern(m);
      String portPattern = buildPortPattern(m);
      String slashAfterAuthority = getGroup(m, MetaRegexGroup.SLASH_AFTER_AUTHORITY);
      String filePattern = buildQuotedFilePattern(m);
      String rightAnchor = getGroup(m, MetaRegexGroup.RIGHT_ANCHOR);
      // prefix patterns need to be handled specially
      if (!isNullOrEmpty(leftAnchor)) {
        sb.append(OUTPUT_LEFT_ANCHOR_PATTERN_STRING);
      }
      if (!isNullOrEmpty(protocolUserinfoHostPattern)) {
        sb.append(protocolUserinfoHostPattern);
      }
      if (!isNullOrEmpty(portPattern)) {
        if (sb.length() > 0) {
          sb.append(portPattern);
        }
      }
      if (!isNullOrEmpty(slashAfterAuthority)) {
        if ("</>".equals(slashAfterAuthority)) {
          if (sb.length() < 1) {
            sb.append(OUTPUT_LEFT_ANCHOR_PATTERN_STRING);
            sb.append("[^/]*//[^/]*");
          }
        }
        sb.append(OUTPUT_SLASH);
      }
      if (!isNullOrEmpty(filePattern)) {
        sb.append(filePattern);
      }
      if (!isNullOrEmpty(rightAnchor)) {
        sb.append(rightAnchor);
      }
      return sb.toString();
    }

    private String buildHostRegex(Matcher m) {
      StringBuilder sb = new StringBuilder();
      String leftAnchor = getGroup(m, MetaRegexGroup.LEFT_ANCHOR);
      String protocolUserinfoHostPattern = buildProtocolUserinfoHostPattern(m);
      String portPattern = buildPortPattern(m);
      String slashAfterAuthority = getGroup(m, MetaRegexGroup.SLASH_AFTER_AUTHORITY);
      // prefix patterns need to be handled specially
      if (!isNullOrEmpty(leftAnchor)) {
        sb.append(OUTPUT_LEFT_ANCHOR_PATTERN_STRING);
      }
      if (!isNullOrEmpty(protocolUserinfoHostPattern)) {
        sb.append(protocolUserinfoHostPattern);
      }
      if (!isNullOrEmpty(portPattern)) {
        sb.append(portPattern);
      }
      if (!isNullOrEmpty(slashAfterAuthority)) {
        sb.append(OUTPUT_SLASH);
      }
      return sb.toString();
    }

    // We expect that, in practice, the fileRegex will be used much more often
    // than the protocolAuthority regex (there will probably be a hashtable for
    // the protocol-authority portion), so we really want to makes sure that the
    // fileRegexes are simple prefix matches, as often as possible.
    private String buildPathRegex(Matcher m) {
      boolean hasLeftAnchor = false;
      boolean hasRightAnchor = false;
      StringBuilder sb = new StringBuilder();
      String protocolAuthority = getGroup(m, MetaRegexGroup.PROTOCOL_AUTHORITY);
      String slashAfterAuthority = getGroup(m, MetaRegexGroup.SLASH_AFTER_AUTHORITY);
      String unquotedFilePattern = buildUnquotedFilePattern(m);
      String rightAnchor = getGroup(m, MetaRegexGroup.RIGHT_ANCHOR);
      // two conditions for this being an prefix pattern:
      // either there was a protocolAuthority OR there was a </>
      // slashAfterAuthority
      if (!isNullOrEmpty(protocolAuthority) || "</>".equals(slashAfterAuthority)) {
        hasLeftAnchor = true;
        sb.append(OUTPUT_LEFT_ANCHOR_PATTERN_STRING);
      }
      if (!isNullOrEmpty(slashAfterAuthority)) {
        sb.append(OUTPUT_SLASH);
      }
      sb.append(Pattern.quote(unquotedFilePattern));
      if (!isNullOrEmpty(rightAnchor)) {
        hasRightAnchor = true;
        sb.append(OUTPUT_RIGHT_ANCHOR_PATTERN_STRING);
      }
      if (hasLeftAnchor) {
        if (hasRightAnchor) {
          this.pathExactMatch = true;
          this.pathExactMatchPattern = "/" + unquotedFilePattern;
          this.prefixPathMatch = false;
          this.prefixPathMatchPattern = null;
        } else {
          this.pathExactMatch = false;
          this.pathExactMatchPattern = null;
          this.prefixPathMatch = true;
          this.prefixPathMatchPattern = "/" + unquotedFilePattern;
        }
      }
      return sb.toString();
    }

    private static void checkPatternValidity(String s) {
      Preconditions.checkNotNull(s);
      Matcher m = UNSUPPORTED_PATTERNS_METAPATTERN.matcher(s);
      Preconditions.checkArgument(!m.find(), "unsupported urlpattern: " + s);
    }
  }
}
