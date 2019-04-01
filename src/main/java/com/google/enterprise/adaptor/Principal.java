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

import java.util.Arrays;

/**
 * Represents either a user or a group.
 */
public abstract class Principal implements Comparable<Principal> {
  public static final String DEFAULT_NAMESPACE = "Default";

  private final String name;
  private final String namespace;

  /** The name is trimmed because GSA trims principal names.
   *  An empty name results in IllegalArgumentException.  */
  Principal(String n, String ns) {
    if (null == n || null == ns) {
      throw new NullPointerException();
    }
    n = n.trim();
    if (n.isEmpty()) {
      throw new IllegalArgumentException("name cannot be empty");
    }
    name = n;
    namespace = ns;
  }

  Principal(String n) {
    this(n, DEFAULT_NAMESPACE);
  }

  public String getName() {
    return name;
  }

  public String getNamespace() {
    return namespace;
  }

  public abstract boolean isUser();

  public abstract boolean isGroup();

  @Override
  public boolean equals(Object other) {
    boolean same = other instanceof Principal;
    if (same) {
      Principal p = (Principal) other;
      same = p.isUser() == isUser()
          && p.parse().domain.equals(parse().domain)
          && p.parse().plainName.equals(parse().plainName)
          && p.namespace.equals(namespace);
    }
    return same;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[]{ isUser(), parse().domain,
        parse().plainName, namespace });
  }

  @Override
  public String toString() {
    String type = isUser() ? "User" : "Group";
    return "Principal(" + type + "," + name + "," + namespace + ")";
  }

  /**
   * Sorts by 1) namespace, 2) user or group, 3) name.
   */
  @Override
  public int compareTo(Principal other) {
    int spacecmp = namespace.compareTo(other.namespace);
    if (0 != spacecmp) {
      return spacecmp;
    }
    // OK, same namespace

    if (isUser() != other.isUser()) {
      return isUser() ? -1 : 1;
    }
    // OK, same namespace and same type

    int domainCmp = parse().domain.compareTo(other.parse().domain);
    if (0 != domainCmp) {
      return domainCmp;
    }
    // OK, same domain

    return parse().plainName.compareTo(other.parse().plainName);
  }

  ParsedPrincipal parse() {
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      switch (c) {
        case '\\':
          return new ParsedPrincipal(isGroup(), name.substring(i + 1),
              name.substring(0, i), DomainFormat.NETBIOS, namespace);
        case '/':
          return new ParsedPrincipal(isGroup(), name.substring(i + 1),
              name.substring(0, i), DomainFormat.NETBIOS_FORWARDSLASH,
              namespace);
        case '@':
          return new ParsedPrincipal(isGroup(), name.substring(0, i),
              name.substring(i + 1), DomainFormat.DNS, namespace);
        default:
      }
    }
    return new ParsedPrincipal(isGroup(), name, "", DomainFormat.NONE,
        namespace);
  }

  static enum DomainFormat {
    NONE,
    DNS,
    NETBIOS,
    /**
     * Same as NETBIOS, but a forward slash is used instead of a backslash. This
     * is to support round-tripping all Principals; if you don't modify the
     * ParsedPrincipal, you shouldn't see any modifications.
     */
    NETBIOS_FORWARDSLASH,
    ;

    String format(String plainName, String domain) {
      String name;
      switch (this) {
        case NONE:
          name = plainName;
          break;
        case DNS:
          name = plainName + "@" + domain;
          break;
        case NETBIOS:
          name = domain + "\\" + plainName;
          break;
        case NETBIOS_FORWARDSLASH:
          name = domain + "/" + plainName;
          break;
        default:
          throw new AssertionError();
      }
      return name;
    }
  }

  /**
   * Immutable form of Principal where user's name has been split into name and
   * domain components. This class guarantees full fidelity of Principal
   * objects: {@code principal.equals(principal.parse().toPrincipal())} is
   * always {@code true}.
   */
  static class ParsedPrincipal {
    public final boolean isGroup;
    public final String plainName;
    public final String domain;
    public final DomainFormat domainFormat;
    public final String namespace;

    public ParsedPrincipal(boolean isGroup, String plainName, String domain,
        DomainFormat domainFormat, String namespace) {
      if (plainName == null || domain == null || domainFormat == null
          || namespace == null) {
        throw new NullPointerException();
      }
      this.isGroup = isGroup;
      this.plainName = plainName;
      this.domain = domain;
      this.domainFormat = domainFormat;
      this.namespace = namespace;
    }

    public ParsedPrincipal plainName(String plainName) {
      return new ParsedPrincipal(isGroup, plainName, domain, domainFormat,
          namespace);
    }

    public ParsedPrincipal domain(String domain) {
      return new ParsedPrincipal(isGroup, plainName, domain, domainFormat,
          namespace);
    }

    public ParsedPrincipal domainFormat(DomainFormat domainFormat) {
      return new ParsedPrincipal(isGroup, plainName, domain, domainFormat,
          namespace);
    }

    public ParsedPrincipal namespace(String namespace) {
      return new ParsedPrincipal(isGroup, plainName, domain, domainFormat,
          namespace);
    }

    /**
     * Determine the format that should be used for combining the name and
     * domain together. This does not simply return domainFormat, because it
     * needs to make sure that using such a format will not cause ambiguities.
     */
    private DomainFormat determineEffectiveFormat() {
      DomainFormat format = domainFormat;
      if (domain.equals("")) {
        return format;
      }

      // Domain handling
      if (format == DomainFormat.NONE) {
        format = DomainFormat.NETBIOS;
      }
      if ((format == DomainFormat.NETBIOS
            || format == DomainFormat.NETBIOS_FORWARDSLASH)
          && containsSpecial(domain)) {
        format = DomainFormat.DNS;
      }
      if (format == DomainFormat.DNS && containsSpecial(plainName)) {
        if (containsSpecial(domain)) {
          throw new IllegalStateException("Neither NETBIOS nor DNS formats can "
              + "be used: plainName=" + plainName + " domain=" + domain);
        }
        format = DomainFormat.NETBIOS;
      }
      return format;
    }

    private boolean containsSpecial(String s) {
      return s.contains("\\") || s.contains("/") || s.contains("@");
    }

    public Principal toPrincipal() {
      String name = determineEffectiveFormat().format(plainName, domain);
      if (isGroup) {
        return new GroupPrincipal(name, namespace);
      } else {
        return new UserPrincipal(name, namespace);
      }
    }

    @Override
    public String toString() {
      return "ParsedPrincipal(isGroup=" + isGroup + ",plainName=" + plainName
          + ",domain=" + domain + ",domainFormat=" + domainFormat
          + ",namespace=" + namespace + ")";
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(
          new Object[] {isGroup, plainName, domain, domainFormat, namespace});
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ParsedPrincipal)) {
        return false;
      }
      ParsedPrincipal p = (ParsedPrincipal) o;
      return isGroup == p.isGroup && plainName.equals(p.plainName)
          && domainFormat == p.domainFormat && namespace.equals(p.namespace)
          && domain.equals(p.domain);
    }
  }
}
