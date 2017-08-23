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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Sets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test cases for {@link Acl}.
 */
public class AclTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static Set<UserPrincipal> user(String... names) {
    Set<UserPrincipal> set = new TreeSet<UserPrincipal>();
    for (String n : names) {
      set.add(new UserPrincipal(n));
    }
    return set;
  }

  private static Set<GroupPrincipal> group(String... names) {
    Set<GroupPrincipal> set = new TreeSet<GroupPrincipal>();
    for (String n : names) {
      set.add(new GroupPrincipal(n));
    }
    return set;
  }

  @Test
  public void testNonHierarchialUsage() {
    Acl acl = new Acl.Builder()
        .setPermitGroups(group("permitGroup"))
        .setDenyGroups(group("denyGroup"))
        .setPermitUsers(user("permitUser", "bothUser"))
        .setDenyUsers(user("denyUser", "bothUser"))
        .build();
    // Test all main combinations:
    // (user permitted, denied, or indeterminate) x (group permitted, denied,
    //   indeterminate, no groups)
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permitUser", "permitGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("permitUser", "denyGroup")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permitUser", "unknownGroup")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permitUser")));

    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("denyUser", "permitGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("denyUser", "denyGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("denyUser", "unknownGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("denyUser")));

    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("unknownUser", "permitGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("unknownUser", "denyGroup")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("unknownUser", "unknownGroup")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("unknownUser")));

    // Make sure that deny wins.
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("permitUser", "denyGroup", "permitGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("bothUser", "permitGroup")));
  }

  @Test
  public void testNullInheritanceType() {
    thrown.expect(NullPointerException.class);
    Acl.Builder builder = new Acl.Builder().setInheritanceType(null);
  }

  @Test
  public void testDefaults() {
    Acl acl = Acl.EMPTY;
    assertEquals(Collections.emptySet(), acl.getDenyGroups());
    assertEquals(Collections.emptySet(), acl.getPermitGroups());
    assertEquals(Collections.emptySet(), acl.getDenyUsers());
    assertEquals(Collections.emptySet(), acl.getPermitUsers());
    assertNull(acl.getInheritFrom());
    assertEquals(Acl.InheritanceType.LEAF_NODE, acl.getInheritanceType());
  }

  @Test
  public void testAccessors() {
    final Set<GroupPrincipal> goldenDenyGroups = Collections.unmodifiableSet(
        new HashSet<GroupPrincipal>(group("dg1", "dg2", "g3")));
    final Set<GroupPrincipal> goldenPermitGroups = Collections.unmodifiableSet(
        new HashSet<GroupPrincipal>(group("pg1", "pg2", "g3")));
    final Set<UserPrincipal> goldenDenyUsers = Collections.unmodifiableSet(
        new HashSet<UserPrincipal>(user("du1", "du2", "du3", "u4")));
    final Set<UserPrincipal> goldenPermitUsers = Collections.unmodifiableSet(
        new HashSet<UserPrincipal>(user("pu1", "pu2", "u4", "g3")));
    final DocId goldenInheritFrom = new DocId("something");
    final Acl.InheritanceType goldenInheritType
        = Acl.InheritanceType.CHILD_OVERRIDES;

    Set<GroupPrincipal> denyGroups
        = new HashSet<GroupPrincipal>(goldenDenyGroups);
    Set<GroupPrincipal> permitGroups
        = new HashSet<GroupPrincipal>(goldenPermitGroups);
    Set<UserPrincipal> denyUsers
        = new HashSet<UserPrincipal>(goldenDenyUsers);
    Set<UserPrincipal> permitUsers
        = new HashSet<UserPrincipal>(goldenPermitUsers);
    Acl.Builder builder = new Acl.Builder()
        .setDenyGroups(denyGroups).setPermitGroups(permitGroups)
        .setDenyUsers(denyUsers).setPermitUsers(permitUsers)
        .setInheritFrom(goldenInheritFrom)
        .setInheritanceType(goldenInheritType);
    Acl acl = builder.build();
    assertEquals(goldenDenyGroups, acl.getDenyGroups());
    assertEquals(goldenPermitGroups, acl.getPermitGroups());
    assertEquals(goldenDenyUsers, acl.getDenyUsers());
    assertEquals(goldenPermitUsers, acl.getPermitUsers());
    assertEquals(goldenInheritFrom, acl.getInheritFrom());
    assertEquals(goldenInheritType, acl.getInheritanceType());

    // Verify the builder makes copies of provided data.
    denyGroups.clear();
    permitGroups.clear();
    denyUsers.clear();
    permitUsers.clear();
    assertEquals(acl, builder.build());

    // Verify that Acl doesn't mutate due to changes in Builder.
    builder.setDenyGroups(Collections.<GroupPrincipal>emptySet());
    builder.setPermitGroups(Collections.<GroupPrincipal>emptySet());
    builder.setDenyUsers(Collections.<UserPrincipal>emptySet());
    builder.setPermitUsers(Collections.<UserPrincipal>emptySet());
    builder.setInheritFrom(null);
    builder.setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES);

    assertEquals(goldenDenyGroups, acl.getDenyGroups());
    assertEquals(goldenPermitGroups, acl.getPermitGroups());
    assertEquals(goldenDenyUsers, acl.getDenyUsers());
    assertEquals(goldenPermitUsers, acl.getPermitUsers());
    assertEquals(goldenInheritFrom, acl.getInheritFrom());
    assertEquals(goldenInheritType, acl.getInheritanceType());
  }

  @Test
  public void testTypeAgnosticAccessors() {
    Acl acl = new Acl.Builder()
        .setPermitUsers(user("todelete1")).setDenyUsers(user("todelete2"))
        .setPermitGroups(group("todelete3")).setDenyGroups(group("todelete4"))
        .setPermits(Sets.union(user("good1"), group("good2")))
        .setDenies(Sets.union(user("good3"), group("good4")))
        .build();
    assertEquals(new Acl.Builder()
        .setPermitUsers(user("good1")).setDenyUsers(user("good3"))
        .setPermitGroups(group("good2")).setDenyGroups(group("good4"))
        .build(), acl);
    assertEquals(Sets.union(user("good1"), group("good2")), acl.getPermits());
    assertEquals(Sets.union(user("good3"), group("good4")), acl.getDenies());
  }

  @Test
  public void testDenyGroupsImmutability() {
    Acl acl = new Acl.Builder().setEverythingCaseInsensitive()
        .setDenyGroups(group("item")).build();
    thrown.expect(UnsupportedOperationException.class);
    acl.getDenyGroups().clear();
  }

  @Test
  public void testPermitGroupsImmutability() {
    Acl acl = new Acl.Builder().setPermitGroups(group("item")).build();
    thrown.expect(UnsupportedOperationException.class);
    acl.getPermitGroups().clear();
  }

  @Test
  public void testDenyUsersImmutability() {
    Acl acl = new Acl.Builder().setDenyUsers(user("item")).build();
    thrown.expect(UnsupportedOperationException.class);
    acl.getDenyUsers().clear();
  }

  @Test
  public void testPermitUsersImmutability() {
    Acl acl = new Acl.Builder().setEverythingCaseInsensitive()
        .setPermitUsers(user("item")).build();
    thrown.expect(UnsupportedOperationException.class);
    acl.getPermitUsers().clear();
  }

  @Test
  public void testNullUser() {
    Acl.Builder builder = new Acl.Builder();
    thrown.expect(NullPointerException.class);
    builder.setPermitUsers(Arrays.asList((UserPrincipal) null));
  }

  @Test
  public void testEmptyGroup() {
    Acl.Builder builder = new Acl.Builder();
    thrown.expect(IllegalArgumentException.class);
    builder.setDenyGroups(group(""));
  }

  @Test
  public void testWhitespaceSurroundingUserBefore() {
    Acl.Builder builder = new Acl.Builder();
    Acl a = builder.setDenyUsers(user(" test")).build();
    assertEquals(user("test"), a.getDenyUsers());
  }

  @Test
  public void testWhitespaceSurroundingUserAfter() {
    Acl.Builder builder = new Acl.Builder();
    Acl a = builder.setDenyUsers(user("test\t")).build();
    assertEquals(user("test"), a.getDenyUsers());
  }

  @Test
  public void testToString() {
    Acl acl = new Acl.Builder()
        .setDenyGroups(new HashSet<GroupPrincipal>(group("dg1", "dg2", "g3")))
        .setPermitGroups(new HashSet<GroupPrincipal>(group("pg1", "pg2", "g3")))
        .setDenyUsers(new HashSet<UserPrincipal>(user("du1", "du2", "du3", "u4")))
        .setPermitUsers(new HashSet<UserPrincipal>(user("pu1", "pu2", "u4", "g3")))
        .setInheritFrom(new DocId("something"))
        .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES).build();

    assertEquals("Acl(caseSensitive=true, "
        + "inheritFrom=DocId(something), "
        + "inheritType=CHILD_OVERRIDES, "
        + "permitGroups=[Principal(Group,g3,Default), Principal(Group,pg1,Default), "
            + "Principal(Group,pg2,Default)], "
        + "denyGroups=[Principal(Group,dg1,Default), Principal(Group,dg2,Default), "
            + "Principal(Group,g3,Default)], "
        + "permitUsers=[Principal(User,g3,Default), Principal(User,pu1,Default), "
            + "Principal(User,pu2,Default), Principal(User,u4,Default)], "
        + "denyUsers=[Principal(User,du1,Default), Principal(User,du2,Default), "
            + "Principal(User,du3,Default), Principal(User,u4,Default)])"
        , acl.toString());
  }

  @Test
  public void testEquals() {
    Acl.Builder builder = new Acl.Builder();
    Acl base = builder.build();

    builder.setPermitGroups(group("testing"));
    Acl permitGroups = builder.build();
    builder.setPermitGroups(Collections.<GroupPrincipal>emptySet());

    builder.setDenyGroups(group("testing"));
    Acl denyGroups = builder.build();
    builder.setDenyGroups(Collections.<GroupPrincipal>emptySet());

    builder.setPermitUsers(user("testing"));
    Acl permitUsers = builder.build();
    builder.setPermitUsers(Collections.<UserPrincipal>emptySet());

    builder.setDenyUsers(user("testing"));
    Acl denyUsers = builder.build();
    builder.setDenyUsers(Collections.<UserPrincipal>emptySet());

    builder.setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES);
    builder.setInheritFrom(new DocId("testing"));
    Acl inheritFrom1 = builder.build();

    builder.setInheritFrom(new DocId("testing2"));
    Acl inheritFrom2 = builder.build();

    builder.setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES);
    Acl childOverrides = builder.build();

    builder.setInheritFrom(new DocId("testing2"));
    Acl childOverridesAgain = builder.build();
    builder.setInheritanceType(Acl.InheritanceType.LEAF_NODE);
    builder.setInheritFrom(null);

    Acl baseAgain = builder.build();

    assertFalse(base.equals(null));
    assertTrue(base.equals(base));
    assertFalse(base.equals(permitGroups));
    assertFalse(base.equals(denyGroups));
    assertFalse(base.equals(permitUsers));
    assertFalse(base.equals(denyUsers));
    assertFalse(base.equals(inheritFrom1));
    assertFalse(base.equals(inheritFrom2));
    assertFalse(base.equals(childOverrides));
    assertTrue(base.equals(baseAgain));

    assertFalse(inheritFrom1.equals(inheritFrom2));
    assertFalse(inheritFrom2.equals(childOverrides));
    assertTrue(childOverrides.equals(childOverridesAgain));

    assertEquals(base.hashCode(), base.hashCode());
    assertEquals(base.hashCode(), baseAgain.hashCode());

    builder.setPermitGroups(group("testing"));
    builder.setDenyGroups(group("testing"));
    builder.setPermitUsers(user("testing"));
    builder.setDenyUsers(user("testing"));
    Acl withCase = builder.build();
    Acl noCase = builder.setEverythingCaseInsensitive().build();
    Acl caseAgain = builder.setEverythingCaseSensitive().build();
    builder.setEverythingCaseInsensitive();
    Acl noCase2 = builder.setPermitUsers(user("TeSTiNg")).build();
    Acl noCase3 = builder.setPermitUsers(user("tEstInG")).build();
    assertEquals(withCase, caseAgain);
    assertEquals(noCase2, noCase);
    assertEquals(noCase2, noCase3);
    assertFalse(withCase.equals(noCase));
    assertFalse(caseAgain.equals(noCase));
    assertEquals(withCase.hashCode(), caseAgain.hashCode());
    /* It is conceivable that hashCode of different object matches.
       So this test could fail in the future; it is unlikely. */
    assertFalse(withCase.hashCode() == noCase.hashCode());
    assertFalse(caseAgain.hashCode() == noCase.hashCode());
  }

  @Test
  public void testCopy() {
    Set<GroupPrincipal> denyGroups = Collections.unmodifiableSet(
        new HashSet<GroupPrincipal>(group("dg1", "dg2", "g3")));
    Set<GroupPrincipal> permitGroups = Collections.unmodifiableSet(
        new HashSet<GroupPrincipal>(group("pg1", "pg2", "g3")));
    Set<UserPrincipal> denyUsers = Collections.unmodifiableSet(
        new HashSet<UserPrincipal>(user("du1", "du2", "du3", "u4")));
    Set<UserPrincipal> permitUsers = Collections.unmodifiableSet(
        new HashSet<UserPrincipal>(user("pu1", "pu2", "u4", "g3")));
    DocId inheritFrom = new DocId("something");
    Acl.InheritanceType inheritType = Acl.InheritanceType.CHILD_OVERRIDES;

    Acl aclOrig = new Acl.Builder()
        .setDenyGroups(denyGroups).setPermitGroups(permitGroups)
        .setDenyUsers(denyUsers).setPermitUsers(permitUsers)
        .setInheritFrom(inheritFrom).setInheritanceType(inheritType).build();
    Acl acl = new Acl.Builder(aclOrig).build();
    assertEquals(denyGroups, acl.getDenyGroups());
    assertEquals(permitGroups, acl.getPermitGroups());
    assertEquals(denyUsers, acl.getDenyUsers());
    assertEquals(permitUsers, acl.getPermitUsers());
    assertEquals(inheritFrom, acl.getInheritFrom());
    assertEquals(inheritType, acl.getInheritanceType());
    assertTrue(aclOrig.equals(acl));
    assertEquals(aclOrig.hashCode(), acl.hashCode());
  }

  @Test
  public void testInheritance() {
    Acl root = new Acl.Builder().setPermitUsers(user("user"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build();
    Acl middle = new Acl.Builder()
        .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
        .setInheritFrom(new DocId("parent")).build();
    Acl leaf = new Acl.Builder().setDenyUsers(user("user"))
        .setInheritFrom(new DocId("parent")).build();
    Collection<String> empty
        = Collections.unmodifiableList(Collections.<String>emptyList());

    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity("user"),
          Arrays.asList(root, middle, leaf)));
  }

  @Test
  public void testEmptyAclChain() {
    thrown.expect(IllegalArgumentException.class);
    Acl.isAuthorized(createIdentity("user"), Collections.<Acl>emptyList());
  }

  @Test
  public void testInvalidRootAclChain() {
    Acl acl = new Acl.Builder().setInheritFrom(new DocId("parent")).build();
    thrown.expect(IllegalArgumentException.class);
    Acl.isAuthorized(createIdentity("user"), Collections.singletonList(acl));
  }

  @Test
  public void testInvalidChildInChain() {
    Acl.Builder builder = new Acl.Builder();
    Acl root = builder.build();
    Acl child = builder.setInheritFrom(new DocId("parent")).build();
    Acl broken = root;

    thrown.expect(IllegalArgumentException.class);
    Acl.isAuthorized(createIdentity("user"),
                     Arrays.asList(root, child, broken));
  }

  @Test
  public void testIsAuthorizedBatchNullEntry() throws IOException {
    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), null);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("alice", "eng");
    thrown.expect(NullPointerException.class);
    Acl.isAuthorizedBatch(identity, Arrays.asList(new DocId("1")),
                          retriever);
  }

  @Test
  public void testIsAuthorizedBatchMissingCachedParent() throws IOException {
    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    DocId id = new DocId("1");
    DocId id2 = new DocId("2");
    acls.put(id, new Acl.Builder().setInheritFrom(id2)
        .setPermitUsers(user("user")).build());
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("user", "wrong group");
    assertEquals(AuthzStatus.INDETERMINATE, Acl.isAuthorizedBatch(identity,
        Arrays.asList(id, id2), retriever).get(id));
  }

  @Test
  public void testIsAuthorizedBatchOptimized() throws IOException {
    final DocId file = new DocId("file1");
    final DocId parent = new DocId("parent");

    final Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(file, new Acl.Builder().setInheritFrom(parent).build());
    acls.put(parent,
        new Acl.Builder().setPermitUsers(user("user"))
        .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES).build());

    Acl.BatchRetriever retriever = new Acl.BatchRetriever() {
      private boolean accessed = false;

      @Override
      public Map<DocId, Acl> retrieveAcls(Set<DocId> ids) {
        assertEquals(false, accessed);
        accessed = true;
        assertEquals(Arrays.asList(file), new ArrayList<DocId>(ids));
        return acls;
      }
    };
    AuthnIdentity identity = createIdentity("user");
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorizedBatch(identity,
        Arrays.asList(file), retriever).get(file));
  }

  @Test
  public void testIsAuthorizedBatchPoorOptimized() throws IOException {
    final DocId file1 = new DocId("file1");
    final DocId file2 = new DocId("file2");
    final DocId missingParent = new DocId("missing parent");
    final DocId parent = new DocId("parent");
    final DocId root = new DocId("root");

    final Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(file1, new Acl.Builder().setInheritFrom(parent).build());
    acls.put(file2, new Acl.Builder().setInheritFrom(missingParent)
        .setDenyUsers(user("unrelated")).build());
    acls.put(parent, new Acl.Builder().setInheritFrom(root)
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build());
    acls.put(root,
        new Acl.Builder().setPermitUsers(user("user"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build());

    Acl.BatchRetriever retriever = new Acl.BatchRetriever() {
      @Override
      public Map<DocId, Acl> retrieveAcls(Set<DocId> ids) {
        Set<DocId> case1 = new HashSet<DocId>(Arrays.asList(file1, file2));
        Set<DocId> case2
            = new HashSet<DocId>(Arrays.asList(missingParent, parent));
        Set<DocId> case3 = new HashSet<DocId>(Arrays.asList(root));
        Map<DocId, Acl> result = new HashMap<DocId, Acl>();
        if (case1.equals(ids)) {
          result.put(file1, acls.get(file1));
          result.put(file2, acls.get(file2));
        } else if (case2.equals(ids)) {
          // Don't provide missingParent, since it is missing.
          result.put(parent, acls.get(parent));
          // Be inefficient and re-provide some ACLs.
          result.put(file1, acls.get(file1));
        } else if (case3.equals(ids)) {
          // Provide results for something we didn't previously. This shouldn't
          // change our results.
          result.put(missingParent, acls.get(root));
          result.put(root, acls.get(root));
        } else {
          fail();
        }
        return result;
      }
    };

    Map<DocId, AuthzStatus> golden = new HashMap<DocId, AuthzStatus>();
    golden.put(file1, AuthzStatus.PERMIT);
    golden.put(file2, AuthzStatus.INDETERMINATE);
    golden = Collections.unmodifiableMap(golden);

    AuthnIdentity identity = createIdentity("user");
    assertEquals(golden, Acl.isAuthorizedBatch(identity,
        Arrays.asList(file1, file2), retriever));
  }

  @Test
  public void testEmptyIsAuthorized() {
    assertEquals(AuthzStatus.INDETERMINATE, Acl.isAuthorized(
        createIdentity("user"), Arrays.asList(Acl.EMPTY)));
  }

  @Test
  public void testCommonForm() {
    assertEquals("child-overrides",
                 Acl.InheritanceType.CHILD_OVERRIDES.getCommonForm());
  }

  private static String split(String usersOrGroups)[] {
    if ("".equals(usersOrGroups)) {
      return new String[0];
    }
    return usersOrGroups.split(",");
  }

  private static Acl buildAcl(String permitUsers, String permitGroups,
                              String denyUsers, String denyGroups) {
    return new Acl.Builder()
        .setPermitUsers(user(split(permitUsers)))
        .setPermitGroups(group(split(permitGroups)))
        .setDenyUsers(user(split(denyUsers)))
        .setDenyGroups(group(split(denyGroups)))
        .build();
  }

  private static Acl buildAcl(String permitUsers, String permitGroups,
                              String denyUsers, String denyGroups,
                              Acl.InheritanceType type) {
    return new Acl.Builder(
        buildAcl(permitUsers, permitGroups, denyUsers, denyGroups))
        .setInheritanceType(type).build();
  }

  private static Acl buildAcl(String permitUsers, String permitGroups,
                              String denyUsers, String denyGroups,
                              String inheritFrom) {
    return new Acl.Builder(
        buildAcl(permitUsers, permitGroups, denyUsers, denyGroups))
        .setInheritFrom(new DocId(inheritFrom)).build();
  }

  private static Acl buildAcl(String permitUsers, String permitGroups,
                              String denyUsers, String denyGroups,
                              Acl.InheritanceType type, String inheritFrom) {
    return new Acl.Builder(
        buildAcl(permitUsers, permitGroups, denyUsers, denyGroups))
        .setInheritanceType(type).setInheritFrom(new DocId(inheritFrom))
        .build();
  }

  private Acl buildAcl(Acl.InheritanceType type,
      AuthzStatus decision, String user, String parent) {
    String permittedUsers = "";
    String deniedUsers = "";

    switch (decision) {
      case PERMIT:
        permittedUsers = user;
        break;
      case DENY:
        deniedUsers = user;
        break;
      default:
        if (AuthzStatus.INDETERMINATE != decision) {
          throw new AssertionError("unexpected AuthzStatus: " + decision);
        }
    }
    if (null == parent || "".equals(parent.trim())) {
      return buildAcl(permittedUsers, "", deniedUsers, "", type);
    } else {
      return buildAcl(permittedUsers, "", deniedUsers, "", type, parent);
    }
  }

  private AuthzStatus callIsAuthorized(AuthnIdentity identity, DocId id,
      Acl.BatchRetriever retriever) throws IOException {
    return Acl.isAuthorizedBatch(identity, Arrays.asList(id), retriever)
        .get(id);
  }

  private void testRule(Acl.InheritanceType rule, AuthzStatus child,
                        AuthzStatus parent, AuthzStatus expectedResult) {
    assertEquals(expectedResult, rule.isAuthorized(
        new MockDecision(child), new MockDecision(parent)));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  @Test
  public void testCombineChildOverrides() {
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.PERMIT, AuthzStatus.PERMIT,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.PERMIT, AuthzStatus.DENY,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.PERMIT, AuthzStatus.INDETERMINATE,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.DENY, AuthzStatus.PERMIT,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.DENY, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.DENY, AuthzStatus.INDETERMINATE,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.INDETERMINATE, AuthzStatus.PERMIT,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.INDETERMINATE, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.CHILD_OVERRIDES,
             AuthzStatus.INDETERMINATE, AuthzStatus.INDETERMINATE,
             AuthzStatus.INDETERMINATE);
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  @Test
  public void testCombineParentOverrides() {
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.PERMIT, AuthzStatus.PERMIT,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.PERMIT, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.PERMIT, AuthzStatus.INDETERMINATE,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.DENY, AuthzStatus.PERMIT,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.DENY, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.DENY, AuthzStatus.INDETERMINATE,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.INDETERMINATE, AuthzStatus.PERMIT,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.INDETERMINATE, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.PARENT_OVERRIDES,
             AuthzStatus.INDETERMINATE, AuthzStatus.INDETERMINATE,
             AuthzStatus.INDETERMINATE);
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  @Test
  public void testCombineAndBothPermit() {
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.PERMIT, AuthzStatus.PERMIT,
             AuthzStatus.PERMIT);
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.PERMIT, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.PERMIT, AuthzStatus.INDETERMINATE,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.DENY, AuthzStatus.PERMIT,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.DENY, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.DENY, AuthzStatus.INDETERMINATE,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.INDETERMINATE, AuthzStatus.PERMIT,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.INDETERMINATE, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.AND_BOTH_PERMIT,
             AuthzStatus.INDETERMINATE, AuthzStatus.INDETERMINATE,
             AuthzStatus.DENY);
  }

  @Test
  public void testCombineLeafNode() {
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.PERMIT, AuthzStatus.PERMIT,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.PERMIT, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.PERMIT, AuthzStatus.INDETERMINATE,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.DENY, AuthzStatus.PERMIT,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.DENY, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.DENY, AuthzStatus.INDETERMINATE,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.INDETERMINATE, AuthzStatus.PERMIT,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.INDETERMINATE, AuthzStatus.DENY,
             AuthzStatus.DENY);
    testRule(Acl.InheritanceType.LEAF_NODE,
             AuthzStatus.INDETERMINATE, AuthzStatus.INDETERMINATE,
             AuthzStatus.DENY);
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  @Test
  public void testAuthorizeNoInheritance() throws IOException {
    // Test basic rule: DENY overrides PERMIT.
    Acl expectedAcl1 = buildAcl("", "eng,hr", "", "eng,finance,interns");
    Acl expectedAcl2 = buildAcl("alice,bob", "", "bob,charlie,dave", "");
    Acl expectedAcl3 = buildAcl("alice,bob", "", "", "");
    Acl expectedAcl4 = buildAcl("", "", "bob,charlie,dave", "");
    Acl emptyAcl = Acl.EMPTY;

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), expectedAcl1);
    acls.put(new DocId("2"), expectedAcl2);
    acls.put(new DocId("3"), expectedAcl3);
    acls.put(new DocId("4"), expectedAcl4);
    acls.put(new DocId("5"), emptyAcl);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("alice", "eng");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));

    identity = createIdentity("bob", "hr");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("1"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("2"), retriever));
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("3"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("4"), retriever));

    identity = createIdentity("alice", "hr");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("1"), retriever));
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("2"), retriever));
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("3"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("4"), retriever));

    identity = createIdentity("eve");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("3"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("4"), retriever));

    identity = createIdentity("no-importante", "qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("3"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("4"), retriever));

    // Docs with empty ACLs should return INDETERMINATE.
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(identity, new DocId("5"), retriever));
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(identity, new DocId("9"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // A Windows filesystem-like scenario:
  // a file has no ACL, inherits PERMIT from folder
  @Test
  public void testAuthorizeChildInheritsPermit() throws IOException {
    Acl file = buildAcl("", "", "", "", "Folder");
    Acl folder = buildAcl("adam", "", "", "",
        Acl.InheritanceType.CHILD_OVERRIDES);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Folder"), folder);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("adam");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // A Windows filesystem-like scenario:
  // a file has no ACL, inherits DENY from folder
  @Test
  public void testAuthorizeChildInheritsDeny() throws IOException {
    Acl file = buildAcl("", "", "", "", "Folder");
    Acl folder = buildAcl("", "", "adam", "",
        Acl.InheritanceType.CHILD_OVERRIDES);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Folder"), folder);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("adam");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // A Windows filesystem-like scenario:
  // a file has a PERMIT ACL, overrides folder's DENY
  @Test
  public void testAuthorizeChildOverridesDeny() throws IOException {
    Acl file = buildAcl("adam", "", "", "", "Folder");
    Acl folder = buildAcl("", "", "", "eng",
        Acl.InheritanceType.CHILD_OVERRIDES);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Folder"), folder);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("adam", "eng");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // A Windows filesystem-like scenario:
  // a file has a DENY ACL, overrides folder's PERMIT
  @Test
  public void testAuthorizeChildOverridesPermit() throws IOException {
    Acl file = buildAcl("", "", "eve", "", "Folder");
    Acl folder = buildAcl("", "qa", "", "",
        Acl.InheritanceType.CHILD_OVERRIDES);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Folder"), folder);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    // eve is denied...
    AuthnIdentity identity = createIdentity("eve", "qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));
    // although qa is in general permitted.
    identity = createIdentity("bob", "qa");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // A Windows share-like scenario:
  // - a share has a DENY ACL, which overrides any PERMITS underneath it.
  // - a share has a PERMIT ACL, which matches a PERMIT from underneath
  // - a share has a PERMIT ACL, but does not match INDETERMINATE from
  //   underneath.
  @Test
  public void testAuthorizeParentOverridesPermit() throws IOException {
    Acl file = buildAcl("bob", "qa", "", "", "Folder");
    Acl folder = buildAcl("adam", "", "", "",
        Acl.InheritanceType.CHILD_OVERRIDES, "Share");
    Acl share = buildAcl("charlie", "finance", "", "eng",
        Acl.InheritanceType.AND_BOTH_PERMIT);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Folder"), folder);
    acls.put(new DocId("Share"), share);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    // Permitted by Folder, but denied by Share
    AuthnIdentity identity = createIdentity("adam", "eng");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));

    // Permitted by File (via group), but denied by Share (by omission)
    identity = createIdentity("eve", "qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));

    // Permitted by File (via user), permitted by Share (via group)
    identity = createIdentity("bob", "finance");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("1"), retriever));

    // Permitted by Share (via user), but INDETERMINATE otherwise, so DENY
    identity = createIdentity("charlie");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // A somewhat contrived Windows share scenario:
  // a file has a DENY ACL, does not agree with share's PERMIT
  @Test
  public void testAuthorizeParentOverridesDeny() throws IOException {
    Acl file = buildAcl("", "", "eve", "", "Share");
    Acl share = buildAcl("", "qa", "", "", Acl.InheritanceType.AND_BOTH_PERMIT);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Share"), share);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("eve", "qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // A contrived Windows file system scenario:
  // neither file nor folder has any ACLs
  @Test
  public void testAuthorizeInheritanceNoAcl() throws IOException {
    Acl file = buildAcl("", "", "", "", "Folder");
    Acl folder = buildAcl("", "", "", "", Acl.InheritanceType.CHILD_OVERRIDES);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Folder"), folder);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("eve", "qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // A SharePoint example: a subsite grants PERMIT and website overrides a
  // PERMIT
  @Test
  public void testAuthorizeSP() throws IOException {
    Acl file = buildAcl("eve", "", "", "", "Subsite");
    Acl subsite = buildAcl("", "eng", "", "",
        Acl.InheritanceType.CHILD_OVERRIDES, "Website");
    Acl website = buildAcl("", "", "", "qa",
        Acl.InheritanceType.PARENT_OVERRIDES);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Subsite"), subsite);
    acls.put(new DocId("Website"), website);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("adam", "eng");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(identity, new DocId("1"), retriever));

    identity = createIdentity("eve", "qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // We hope the backend will not have cycles in inhertitance trees,
  // but Authorize has to be robust against the possibility.
  @Test
  public void testAuthorizeInheritanceCycle() throws IOException {
    Acl file = buildAcl("", "qa", "", "", "Folder");
    Acl folder = buildAcl("adam", "", "", "",
        Acl.InheritanceType.CHILD_OVERRIDES, "Share");
    Acl share = buildAcl("", "", "", "eng",
        Acl.InheritanceType.AND_BOTH_PERMIT, "File");

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    DocId fid = new DocId("File");
    acls.put(fid, file);
    acls.put(new DocId("Folder"), folder);
    acls.put(new DocId("Share"), share);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("adam", "eng");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(identity, fid, retriever));

    identity = createIdentity("eve", "qa");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(identity, fid, retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  @Test
  public void testAuthorizeInheritanceMissingAclNonEmpty() throws IOException {
    Acl file = buildAcl("", "group", "", "", "Folder");

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    // No "Folder" ACLs
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("user", "group");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  @Test
  public void testAuthorizeInheritanceMissingAclEmpty() throws IOException {
    Acl file = buildAcl("", "", "", "", "Folder");

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    // No "Folder" ACLs
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("user", "group");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  // Illegal chain: it contains a LEAF_NODE in the middle
  @Test
  public void testAuthorizeIllegalChain() throws IOException {
    Acl file = buildAcl("", "", "", "", "Folder1");
    Acl folder1 = buildAcl("adam", "", "", "", Acl.InheritanceType.LEAF_NODE,
        "Folder2");
    Acl folder2 = buildAcl("", "", "", "", Acl.InheritanceType.CHILD_OVERRIDES);

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    acls.put(new DocId("Folder1"), folder1);
    acls.put(new DocId("Folder2"), folder2);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    AuthnIdentity identity = createIdentity("adam");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(identity, new DocId("1"), retriever));
  }

  @Test
  public void testDefaultCaseSensitive() {
    assertTrue(new Acl.Builder().build().isEverythingCaseSensitive());
    assertTrue(!new Acl.Builder().build().isEverythingCaseInsensitive());
  }

  @Test
  public void testCanChangeCaseSensitivity() {
    assertFalse(new Acl.Builder().setEverythingCaseInsensitive()
        .build().isEverythingCaseSensitive());
    assertTrue(new Acl.Builder()
        .setEverythingCaseInsensitive().setEverythingCaseSensitive()
        .build().isEverythingCaseSensitive());
  }

  @Test
  public void testCaseInsensitiveUsage() {
    Acl acl = new Acl.Builder()
        .setPermitGroups(group("PermiTGroup"))
        .setDenyGroups(group("DenYGroup"))
        .setPermitUsers(user("PermiTUser", "BotHUser"))
        .setDenyUsers(user("DenYUser", "BotHUser"))
        .setEverythingCaseInsensitive()
        .build();
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permitUser")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("denyUser")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("unknownUser")));
  }

  @Test
  public void testDomainFormatUserCaseInsensitive() {
    Acl acl = new Acl.Builder()
        .setPermitUsers(user("PermiTUser@Domain", "BotHUser"))
        .setDenyUsers(user("DenYUser@Domain", "BotHUser"))
        .setEverythingCaseInsensitive()
        .build();
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permituser@domain")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("domain\\permituser")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("domain/permituser")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("denyuser@domain")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("domain\\denyuser")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("domain/denyuser")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("permituser@nb-domain")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("nb-domain\\permituser")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("nb-domain/permituser")));
  }

  @Test
  public void testDomainFormatGroupCaseInsensitive() {
    Acl acl = new Acl.Builder()
        .setPermitGroups(group("PermiTGroup@Domain"))
        .setDenyGroups(group("DenYGroup@Domain"))
        .setEverythingCaseInsensitive()
        .build();
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "permitgroup@domain")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "domain\\permitgroup")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "domain/permitgroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "denygroup@domain")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "domain\\denygroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "domain/denygroup")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "permitgroup@nb-domain")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "nb-domain\\permitgroup")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "nb-domain/permitgroup")));
  }

  @Test
  public void testDomainFormatUserCaseSensitive() {
    Acl acl = new Acl.Builder()
        .setPermitUsers(user("PermiTUser@Domain", "BotHUser"))
        .setDenyUsers(user("DenYUser@Domain", "BotHUser"))
        .setEverythingCaseSensitive()
        .build();
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("PermiTUser@Domain")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("Domain\\PermiTUser")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("Domain/PermiTUser")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("DenYUser@Domain")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("Domain\\DenYUser")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("Domain/DenYUser")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("permituser@domain")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("domain\\permituser")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("domain/permituser")));
  }

  @Test
  public void testDomainFormatGroupCaseSensitive() {
    Acl acl = new Acl.Builder()
        .setPermitGroups(group("PermiTGroup@Domain"))
        .setDenyGroups(group("DenYGroup@Domain"))
        .setEverythingCaseSensitive()
        .build();
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "PermiTGroup@Domain")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "Domain\\PermiTGroup")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "Domain/PermiTGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "DenYGroup@Domain")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "Domain\\DenYGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "Domain/DenYGroup")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "permitgroup@domain")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "domain\\permitgroup")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", "domain/permitgroup")));
  }

  @Test
  public void testNullGetGroups() {
    Acl acl = new Acl.Builder()
        .setPermitUsers(user("permituser@domain"))
        .setDenyUsers(user("denyuser@domain"))
        .setPermitGroups(group("PermitGroup@Domain"))
        .setDenyGroups(group("DenyGroup@Domain"))
        .build();
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal(
        createIdentity("denyuser@domain", (List<String>) null)));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal(
        createIdentity("permituser@domain", (List<String>) null)));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal(
        createIdentity("someuser@domain", (List<String>) null)));
  }

  private AuthnIdentity createIdentity(String username, String... groups) {
    return createIdentity(username, Arrays.asList(groups));
  }

  private AuthnIdentity createIdentity(final String username,
      List<String> groups) {
    final UserPrincipal user = new UserPrincipal(username);
    final Set<GroupPrincipal> groupSet = (groups == null) ? null
        : Collections.unmodifiableSet(
              new TreeSet<GroupPrincipal>(GroupPrincipal.makeSet(groups)));
    return new AuthnIdentity() {
      @Override
      public UserPrincipal getUser() {
        return user;
      }

      @Override
      public String getPassword() {
        return null;
      }

      @Override
      public Set<GroupPrincipal> getGroups() {
        return groupSet;
      }
    };
  }

  private static class MockBatchRetriever implements Acl.BatchRetriever {
    private Map<DocId, Acl> acls;

    public MockBatchRetriever(Map<DocId, Acl> acls) {
      this.acls = acls;
    }

    @Override
    public Map<DocId, Acl> retrieveAcls(Set<DocId> ids) {
      Map<DocId, Acl> result = new HashMap<DocId, Acl>(ids.size() * 2);
      for (DocId docId : ids) {
        if (acls.containsKey(docId)) {
          result.put(docId, acls.get(docId));
        }
      }
      return Collections.unmodifiableMap(result);
    }
  }

  private static class MockDecision extends Acl.Decision {
    private final AuthzStatus status;

    public MockDecision(AuthzStatus status) {
      this.status = status;
    }

    @Override
    protected AuthzStatus computeDecision() {
      return status;
    }
  }

  @Test
  public void aclChainTest1() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest2() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest3() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest4() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest5() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest6() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest7() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest8() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest9() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest10() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest11() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest12() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest13() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest14() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest15() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest16() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest17() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest18() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest19() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest20() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest21() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest22() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest23() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest24() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest25() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest26() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest27() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest28() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest29() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest30() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest31() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest32() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest33() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest34() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest35() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest36() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest37() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest38() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest39() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest40() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest41() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest42() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest43() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest44() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest45() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest46() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest47() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest48() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest49() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest50() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest51() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest52() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest53() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest54() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest55() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest56() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest57() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest58() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest59() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest60() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest61() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest62() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest63() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest64() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest65() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest66() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest67() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest68() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest69() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest70() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest71() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest72() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest73() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest74() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest75() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest76() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest77() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest78() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest79() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest80() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest81() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest82() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest83() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest84() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest85() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest86() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest87() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest88() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest89() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest90() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest91() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest92() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest93() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest94() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest95() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest96() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest97() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest98() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest99() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest100() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest101() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest102() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest103() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest104() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest105() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest106() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest107() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest108() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest109() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest110() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest111() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest112() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest113() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest114() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest115() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest116() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest117() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest118() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest119() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest120() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest121() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest122() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest123() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest124() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest125() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest126() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest127() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest128() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest129() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest130() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest131() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest132() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest133() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest134() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest135() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest136() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest137() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest138() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest139() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest140() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest141() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest142() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest143() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest144() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest145() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest146() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest147() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest148() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest149() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest150() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest151() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest152() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest153() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest154() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest155() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest156() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest157() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest158() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest159() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest160() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest161() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest162() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest163() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest164() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest165() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest166() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest167() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest168() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest169() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest170() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest171() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest172() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest173() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest174() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest175() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest176() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest177() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest178() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest179() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest180() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest181() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest182() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest183() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest184() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest185() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest186() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest187() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest188() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest189() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest190() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest191() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest192() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest193() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest194() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest195() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest196() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest197() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest198() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest199() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest200() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest201() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest202() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest203() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest204() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest205() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest206() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest207() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest208() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest209() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest210() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest211() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest212() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest213() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest214() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest215() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest216() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest217() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest218() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest219() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest220() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest221() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest222() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest223() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest224() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest225() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.CHILD_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest226() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest227() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest228() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest229() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest230() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest231() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest232() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest233() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest234() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.PARENT_OVERRIDES,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest235() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest236() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest237() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.PERMIT,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest238() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest239() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest240() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.DENY,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest241() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.PERMIT,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest242() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.DENY,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }

  @Test
  public void aclChainTest243() throws IOException {
    String user = "admin";
    Acl file = buildAcl(
        Acl.InheritanceType.LEAF_NODE,
        AuthzStatus.INDETERMINATE,
        user, "subsite");
    Acl subsite = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, "website");
    Acl website = buildAcl(
        Acl.InheritanceType.AND_BOTH_PERMIT,
        AuthzStatus.INDETERMINATE,
        user, null);
    assertEquals(AuthzStatus.DENY, Acl.isAuthorized(createIdentity(user),
        Arrays.asList(website, subsite, file)));
  }
}
