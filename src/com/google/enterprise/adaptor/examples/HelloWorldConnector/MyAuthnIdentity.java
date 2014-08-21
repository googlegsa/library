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

import java.util.Set;

/**
 * Stub of AuthnIdentity
 */
public class MyAuthnIdentity implements AuthnIdentity {

  UserPrincipal user;
  Set<GroupPrincipal> groups;

  public void setGroups(Set<GroupPrincipal> groups) {
    this.groups = groups;
  }

  public void setUser(UserPrincipal user) {
    this.user = user;
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
