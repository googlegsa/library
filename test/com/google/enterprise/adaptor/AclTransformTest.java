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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.enterprise.adaptor.AclTransform.MatchData;
import com.google.enterprise.adaptor.AclTransform.Rule;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link AclTransform}. */
public class AclTransformTest {
  private static final Acl baseAcl = new Acl.Builder()
      .setInheritFrom(new DocId("inherit place"))
      .setEverythingCaseInsensitive()
      .setPermits(Arrays.asList(
          new UserPrincipal("u1"),
          new GroupPrincipal("g1@d1"),
          new GroupPrincipal("D1\\g1"),
          new GroupPrincipal("D1/g1"),
          new GroupPrincipal("u1", "ns1"),
          new GroupPrincipal("u1@d1", "ns2")))
      .setDenies(Arrays.asList(
          new UserPrincipal("u1"),
          new GroupPrincipal("g1"),
          new GroupPrincipal("g1", "ns1")))
      .build();

  @Test
  public void testEmptyTransform() {
    AclTransform transform = new AclTransform(Arrays.<Rule>asList());
    assertSame(baseAcl, transform.transform(baseAcl));
    Principal principal = new UserPrincipal("user");
    List<Principal> principals = Arrays.asList(principal);
    assertSame(principals, transform.transform(principals));
    assertSame(principal, transform.transform(principal));
  }

  @Test
  public void testNonMatchingTransform() {
    List<Rule> rules = Arrays.asList(
        new Rule(new MatchData(null, "nonmatching", null, null),
          new MatchData(null, "noprincipal", "nodomain", "nons")));
    assertEquals(baseAcl, new AclTransform(rules).transform(baseAcl));
  }

  @Test
  public void testAllMatchingTransform() {
    List<Rule> rules = Arrays.asList(
        new Rule(new MatchData(null, null, null, null),
          new MatchData(null, "anyprincipal", "anydomain", "anyns")));
    AclTransform transform = new AclTransform(rules);
    // Test null passthrough and re-use of AclTransform
    assertNull(transform.transform((Acl) null));
    assertEquals(new Acl.Builder(baseAcl)
          .setPermits(Arrays.asList(
            new UserPrincipal("anydomain\\anyprincipal", "anyns"),
            new GroupPrincipal("anyprincipal@anydomain", "anyns"),
            new GroupPrincipal("anydomain\\anyprincipal", "anyns"),
            new GroupPrincipal("anydomain/anyprincipal", "anyns")))
          .setDenies(Arrays.asList(
            new UserPrincipal("anydomain\\anyprincipal", "anyns"),
            new GroupPrincipal("anydomain\\anyprincipal", "anyns")))
          .build(),
        transform.transform(baseAcl));
  }

  @Test
  public void testAllMatchingTransformNoop() {
    List<Rule> rules = Arrays.asList(
        new Rule(new MatchData(null, null, null, null),
          new MatchData(null, null, null, null)));
    assertEquals(baseAcl, new AclTransform(rules).transform(baseAcl));
  }

  @Test
  public void testGroupMatching() {
    List<Rule> rules = Arrays.asList(
        new Rule(new MatchData(true, null, null, null),
          new MatchData(null, "anygroup", null, null)));
    assertEquals(new Acl.Builder(baseAcl)
          .setPermits(Arrays.asList(
            new UserPrincipal("u1"),
            new GroupPrincipal("anygroup@d1"),
            new GroupPrincipal("D1\\anygroup"),
            new GroupPrincipal("D1/anygroup"),
            new GroupPrincipal("anygroup", "ns1"),
            new GroupPrincipal("anygroup@d1", "ns2")))
          .setDenies(Arrays.asList(
            new UserPrincipal("u1"),
            new GroupPrincipal("anygroup", "ns1"),
            new GroupPrincipal("anygroup")))
          .build(),
        new AclTransform(rules).transform(baseAcl));
  }

  @Test
  public void testSpecificMatching() {
    List<Rule> rules = Arrays.asList(
        new Rule(new MatchData(true, "g1", "", "ns1"),
          new MatchData(null, null, "foundit", null)));
    assertEquals(new Acl.Builder(baseAcl)
          .setDenies(Arrays.asList(
            new UserPrincipal("u1"),
            new GroupPrincipal("g1"),
            new GroupPrincipal("foundit\\g1", "ns1")))
          .build(),
        new AclTransform(rules).transform(baseAcl));
  }

  @Test
  public void testRegexMatching() {
    List<Rule> rules = Arrays.asList(
        new Rule(new MatchData(true, ".1", ".*", "ns[0-9]"),
          new MatchData(null, null, "foundit", null)));
    assertEquals(new Acl.Builder(baseAcl)
          .setPermits(Arrays.asList(                   // originals:
            new UserPrincipal("u1"),                   // U("u1")
            new GroupPrincipal("g1@d1"),               // G("g1@d1")
            new GroupPrincipal("D1\\g1"),              // G("D1\\g1")
            new GroupPrincipal("D1/g1"),               // G("D1/g1")
            new GroupPrincipal("foundit\\u1", "ns1"),  // G("u1", "ns1")
            new GroupPrincipal("u1@foundit", "ns2")))  // G("u1@d1", "ns2")
          .setDenies(Arrays.asList(
            new UserPrincipal("u1"),                   // U("u1")
            new GroupPrincipal("g1"),                  // G("g1")
            new GroupPrincipal("foundit\\g1", "ns1"))) // G(g1", "ns1")
          .build(),
        new AclTransform(rules).transform(baseAcl));
  }

