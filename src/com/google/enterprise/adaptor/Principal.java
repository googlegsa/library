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
public class Principal implements Comparable<Principal> {
  private final String name;
  private final String namespace;

  Principal(String n, String ns) {
    if (null == n || null == ns) {
      throw new NullPointerException();
    }
    name = n;
    namespace = ns;
  }

  Principal(String n) {
    this(n, "Default");
  }

  public String getName() {
    return name;
  }

  public String getNamespace() {
    return namespace;
  }

  public boolean isUser() {
    return this instanceof UserPrincipal;
  }

  public boolean isGroup() {
    return this instanceof GroupPrincipal;
  }

  public boolean equals(Object other) {
    boolean same = other instanceof Principal;
    if (same) {
      Principal p = (Principal) other;
      same = p.isUser() == isUser() && p.name.equals(name)
          && p.namespace.equals(namespace);
    }
    return same;
  }

  public int hashCode() {
    return Arrays.hashCode(new Object[]{ isUser(), name, namespace });
  }

  public String toString() {
    String type = isUser() ? "User" : "Group";
    return "Principal(" + type + "," + name + "," + namespace + ")";
  }

  /**
   * Sorts by 1) namespace, 2) user or group, 3) name.
   */
  @Override
  public int compareTo(Principal other){
    int spacecmp = namespace.compareTo(other.namespace);
    if (0 != spacecmp) {
      return spacecmp;
    }
    // OK, same namespace

    if (isUser() != other.isUser()) {
      return isUser() ? -1 : 1;
    }
    // OK, same namespace and same type

    return name.compareTo(other.name);
  }
}
