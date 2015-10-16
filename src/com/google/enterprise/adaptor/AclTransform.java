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

import com.google.common.base.Objects;
import com.google.enterprise.adaptor.Principal.ParsedPrincipal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms Principals in ACLs based on provided rules.
 */
final class AclTransform {
  private static final Logger log
      = Logger.getLogger(AclTransform.class.getName());
  private final List<Rule> rules;

  public AclTransform(List<Rule> rules) {
    this.rules = Collections.unmodifiableList(new ArrayList<Rule>(rules));
  }

  public Acl transform(Acl acl) {
    if (acl == null) {
      return null;
    }
    if (rules.isEmpty()) {
      return acl;
    }
    return new Acl.Builder(acl)
        .setPermits(transformInternal(acl.getPermits()))
        .setDenies(transformInternal(acl.getDenies()))
        .build();
  }

  public <T extends Principal> Collection<T> transform(
      Collection<T> principals) {
    if (rules.isEmpty()) {
      return principals;
    }
    return transformInternal(principals);
  }

  public <T extends Principal> T transform(T principal) {
    if (rules.isEmpty()) {
      return principal;
    }
    return transformInternal(principal);
  }

  private <T extends Principal> Collection<T> transformInternal(
      Collection<T> principals) {
    Collection<T> newPrincipals = new ArrayList<T>(principals.size());
    for (T principal : principals) {
      newPrincipals.add(transformInternal(principal));
    }
    return Collections.unmodifiableCollection(newPrincipals);
  }

  private <T extends Principal> T transformInternal(T principal) {
    ParsedPrincipal parsed = principal.parse();
    for (Rule rule : rules) {
      MatchResult m = rule.match.matches(parsed);
      if (m.matches()) {
        parsed = rule.replace.replace(m.getCapturedGroups(), parsed);
      }
    }
    @SuppressWarnings("unchecked")
    T principalNew = (T) parsed.toPrincipal();
    return principalNew;
  }

  @Override
  public String toString() {
    return "AclTransform(rules=" + rules + ")";
  }

