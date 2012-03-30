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

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
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
public class Metadata implements Iterable<Entry<String, String>> {
  private TreeMap<String, Set<String>> mappings
      = new TreeMap<String, Set<String>>();

  /** Create empty instance. */
  public Metadata() {
  }

  /** Duplicate. */
  public Metadata(Metadata m) {
    for (Entry<String, String> e : m) {
      add(e.getKey(), e.getValue());
    }    
  }

  /** Make v be only value associated with key. */
  public void set(String k, String v) {
    if (null == k) {
      throw new NullPointerException();
    }
    if (null == v) {
      throw new NullPointerException();
    }
    TreeSet<String> single = new TreeSet<String>();
    single.add(v);
    mappings.put(k, single);
  }

  /** Throws NullPointerException if a null is found. */
  private static void assureNoNulls(Set<String> items) {
    if (items.contains(null)) {
      throw new NullPointerException();
    }
  }

  /** Make copy of v be the values associated with key. */
  public void set(String k, Set<String> v) {
    if (null == k) {
      throw new NullPointerException();
    }
    if (null == v) {
      throw new NullPointerException();
    }
    assureNoNulls(v);
    if (v.isEmpty()) {
      mappings.remove(k);
    } else {
      v = new TreeSet<String>(v);
      mappings.put(k, v);
    }
  }

  /** Increases values mapped to k with v. */
  public void add(String k, String v) {
    if (null == k) {
      throw new NullPointerException();
    }
    if (null == v) {
      throw new NullPointerException();
    }
    Set<String> found = mappings.get(k);
    if (null == found) {
      set(k, v);
    } else {
      found.add(v);
    }
  }

  /** Replaces represented entries with provided ones. */
  public void set(Iterable<Entry<String, String>> it) {
    mappings.clear();
    for (Entry<String, String> e : it) {
      add(e.getKey(), e.getValue());
    }    
  }

  /** Gives unmodifiable reference to inserted values for key, empty if none. */
  public Set<String> getAllValues(String key) {
    Set<String> found = mappings.get(key);
    if (null == found) {
      found = Collections.emptySet(); 
    }
    return Collections.unmodifiableSet(found);
  }

  /** One of the inserted values, or null if none. */
  public String getOneValue(String key) {
    Set<String> all = getAllValues(key);
    String first = null;
    if (null != all) {
      if (all.isEmpty()) {
        throw new AssertionError();
      }
      first = all.iterator().next(); 
    }
    return first;
  }

  /** Get modifiable set of all keys with at least one value. */
  public Set<String> getKeys() {
    return mappings.keySet();
  }

  public Iterator<Entry<String, String>> iterator() {
    return new EntriesIterator();
  }

  /** Loops through keys and values, with keys being outer loop. */
  private class EntriesIterator implements Iterator<Entry<String, String>> {
    private String currentKey;
    private Iterator<Entry<String, Set<String>>> outer = mappings.entrySet().iterator();
    private Iterator<String> inner = Collections.<String>emptyList().iterator();

    @Override
    public boolean hasNext() {
      if (inner.hasNext()) {
        return true;
      }
      if (!outer.hasNext()) {
        return false;
      }
      Entry<String, Set<String>> currentEntry = outer.next();
      currentKey = currentEntry.getKey();
      inner = currentEntry.getValue().iterator();
      return hasNext();
    }

    @Override
    public Entry<String, String> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return new SimpleEntry<String, String>(currentKey, inner.next());
    }

    /** Not supported. */
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  public boolean equals(Object o) {
    if (!(o instanceof Metadata)) {
      return false;
    }
    Metadata other = (Metadata) o;
    if (!this.getKeys().equals(other.getKeys())) {
      return false;
    }
    for (String k : this.getKeys()) {
      if (!this.getAllValues(k).equals(other.getAllValues(k))) {
        return false;
      }
    }
    return true;
  }

  public boolean isEmpty() {
    return mappings.isEmpty();
  }
}
