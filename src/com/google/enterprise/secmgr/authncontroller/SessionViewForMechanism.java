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
import com.google.common.base.Predicates;
import com.google.enterprise.secmgr.config.AuthnAuthority;
import com.google.enterprise.secmgr.config.AuthnMechanism;
import com.google.enterprise.secmgr.config.CredentialGroup;
import com.google.enterprise.secmgr.identity.Verification;
import com.google.enterprise.secmgr.identity.VerificationStatus;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * A view of a session snapshot that's specialized for an authority.
 */
@Immutable
@ParametersAreNonnullByDefault
final class SessionViewForMechanism extends SessionView {
  @Nonnull private final AuthnMechanism mechanism;
  @Nonnull private final CredentialGroup credentialGroup;

  SessionViewForMechanism(SessionSnapshot snapshot, AuthnMechanism mechanism) {
    super(snapshot);
    Preconditions.checkNotNull(mechanism);
    this.mechanism = mechanism;
    credentialGroup = snapshot.getConfig().getCredentialGroup(mechanism);
  }

  @Override
  protected SessionView withNewSnapshot(SessionSnapshot snapshot) {
    return snapshot.getView(mechanism);
  }

  @Override
  public boolean isSpecializedForMechanism() {
    return true;
  }

  @Override
  public AuthnAuthority getAuthority() {
    return mechanism.getAuthority();
  }

  @Override
  public CredentialGroup getCredentialGroup() {
    return credentialGroup;
  }

  @Override
  public AuthnMechanism getMechanism() {
    return mechanism;
  }

  @Override
  protected Predicate<AuthnAuthority> getCookieFilter() {
    return Predicates.equalTo(getAuthority());
  }

  @Override
  protected Predicate<AuthnAuthority> getCredentialFilter() {
    return snapshot.getConfig().getAuthorityPredicate(credentialGroup);
  }

  @Override
  public VerificationStatus getVerificationStatus() {
    return Verification.getStatus(
        getSummary().getVerifications(Predicates.equalTo(getAuthority()), getTimeStamp()));
  }

  @Override
  public boolean isSatisfied() {
    return isVerified();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("{SessionView of ");
    builder.append(snapshot);
    builder.append(" specialized for: ");
    builder.append(mechanism);
    builder.append("}");
    return builder.toString();
  }
}
