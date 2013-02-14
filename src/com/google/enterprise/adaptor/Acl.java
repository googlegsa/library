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

import java.io.IOException;
import java.util.*;
import java.util.logging.*;

/**
 * Immutable access control list. For description of the semantics of the
 * various fields, see {@link #isAuthorizedLocal isAuthorizedLocal} and
 * {@link #isAuthorized isAuthorized}. Users and groups must not be {@code
 * null}, {@code ""}, or have surrounding whitespace. These values are
 * disallowed to prevent confusion since {@code null} doesn't make sense, {@code
 * ""} would be ignored by the GSA, and surrounding whitespace is automatically
 * trimmed by the GSA.
 */
public class Acl {
  /**
   * Empty convenience instance with all defaults used.
   *
   * @see Builder#Acl.Builder()
   */
  public static final Acl EMPTY = new Acl.Builder().build();
  /**
   * An almost-empty ACL that can be used instead of {@link #EMPTY} when sending
   * ACLs to the GSA. This allows the GSA to distinguish between an empty ACL
   * and a non-existant ACL.
   */
  static final Acl FAKE_EMPTY = new Acl.Builder()
      .setDenyUsers(Arrays.asList("google:fakeUserToPreventMissingAcl"))
      .build();

  private static final Logger log = Logger.getLogger(Acl.class.getName());

  private final Set<String> permitGroups;
  private final Set<String> denyGroups;
  private final Set<String> permitUsers;
  private final Set<String> denyUsers;
  private final DocId inheritFrom;
  private final InheritanceType inheritType;

  private Acl(Set<String> permitGroups, Set<String> denyGroups,
              Set<String> permitUsers, Set<String> denyUsers, DocId inheritFrom,
              InheritanceType inheritType) {
    this.permitGroups = permitGroups;
    this.denyGroups = denyGroups;
    this.permitUsers = permitUsers;
    this.denyUsers = denyUsers;
    this.inheritFrom = inheritFrom;
    this.inheritType = inheritType;
  }

  /**
   * Returns immutable set of permitted groups.
   */
  public Set<String> getPermitGroups() {
    return permitGroups;
  }

  /**
   * Returns immutable set of denied groups.
   */
  public Set<String> getDenyGroups() {
    return denyGroups;
  }

  /**
   * Returns immutable set of permitted users.
   */
  public Set<String> getPermitUsers() {
    return permitUsers;
  }

  /**
   * Returns immutable set of denied users.
   */
  public Set<String> getDenyUsers() {
    return denyUsers;
  }

  /**
   * Returns {@code DocId} these ACLs are inherited from. This is also known as
   * the "parent's" ACLs. Note that the parent's {@code InheritanceType}
   * determines how to combine results with this ACL.
   *
   * @see #getInheritanceType
   */
  public DocId getInheritFrom() {
    return inheritFrom;
  }

  /**
   * Returns the inheritance type used to combine authz decisions of these ACLs
   * with its <em>child</em>. The inheritance type applies to the interaction
   * between this ACL and any <em>children</em> it has.
   *
   * @see #getInheritFrom
   */
  public InheritanceType getInheritanceType() {
    return inheritType;
  }

  /**
   * Determine if the provided {@code userIdentifier} belonging to {@code
   * groups} is authorized, ignoring inheritance. Deny trumps permit,
   * independent of how specific the rule is. So if a user is in permitUsers and
   * one of the user's groups is in denyGroups, that user will be denied. If a
   * user and his groups are unspecified in the ACL, then the response is
   * indeterminate.
   */
  public AuthzStatus isAuthorizedLocal(AuthnIdentity userIdentity) {
    String userIdentifier = userIdentity.getUsername();
    Collection<String> groups = userIdentity.getGroups();
    Set<String> commonGroups = new HashSet<String>(denyGroups);
    commonGroups.retainAll(groups);
    if (denyUsers.contains(userIdentifier) || !commonGroups.isEmpty()) {
      return AuthzStatus.DENY;
    }

    commonGroups.clear();
    commonGroups.addAll(permitGroups);
    commonGroups.retainAll(groups);
    if (permitUsers.contains(userIdentifier) || !commonGroups.isEmpty()) {
      return AuthzStatus.PERMIT;
    }

    return AuthzStatus.INDETERMINATE;
  }

