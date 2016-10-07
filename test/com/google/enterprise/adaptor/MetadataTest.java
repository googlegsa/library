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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/** Test cases for {@link Metadata}. */
public class MetadataTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testInitiallyEmpty() {
    Metadata m = new Metadata();
    assertTrue(m.isEmpty());
    assertTrue(m.getKeys().isEmpty());
    assertFalse(m.iterator().hasNext());
  }

  @Test
  public void testDupConstructor() {
    Metadata m1 = new Metadata();
    m1.add("foo", "home");
    m1.add("bar", "far");
    Metadata m2 = new Metadata(m1);
    assertEquals(m1, m2);
    m1.set("shoes", makeSet("bing", "bongo"));
    m2 = new Metadata(m1);
    assertEquals(m1, m2);
    // New values.
    m1.set("shoes", makeSet("bongo", "bing"));
    assertEquals(m1, m2);
  }

  @Test
  public void testSingleSetAndGet() {
    Metadata m = new Metadata();
    assertEquals(null, m.getOneValue("foo"));
    m.set("foo", "bar");
    assertEquals("bar", m.getOneValue("foo"));
    m.set("foo", "foo");
    assertEquals("foo", m.getOneValue("foo"));
    m.set("foo", "bar");
    assertEquals("bar", m.getOneValue("foo"));
  }

  @Test
  public void testSingleSetNullValue() {
    Metadata m = new Metadata();
    thrown.expect(NullPointerException.class);
    m.set("foo", (String) null);
  }

  @Test
  public void testSetWithTreeSet() {
    Metadata m = new Metadata();
    // Previous versions would throw an NPE because TreeSet.contains(null)
    // throws a NPE.
    m.set("foo", new TreeSet<String>());
  }

  @Test
  public void testSingleSetNullKey() {
    Metadata m = new Metadata();
    thrown.expect(NullPointerException.class);
    m.set(null, "bar");
  }

  @Test
  public void testSingleSetEffectOnKeys() {
    Metadata m = new Metadata();
    assertEquals(0, m.getKeys().size());
    m.set("foo", "bar");
    assertEquals(1, m.getKeys().size());
    m.set("foo", "bar");
    assertEquals(1, m.getKeys().size());
    m.set("bar", "foo");
    assertEquals(2, m.getKeys().size());
  }

  private static Set<String> makeSet(String ... s) {
    return new HashSet<String>(Arrays.asList(s));
  }

  @Test
  public void testMultipleSetAndGet() {
    Metadata m = new Metadata();
    assertEquals(null, m.getOneValue("foo"));
    m.set("foo", makeSet("bar", "home"));
    assertTrue(makeSet("bar", "home").contains(m.getOneValue("foo")));
    assertEquals(makeSet("bar", "home"), m.getAllValues("foo"));
    assertEquals(makeSet("home", "bar"), m.getAllValues("foo"));
    m.set("foo", makeSet("foo"));
    assertEquals("foo", m.getOneValue("foo"));
    assertEquals(makeSet("foo"), m.getAllValues("foo"));
    m.set("foo", makeSet("barf", "floor"));
    assertTrue(makeSet("barf", "floor").contains(m.getOneValue("foo")));
    assertEquals(makeSet("barf", "floor"), m.getAllValues("foo"));
  }

  @Test
  public void testMultipleGetNoKey() {
    Metadata m = new Metadata();
    assertEquals(makeSet(), m.getAllValues("foo"));
  }

  @Test
  public void testMultipleSetEmptySetRemoves() {
    Metadata m = new Metadata();
    m.set("foo", makeSet("bar", "home"));
    assertFalse(m.isEmpty());
    m.set("foo", makeSet());
    assertTrue(m.isEmpty());
  }

  @Test
  public void testMultipleSetNullValue() {
    Metadata m = new Metadata();
    assertTrue(m.isEmpty());
    Set<String> nullSet = null;
    thrown.expect(NullPointerException.class);
    m.set("foo", nullSet);
  }

  @Test
  public void testMultipleSetEmptySet() {
    Metadata m = new Metadata();
    assertTrue(m.isEmpty());
    Set<String> emptySet = makeSet();
    m.set("foo", emptySet);
    assertTrue(m.isEmpty());
    m.set("bar", makeSet("bar"));
    assertEquals(1, m.getKeys().size());
    m.set("foo", emptySet);
    assertEquals(1, m.getKeys().size());
    m.set("bar", emptySet);
    assertTrue(m.isEmpty());
  }

  @Test
  public void testSetEntriesIterable() {
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
  public void testSetEntriesNull() {
    Metadata m = new Metadata();
    thrown.expect(NullPointerException.class);
    m.set(null);
  }

  @Test
  public void testSetMetadata() {
    Metadata m1 = new Metadata();
    Metadata m2 = new Metadata();
    m1.set("foo", makeSet("home", "floor"));
    m2.set(m1);
    assertEquals(m1, m2);
    m1.set("foo", makeSet("home", "floor"));
    assertEquals(m1, m2);
    Set<String> vals = new HashSet<String>();
    vals.add("pigeon");
    vals.add("eagle");
    m1.set("foo", vals);
    m2.set(m1);
    assertTrue(m1.equals(m2));
    // Should not change m2.
    m1.add("foo", "bird");
    assertFalse(m1.equals(m2));
  }

  @Test
  public void testReturningUnmodifiableSetsA() {
    Metadata m = new Metadata();
    m.set("foo", makeSet("bar", "home", "villa"));
    Set<String> all = m.getAllValues("foo");
    thrown.expect(UnsupportedOperationException.class);
    all.add("newnew");
  }

  @Test
  public void testReturningUnmodifiableSetsB() {
    Metadata m = new Metadata();
    m.set("foo", makeSet("bar", "home", "villa"));
    Set<String> all = m.getAllValues("foo");
    thrown.expect(UnsupportedOperationException.class);
    all.remove("bar");
  }

  @Test
  public void testEasyToWriteModificationLoopOverValues() {
    Metadata m = new Metadata();
    m.set("foo", makeSet("bar", "home", "villa"));
    // End setup.
    // Loop should be idiomatic to write.
    Set<String> dest = new HashSet<String>();
    for (String v : m.getAllValues("foo")) {
      dest.add(v.toUpperCase());
    }
    m.set("foo", dest);
    // Double check function.
    assertEquals(makeSet("BAR", "HOME", "VILLA"), m.getAllValues("foo"));
  }

  @Test
  public void testReturningRemovalKeys() {
    Metadata m = new Metadata();
    m.set("foo", makeSet("bar", "home"));
    m.set("sna", makeSet("fu"));
    Set<String> keys = m.getKeys();
    assertEquals(2, keys.size());
    keys.remove("sna");
    assertEquals(1, keys.size());
    assertEquals(1, m.getKeys().size());
    assertEquals(keys, m.getKeys());
  }

  @Test
  public void testIteratingOverImmutableEntries() {
    Metadata m = new Metadata();
    m.set("foo", makeSet("bar", "home"));
    thrown.expect(UnsupportedOperationException.class);
    m.iterator().next().setValue("HOME");
  }

  private static Entry<String, String> ne(String k, String v) {
    return new SimpleEntry<String, String>(k, v);
  }

  @Test
  public void testEntriesGivenSorted() {
    Metadata m = new Metadata();
    m.set("foo", makeSet("home", "bar"));
    m.set("early", makeSet("bird"));
    m.set("cleary", makeSet("obfuscated"));
    m.set("dearly", makeSet("beloved"));
    m.set("badly", makeSet("traversed", "implied"));
    Iterator<Entry<String, String>> it = m.iterator();
    assertEquals(ne("badly", "implied"), it.next());
    assertEquals(ne("badly", "traversed"), it.next());
    assertEquals(ne("cleary", "obfuscated"), it.next());
    assertEquals(ne("dearly", "beloved"), it.next());
    assertEquals(ne("early", "bird"), it.next());
    assertEquals(ne("foo", "bar"), it.next());
    assertEquals(ne("foo", "home"), it.next());
    assertFalse(it.hasNext());
  }

  @Test
  public void testEmptyIterator() {
    Metadata m = new Metadata();
    assertFalse(m.iterator().hasNext());
  }

  @Test
  public void testNormalAdd() {
    Metadata m = new Metadata();
    m.add("foo", "home");
    assertEquals("home", m.getOneValue("foo"));
    m.add("foo", "bar");
    assertTrue(makeSet("bar", "home").contains(m.getOneValue("foo")));
    assertEquals(makeSet("home", "bar"), m.getAllValues("foo"));
    assertEquals(1, m.getKeys().size());
    m.add("foo", "few");
    assertEquals(1, m.getKeys().size());
    m.add("bar", "mor");
    assertEquals(makeSet("mor"), m.getAllValues("bar"));
    m.add("bar", "far");
    assertEquals(2, m.getKeys().size());
    assertEquals(makeSet("far", "mor"), m.getAllValues("bar"));
    m.add("bar", "far");
    assertEquals(makeSet("far", "mor"), m.getAllValues("bar"));
    m.add("bar", "mor");
    assertEquals(makeSet("far", "mor"), m.getAllValues("bar"));
  }

  @Test
  public void testNullAddA() {
    Metadata m = new Metadata();
    thrown.expect(NullPointerException.class);
    m.add("foo", null);
  }

  @Test
  public void testNullAddB() {
    Metadata m = new Metadata();
    m.add("foo", "bar");
    thrown.expect(NullPointerException.class);
    m.add("foo", null);
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

    m1.set("bar", makeSet("floor", "door"));
    m2.set("bar", makeSet("floor", "door"));
    assertEquals(m1, m2);

    m1.set("bar", makeSet("near", "far"));
    assertFalse(m1.equals(m2));
    m2.set("bar", makeSet("near", "far"));
    assertEquals(m1, m2);
  }

  @Test
  public void testHashCode() {
    Metadata m1 = new Metadata();
    m1.add("foo", "home");
    Metadata m2 = new Metadata();
    m2.add("foo", "home");
    assertEquals(m1.hashCode(), m2.hashCode());

    m1.add("foo", "bar");
    m2.add("foo", "bar");
    assertEquals(m1.hashCode(), m2.hashCode());

    m1.set("foo", "high");
    m2.set("foo", "low");
    assertFalse(m1.hashCode() == m2.hashCode());

    m2.set("foo", "high");
    assertEquals(m1.hashCode(), m2.hashCode());

    m1.set("bar", makeSet("floor", "door"));
    m2.set("bar", makeSet("floor", "door"));
    assertEquals(m1.hashCode(), m2.hashCode());

    m1.set("bar", makeSet("near", "far"));
    assertFalse(m1.hashCode() == m2.hashCode());
    m2.set("bar", makeSet("near", "far"));
    assertEquals(m1.hashCode(), m2.hashCode());
  }

  @Test
  public void testUnmodifiableSetSingle() {
    Metadata m = new Metadata().unmodifiableView();
    thrown.expect(UnsupportedOperationException.class);
    m.set("foo", "bar");
  }

  @Test
  public void testUnmodifiableSetMultiple() {
    Metadata m = new Metadata().unmodifiableView();
    thrown.expect(UnsupportedOperationException.class);
    m.set("foo", makeSet("bar", "home"));
  }

  @Test
  public void testUnmodifiableAdd() {
    Metadata m = new Metadata().unmodifiableView();
    thrown.expect(UnsupportedOperationException.class);
    m.add("foo", "bar");
  }

  @Test
  public void testUnmodifiableSetIterable() {
    Metadata m = new Metadata().unmodifiableView();
    thrown.expect(UnsupportedOperationException.class);
    m.set(new Metadata());
  }
  @Test
  public void testUnmodifiableSetSingleB() {
    Metadata m = new Metadata().unmodifiableView();
    thrown.expect(UnsupportedOperationException.class);
    m.set("foo", (String) null);
  }

  @Test
  public void testUnmodifiableSetMultipleB() {
    Metadata m = new Metadata().unmodifiableView();
    thrown.expect(UnsupportedOperationException.class);
    m.set("foo", (Set<String>) null);
  }

  @Test
  public void testUnmodifiableAddB() {
    Metadata m = new Metadata().unmodifiableView();
    thrown.expect(UnsupportedOperationException.class);
    m.add("foo", null);
  }

  @Test
  public void testUnmodifiableSetIterableB() {
    Metadata m = new Metadata().unmodifiableView();
    thrown.expect(UnsupportedOperationException.class);
    m.set(null);
  }

  @Test
  public void testUnmodifiableDoesNotAllowKeyRemoval() {
    Metadata m = new Metadata();
    m.set("foo", makeSet("bar", "home"));
    m.set("sna", makeSet("fu"));
    m = m.unmodifiableView();
    Set<String> keys = m.getKeys();
    assertEquals(2, keys.size());
    thrown.expect(UnsupportedOperationException.class);
    keys.remove("sna");
  }
}