  @Test
  public void testRegexReplacing() {
    List<Rule> rules = Arrays.asList(
        new Rule(
            new MatchData(true, "(..)", "(.)(.*)", null),
            new MatchData(null, "\\domain1\\name1", "hello", null)));
    assertEquals(new Acl.Builder(baseAcl)
          .setPermits(Arrays.asList(                   // originals:
            new UserPrincipal("u1"),                   // U("u1")
            new GroupPrincipal("dg1@hello"),           // G("g1@d1")
            new GroupPrincipal("hello\\dg1"),          // G("D1\\g1")
            new GroupPrincipal("hello/dg1"),           // G("D1/g1")
            new GroupPrincipal("u1", "ns1"),           // G("u1", "ns1")
            new GroupPrincipal("du1@hello", "ns2")))   // G("u1@d1", "ns2")
          .setDenies(Arrays.asList(
            new UserPrincipal("u1"),                   // U("u1")
            new GroupPrincipal("g1"),                  // G("g1")
            new GroupPrincipal("g1", "ns1")))          // G(g1", "ns1")
          .build(),
        new AclTransform(rules).transform(baseAcl));
  }

  @Test
  public void testBackSlashEscape() {
    List<Rule> rules = Arrays.asList(
        new Rule(
            new MatchData(true, "(..)", "(.)(.*)", null),
            new MatchData(null, "\\domain1ab\\name1", "hello", "\\\\name1")));
    assertEquals(new Acl.Builder(baseAcl)
          .setPermits(Arrays.asList(                       // originals:
            new UserPrincipal("u1"),                       // U("u1")
            new GroupPrincipal("dabg1@hello", "\\name1"),  // G("g1@d1")
            new GroupPrincipal("hello\\Dabg1", "\\name1"), // G("D1\\g1")
            new GroupPrincipal("hello/Dabg1", "\\name1"),  // G("D1/g1")
            new GroupPrincipal("u1", "ns1"),               // G("u1", "ns1")
            new GroupPrincipal("dabu1@hello", "\\name1"))) // G("u1@d1", "ns2")
          .setDenies(Arrays.asList(
            new UserPrincipal("u1"),                       // U("u1")
            new GroupPrincipal("g1"),                      // G("g1")
            new GroupPrincipal("g1", "ns1")))              // G(g1", "ns1")
          .build(),
        new AclTransform(rules).transform(baseAcl));  }

  @Test(expected = NullPointerException.class)
  public void testRuleNullMatch() {
    new Rule(null, new MatchData(null, null, null, null));
  }

  @Test(expected = NullPointerException.class)
  public void testRuleNullReplacement() {
    new Rule(new MatchData(null, null, null, null), null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRuleWithNonNullReplacementGroup() {
    new Rule(new MatchData(null, null, null, null),
        new MatchData(false, null, null, null));
  }

  @Test
  public void testToString() {
    assertEquals("AclTransform(rules=[Rule("
        + "match=MatchData(isGroup=true,name=a,domain=b,namespace=c),"
        + "replace=MatchData(isGroup=null,name=null,domain=null,namespace=null)"
        + ")])",
        new AclTransform(Arrays.asList(new Rule(
          new MatchData(true, "a", "b", "c"),
          new MatchData(null, null, null, null)))).toString());
  }

  @Test
  public void testEquals() {
    MatchData m1 = new MatchData(true, "a", "b", "c");
    MatchData m2 = new MatchData(true, new String("a"), new String("b"),
        new String("c"));
    MatchData m3 = new MatchData(false, "a", "b", "c");
    MatchData m4 = new MatchData(null, "a", "b", "c");
    MatchData m5 = new MatchData(true, "z", "b", "c");
    MatchData m6 = new MatchData(true, null, "b", "c");
    MatchData m7 = new MatchData(true, "a", "z", "c");
    MatchData m8 = new MatchData(true, "a", null, "c");
    MatchData m9 = new MatchData(true, "a", "b", "z");
    MatchData m10 = new MatchData(true, "a", "b", null);
    assertEquals(m1, m2);
    assertEquals(m1.hashCode(), m2.hashCode());
    assertFalse(m1.equals(m3));
    assertFalse(m1.equals(m4));
    assertFalse(m1.equals(m5));
    assertFalse(m1.equals(m6));
    assertFalse(m1.equals(m7));
    assertFalse(m1.equals(m8));
    assertFalse(m1.equals(m9));
    assertFalse(m1.equals(m10));
    assertFalse(m1.equals(new Object()));

    // Same as m4
    MatchData m11 = new MatchData(null, new String("a"), new String("b"),
        new String("c"));
    MatchData m12 = new MatchData(null, "a", "b", null);
    Rule r1 = new Rule(m1, m4);
    Rule r2 = new Rule(m2, m11);
    Rule r3 = new Rule(m3, m4);
    Rule r4 = new Rule(m1, m12);
    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
    assertFalse(r1.equals(r3));
    assertFalse(r1.equals(r4));
    assertFalse(r1.equals(new Object()));

    AclTransform t1 = new AclTransform(Arrays.asList(r1));
    AclTransform t2 = new AclTransform(Arrays.asList(r2));
    AclTransform t3 = new AclTransform(Arrays.asList(r3));
    AclTransform t4 = new AclTransform(Arrays.asList(r1, r1));
    assertEquals(t1, t2);
    assertEquals(t1.hashCode(), t2.hashCode());
    assertFalse(t1.equals(t3));
    assertFalse(t1.equals(t4));
    assertFalse(t1.equals(new Object()));
  }
}
