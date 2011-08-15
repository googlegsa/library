// Copyright 2011 Google Inc.
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

package com.google.enterprise.secmgr.authncontroller;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.enterprise.secmgr.config.AuthnAuthority;
import com.google.enterprise.secmgr.config.AuthnMechanism;
import com.google.enterprise.secmgr.config.CredentialGroup;
import com.google.enterprise.secmgr.identity.AuthnPrincipal;
import com.google.enterprise.secmgr.identity.CredPassword;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * A view of a session snapshot that's specialized for a credential group.
 */
@Immutable
@ParametersAreNonnullByDefault
final class SessionViewForCredentialGroup extends SessionView {

  @Nonnull private final CredentialGroup credentialGroup;

  SessionViewForCredentialGroup(SessionSnapshot snapshot, CredentialGroup credentialGroup) {
    super(snapshot);
    Preconditions.checkNotNull(credentialGroup);
    Preconditions.checkState(
        snapshot.getConfig().getCredentialGroups().contains(credentialGroup));
    this.credentialGroup = credentialGroup;
  }

  @Override
  protected SessionView withNewSnapshot(SessionSnapshot snapshot) {
    return snapshot.getView(credentialGroup);
  }

  @Override
  public boolean isSpecializedForCredentialGroup() {
    return true;
  }

  @Override
  public AuthnAuthority getAuthority() {
    return credentialGroup.getAuthority();
  }

  @Override
  public AuthnMechanism getMechanism() {
    throw new UnsupportedOperationException();
  }

  @Override
  public CredentialGroup getCredentialGroup() {
    return credentialGroup;
  }

  @Override
  protected Predicate<AuthnAuthority> getCookieFilter() {
    return snapshot.getConfig().getAuthorityPredicate(credentialGroup);
  }

  @Override
  protected Predicate<AuthnAuthority> getCredentialFilter() {
    return snapshot.getConfig().getAuthorityPredicate(credentialGroup);
  }

  @Override
  public boolean isSatisfied() {
    if (credentialGroup.getMechanisms().isEmpty()) {
      // An empty group is never satisfied.
      logMessage("Credential group %s not satisfied because it is empty.",
          credentialGroup.getName());
      return false;
    }

    if (isRefuted()) {
      logMessage("Credential group %s not satisfied because it is refuted.",
          credentialGroup.getName());
      return false;
    }

    // If principal is required, it must be present and non-empty.
    if (credentialGroup.getRequiresUsername() && !hasVerifiedPrincipal()) {
      logMessage("Credential group %s not satisfied because it requires a username.",
          credentialGroup.getName());
      return false;
    }

    // If password is required, it must be present and non-empty.
    if (credentialGroup.getRequiresPassword() && !hasVerifiedPassword()) {
      logMessage("Credential group %s not satisfied because it requires a password.",
          credentialGroup.getName());
      return false;
    }

    // If group is optional, empty principal and password are sufficient.
    // Note that both the principal and the password must be present; otherwise
    // that means we haven't yet gathered credentials, so we must not return
    // satisfied.  When they are both empty, it means we gathered credentials
    // and the user didn't fill them in.
    if (credentialGroup.getIsOptional() && hasEmptyPrincipal() && hasEmptyPassword()) {
      logMessage("Credential group %s satisfied because it's optional and was left blank.",
          credentialGroup.getName());
      return true;
    }

    // TODO(cph): This doesn't check the credentials -- so the username and/or
    // password might not be verified.  Unfortunately, the program's current
    // logic doesn't understand that credentials must be verified independently
    // of the satisfaction of their identity group.
    return isVerified();
  }

  private boolean hasEmptyPrincipal() {
    AuthnPrincipal principal = getPrincipal();
    return principal != null && principal.getName().isEmpty();
  }

  private boolean hasEmptyPassword() {
    CredPassword password = getPasswordCredential();
    return password != null && password.getText().isEmpty();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("{SessionView of ");
    builder.append(snapshot);
    builder.append(" specialized for: ");
    builder.append(credentialGroup);
    builder.append("}");
    return builder.toString();
  }
}
