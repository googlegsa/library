// Copyright 2009 Google Inc.
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
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.gson.GsonBuilder;

import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * The configuration of an NTLM authentication mechanism.
 */
@Immutable
public final class AuthnMechNtlm extends AuthnMechanism {

  public static final String TYPE_NAME = "NTLM";
  private static final long DEFAULT_TRUST_DURATION = 20 * 60 * 1000;  // 20 mins

  private final String sampleUrl;
  private final long trustDuration;

  /**
   * Make a new NTLM mechanism.
   *
   * @param sampleUrl A sample URL that requires NTLM authentication.
   * @param trustDuration The number of milliseconds that successfully
   *     verified credentials are trusted.  This must be a non-negative number.
   * @return A new mechanism with the given elements.
   */
  public static AuthnMechNtlm make(String name, String sampleUrl, long trustDuration) {
    return new AuthnMechNtlm(name, sampleUrl, trustDuration);
  }

  /**
   * Make a new NTLM mechanism with a default trust duration.
   *
   * @param sampleUrl A sample URL that requires NTLM authentication.
   * @return A new mechanism with the given elements.
   */
  public static AuthnMechNtlm make(String name, String sampleUrl) {
    return make(name, sampleUrl, getDefaultTrustDuration());
  }

  private AuthnMechNtlm(String name, String sampleUrl, long trustDuration) {
    super(name);
    this.sampleUrl = checkString(sampleUrl);
    this.trustDuration = checkTrustDuration(trustDuration);
  }

  /**
   * Make a new unconfigured NTLM mechanism.
   */
  public static AuthnMechNtlm makeEmpty() {
    return new AuthnMechNtlm();
  }

  private AuthnMechNtlm() {
    super();
    this.sampleUrl = null;
    this.trustDuration = getDefaultTrustDuration();
  }

  @Override
  public String getTypeName() {
    return TYPE_NAME;
  }

  /**
   * Get the default trust-duration value.
   */
  public static long getDefaultTrustDuration() {
    return DEFAULT_TRUST_DURATION;
  }

  @Override
  public List<CredentialTransform> getCredentialTransforms() {
    return ImmutableList.of(
        CredentialTransform.make(
            CredentialTypeSet.PRINCIPAL_AND_PASSWORD,
            CredentialTypeSet.VERIFIED_PRINCIPAL_AND_PASSWORD));
  }

  @Override
  public AuthnMechanism copyWithNewName(String name) {
    return make(name, getSampleUrl(), getTrustDuration());
  }

  @Override
  public String getSampleUrl() {
    return sampleUrl;
  }

  @Override
  public long getTrustDuration() {
    return trustDuration;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof AuthnMechNtlm)) { return false; }
    AuthnMechNtlm mech = (AuthnMechNtlm) object;
    return super.equals(mech)
        && Objects.equal(getSampleUrl(), mech.getSampleUrl())
        && Objects.equal(getTrustDuration(), mech.getTrustDuration());
  }

  @Override
  public int hashCode() {
    return super.hashCode(getSampleUrl(), getTrustDuration());
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(AuthnMechNtlm.class,
        ProxyTypeAdapter.make(AuthnMechNtlm.class, LocalProxy.class));
  }

  private static final class LocalProxy extends MechanismProxy<AuthnMechNtlm> {
    String sampleUrl;
    long trustDuration = DEFAULT_TRUST_DURATION;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(AuthnMechNtlm mechanism) {
      super(mechanism);
      sampleUrl = mechanism.getSampleUrl();
      trustDuration = mechanism.getTrustDuration();
    }

    @Override
    public AuthnMechNtlm build() {
      return make(name, sampleUrl, trustDuration);
    }
  }
}
