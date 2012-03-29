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

import static org.junit.Assert.*;

import static java.util.AbstractMap.SimpleEntry;
import static java.util.Map.Entry;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test cases for {@link Metadata}.
 */
public class MetadataTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testSingleSetAndGet() {
    Metadata m = new Metadata();
    assertEquals(m.getFirstValue("foo"), null);
    m.set("foo", "bar");
    assertEquals(m.getFirstValue("foo"), "bar");
    m.set("foo", "foo");
    assertEquals(m.getFirstValue("foo"), "foo");
    m.set("foo", "bar");
    assertEquals(m.getFirstValue("foo"), "bar");
  }

  @Test
  public void testSingleSetAffectOnKeys() {
    Metadata m = new Metadata();
    assertEquals(0, m.getKeys().size());
    m.set("foo", "bar");
    assertEquals(1, m.getKeys().size());
    m.set("foo", "bar");
    assertEquals(1, m.getKeys().size());
    m.set("bar", "foo");
    assertEquals(2, m.getKeys().size());
  }

  @Test
  public void testSingleSetNull() {
    Metadata m = new Metadata();
    assertEquals(0, m.getKeys().size());
    String nullStr = null;
    m.set("foo", nullStr);
    assertEquals(0, m.getKeys().size());
    m.set("bar", nullStr);
    assertEquals(0, m.getKeys().size());
    m.set("bar", "foo");
    assertEquals(1, m.getKeys().size());
    m.set("bar", nullStr);
    assertEquals(0, m.getKeys().size());
  }

  @Test
  public void testSetWithList() {
    Metadata m = new Metadata();
    assertEquals(m.getFirstValue("foo"), null);
    m.set("foo", Arrays.asList("bar", "home"));
    assertEquals(m.getFirstValue("foo"), "bar");
    assertEquals(m.getAllValues("foo"), Arrays.asList("bar", "home"));
    m.set("foo", Arrays.asList("foo"));
    assertEquals(m.getFirstValue("foo"), "foo");
    assertEquals(m.getAllValues("foo"), Arrays.asList("foo"));
    m.set("foo", Arrays.asList("barf", "floor"));
    assertEquals(m.getFirstValue("foo"), "barf");
    assertEquals(m.getAllValues("foo"), Arrays.asList("barf", "floor"));
  }

  @Test
  public void testSetWithNullList() {
    Metadata m = new Metadata();
    assertEquals(0, m.getKeys().size());
    List<String> nullList = null;
    m.set("foo", nullList);
    assertEquals(0, m.getKeys().size());
    m.set("bar", Arrays.asList("bar"));
    assertEquals(1, m.getKeys().size());
    m.set("foo", nullList);
    assertEquals(1, m.getKeys().size());
    m.set("bar", nullList);
    assertEquals(0, m.getKeys().size());
  }

  @Test
  public void testSetWithEmptyList() {
    Metadata m = new Metadata();
    assertEquals(0, m.getKeys().size());
    List<String> emptyList = new ArrayList<String>();;
    m.set("foo", emptyList);
    assertEquals(0, m.getKeys().size());
    m.set("bar", Arrays.asList("bar"));
    assertEquals(1, m.getKeys().size());
    m.set("foo", emptyList);
    assertEquals(1, m.getKeys().size());
    m.set("bar", emptyList);
    assertEquals(0, m.getKeys().size());
  }

  @Test
  public void testSetWithListHavingAllNulls() {
    Metadata m = new Metadata();
    assertEquals(0, m.getKeys().size());
    List<String> listWithOneNull = new ArrayList<String>();;
    listWithOneNull.add(null);
    List<String> listWithTwoNulls = new ArrayList<String>();;
    listWithTwoNulls.add(null);
    listWithTwoNulls.add(null);
    assertEquals(1, listWithOneNull.size());
    assertEquals(2, listWithTwoNulls.size());
    m.set("foo", listWithOneNull);
    assertEquals(0, m.getKeys().size());
    m.set("bar", listWithTwoNulls);
    assertEquals(0, m.getKeys().size());
  }

  @Test
  public void testSetWithListHavingSomeNulls() {
    Metadata m = new Metadata();
    assertEquals(0, m.getKeys().size());
    List<String> listWith2Nulls = new ArrayList<String>();;
    listWith2Nulls.add(null);
    listWith2Nulls.add("aaa");
    listWith2Nulls.add(null);
    listWith2Nulls.add("bbbb");
    List<String> listWith3Nulls = new ArrayList<String>();;
    listWith3Nulls.add("cc");
    listWith3Nulls.add(null);
    listWith3Nulls.add(null);
    listWith3Nulls.add("DDD");
    assertEquals(4, listWith2Nulls.size());
    assertEquals(4, listWith3Nulls.size());
    m.set("foo", listWith2Nulls);
    assertEquals(1, m.getKeys().size());
    m.set("bar", listWith3Nulls);
    assertEquals(2, m.getKeys().size());
    assertEquals(m.getAllValues("foo"), Arrays.asList("aaa", "bbbb"));
    assertEquals(m.getAllValues("bar"), Arrays.asList("cc", "DDD"));
  }

  @Test
  public void testNotReturningBackingLists() {
    Metadata m = new Metadata();
    m.set("foo", Arrays.asList("bar", "home", "villa"));
    List<String> all = m.getAllValues("foo");
    for (int i = 0; i < all.size(); i++) {
      all.set(i, all.get(i).toUpperCase());
    }
    assertEquals(all, Arrays.asList("BAR", "HOME", "VILLA"));
    assertEquals(m.getAllValues("foo"), Arrays.asList("bar", "home", "villa"));
    m.set("foo", all);
    assertEquals(m.getAllValues("foo"), Arrays.asList("BAR", "HOME", "VILLA"));
  }

  @Test
  public void testNotReturningBackingKeys() {
    Metadata m = new Metadata();
    m.set("foo", Arrays.asList("bar", "home"));
    m.set("sna", Arrays.asList("fu"));
    Set<String> keys = m.getKeys();
    assertEquals(2, keys.size());
    keys.add("another-key");
    assertEquals(3, keys.size());
    assertEquals(2, m.getKeys().size());
  }

  @Test
  public void testNotReturningBackingMapEntries() {
    Metadata m = new Metadata();
    m.set("foo", Arrays.asList("bar", "home"));
    m.set("sna", Arrays.asList("fu"));
    Set<Entry<String, String>> entries = m.getAllEntries();
    assertEquals(3, entries.size());
    entries.add(new SimpleEntry<String, String>("another-key", "v"));
    assertEquals(4, entries.size());
    assertEquals(3, m.getAllEntries().size());
  }

  @Test
  public void testEntriesGivenSorted() {
    Metadata m = new Metadata();
    m.set("foo", Arrays.asList("home", "bar"));
    m.set("early", Arrays.asList("bird"));
    m.set("cleary", Arrays.asList("obfuscated"));
    m.set("dearly", Arrays.asList("beloved"));
    m.set("badly", Arrays.asList("traversed", "implied"));
    Set<Entry<String, String>> entries = m.getAllEntries();
    String golden = "[badly=implied, badly=traversed, "
        + "cleary=obfuscated, dearly=beloved, early=bird, foo=bar, foo=home]";
    assertEquals(golden, "" + entries);
  }

  @Test
  public void testNullKeysNotAllowedSingleSet() {
    Metadata m = new Metadata();
    thrown.expect(NullPointerException.class);
    m.set(null, "no so fast");
  }

  @Test
  public void testNullKeysNotAllowedListSet() {
    Metadata m = new Metadata();
    thrown.expect(NullPointerException.class);
    m.set(null, Arrays.asList("fooee", "eee-ah-ah-o-o"));
  }

  @Test
  public void testNormalAdd() {
    Metadata m = new Metadata();
    m.add("foo", "home");
    assertEquals(m.getFirstValue("foo"), "home");
    m.add("foo", "bar");
    assertEquals(m.getFirstValue("foo"), "home");
    assertEquals(m.getAllValues("foo"), Arrays.asList("home", "bar"));
  }

  @Test
  public void testNullAdd() {
    Metadata m = new Metadata();
    m.add("foo", "home");
    m.add("foo", "bar");
    assertEquals(m.getAllValues("foo"), Arrays.asList("home", "bar"));
    m.add("foo", null);
    assertEquals(m.getAllValues("foo"), Arrays.asList("home", "bar"));
    m.add("foo", null);
    assertEquals(m.getAllValues("foo"), Arrays.asList("home", "bar"));
    m.add("foo", "home");
    assertEquals(m.getAllValues("foo"), Arrays.asList("home", "bar", "home"));
    m.add("foo", null);
    assertEquals(m.getAllValues("foo"), Arrays.asList("home", "bar", "home"));
  }

  @Test
  public void testEmptyConstructor() {
    Metadata m = new Metadata();
    assertEquals(0, m.getKeys().size());
    assertEquals(0, m.getAllEntries().size());
  }

  @Test
  public void testSourcedConstructor() {
    HashMap<String, String> map = new HashMap<String, String>();
    map.put("a", "b");
    map.put("c", "d");
    map.put("e", "f");
    map.put("g", "h");
    Metadata m = new Metadata(map.entrySet()); 
    assertEquals(4, m.getKeys().size());
    assertEquals(4, m.getAllEntries().size());
  }

  @Test
  public void testSourcedConstructorMultipleValues() {
    HashMap<String, String> map = new HashMap<String, String>();
    map.put("a", "b");
    map.put("c", "d");
    map.put("e", "f");
    map.put("g", "h");
    Set<Entry<String, String>> e = new HashSet<Entry<String, String>>();
    e.addAll(map.entrySet());
    e.add(new SimpleEntry<String, String>("a", "z"));
    e.add(new SimpleEntry<String, String>("g", "y"));
    Metadata m = new Metadata(e); 
    assertEquals(4, m.getKeys().size());
    assertEquals(6, m.getAllEntries().size());
  }

  @Test
  public void testNullKeysNotAllowedInAdd() {
    Metadata m = new Metadata();
    thrown.expect(NullPointerException.class);
    m.add(null, "can-no-add");
  }

  @Test
  public void testEquals() {
    Metadata m1 = new Metadata();
    m1.add("foo", "home");
    Metadata m2 = new Metadata();
    m2.add("foo", "home");
    assertEquals(m1, m2);

    m1.add("foo", "bar");
    m2.add("foo", "bar");
    assertEquals(m1, m2);

    m1.set("foo", "high");
    m2.set("foo", "low");
    assertFalse(m1.equals(m2));

    m2.set("foo", "high");
    assertEquals(m1, m2);

    m1.set("bar", Arrays.asList("floor", "door"));
    m2.set("bar", Arrays.asList("floor", "door"));
    assertEquals(m1, m2);

    m1.set("bar", Arrays.asList("near", "far"));
    assertFalse(m1.equals(m2));
    m2.set("bar", Arrays.asList("near", "far"));
    assertEquals(m1, m2);
  }

  @Test
  public void testDupConstructor() {
    Metadata m1 = new Metadata();
    m1.add("foo", "home");
    m1.add("bar", "far");

    Metadata m2 = new Metadata(m1);
    assertEquals(m1, m2);

    m1.set("shoes", Arrays.asList("bing", "bongo"));
    m2 = new Metadata(m1);
    assertEquals(m1, m2);

    // New array.
    m1.set("shoes", Arrays.asList("bing", "bongo"));
    assertEquals(m1, m2);
  }

  @Test
  public void testSetEntries() {
    HashSet<Entry<String, String>> e1 = new HashSet<Entry<String, String>>();
    HashSet<Entry<String, String>> e2 = new HashSet<Entry<String, String>>();
    e1.add(new SimpleEntry<String, String>("a", "b"));
    e1.add(new SimpleEntry<String, String>("b", "q"));
    e2.add(new SimpleEntry<String, String>("a", "b"));
    e2.add(new SimpleEntry<String, String>("b", "q"));
    Metadata m1 = new Metadata();
    m1.set(e1);
    Metadata m2 = new Metadata();
    m2.set(e2);
    assertEquals(m1, m2);
  }

  @Test
  public void testSetMetadata() {
    Metadata m1 = new Metadata();
    Metadata m2 = new Metadata();
    m1.set("foo", Arrays.asList("home", "floor"));
    m2.set(m1);
    assertEquals(m1, m2);
    m1.set("foo", Arrays.asList("home", "floor"));
    assertEquals(m1, m2);
    List<String> vals = new ArrayList<String>();
    vals.add("pigeon");
    vals.add("eagle");
    m1.set("foo", vals);
    m2.set(m1);
    assertTrue(m1.equals(m2));
    // Should not change m2.
    m1.add("foo", "bird");
    assertFalse(m1.equals(m2));
  }
}
