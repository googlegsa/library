// Copyright 2014 Google Inc.
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

package com.google.enterprise.adaptor.secmgr.identity;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Class that contains the info related to a group.
 */
public class Group {
  @Nonnull private final String name;
  @Nonnull private final String namespace;
  @Nullable private final String domain;

  private Group(String name, String namespace, @Nullable String domain) {
    Preconditions.checkNotNull(name);
    this.name = name;
    this.namespace = namespace;
    this.domain = domain;
  }

  @Nonnull
  public static Group make(String name, String namespace,
      @Nullable String domain) {
    return new Group(name, namespace, domain);
  }

  @Nonnull
  public static Group make(String name, String namespace) {
    return new Group(name, namespace, null);
  }

  @Nonnull
  public String getName() {
    return name;
  }

  @Nonnull
  public String getNamespace() {
    return namespace;
  }

  public String getDomain() {
    return domain;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[] { namespace, name, domain });
  }

  private static boolean oequals(Object a, Object b) {
    return (a == b) || (a != null && a.equals(b));
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof Group)) { return false; }
    Group group = (Group) object;
    return oequals(name, group.getName())
        && oequals(namespace, group.getNamespace())
        && oequals(domain, group.getDomain());
  }

  @Override
  public String toString() {
    return (Strings.isNullOrEmpty(domain)) ? namespace + ":" + name
        : namespace + ":" + name + "@" + domain;
  }
}
