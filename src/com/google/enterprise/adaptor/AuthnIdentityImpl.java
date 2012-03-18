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

import java.util.*;

/**
 * Immutable implementation of {@link AuthnIdentity}.
 */
class AuthnIdentityImpl implements AuthnIdentity {
  private final String username;
  private final String password;
  private final Set<String> groups;

  private AuthnIdentityImpl(String username, String password,
                            Set<String> groups) {
    this.username = username;
    this.password = password;
    this.groups = groups;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getUsername() {
    return username;
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
  public Set<String> getGroups() {
    return groups;
  }

  @Override
  public String toString() {
    return "User(" + username + ","
        + (password == null ? "no password" : "contains password") + ","
        + "groups=" + groups + ")";
  }

  /**
   * Builder for creating {@link AuthnIdentityImpl} instances.
   */
  public static class Builder {
    private String username;
    private String password;
    private Set<String> groups;

    /**
     * Construct new builder. All values are initialized to {@code null}, except
     * for username.
     *
     * @param username non-{@code null} username
     */
    public Builder(String username) {
      setUsername(username);
    }

    public AuthnIdentityImpl build() {
      return new AuthnIdentityImpl(username, password, groups);
    }

    public Builder setUsername(String username) {
      if (username == null) {
        throw new NullPointerException();
      }
      this.username = username;
      return this;
    }

    public Builder setPassword(String password) {
      this.password = password;
      return this;
    }

    public Builder setGroups(Set<String> groups) {
      if (groups == null) {
        this.groups = null;
        return this;
      }
      this.groups = Collections.unmodifiableSet(new HashSet<String>(groups));
      return this;
    }
  }
}
