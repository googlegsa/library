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

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents group.
 */
public final class GroupPrincipal extends Principal {
  public GroupPrincipal(String name, String namespace) {
    super(name, namespace);
  }

  public GroupPrincipal(String name) {
    super(name);
  }

  /** Always returns {@code false}. */
  @Override
  public boolean isUser() {
    return false;
  }

  /** Always returns {@code true}. */
  @Override
  public boolean isGroup() {
    return true;
  }

  static Set<GroupPrincipal> makeSet(Collection<String> names) {
    if (null == names) {
      return null;
    }
    Set<GroupPrincipal> groups = new TreeSet<GroupPrincipal>();
    for (String n : names) {
      groups.add(new GroupPrincipal(n));
    }
    return groups;
  }

  static Set<GroupPrincipal> makeSet(Collection<String> names,
      String namespace) {
    if (null == names) {
      return null;
    }
    Set<GroupPrincipal> groups = new TreeSet<GroupPrincipal>();
    for (String n : names) {
      groups.add(new GroupPrincipal(n, namespace));
    }
    return groups;
  }
}