  @Override
  public int hashCode() {
    return rules.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AclTransform)) {
      return false;
    }
    AclTransform a = (AclTransform) o;
    return rules.equals(a.rules);
  }

  public static final class Rule {
    private final MatchData match;
    private final MatchData replace;

    public Rule(MatchData match, MatchData replace) {
      if (match == null || replace == null) {
        throw new NullPointerException();
      }
      if (replace.isGroup != null) {
        throw new IllegalArgumentException(
            "isGroup must be null for replacements");
      }
      this.match = match;
      this.replace = replace;
    }

    @Override
    public String toString() {
      return "Rule(match=" + match + ",replace=" + replace + ")";
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new Object[] {match, replace});
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Rule)) {
        return false;
      }
      Rule r = (Rule) o;
      return match.equals(r.match) && replace.equals(r.replace);
    }
  }

  public static final class MatchResult {
    private boolean matches;
    /*
     * AclTransforms that contain matching regular expressions
     * result in capturedGroups being populated.
     *
     * The keys of captured groups are one of "name", "domain", "namespace"
     * followed by a number.
     *
     * For example (not escaped to illustrate the string representation)
     *   domain=(.)(.*), name=A(.*); name=\name1@\domain2, domain=\domain1
     *
     * will result in keys of "name1", "domain1" and "domain2". The values
     * will later be used as substitutes for "\name1" "\domain1" and
     * "\domain2".
     */
    private Map<String, String> capturedGroups;

    public MatchResult(boolean matches, Map<String, String> capturedGroups) {
      this.matches = matches;
      this.capturedGroups = capturedGroups;
    }

    public boolean matches() {
      return matches;
    }

    public Map<String, String> getCapturedGroups() {
      return capturedGroups;
    }

    @Override
    public String toString() {
      return "MatchResult(matches=" + matches
          + ",captured=" + capturedGroups + ")";
    }
  }

  public static final class MatchData {
    // Visible for GsaCommunicationHandler
    final Boolean isGroup;
    private final String name;
    private final String domain;
    private final String namespace;

    // patterns are used only when matching ACL transformation rules
    // it will be null when using MatchData for replacements
    private final Pattern namePattern;
    private final Pattern domainPattern;
    private final Pattern namespacePattern;

    /**
     * For matching, non-{@code null} fields must be equal on Principal. For
     * replacing, non-{@code null} fields will get set on Principal.
     * {@code isGroup} must be {@code null} for replacements.
     */
    public MatchData(Boolean isGroup, String name, String domain,
        String namespace) {
      this.isGroup = isGroup;
      this.name = name;
      this.domain = domain;
      this.namespace = namespace;

      namePattern = name != null ? Pattern.compile(name) : null;
      domainPattern = domain != null ? Pattern.compile(domain) : null;
      namespacePattern = namespace != null ? Pattern.compile(namespace) : null;
    }

    /**
     * Performs regular expression match and captures any defined groups
     * in the groups parameter
     *
     * @param pattern to match against
     * @param string which is being matched
     * @param prefix name of how we are going to capture the group
     * @param groups map of captured values
     * @return if match has been found
     */
    private boolean match(Pattern pattern, String string,
        String prefix, Map<String, String> groups) {
      Matcher m = pattern.matcher(string);
      if (m.matches()) {
        for (int i = 1; i <= m.groupCount(); ++i) {
          groups.put(prefix + i, m.group(i));
        }
      }
      return m.matches();
    }

    private MatchResult matches(ParsedPrincipal principal) {
      boolean matches = true;
      if (isGroup != null) {
        matches = matches && isGroup.equals(principal.isGroup);
      }
      Map<String, String> groups = new HashMap<String, String>();
      if (name != null) {
        matches = matches && match(
            namePattern, principal.plainName, "name", groups);
      }
      if (domain != null) {
        matches = matches && match(
            domainPattern, principal.domain, "domain", groups);
      }
      if (namespace != null) {
        matches = matches && match(
            namespacePattern, principal.namespace, "namespace", groups);
      }
      MatchResult result = new MatchResult(matches, groups);
      log.finest("Matching " + principal + " against "
          + this.toString() + "; result: " + result.toString());
      return result;
    }

    private String replace(Map<String, String> groups, String template) {
      StringBuilder sb = new StringBuilder(template.length());
      try {
        int i = 0;
        while (i < template.length()) {
          // found either slash or substitution pattern
          boolean found = false;

          if (template.charAt(i) == '\\') {
            // if the backslash is escaped, skip it
            if (i + 1 < template.length() && template.charAt(i+1) == '\\') {
              sb.append('\\');
              i++; // the second slash will be skipped at the end of the loop
              found = true;
            } else {
              // if the template starts with escaped key, replace it
              for (String key : groups.keySet()) {
                if (template.regionMatches(i+1, key, 0, key.length())) {
                  found = true;
                  // slash is consumed at the end of the loop
                  i = i + key.length();
                  sb.append(groups.get(key));
                  break;
                }
              }
            }
          }

          if (!found) {
            sb.append(template.charAt(i));
          }
          i++;
        }
      } catch (IndexOutOfBoundsException e) {
        log.warning("Failed processing ACL transform template " + template);
        return template;
      }

      return sb.toString();
    }

    private ParsedPrincipal replace(
        Map<String, String> capturedGroups, ParsedPrincipal principal) {
      if (isGroup != null) {
        throw new AssertionError();
      }
      if (name != null) {
        principal = principal.plainName(replace(capturedGroups, name));
      }
      if (domain != null) {
        principal = principal.domain(replace(capturedGroups, domain));
      }
      if (namespace != null) {
        principal = principal.namespace(replace(capturedGroups, namespace));
      }
      return principal;
    }

    @Override
    public String toString() {
      return "MatchData(isGroup=" + isGroup + ",name=" + name
          + ",domain=" + domain + ",namespace=" + namespace + ")";
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new Object[] {isGroup, name, domain, namespace});
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof MatchData)) {
        return false;
      }
      MatchData m = (MatchData) o;
      return Objects.equal(isGroup, m.isGroup)
          && Objects.equal(name, m.name)
          && Objects.equal(domain, m.domain)
          && Objects.equal(namespace, m.namespace);
    }
  }
}
