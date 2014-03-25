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
import java.util.List;

/**
 * Transforms Principals in ACLs based on provided rules.
 */
final class AclTransform {
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
      if (rule.match.matches(parsed)) {
        parsed = rule.replace.replace(parsed);
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

  public static final class MatchData {
    // Visible for GsaCommunicationHandler
    final Boolean isGroup;
    private final String name;
    private final String domain;
    private final String namespace;

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
    }

    private boolean matches(ParsedPrincipal principal) {
      boolean matches = true;
      if (isGroup != null) {
        matches = matches && isGroup.equals(principal.isGroup);
      }
      if (name != null) {
        matches = matches && name.equals(principal.plainName);
      }
      if (domain != null) {
        matches = matches && domain.equals(principal.domain);
      }
      if (namespace != null) {
        matches = matches && namespace.equals(principal.namespace);
      }
      return matches;
    }

    private ParsedPrincipal replace(ParsedPrincipal principal) {
      if (isGroup != null) {
        throw new AssertionError();
      }
      if (name != null) {
        principal = principal.plainName(name);
      }
      if (domain != null) {
        principal = principal.domain(domain);
      }
      if (namespace != null) {
        principal = principal.namespace(namespace);
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
