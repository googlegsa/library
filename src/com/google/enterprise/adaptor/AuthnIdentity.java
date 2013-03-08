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

import java.util.Set;

/**
 * User identification information for understanding who a user is or if they
 * are allowed to access a resource.
 */
public interface AuthnIdentity {
  /**
   * Gets the user principal. This value will always be available.
   *
   * @return the user's identifier.
   */
  public UserPrincipal getUser();

  /**
   * Gets the user's password.
   *
   * @return the user's password, or {@code null} if it is unavailable.
   */
  public String getPassword();

  /**
   * Gets all the groups a user belongs to in an immutable set.
   *
   * @return the user's groups, or {@code null} if they are unavailable.
   */
  public Set<GroupPrincipal> getGroups();
}