  /**
   * Determine if the provided {@code userIdentifier} belonging to {@code
   * groups} is authorized for the provided {@code aclChain}. The chain should
   * be in order of root to leaf; that means that the particular file or folder
   * you are checking for authz will be at the end of the chain.
   *
   * <p>If you have an ACL and wish to determine if a user is authorized, you
   * should manually generate an aclChain by recursively retrieving the ACLs of
   * the {@code inheritFrom} {@link DocId}. The ACL you started with should be
   * at the end of the chain. Alternatively, you can use {@link
   * #isAuthorizedBatch isAuthorizedBatch()}.
   *
   * <p>If the entire chain has empty permit/deny sets, then the result is
   * {@link AuthzStatus#INDETERMINATE}.
   *
   * <p>The result of the entire chain is the non-local decision of the root.
   * The non-local decision of any entry in the chain is the local decision of
   * that entry (as calculated with {@link #isAuthorizedLocal
   * isAuthorizedLocal()}) combined with the non-local decision of the next
   * entry in the chain via the {@code InheritanceType} of the original entry.
   * To repeat, the non-local decision of an entry is that entry's local
   * decision combined using its {@code InheritanceType} with its child's
   * non-local decision (which is recursive). Thus, if the root's inheritance
   * type is {@link InheritanceType#PARENT_OVERRIDES} and its local decision is
   * {@link AuthzStatus#DENY}, then independent of any decendant's local
   * decision, the decision of the chain will be {@code DENY}.
   *
   * <p>It should also be noted that the leaf's inheritance type does not matter
   * and is ignored.
   *
   * <p>It is very important to note that a completely empty ACL (one that has
   * all defaults) is equivalent to having no ACLs. The GSA considers content
   * from the Adaptor as public unless it provides an ACL. Thus, empty ACLs
   * cause a document to become public and the GSA does not use ACLs when
   * considering public documents (and all results are PERMIT). However, for
   * non-Adaptor situations, you can get a document to be private and have no
   * ACLs. In these situations the ACLs are checked, but the result is
   * INDETERMINATE and different authz checks must be made.
   *
   * @param userIdentity identity containing the user's username and all the
   *     groups the user belongs to
   * @param aclChain ordered list of ACLs from root to leaf
   * @throws IllegalArgumentException if the chain is empty, the first element
   *     of the chain's {@code getInheritFrom() != null}, or if any element but
   *     the first has {@code getInheritFrom() == null}.
   * @see #isAuthorizedLocal
   * @see InheritanceType
   */
  public static AuthzStatus isAuthorized(AuthnIdentity userIdentity,
                                         List<Acl> aclChain) {
    // Check for completely broken chains. Users of the API should be aware
    // enough to easily prevent these from happening. These also don't directly
    // relate to a case on the GSA because the GSA is working more on the
    // isAuthorizedRecurse level.
    if (aclChain.size() < 1) {
      throw new IllegalArgumentException(
          "aclChain must contain at least one ACL");
    }
    if (aclChain.get(0).getInheritFrom() != null) {
      throw new IllegalArgumentException(
          "Chain must start at the root, which must not have an inheritFrom");
    }
    for (int i = 1; i < aclChain.size(); i++) {
      if (aclChain.get(i).getInheritFrom() == null) {
        throw new IllegalArgumentException(
            "Each ACL in the chain except the first should have an "
            + "inheritFrom");
      }
    }

    // Check for broken chain constructions. These don't throw an exception to
    // 1) match the GSA's identical handling of these situations and 2) because
    // we don't want to throw an exception if the caller can't easily prevent
    // it from ever occuring.
    if (aclChain.size() == 1) {
      Acl acl = aclChain.get(0);
      if (acl.equals(EMPTY)) {
        log.log(Level.FINE, "Chain only has one ACL and it is empty. This "
            + "implies 'no ACLs.'");
        return AuthzStatus.INDETERMINATE;
      }
    }
    for (int i = 0; i < aclChain.size() - 1; i++) {
      if (aclChain.get(i).getInheritanceType() == InheritanceType.LEAF_NODE) {
        log.log(Level.WARNING, "Only the last ACL in a chain can have the "
            + "inheritance type LEAF");
        return AuthzStatus.INDETERMINATE;
      }
    }
    AuthzStatus result = isAuthorizedRecurse(userIdentity, aclChain);
    return (result == AuthzStatus.INDETERMINATE) ? AuthzStatus.DENY : result;
  }

