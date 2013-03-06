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

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.*;

/**
 * Test cases for {@link Principal}.
 */
public class PrincipalTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testDefaultNamespaceOfUser() {
    Principal p = new UserPrincipal("id");
    assertEquals("Default", p.getNamespace());
  }

  @Test
  public void testDefaultNamespaceOfGroup() {
    Principal p = new GroupPrincipal("grooop");
    assertEquals("Default", p.getNamespace());
  }

  @Test
  public void testSimpleUser() {
    Principal p = new UserPrincipal("user", "ns");
    assertEquals("user", p.getName());
    assertEquals("ns", p.getNamespace());
    assertTrue(p.isUser());
    assertFalse(p.isGroup());
  }

  @Test
  public void testSimpleGroup() {
    Principal p = new GroupPrincipal("ggg", "ns");
    assertEquals("ggg", p.getName());
    assertEquals("ns", p.getNamespace());
    assertTrue(p.isGroup());
    assertFalse(p.isUser());
  }

  @Test
  public void testNullNameOfUser() {
    thrown.expect(NullPointerException.class);
    new UserPrincipal(null);
  }

  @Test
  public void testNullNameOfGroup() {
    thrown.expect(NullPointerException.class);
    new GroupPrincipal(null);
  }

  @Test
  public void testNullNamespaceOfUser() {
    thrown.expect(NullPointerException.class);
    new UserPrincipal("yowzer", null);
  }

  @Test
  public void testNullNamespaceOfGroup() {
    thrown.expect(NullPointerException.class);
    new GroupPrincipal("gooop", null);
  }

  @Test
  public void testUserEquals() {
    Principal p1 = new UserPrincipal("id");
    assertFalse(p1.equals(null));
    Principal p2 = new UserPrincipal("id");
    assertEquals(p1, p2);
    Principal p3 = new UserPrincipal("id", "N");
    assertFalse(p1.equals(p3));
    assertFalse(p2.equals(p3));
  }

  @Test
  public void testGroupEquals() {
    Principal p1 = new GroupPrincipal("id");
    assertFalse(p1.equals(null));
    Principal p2 = new GroupPrincipal("id");
    assertEquals(p1, p2);
    Principal p3 = new GroupPrincipal("id", "N");
    assertFalse(p1.equals(p3));
    assertFalse(p2.equals(p3));
  }

  @Test
  public void testUserAndGroupNotEqual() {
    Principal u = new UserPrincipal("id");
    Principal g = new GroupPrincipal("id");
    assertFalse(u.equals(g));
  }

  @Test
  public void testUserHashCode() {
    Principal p1 = new UserPrincipal("id");
    Principal p2 = new UserPrincipal("id");
    assertEquals(p1.hashCode(), p2.hashCode());
    Set<Principal> p = new HashSet<Principal>();
    p.add(p1);
    p.add(p2);
    assertEquals(1, p.size()); 
  }

  @Test
  public void testGroupHashCode() {
    Principal p1 = new GroupPrincipal("id");
    Principal p2 = new GroupPrincipal("id");
    assertEquals(p1.hashCode(), p2.hashCode());
    Set<Principal> p = new HashSet<Principal>();
    p.add(p1);
    p.add(p2);
    assertEquals(1, p.size()); 
  }

  @Test
  public void testToString() {
    Principal u = new UserPrincipal("1d");
    String s = "" + u;
    assertTrue(s.contains("User"));
    assertTrue(s.contains("1d"));
    assertTrue(s.contains("Default"));
  }
}
