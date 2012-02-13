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

import java.util.Arrays;

/** A single meta item consists of a name and value. */
public final class MetaItem implements Comparable<MetaItem> {
  private final String n, v;

  public String getName() {
    return n;
  }

  public String getValue() {
    return v;
  }

  /**
   * Requires non-null and not-empty name and equates null
   * value with empty value.
   */
  private MetaItem(String name, String value) {
    if (null == name || name.isEmpty()) {
      throw new IllegalArgumentException("name cannot be empty");
    }
    if (null == value) {
      value = "";
    }
    this.n = name;
    this.v = value;
  }

  /** Define your own metaname and give it a value. */
  public static MetaItem raw(String name, String value) {
    return new MetaItem(name, value);
  }

  @Override
  public boolean equals(Object o) {
    boolean same = false;
    if (null != o && getClass().equals(o.getClass())) {
      MetaItem mi = (MetaItem) o;
      same = n.equals(mi.n) && v.equals(mi.v);
    }
    return same;
  }

  @Override
  public int hashCode() {
    Object parts[] = new Object[] { n, v };
    return Arrays.hashCode(parts);
  }

  /** Primary sort by name, secondary by value. */
  @Override
  public int compareTo(MetaItem o) {
    if (null == o) {
      throw new IllegalArgumentException("null object");
    }
    if (!getClass().equals(o.getClass())) {
      throw new ClassCastException("wrong type: " + o.getClass());
    }
    MetaItem other = (MetaItem) o;
    int cmp = n.compareTo(other.n);
    if (0 == cmp) {
      cmp = v.compareTo(other.v);
    }
    return cmp;
  }

  /** "MetaItem(" + n + "," + v + ")" */
  @Override
  public String toString() {
    return "MetaItem(" + n + "," + v + ")";
  }
}
