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

import java.util.*;

/** Represents a fixed set of validated {@link MetaItem}s. */
public final class Metadata implements Iterable<MetaItem> {
  /** Empty convenience instance. */
  public static final Metadata EMPTY = new Metadata.Builder().build();

  private final Map<String, MetaItem> items;
 
  /**
   * Validates that each meta name is unique, there is either
   * public-indicator or ACLs and that ACLs values are acceptable.
   */ 
  private Metadata(Map<String, MetaItem> allMeta) {
    items = Collections.unmodifiableMap(new TreeMap<String, MetaItem>(allMeta));
    checkConsistency(items);
  }

  @Override
  public boolean equals(Object o) {
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
    return items.values().iterator();
  }

  @Override
  public String toString() {
    return items.values().toString();
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

  private static void checkConsistency(Map<String, MetaItem> allMeta) {
    checkNandPublicAndAcls(allMeta);
    checkBothOrNoneAcls(allMeta);
    checkPublicIsBoolean(allMeta);
  }

  /** Either have public indicator or ACLs, but not both. */
  private static void checkNandPublicAndAcls(Map<String, MetaItem> m) {
    boolean hasPublicName = m.containsKey("google:ispublic");
    boolean hasAcls = m.containsKey("google:aclusers")
        || m.containsKey("google:aclgroups");
    if (hasPublicName && hasAcls) {
      throw new IllegalArgumentException("has both ispublic and ACLs");
    }
  }

  /** Cannot provide users without groups and vice-versa. */
  private static void checkBothOrNoneAcls(Map<String, MetaItem> m) {
    boolean hasUserAcls = m.containsKey("google:aclusers");
    boolean hasGroupAcls = m.containsKey("google:aclgroups");
    if (hasUserAcls && !hasGroupAcls) {
      throw new IllegalArgumentException("has users, but not groups");
    } else if (hasGroupAcls && !hasUserAcls) {
      throw new IllegalArgumentException("has groups, but not users");
    } else if (hasGroupAcls && hasUserAcls) {
      String userLine = m.get("google:aclusers").getValue().trim();
      String groupLine = m.get("google:aclgroups").getValue().trim();
      if (userLine.isEmpty() && groupLine.isEmpty()) {
        throw new IllegalArgumentException("both users and groups empty");
      }
    }
  }

  /** If has public indicator value is acceptable. */
  private static void checkPublicIsBoolean(Map<String, MetaItem> m) {
    MetaItem item = m.get("google:ispublic");
    if (null != item) {
      String value = item.getValue();
      if (!"true".equals(value) && !"false".equals(value)) {
        throw new IllegalArgumentException("ispublic is not true nor false");
      }
    }
  }

  /**
   * Builder for instances of {@link Metadata}.
   */
  public static class Builder {
    private Map<String, MetaItem> items = new TreeMap<String, MetaItem>();

    /**
     * Create new empty builder.
     */
    public Builder() {}

    /**
     * Initialize builder with {@code MetaItems} from {@code iterable}. Useful
     * to make tweaked copies of {@link Metadata}.
     */
    public Builder(Iterable<MetaItem> iterable) {
      for (MetaItem item : iterable) {
        items.put(item.getName(), item);
      }
    }

    /**
     * Add a new {@code MetaItem} to the builder, replacing any previously-added
     * {@code MetaItem} with the same name.
     */
    public Builder add(MetaItem item) {
      items.put(item.getName(), item);
      return this;
    }

    /**
     * Returns a metadata instance that reflects current builder state. It does
     * not reset the builder.
     */
    public Metadata build() {
      return new Metadata(items);
    }
  }
}
