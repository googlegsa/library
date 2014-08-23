package com.google.enterprise.adaptor.examples.HelloWorldConnector;

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
import java.util.Set;
import java.util.TreeSet;

/**
 * Stub of AuthnIdentity
 */
public class MyAuthnIdentity implements AuthnIdentity {

  private UserPrincipal user;
  private Set<GroupPrincipal> groups;

  // Constructor  with user only
  public MyAuthnIdentity(String uid) {
    this.user = new UserPrincipal(uid);
  }

  //Constructor with user & single group
  public MyAuthnIdentity(String uid, String gid) {
    this.user = new UserPrincipal(uid);
    this.groups = new TreeSet<GroupPrincipal>();
    this.groups.add(new GroupPrincipal(gid));
  }

  // Constructor with user & groups
  public MyAuthnIdentity(String uid, Collection<String> gids) {
    this.user = new UserPrincipal(uid);
    this.groups = new TreeSet<GroupPrincipal>();
    for (String n : gids) {
      this.groups.add(new GroupPrincipal(n));
    }
  }

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
    return groups;
  }
}
