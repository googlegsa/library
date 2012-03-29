// Copyright 2012 Google Inc. All Rights Reserved.
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

import static java.util.AbstractMap.SimpleEntry;
import static java.util.Map.Entry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Allows storing multiple metadata values to a single key.
 * <p>
 * Null keys are invalid as arguments.  Null values are
 * invalid as arguments.
 * <p>
 * This class is mutable and not thread-safe.
 */
public class Metadata {
  private TreeMap<String, List<String>> mappings;

  /** Create empty instance. */
  public Metadata() {
    mappings = new TreeMap<String, List<String>>();
  }

  /** Create instance with all entries.  Drops null values. */
  public Metadata(Set<Entry<String, String>> src) {
    this();
    for (Entry<String, String> e : src) {
      add(e.getKey(), e.getValue());
    }    
  }

  /** Duplicate. */
  public Metadata(Metadata m) {
    this(m.getAllEntries());
  }

  /** Eliminates all elements equal to null. */
  private static void removeNulls(List<String> list) {
    for (Iterator<String> it = list.iterator(); it.hasNext(); ) {
      if (null == it.next()) {
        it.remove();
      }
    }
  }

  /** Make copy of v be the values associated with key. */
  public void set(String k, List<String> v) {
    if (null == k) {
      throw new NullPointerException();
    }
    if (null == v) {
      mappings.remove(k);
      return;
    }
    v = new ArrayList<String>(v);
    removeNulls(v);
    if (v.isEmpty()) {
      mappings.remove(k);
    } else {
      mappings.put(k, v);
    }
  }

  /** Make v be only value associated with key. */
  public void set(String k, String v) {
    if (null == k) {
      throw new NullPointerException();
    }
    if (null == v) {
      mappings.remove(k);
    } else {
      List<String> vals = new ArrayList<String>(1);
      vals.add(v);
      mappings.put(k, vals);
    }
  }

  /** Make this metadata a deep copy of parameter. */
  public void set(Metadata src) {
    mappings.clear();
    for (Entry<String, List<String>> e : src.mappings.entrySet()) {
      // set, instead of mappings.put, assures deep copy of value.
      set(e.getKey(), e.getValue());
    }
  }

  public void set(Set<Entry<String, String>> s) {
    set(new Metadata(s));
    // TODO(ejona): Careful not to copy too many times.
  }

  /** Increases values mapped to k with v. */
  public void add(String k, String v) {
    if (null == k) {
      throw new NullPointerException();
    }
    if (null == v) {
      return;
    }
    List<String> found = mappings.get(k);
    if (null == found) {
      set(k, v);
    } else {
      found.add(v);
    }
  }

  /** Copy of inserted values for key, potentially null. */
  public List<String> getAllValues(String key) {
    List<String> found = mappings.get(key);
    if (null != found) {
      found = new ArrayList<String>(found);
    }
    return found;
  }

  /** Earliest inserted value for key still in map, or null if none. */
  public String getFirstValue(String key) {
    List<String> all = getAllValues(key);
    String first = null;
    if (null != all) {
      if (all.isEmpty()) {
        throw new AssertionError();
      }
      first = all.get(0); 
    }
    return first;
  }

  /** Get all keys with at least one value. */
  public Set<String> getKeys() {
    return new TreeSet<String>(mappings.keySet());
  }

  private static class EntryComparator
      implements Comparator<Entry<String, String>> {
    public int compare(Entry<String, String> a, Entry<String, String> b) {
      if (a.getKey().equals(b.getKey())) {
        return a.getValue().compareTo(b.getValue());
      } else {
        return a.getKey().compareTo(b.getKey());
      }
    }

    public boolean equals(Object o) {
      return o instanceof EntryComparator;
    }
  }

  private static final EntryComparator ENTRY_COMPARATOR = new EntryComparator();

  /** Copy of all mappings given in alphabetical order, by key first. */
  public Set<Entry<String, String>> getAllEntries() {
    Comparator<Entry<String, String>> cmp = ENTRY_COMPARATOR;
    Set<Entry<String, String>> accum = new TreeSet<Entry<String, String>>(cmp);
    for (String k : mappings.keySet()) {
      for (String v : mappings.get(k)) {
        accum.add(new SimpleEntry<String, String>(k, v));
      }
    }
    return accum;
  }

  public boolean equals(Object o) {
    if (null == o) {
      return false;
    }
    if (!(o instanceof Metadata)) {
      return false;
    }
    Metadata other = (Metadata) o;
    return getAllEntries().equals(other.getAllEntries());
  }

  public boolean isEmpty() {
    return mappings.isEmpty();
  }
}
