package com.google.enterprise.adaptor.examples.helloworldconnector;

// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.UserPrincipal;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Simple implementation of AuthnIdentity
 */
class SimpleAuthnIdentity implements AuthnIdentity {

  private UserPrincipal user;
  private Set<GroupPrincipal> groups;

  public SimpleAuthnIdentity(String uid) throws NullPointerException {
    if (uid == null) {
      throw(new NullPointerException("Null user not allowed"));
    }
    this.user = new UserPrincipal(uid);
  }

  //Constructor with user & single group
  public SimpleAuthnIdentity(String uid, String gid)
      throws NullPointerException {
    this(uid);
    this.groups = new TreeSet<GroupPrincipal>();
    if (gid != null && !"".equals(gid)) {
      this.groups.add(new GroupPrincipal(gid));
    }
    this.groups =
        (Set<GroupPrincipal>) Collections.unmodifiableCollection(this.groups);
  }

  // Constructor with user & groups
  public SimpleAuthnIdentity(String uid, Collection<String> gids)
      throws NullPointerException {
    this(uid);
    this.groups = new TreeSet<GroupPrincipal>();
    for (String n : gids) {
      if (n != null && !"".equals(n)) {
        this.groups.add(new GroupPrincipal(n));
      }
    }
    this.groups =
        (Set<GroupPrincipal>) Collections.unmodifiableCollection(this.groups);
  }

  @Override
  public UserPrincipal getUser() {
    return user;
  }

  /**
   * Returns null in this example since we don't do anything with the
   * password, but getPassword() must be implemented for AuthnIdentity
   */
  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public Set<GroupPrincipal> getGroups() {
    return groups;
  }
}
