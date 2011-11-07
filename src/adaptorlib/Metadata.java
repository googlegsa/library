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

package adaptorlib;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Represents a fixed set of validated {@link MetaItem}s. */
public final class Metadata implements Iterable<MetaItem> {
  /** Object instance to denote documents that have been deleted. */
  public static final Metadata DELETED
      = new Metadata(Collections.<MetaItem>emptySet());
  /** Empty convenience instance. */
  public static final Metadata EMPTY
      = new Metadata(Collections.<MetaItem>emptySet());

  private final Set<MetaItem> items;
 
  /**
   * Validates that each meta name is unique, there is either
   * public-indicator or ACLs and that ACLs values are acceptable.
   */ 
  public Metadata(Set<MetaItem> allMeta) {
    items = Collections.unmodifiableSet(new TreeSet<MetaItem>(allMeta));
    checkConsistency(items, toMap());
  }

  @Override
  public boolean equals(Object o) {
    if (this == DELETED || o == DELETED) {
      return this == o;
    }
    boolean same = false;
    if (null != o && this.getClass().equals(o.getClass())) {
      Metadata other = (Metadata) o;
      same = items.equals(other.items);
    } 
    return same;
  }

  @Override
  public int hashCode() {
    return items.hashCode();
  }

  @Override
  public Iterator<MetaItem> iterator() {
    return items.iterator();
  }

  @Override
  public String toString() {
    return items.toString();
  }

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<String, String>(items.size() * 2);
    for (MetaItem item : this) {
      map.put(item.getName(), item.getValue());
    }
    return map;
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public int size() {
    return items.size();
  }

  private static void checkConsistency(Set<MetaItem> items,
                                       Map<String, String> allMeta) {
    checkEachNameIsUnique(items); 
    checkNandPublicAndAcls(allMeta);
    checkBothOrNoneAcls(allMeta); 
    checkPublicIsBoolean(allMeta); 
  }

  /** Each MetaItem name needs be unique. */
  private static void checkEachNameIsUnique(Set<MetaItem> m) {
    HashSet<String> unique = new HashSet<String>();
    HashSet<String> dup = new HashSet<String>();
    for (MetaItem item : m) {
      String name = item.getName();
      if (unique.contains(name)) {
        dup.add(name);
      } else {
        unique.add(name);
      }
    }
    if (0 < dup.size()) {
      throw new IllegalArgumentException("duplicate names: " + dup);
    }
  }

  /** Either have public indicator or ACLs, but not both. */
  private static void checkNandPublicAndAcls(Map<String, String> m) {
    boolean hasPublicName = m.containsKey("google:ispublic");
    boolean hasAcls = m.containsKey("google:aclusers")
        || m.containsKey("google:aclgroups");
    if (hasPublicName && hasAcls) {
      throw new IllegalArgumentException("has both ispublic and ACLs");
    }
  }

  /** Cannot provide users without groups and vice-versa. */
  private static void checkBothOrNoneAcls(Map<String, String> m) {
    boolean hasUserAcls = m.containsKey("google:aclusers");
    boolean hasGroupAcls = m.containsKey("google:aclgroups");
    if (hasUserAcls && !hasGroupAcls) {
      throw new IllegalArgumentException("has users, but not groups");
    } else if (hasGroupAcls && !hasUserAcls) {
      throw new IllegalArgumentException("has groups, but not users");
    } else if (hasGroupAcls && hasUserAcls) {
      String userLine = m.get("google:aclusers").trim();
      String groupLine = m.get("google:aclgroups").trim();
      if (userLine.isEmpty() && groupLine.isEmpty()) {
        throw new IllegalArgumentException("both users and groups empty");
      }
    }
  }

  /** If has public indicator value is acceptable. */
  private static void checkPublicIsBoolean(Map<String, String> m) {
    String value = m.get("google:ispublic");
    if (null != value) {
      if (!"true".equals(value) && !"false".equals(value)) {
        throw new IllegalArgumentException("ispublic is not true nor false");
      }
    }
  }
}