  private static AuthzStatus isAuthorizedRecurse(
      final AuthnIdentity userIdentity, final List<Acl> aclChain) {
    if (aclChain.size() == 1) {
      return aclChain.get(0).isAuthorizedLocal(userIdentity);
    }
    Decision parentDecision = new Decision() {
      @Override
      protected AuthzStatus computeDecision() {
        return aclChain.get(0).isAuthorizedLocal(userIdentity);
      }
    };
    Decision childDecision = new Decision() {
      @Override
      protected AuthzStatus computeDecision() {
        // Recurse.
        return isAuthorizedRecurse(userIdentity,
            aclChain.subList(1, aclChain.size()));
      }
    };
    return aclChain.get(0).getInheritanceType()
        .isAuthorized(childDecision, parentDecision);
  }

  /**
   * Check authz for many DocIds at once. This will only fetch ACL information
   * for a DocId once, even when considering inheritFrom. It will then create
   * the appropriate chains and call {@link #isAuthorized isAuthorized()}.
   *
   * <p>If there is an inheritance cycle, an ACL for a DocId in {@code ids} was
   * not returned by {@code retriever} when requested, or an inherited ACL was
   * not returned by {@code retriever} when requested, its response will be
   * {@link AuthzStatus#INDETERMINATE} for that DocId.
   *
   * @param userIdentity identity containing the user's username and all the
   *     groups the user belongs to
   * @param ids collection of DocIds that need authz performed
   * @param retriever object to use to obtain an ACL for a given DocId
   * @throws IOException if the retriever throws an IOException
   */
  public static Map<DocId, AuthzStatus> isAuthorizedBatch(
      AuthnIdentity userIdentity, Collection<DocId> ids,
      BatchRetriever retriever) throws IOException {
    Map<DocId, Acl> acls = retrieveNecessaryAcls(ids, retriever);
    Map<DocId, AuthzStatus> results
        = new HashMap<DocId, AuthzStatus>(ids.size() * 2);
    for (DocId docId : ids) {
      List<Acl> chain = createChain(docId, acls);
      AuthzStatus result;
      if (chain == null) {
        // There was a cycle or other problem generating the chain.
        result = AuthzStatus.INDETERMINATE;
      } else {
        result = isAuthorized(userIdentity, chain);
      }
      results.put(docId, result);
    }
    return Collections.unmodifiableMap(results);
  }

