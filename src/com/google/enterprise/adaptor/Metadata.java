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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
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
  private Map<String, Set<String>> mappings 
      = new TreeMap<String, Set<String>>();

  /** Create empty instance. */
  public Metadata() {
  }

  /** Duplicate. */
  public Metadata(Iterable<Entry<String, String>> m) {
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

  /** Replaces entries inside of this metadata with provided ones. */
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
    Set<String> found = mappings.get(key);
    String first = null;
    if (null != found) {
      if (found.isEmpty()) {
        throw new AssertionError();
      }
      first = found.iterator().next(); 
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

  /** Loops through keys and for each key all values. */
  private class EntriesIterator implements Iterator<Entry<String, String>> {
    private Iterator<Entry<String, Set<String>>> byKey
        = mappings.entrySet().iterator();
    private String currentKey;
    private Iterator<String> currentValues
        = Collections.<String>emptyList().iterator();

    @Override
    public boolean hasNext() {
      if (currentValues.hasNext()) {
        return true;
      }
      if (!byKey.hasNext()) {
        return false;
      }
      Entry<String, Set<String>> currentEntry = byKey.next();
      currentKey = currentEntry.getKey();
      currentValues = currentEntry.getValue().iterator();
      return hasNext();
    }

    @Override
    public Entry<String, String> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return new SimpleEntry<String, String>(currentKey, currentValues.next());
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
    if (this == o) {
      return true;
    }
    Metadata other = (Metadata) o;
    return mappings.equals(other.mappings);
  }

  public boolean isEmpty() {
    return mappings.isEmpty();
  }

  public String toString() {
    String sep = ", ";
    StringBuilder builder = new StringBuilder();
    for (Entry<String, String> e : this) {
      builder.append(sep);
      builder.append(e.getKey()).append("=").append(e.getValue());
    }
    String body = "";
    if (0 != builder.length()) {
      body = builder.substring(sep.length());
    }
    return "[" + body + "]";
  }

  /** Does not allow any mutating operations. */
  private static class ReadableMetadata extends Metadata {
    @Override
    public void set(String k, String v) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void set(String k, Set<String> v) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(String k, String v) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void set(Iterable<Entry<String, String>> it) {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public Set<String> getKeys() {
      return Collections.unmodifiableSet(super.getKeys());
    }
  };

  /** Get a reference to an unmodifiable view of this object. */
  public Metadata unmodifiableView() {
    Metadata unmodifiable = new ReadableMetadata();
    // Extra precaution against mappings use, but not against moding
    // sets that are values inside it.
    unmodifiable.mappings = Collections.unmodifiableMap(this.mappings); 
    return unmodifiable;
  }
}
