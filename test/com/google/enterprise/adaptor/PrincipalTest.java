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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.enterprise.adaptor.Principal.DomainFormat;
import com.google.enterprise.adaptor.Principal.ParsedPrincipal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
  public void testEmptyNameOfUser() {
    thrown.expect(IllegalArgumentException.class);
    new UserPrincipal("");
  }

  @Test
 public void testEmptyNameOfGroup() {
    thrown.expect(IllegalArgumentException.class);
    new GroupPrincipal("");
  }

  @Test
  public void testLeadingSpaceOfUser() {
    assertEquals("yowser", new UserPrincipal("\n yowser").getName());
  }

  @Test
  public void testLeadingSpaceOfGroup() {
    assertEquals("gooop", new GroupPrincipal("\n gooop").getName());
  }

  @Test
  public void testTrailingSpaceOfUser() {
    assertEquals("yowser", new UserPrincipal("yowser \t").getName());
  }

  @Test
  public void testTrailingSpaceOfGroup() {
    assertEquals("gooop", new GroupPrincipal("gooop \t").getName());
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

  @Test
  public void testComparator() {
     List<Principal> sorted = Collections.unmodifiableList(Arrays.asList(
       new UserPrincipal("Garbie", "Newbies"),
       new GroupPrincipal("Dragons", "Newbies"),
       new UserPrincipal("Honeysuckle", "Oldies"), 
       new UserPrincipal("Honeysuckle", "Oldies"), 
       new UserPrincipal("Honeysuckle", "Oldies"), 
       new UserPrincipal("Morning Glory", "Oldies"), 
       new UserPrincipal("Rosedust", "Oldies"), 
       new GroupPrincipal("Flutter Ponies", "Oldies")
     ));
     int ntrials = 10;
     for (int i = 0; i < ntrials; i++) {
       List<Principal> dup = new ArrayList<Principal>(sorted);
       Collections.shuffle(dup);
       Collections.sort(dup);
       assertEquals(sorted, dup);
     }
  }

  @Test
  public void testPrincipalParse() {
    assertEquals(
        new ParsedPrincipal(false, "user", "", DomainFormat.NONE, "Default"),
        new UserPrincipal("user").parse());
    assertEquals(new ParsedPrincipal(false, "user", "example.com",
          DomainFormat.DNS, "somens"),
        new UserPrincipal("user@example.com", "somens").parse());
    assertEquals(new ParsedPrincipal(false, "usr", "EXAMPLE",
          DomainFormat.NETBIOS, "Default"),
        new UserPrincipal("EXAMPLE\\usr").parse());
    assertEquals(new ParsedPrincipal(true, "grp", "EXAMPLE.COM",
          DomainFormat.NETBIOS_FORWARDSLASH, "Default"),
        new GroupPrincipal("EXAMPLE.COM/grp").parse());
  }

  @Test
  public void testParsedPrincipalRoundtrip() {
    // All principals should round-trip back to an identical principal.
    List<Principal> golden = Arrays.asList(
        new UserPrincipal("user"),
        new UserPrincipal("user@example.com", "ns1"),
        new UserPrincipal("EXAMPLE.COM\\user", "ns2"),
        new UserPrincipal("EXAMPLE.COM/user"),
        new GroupPrincipal("group1"),
        new GroupPrincipal("group@example.com"),
        new GroupPrincipal("\\group"),
        new GroupPrincipal("domain\\"),
        new GroupPrincipal("/group"),
        new GroupPrincipal("domain/"),
        new GroupPrincipal("@domain"),
        new GroupPrincipal("group@"),
        new GroupPrincipal("domain\\group@/\\extra"),
        new GroupPrincipal("domain/group@/\\extra"),
        new GroupPrincipal("group@domain@/\\extra"));
    for (Principal p : golden) {
      assertEquals(p, p.parse().toPrincipal());
    }
  }

  @Test
  public void testParsedPrincipalToPrincipal() {
    assertEquals(new UserPrincipal("a", "ns"),
        new ParsedPrincipal(false, "a", "", DomainFormat.NONE, "ns")
          .toPrincipal());
    assertEquals(new UserPrincipal("a@", "ns"),
        new ParsedPrincipal(false, "a", "", DomainFormat.DNS, "ns")
          .toPrincipal());
    assertEquals(new UserPrincipal("DOMAIN\\a", "ns"),
        new ParsedPrincipal(false, "a", "DOMAIN", DomainFormat.NONE, "ns")
          .toPrincipal());
    assertEquals(new UserPrincipal("a@DOM\\AIN", "ns"),
        new ParsedPrincipal(false, "a", "DOM\\AIN", DomainFormat.NETBIOS, "ns")
          .toPrincipal());
    assertEquals(new UserPrincipal("domain.com\\a@", "ns"),
        new ParsedPrincipal(false, "a@", "domain.com", DomainFormat.DNS, "ns")
          .toPrincipal());
    assertEquals(new UserPrincipal("domain.com\\a/", "ns"),
        new ParsedPrincipal(false, "a/", "domain.com", DomainFormat.DNS, "ns")
          .toPrincipal());
  }

  @Test(expected = IllegalStateException.class)
  public void testParsedPrincipalToPrincipalFail() {
    new ParsedPrincipal(false, "a/", "domain@com", DomainFormat.DNS, "ns")
        .toPrincipal();
  }

  @Test(expected = NullPointerException.class)
  public void testParsedPrincipalNullPlainName() {
    new ParsedPrincipal(false, null, "b", DomainFormat.DNS, "c");
  }

  @Test(expected = NullPointerException.class)
  public void testParsedPrincipalNullDomain() {
    new ParsedPrincipal(false, "a", null, DomainFormat.DNS, "c");
  }

  @Test(expected = NullPointerException.class)
  public void testParsedPrincipalNullDomainFormat() {
    new ParsedPrincipal(false, "a", "b", null, "c");
  }

  @Test(expected = NullPointerException.class)
  public void testParsedPrincipalNullNamespace() {
    new ParsedPrincipal(false, "a", "b", DomainFormat.DNS, null);
  }

  @Test
  public void testParsedPrincipalEquals() {
    ParsedPrincipal p1 = new ParsedPrincipal(false, "a", "b", DomainFormat.DNS,
        "c");
    ParsedPrincipal p2 = new ParsedPrincipal(false, new String("a"),
        new String("b"), DomainFormat.DNS, new String("c"));
    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());

    assertFalse(p1.equals(new Object()));
    assertFalse(p1.equals(new ParsedPrincipal(true, "a", "b", DomainFormat.DNS,
        "c")));
    assertFalse(p1.equals(new ParsedPrincipal(false, "z", "b", DomainFormat.DNS,
        "c")));
    assertFalse(p1.equals(new ParsedPrincipal(false, "a", "z", DomainFormat.DNS,
        "c")));
    assertFalse(p1.equals(new ParsedPrincipal(false, "a", "b",
        DomainFormat.NONE, "c")));
    assertFalse(p1.equals(new ParsedPrincipal(false, "a", "b", DomainFormat.DNS,
        "z")));
  }

  @Test
  public void testParsedPrincipalToString() {
    assertEquals("ParsedPrincipal(isGroup=true,plainName=a,domain=b,"
          + "domainFormat=NETBIOS,namespace=ns)",
        new ParsedPrincipal(true, "a", "b", DomainFormat.NETBIOS, "ns")
          .toString());
  }

  @Test
  public void testParsedPrincipalSetters() {
    ParsedPrincipal p
        = new ParsedPrincipal(false, "a", "b", DomainFormat.DNS, "c");
    assertEquals(new ParsedPrincipal(false, "z", "b", DomainFormat.DNS, "c"),
        p.plainName("z"));
    assertEquals(new ParsedPrincipal(false, "a", "z", DomainFormat.DNS, "c"),
        p.domain("z"));
    assertEquals(new ParsedPrincipal(false, "a", "b", DomainFormat.NONE, "c"),
        p.domainFormat(DomainFormat.NONE));
    assertEquals(new ParsedPrincipal(false, "a", "b", DomainFormat.DNS, "z"),
        p.namespace("z"));
  }
}
