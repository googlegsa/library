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

package adaptorlib;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.*;

/**
 * Test cases for {@link Acl}.
 */
public class AclTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNonHierarchialUsage() {
    Acl acl = new Acl.Builder()
        .setPermitGroups(Collections.singletonList("permitGroup"))
        .setDenyGroups(Collections.singletonList("denyGroup"))
        .setPermitUsers(Arrays.asList("permitUser", "bothUser"))
        .setDenyUsers(Arrays.asList("denyUser", "bothUser"))
        .build();
    // Test all main combinations:
    // (user permitted, denied, or indeterminate) x (group permitted, denied,
    //   indeterminate, no groups)
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal("permitUser",
        Collections.singletonList("permitGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal("permitUser",
        Collections.singletonList("denyGroup")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal("permitUser",
        Collections.singletonList("unknownGroup")));
    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal("permitUser",
        Collections.<String>emptyList()));

    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal("denyUser",
        Collections.singletonList("permitGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal("denyUser",
        Collections.singletonList("denyGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal("denyUser",
        Collections.singletonList("unknownGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal("denyUser",
        Collections.<String>emptyList()));

    assertEquals(AuthzStatus.PERMIT, acl.isAuthorizedLocal("unknownUser",
        Collections.singletonList("permitGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal("unknownUser",
        Collections.singletonList("denyGroup")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal("unknownUser",
        Collections.singletonList("unknownGroup")));
    assertEquals(AuthzStatus.INDETERMINATE, acl.isAuthorizedLocal("unknownUser",
        Collections.<String>emptyList()));

    // Make sure that deny wins.
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal("permitUser",
        Arrays.asList("denyGroup", "permitGroup")));
    assertEquals(AuthzStatus.DENY, acl.isAuthorizedLocal("bothUser",
        Arrays.asList("permitGroup")));
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
    final Set<String> goldenDenyGroups = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("dg1", "dg2", "g3")));
    final Set<String> goldenPermitGroups = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("pg1", "pg2", "g3")));
    final Set<String> goldenDenyUsers = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("du1", "du2", "du3", "u4")));
    final Set<String> goldenPermitUsers = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("pu1", "pu2", "u4", "g3")));
    final DocId goldenInheritFrom = new DocId("something");
    final Acl.InheritanceType goldenInheritType
        = Acl.InheritanceType.CHILD_OVERRIDES;

    Set<String> denyGroups = new HashSet<String>(goldenDenyGroups);
    Set<String> permitGroups = new HashSet<String>(goldenPermitGroups);
    Set<String> denyUsers = new HashSet<String>(goldenDenyUsers);
    Set<String> permitUsers = new HashSet<String>(goldenPermitUsers);
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
    builder.setDenyGroups(Collections.<String>emptySet());
    builder.setPermitGroups(Collections.<String>emptySet());
    builder.setDenyUsers(Collections.<String>emptySet());
    builder.setPermitUsers(Collections.<String>emptySet());
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
  public void testDenyGroupsImmutability() {
    Acl acl = new Acl.Builder().setDenyGroups(
        new HashSet<String>(Arrays.asList("item"))).build();
    thrown.expect(UnsupportedOperationException.class);
    acl.getDenyGroups().clear();
  }

  @Test
  public void testPermitGroupsImmutability() {
    Acl acl = new Acl.Builder().setPermitGroups(
        new HashSet<String>(Arrays.asList("item"))).build();
    thrown.expect(UnsupportedOperationException.class);
    acl.getPermitGroups().clear();
  }

  @Test
  public void testDenyUsersImmutability() {
    Acl acl = new Acl.Builder().setDenyUsers(
        new HashSet<String>(Arrays.asList("item"))).build();
    thrown.expect(UnsupportedOperationException.class);
    acl.getDenyUsers().clear();
  }

  @Test
  public void testPermitUsersImmutability() {
    Acl acl = new Acl.Builder().setPermitUsers(
        new HashSet<String>(Arrays.asList("item"))).build();
    thrown.expect(UnsupportedOperationException.class);
    acl.getPermitUsers().clear();
  }

  @Test
  public void testNullUser() {
    Acl.Builder builder = new Acl.Builder();
    thrown.expect(NullPointerException.class);
    builder.setPermitUsers(Arrays.asList((String) null));
  }

  @Test
  public void testEmptyGroup() {
    Acl.Builder builder = new Acl.Builder();
    thrown.expect(IllegalArgumentException.class);
    builder.setDenyGroups(Arrays.asList(""));
  }

  @Test
  public void testWhitespaceSurroundingUserBefore() {
    Acl.Builder builder = new Acl.Builder();
    thrown.expect(IllegalArgumentException.class);
    builder.setDenyUsers(Arrays.asList(" test"));
  }

  @Test
  public void testWhitespaceSurroundingUserAfter() {
    Acl.Builder builder = new Acl.Builder();
    thrown.expect(IllegalArgumentException.class);
    builder.setDenyUsers(Arrays.asList("test\t"));
  }

  @Test
  public void testEquals() {
    Acl.Builder builder = new Acl.Builder();
    Acl base = builder.build();

    builder.setPermitGroups(Collections.singleton("testing"));
    Acl permitGroups = builder.build();
    builder.setPermitGroups(Collections.<String>emptySet());

    builder.setDenyGroups(Collections.singleton("testing"));
    Acl denyGroups = builder.build();
    builder.setDenyGroups(Collections.<String>emptySet());

    builder.setPermitUsers(Collections.singleton("testing"));
    Acl permitUsers = builder.build();
    builder.setPermitUsers(Collections.<String>emptySet());

    builder.setDenyUsers(Collections.singleton("testing"));
    Acl denyUsers = builder.build();
    builder.setDenyUsers(Collections.<String>emptySet());

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
  }

  @Test
  public void testCopy() {
    Set<String> denyGroups = Collections.unmodifiableSet(new HashSet<String>(
        Arrays.asList("dg1", "dg2", "g3")));
    Set<String> permitGroups = Collections.unmodifiableSet(new HashSet<String>(
        Arrays.asList("pg1", "pg2", "g3")));
    Set<String> denyUsers = Collections.unmodifiableSet(new HashSet<String>(
        Arrays.asList("du1", "du2", "du3", "u4")));
    Set<String> permitUsers = Collections.unmodifiableSet(new HashSet<String>(
        Arrays.asList("pu1", "pu2", "u4", "g3")));
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
    Acl root = new Acl.Builder().setPermitUsers(Arrays.asList("user"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build();
    Acl middle = new Acl.Builder()
        .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
        .setInheritFrom(new DocId("parent")).build();
    Acl leaf = new Acl.Builder().setDenyUsers(Arrays.asList("user"))
        .setInheritFrom(new DocId("parent")).build();
    Collection<String> empty
        = Collections.unmodifiableList(Collections.<String>emptyList());

    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized("user", empty,
          Arrays.asList(root, middle, leaf), false));
  }

  @Test
  public void testEmptyAclChain() {
    thrown.expect(IllegalArgumentException.class);
    Acl.isAuthorized("user", Collections.<String>emptyList(),
                     Collections.<Acl>emptyList(), false);
  }

  @Test
  public void testInvalidRootAclChain() {
    Acl acl = new Acl.Builder().setInheritFrom(new DocId("parent")).build();
    thrown.expect(IllegalArgumentException.class);
    Acl.isAuthorized("user", Collections.<String>emptyList(),
                     Collections.singletonList(acl), false);
  }

  @Test
  public void testInvalidChildInChain() {
    Acl.Builder builder = new Acl.Builder();
    Acl root = builder.build();
    Acl child = builder.setInheritFrom(new DocId("parent")).build();
    Acl broken = root;

    thrown.expect(IllegalArgumentException.class);
    Acl.isAuthorized("user", Collections.<String>emptyList(),
                     Arrays.asList(root, child, broken), false);
  }

  @Test
  public void testIsAuthorizedBatchNullEntry() throws IOException {
    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), null);
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    String user = "alice";
    List<String> groups = Arrays.asList("eng");
    thrown.expect(NullPointerException.class);
    Acl.isAuthorizedBatch(user, groups, Arrays.asList(new DocId("1")),
                          retriever, false);
  }

  @Test
  public void testIsAuthorizedBatchMissingCachedParent() throws IOException {
    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    DocId id = new DocId("1");
    DocId id2 = new DocId("2");
    acls.put(id, new Acl.Builder().setInheritFrom(id2)
             .setPermitUsers(Arrays.asList("group")).build());
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    String user = "user";
    List<String> groups = Arrays.asList("wrong group");
    assertEquals(AuthzStatus.INDETERMINATE, Acl.isAuthorizedBatch(user, groups,
        Arrays.asList(id, id2), retriever, true).get(id));
  }

  @Test
  public void testIsAuthorizedBatchOptimized() throws IOException {
    final DocId file = new DocId("file1");
    final DocId parent = new DocId("parent");

    final Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(file, new Acl.Builder().setInheritFrom(parent).build());
    acls.put(parent,
        new Acl.Builder().setPermitUsers(Arrays.asList("user"))
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
    String user = "user";
    List<String> groups = Collections.emptyList();
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorizedBatch(user, groups,
        Arrays.asList(file), retriever, false).get(file));
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
        .setDenyUsers(Arrays.asList("unrelated")).build());
    acls.put(parent, new Acl.Builder().setInheritFrom(root)
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build());
    acls.put(root,
        new Acl.Builder().setPermitUsers(Arrays.asList("user"))
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

    String user = "user";
    List<String> groups = Collections.emptyList();
    assertEquals(golden, Acl.isAuthorizedBatch(user, groups,
        Arrays.asList(file1, file2), retriever, false));
  }

  @Test
  public void testEmptyImpliesPublic() {
    assertEquals(AuthzStatus.PERMIT, Acl.isAuthorized("user",
        Collections.<String>emptyList(), Arrays.asList(Acl.EMPTY), true));
    assertEquals(AuthzStatus.INDETERMINATE, Acl.isAuthorized("user",
        Collections.<String>emptyList(), Arrays.asList(Acl.EMPTY), false));
  }

  @Test
  public void testCommonForm() {
    assertEquals("child-overrides",
                 Acl.InheritanceType.CHILD_OVERRIDES.getCommonForm());
  }

  private static List<String> split(String usersOrGroups) {
    if ("".equals(usersOrGroups)) {
      return Collections.emptyList();
    }
    return Arrays.asList(usersOrGroups.split(","));
  }

  private static Acl buildAcl(String permitUsers, String permitGroups,
                              String denyUsers, String denyGroups) {
    return new Acl.Builder()
        .setPermitUsers(split(permitUsers)).setPermitGroups(split(permitGroups))
        .setDenyUsers(split(denyUsers)).setDenyGroups(split(denyGroups))
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

  private AuthzStatus callIsAuthorized(String userIdentifier,
      Collection<String> groups, DocId id, Acl.BatchRetriever retriever)
      throws IOException {
    return Acl.isAuthorizedBatch(userIdentifier, groups, Arrays.asList(id),
                                 retriever, false).get(id);
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

    String user = "alice";
    List<String> groups = Arrays.asList("eng");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));

    user = "bob";
    groups = Arrays.asList("hr");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("2"), retriever));
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("3"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("4"), retriever));

    user = "alice";
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("2"), retriever));
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("3"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("4"), retriever));

    user = "eve";
    groups = Collections.emptyList();
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("3"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("4"), retriever));

    user = "";
    groups = Arrays.asList("qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("3"), retriever));
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("4"), retriever));

    // Docs with empty ACLs should return INDETERMINATE.
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(user, groups, new DocId("5"), retriever));
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(user, groups, new DocId("9"), retriever));
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

    String user = "adam";
    List<String> groups = Collections.emptyList();
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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

    String user = "adam";
    List<String> groups = Collections.emptyList();
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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

    String user = "adam";
    List<String> groups = Arrays.asList("eng");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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
    String user = "eve";
    List<String> groups = Arrays.asList("qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
    // although qa is in general permitted.
    user = "bob";
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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
    String user = "adam";
    List<String> groups = Arrays.asList("eng");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));

    // Permitted by File (via group), but denied by Share (by omission)
    user = "eve";
    groups = Arrays.asList("qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));

    // Permitted by File (via user), permitted by Share (via group)
    user = "bob";
    groups = Arrays.asList("finance");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("1"), retriever));

    // Permitted by Share (via user), but INDETERMINATE otherwise, so DENY
    user = "charlie";
    groups = Collections.emptyList();
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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

    String user = "eve";
    List<String> groups = Arrays.asList("qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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

    String user = "eve";
    List<String> groups = Arrays.asList("qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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

    String user = "adam";
    List<String> groups = Arrays.asList("eng");
    assertEquals(AuthzStatus.PERMIT,
        callIsAuthorized(user, groups, new DocId("1"), retriever));

    user = "eve";
    groups = Arrays.asList("qa");
    assertEquals(AuthzStatus.DENY,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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

    String user = "adam";
    List<String> groups = Arrays.asList("eng");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(user, groups, fid, retriever));

    user = "eve";
    groups = Arrays.asList("qa");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(user, groups, fid, retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  @Test
  public void testAuthorizeInheritanceMissingAclNonEmpty() throws IOException {
    Acl file = buildAcl("", "group", "", "", "Folder");

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    // No "Folder" ACLs
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    String user = "user";
    List<String> groups = Arrays.asList("group");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
  }

  // Port of test from GSA with similar name. Should be kept in sync.
  @Test
  public void testAuthorizeInheritanceMissingAclEmpty() throws IOException {
    Acl file = buildAcl("", "", "", "", "Folder");

    Map<DocId, Acl> acls = new HashMap<DocId, Acl>();
    acls.put(new DocId("1"), file);
    // No "Folder" ACLs
    Acl.BatchRetriever retriever = new MockBatchRetriever(acls);

    String user = "user";
    List<String> groups = Arrays.asList("group");
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(user, groups, new DocId("1"), retriever));
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

    String user = "adam";
    List<String> groups = Collections.emptyList();
    assertEquals(AuthzStatus.INDETERMINATE,
        callIsAuthorized(user, groups, new DocId("1"), retriever));

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
}
