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

package com.google.enterprise.adaptor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mock of {@link Adaptor} that does authz by checking whether the subject has
 * correct password.
 */
class AuthzByPasswordMockAdaptor extends MockAdaptor {
  private final Map<String, String> usernamePasswordMap;

  public AuthzByPasswordMockAdaptor(Map<String, String> usernamePasswordMap) {
    this.usernamePasswordMap = usernamePasswordMap;
  }

  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
      Collection<DocId> ids) {
    Map<DocId, AuthzStatus> result =
        new HashMap<DocId, AuthzStatus>(ids.size() * 2);

    String fullUsername = identity.getUser().getName();
    String password = identity.getPassword();

    boolean authenticated = false;
    if (usernamePasswordMap.containsKey(fullUsername)) {
      authenticated =
          Objects.equals(password, usernamePasswordMap.get(fullUsername));
    }

    AuthzStatus decision =
        authenticated ? AuthzStatus.PERMIT : AuthzStatus.DENY;
    for (DocId id : ids) {
      result.put(id, decision);
    }
    return Collections.unmodifiableMap(result);
  }
}