  private static Map<DocId, Acl> retrieveNecessaryAcls(Collection<DocId> ids,
        BatchRetriever retriever) throws IOException {
    Map<DocId, Acl> acls = new HashMap<DocId, Acl>(ids.size() * 2);
    Set<DocId> missingAcls = new HashSet<DocId>();
    Set<DocId> pendingRetrieval = new HashSet<DocId>(ids);
    Set<Acl> checkedAcl = new HashSet<Acl>(ids.size() * 2);
    Set<Acl> toProcess = new HashSet<Acl>(ids.size() * 2);
    while (!pendingRetrieval.isEmpty()) {
      Map<DocId, Acl> returned = retriever.retrieveAcls(pendingRetrieval);
      toProcess.clear();
      for (Map.Entry<DocId, Acl> me : returned.entrySet()) {
        if (me.getValue() == null) {
          throw new NullPointerException(
              "BatchRetriever returned null for a DocId");
        }
        DocId key = me.getKey();
        if (acls.containsKey(key) || missingAcls.contains(key)) {
          // Don't replace previous results since we have already checked them.
          continue;
        }
        acls.put(key, me.getValue());
        // If we requested this ACL, follow its inheritance.
        if (pendingRetrieval.contains(key)) {
          toProcess.add(me.getValue());
        }
      }
      // Compute ACLs that we requested, but did not receive.
      pendingRetrieval.removeAll(returned.keySet());
      missingAcls.addAll(pendingRetrieval);

      pendingRetrieval.clear();

      for (Acl acl : toProcess) {
        // Follow the inheritance chain until it terminates.
        while (true) {
          if (checkedAcl.contains(acl)) {
            // Already processed.
            break;
          }
          checkedAcl.add(acl);

          DocId parent = acl.getInheritFrom();
          if (parent == null) {
            // Inheritance chain terminated; everything looks good.
            break;
          } else if (missingAcls.contains(parent)) {
            // Failed to retrieve parent, so give up.
            break;
          } else if (acls.containsKey(parent)) {
            // Already have the parent ACLs, so check parent.
            acl = acls.get(parent);
          } else {
            // Request parent ACLs.
            pendingRetrieval.add(parent);
            break;
          }
        }
      }
    }
    return acls;
  }

  private static List<Acl> createChain(DocId docId, Map<DocId, Acl> acls) {
    List<Acl> chain = new LinkedList<Acl>();
    Set<Acl> used = new HashSet<Acl>();
    DocId cur = docId;
    while (cur != null) {
      Acl acl = acls.get(cur);
      if (acl == null) {
        if (chain.isEmpty()) {
          // The GSA turns this into a chain containing only an empty ACL (which
          // eventually becomes indeterminate), but we want this to be
          // indeterminate immediately because we do not have public/private
          // flags for documents and we don't want to accidentally cause a
          // document to become public.
          log.log(Level.FINE, "Document does not seem to use ACLs: {0}", cur);
        } else {
          log.log(Level.WARNING, "Missing ACLs for document ''{0}'' inherited "
              + "from another document", cur);
        }
        return null;
      }
      if (used.contains(acl)) {
        log.log(Level.WARNING, "Detected ACL cycle at ''{0}''", cur);
        return null;
      }
      used.add(acl);
      chain.add(0, acl);
      cur = acl.getInheritFrom();
    }
    return Collections.unmodifiableList(chain);
  }

