// Copyright 2010 Google Inc.
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

package com.google.enterprise.secmgr.config;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * A set of credential types, used as an input to or output from a credential
 * transform.
 */
public class CredentialTypeSet {
  private final boolean areVerified;
  private final ImmutableSet<CredentialTypeName> elements;

  private CredentialTypeSet(boolean areVerified, ImmutableSet<CredentialTypeName> elements) {
    this.areVerified = areVerified;
    this.elements = elements;
  }

  /**
   * Make a credential-type set.
   *
   * @param areVerified True if the represented credentials are verified.
   * @param elements The credential types that are the elements of this set.
   * @return A credential-type set with those elements.
   */
  public static CredentialTypeSet make(boolean areVerified, Iterable<CredentialTypeName> elements) {
    return new CredentialTypeSet(areVerified, ImmutableSet.copyOf(elements));
  }

  /**
   * @return True if the credentials are mutually verified.
   */
  public boolean getAreVerified() {
    return areVerified;
  }

  /**
   * @return An immutable set of the credential types that are the elements of
   * this set.
   */
  public Set<CredentialTypeName> getElements() {
    return elements;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof CredentialTypeSet)) { return false; }
    CredentialTypeSet other = (CredentialTypeSet) object;
    return Objects.equal(getAreVerified(), other.getAreVerified())
        && Objects.equal(getElements(), other.getElements());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getAreVerified(), getElements());
  }

  public static final CredentialTypeSet NONE =
      make(false, ImmutableList.<CredentialTypeName>of());

  public static final CredentialTypeSet COOKIES =
      make(false, ImmutableList.of(CredentialTypeName.COOKIES));

  public static final CredentialTypeSet PRINCIPAL_AND_PASSWORD =
      make(false, ImmutableList.of(CredentialTypeName.PRINCIPAL, CredentialTypeName.PASSWORD));

  public static final CredentialTypeSet VERIFIED_PRINCIPAL_AND_PASSWORD =
      make(true, ImmutableList.of(CredentialTypeName.PRINCIPAL, CredentialTypeName.PASSWORD));

  public static final CredentialTypeSet VERIFIED_PRINCIPAL_PASSWORD_AND_GROUPS =
      make(true, ImmutableList.of(CredentialTypeName.PRINCIPAL, CredentialTypeName.PASSWORD,
              CredentialTypeName.GROUPS));

  public static final CredentialTypeSet VERIFIED_PRINCIPAL =
      make(true, ImmutableList.of(CredentialTypeName.PRINCIPAL));

  public static final CredentialTypeSet VERIFIED_ALIASES =
      make(true, ImmutableList.of(CredentialTypeName.ALIASES));

  public static final CredentialTypeSet VERIFIED_GROUPS =
      make(true, ImmutableList.of(CredentialTypeName.GROUPS));
}
