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
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.gson.GsonBuilder;

import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * The configuration of a client certificate authentication mechanism.
 */
@Immutable
public final class AuthnMechClient extends AuthnMechanism {

  public static final String TYPE_NAME = "X509Client";
  private static final long DEFAULT_TRUST_DURATION = 20 * 60 * 1000;  // 20 mins

  /**
   * Make a new ClientAuth mechanism.
   */
  public static AuthnMechClient make(String name) {
    return new AuthnMechClient(name);
  }

  private AuthnMechClient(String name) {
    super(name);
  }

  /**
   * Make a new unconfigured ClientAuth mechanism.
   */
  public static AuthnMechClient makeEmpty() {
    return new AuthnMechClient();
  }

  private AuthnMechClient() {
    super();
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
        CredentialTransform.make(CredentialTypeSet.NONE, CredentialTypeSet.VERIFIED_PRINCIPAL));
  }

  @Override
  public AuthnMechanism copyWithNewName(String name) {
    return make(name);
  }

  @Override
  public boolean equals(Object object) {
    return (object instanceof AuthnMechClient);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(TYPE_NAME);
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(AuthnMechClient.class,
        ProxyTypeAdapter.make(AuthnMechClient.class, LocalProxy.class));
  }

  private static final class LocalProxy extends MechanismProxy<AuthnMechClient> {

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(AuthnMechClient mechanism) {
      super(mechanism);
    }

    @Override
    public AuthnMechClient build() {
      return make(name);
    }
  }
}
