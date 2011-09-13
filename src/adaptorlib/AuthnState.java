// Copyright 2011 Google Inc. All Rights Reserved.
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

import com.google.enterprise.secmgr.modules.SamlClient;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

/**
 * Current state of an authentication attempt. It can represent a pending
 * authentication attempt or a completed authentication.
 */
class AuthnState {
  public static final String SESSION_ATTR_NAME = "authnState";

  /** Client used for pending authn attempt. */
  private SamlClient client;
  /** Original URL that was accessed that caused this authn attempt. */
  private URI originalUri;
  /** Successfully authned principal for the user. */
  private String principal;
  /** Successfully authned groups for the user. */
  private Set<String> groups;
  /** Time in milliseconds that authentication information expires */
  private long expirationTimeMillis;

  public void startAttempt(SamlClient client, URI originalUri) {
    this.client = client;
    this.originalUri = originalUri;
  }

  public void failAttempt() {
    clearAttempt();
  }

  private void clearAttempt() {
    client = null;
    originalUri = null;
  }

  public void authenticated(String principal, Set<String> groups,
                            long expirationTimeMillis) {
    clearAttempt();
    this.principal = principal;
    this.groups = Collections.unmodifiableSet(groups);
    this.expirationTimeMillis = expirationTimeMillis;
  }
  
  public boolean isAuthenticated() {
    if (expirationTimeMillis == 0) {
      return false;
    }

    if (System.currentTimeMillis() > expirationTimeMillis) {
      expirationTimeMillis = 0;
      principal = null;
      groups = null;
      return false;
    }

    return true;
  }

  public SamlClient getSamlClient() {
    return client;
  }

  public URI getOriginalUri() {
    return originalUri;
  }

  public String getPrincipal() {
    return principal;
  }

  public Set<String> getGroups() {
    return groups;
  }
}