  /**
   * Equality is determined if all the permit/deny sets are equal and the
   * inheritance is equal.
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Acl)) {
      return false;
    }
    if (this == o) {
      return true;
    }
    Acl a = (Acl) o;
    return inheritType == a.inheritType
        // Handle null case.
        && (inheritFrom == a.inheritFrom
            || (inheritFrom != null && inheritFrom.equals(a.inheritFrom)))
        && permitGroups.equals(a.permitGroups)
        && denyGroups.equals(a.denyGroups)
        && permitUsers.equals(a.permitUsers) && denyUsers.equals(a.denyUsers);
  }

  /**
   * Returns a hash code for this object that agrees with {@code equals}.
   */
  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[] {
      permitGroups, denyGroups, permitUsers, denyUsers, inheritFrom, inheritType
    });
  }

  /**
   * Generates a string useful for debugging that contains users and groups
   * along with inheritance information.
   */
  @Override
  public String toString() {
    return "Acl(inheritFrom=" + inheritFrom + ", inheritType=" + inheritType
        + ", permitGroups=" + permitGroups + ", denyGroups=" + denyGroups
        + ", permitUsers=" + permitUsers + ", denyUsers=" + denyUsers + ")";
  }

  /**
   * Batch retrieval of ACLs for efficent processing of many authz checks at
   * once.
   *
   * @see Acl#isAuthorizedBatch
   */
  public static interface BatchRetriever {
    /**
     * Retrieve the ACLs for the requested DocIds. This method is permitted to
     * return ACLs for DocIds not requested, but it should never provide a
     * {@code null} value for a DocId's ACLs. If a DocId does not exist, then it
     * should be missing in the returned map.
     *
     * <p>This method should provide any ACLs for named resources (if any are in
     * use, which is not the common case) in addition to any normal documents.
     * For more information about named resources, see {@link
     * DocIdPusher#pushNamedResources}.
     *
     * @throws IOException if there was an error contacting the data store
     */
    public Map<DocId, Acl> retrieveAcls(Set<DocId> ids)
        throws IOException;
  }

  /**
   * Mutable ACL for creating instances of {@link Acl}.
   */
  public static class Builder {
    private Set<String> permitGroups = Collections.emptySet();
    private Set<String> denyGroups = Collections.emptySet();
    private Set<String> permitUsers = Collections.emptySet();
    private Set<String> denyUsers = Collections.emptySet();
    private DocId inheritFrom;
    private InheritanceType inheritType = InheritanceType.LEAF_NODE;

    /**
     * Create new empty builder. All sets are empty, inheritFrom is {@code
     * null}, and inheritType is {@link InheritanceType#LEAF_NODE}.
     */
    public Builder() {}

    /**
     * Create and initialize builder with ACL information provided in {@code
     * acl}.
     */
    public Builder(Acl acl) {
      permitGroups = sanitizeSet(acl.getPermitGroups());
      denyGroups = sanitizeSet(acl.getDenyGroups());
      permitUsers = sanitizeSet(acl.getPermitUsers());
      denyUsers = sanitizeSet(acl.getDenyUsers());
      inheritFrom = acl.getInheritFrom();
      inheritType = acl.getInheritanceType();
    }

    private Set<String> sanitizeSet(Collection<String> set) {
      if (set.isEmpty()) {
        Collections.emptySet();
      }
      // Check all the values to make sure they are valid.
      for (String item : set) {
        if (item == null) {
          throw new NullPointerException("Entries in set may not be null");
        }
        if (!item.equals(item.trim())) {
          throw new IllegalArgumentException("Entries in set must not start or "
                                             + "end with whitespace");
        }
        if ("".equals(item)) {
          throw new IllegalArgumentException("Entries in set must not be the "
                                             + "empty string");
        }
      }
      // Use TreeSets so that sets have predictable order when serializing.
      return Collections.unmodifiableSet(new TreeSet<String>(set));
    }

    /**
     * Create immutable {@link Acl} instance of the current state.
     */
    public Acl build() {
      return new Acl(permitGroups, denyGroups, permitUsers, denyUsers,
                     inheritFrom, inheritType);
    }

    /**
     * Replace existing permit groups.
     *
     * @return the same instance of the builder, for chaining calls
     * @throws NullPointerException if the collection is {@code null} or
     *     contains {@code null}
     * @throws IllegalArgumentException if the collection contains {@code ""}
     *     or a value that has leading or trailing whitespace
     */
    public Builder setPermitGroups(Collection<String> permitGroups) {
      this.permitGroups = sanitizeSet(permitGroups);
      return this;
    }

    /**
     * Replace existing deny groups.
     *
     * @return the same instance of the builder, for chaining calls
     * @throws NullPointerException if the collection is {@code null} or
     *     contains {@code null}
     * @throws IllegalArgumentException if the collection contains {@code ""}
     *     or a value that has leading or trailing whitespace
     */
    public Builder setDenyGroups(Collection<String> denyGroups) {
      this.denyGroups = sanitizeSet(denyGroups);
      return this;
    }

    /**
     * Replace existing permit users.
     *
     * @return the same instance of the builder, for chaining calls
     * @throws NullPointerException if the collection is {@code null} or
     *     contains {@code null}
     * @throws IllegalArgumentException if the collection contains {@code ""}
     *     or a value that has leading or trailing whitespace
     */
    public Builder setPermitUsers(Collection<String> permitUsers) {
      this.permitUsers = sanitizeSet(permitUsers);
      return this;
    }

    /**
     * Replace existing deny users.
     *
     * @return the same instance of the builder, for chaining calls
     * @throws NullPointerException if the collection is {@code null} or
     *     contains {@code null}
     * @throws IllegalArgumentException if the collection contains {@code ""}
     *     or a value that has leading or trailing whitespace
     */
    public Builder setDenyUsers(Collection<String> denyUsers) {
      this.denyUsers = sanitizeSet(denyUsers);
      return this;
    }

    /**
     * Set {@code DocId} to inherit ACLs from. This is also known as the
     * "parent's" ACLs. Note that the parent's {@code InheritanceType}
     * determines how to combine results with this ACL.
     *
     * @return the same instance of the builder, for chaining calls
     * @see #setInheritanceType
     */
    public Builder setInheritFrom(DocId inheritFrom) {
      this.inheritFrom = inheritFrom;
      return this;
    }

    /**
     * Set the type of inheritance of ACL information used to combine authz
     * decisions of these ACLs with its <em>child</em>. The inheritance type
     * applies to the interaction between this ACL and any <em>children</em> it
     * has.
     *
     * @return the same instance of the builder, for chaining calls
     * @throws NullPointerException if {@code inheritType} is {@code null}
     * @see #setInheritFrom
     */
    public Builder setInheritanceType(InheritanceType inheritType) {
      if (inheritType == null) {
        throw new NullPointerException();
      }
      this.inheritType = inheritType;
      return this;
    }
  }

  /**
   * The rule for combining a parent's authz response with its child's. This is
   * stored as part of the parent's ACLs.
   */
  public static enum InheritanceType {
    /**
     * The child's authz result is used, unless it is indeterminate, in which
     * case this ACL's authz result is used.
     */
    CHILD_OVERRIDES("child-overrides") {
      @Override
      AuthzStatus isAuthorized(Decision child, Decision parent) {
        if (child.getStatus() == AuthzStatus.INDETERMINATE) {
          return parent.getStatus();
        }
        return child.getStatus();
      }
    },
    /**
     * This ACL's authz result is used, unless it is indeterminate, in which
     * case the child's authz result is used.
     */
    PARENT_OVERRIDES("parent-overrides") {
      @Override
      AuthzStatus isAuthorized(Decision child, Decision parent) {
        if (parent.getStatus() == AuthzStatus.INDETERMINATE) {
          return child.getStatus();
        }
        return parent.getStatus();
      }
    },
    /**
     * The user is denied, unless both this ACL and the child's authz result is
     * permit.
     */
    AND_BOTH_PERMIT("and-both-permit") {
      @Override
      AuthzStatus isAuthorized(Decision child, Decision parent) {
        if (parent.getStatus() == AuthzStatus.PERMIT
            && child.getStatus() == AuthzStatus.PERMIT) {
          return AuthzStatus.PERMIT;
        }
        return AuthzStatus.DENY;
      }
    },
    /**
     * The ACL should never have a child and thus the inheritance type is
     * unnecessary. If a child inherits from this ACL then the result is deny.
     */
    LEAF_NODE("leaf-node") {
      @Override
      AuthzStatus isAuthorized(Decision child, Decision parent) {
        log.log(Level.WARNING, "Illegal ACL information. A LEAF_NODE is the "
                + "parent of another node.");
        return AuthzStatus.DENY;
      }
    },
    ;

    private final String commonForm;

    private InheritanceType(String commonForm) {
      this.commonForm = commonForm;
    }

    /**
     * The identifier used to represent enum value during communication with the
     * GSA.
     */
    String getCommonForm() {
      return commonForm;
    }

    /**
     * Combine the result of a child and a parent.
     */
    abstract AuthzStatus isAuthorized(Decision child, Decision parent);
  }

  /**
   * Lazy-computing of AuthzStatus.
   */
  abstract static class Decision {
    private AuthzStatus status;

    public AuthzStatus getStatus() {
      if (status == null) {
        status = computeDecision();
        if (status == null) {
          throw new AssertionError();
        }
      }
      return status;
    }

    /**
     * Compute the actual decision. The response will be cached, so this will be
     * called at most once.
     *
     * @return a non-{@code null} authz status
     */
    protected abstract AuthzStatus computeDecision();
  }
}
