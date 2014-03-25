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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable implementation of {@link AuthnIdentity}.
 */
class AuthnIdentityImpl implements AuthnIdentity {
  private final UserPrincipal user;
  private final String password;
  private final Set<GroupPrincipal> groups;

  private AuthnIdentityImpl(UserPrincipal user, String password,
                            Set<GroupPrincipal> groups) {
    this.user = user;
    this.password = password;
    this.groups = groups;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UserPrincipal getUser() {
    return user;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getPassword() {
    return password;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<GroupPrincipal> getGroups() {
    return groups;
  }

  @Override
  public String toString() {
    return "AuthnIdentity(" + user + ","
        + (password == null ? "no password" : "contains password") + ","
        + "groups=" + groups + ")";
  }

  /**
   * Builder for creating {@link AuthnIdentityImpl} instances.
   */
  public static class Builder {
    private UserPrincipal user;
    private String password;
    private Set<GroupPrincipal> groups;

    /**
     * Construct new builder. All values are initialized to {@code null}, except
     * for user.
     *
     * @param user non-{@code null} user
     */
    public Builder(UserPrincipal user) {
      setUser(user);
    }

    public AuthnIdentityImpl build() {
      return new AuthnIdentityImpl(user, password, groups);
    }

    public Builder setUser(UserPrincipal user) {
      if (user == null) {
        throw new NullPointerException();
      }
      this.user = user;
      return this;
    }

    public Builder setPassword(String password) {
      this.password = password;
      return this;
    }

    public Builder setGroups(Set<GroupPrincipal> groups) {
      if (groups == null) {
        this.groups = null;
        return this;
      }
      this.groups = Collections.unmodifiableSet(
          new HashSet<GroupPrincipal>(groups));
      return this;
    }
  }
}
