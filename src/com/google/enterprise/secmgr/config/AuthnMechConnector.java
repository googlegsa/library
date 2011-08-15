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
 * The configuration of a connector authentication mechanism.
 */
@Immutable
public final class AuthnMechConnector extends AuthnMechanism {

  public static final String TYPE_NAME = "Connector";
  private static final long DEFAULT_TRUST_DURATION = 20 * 60 * 1000;  // 20 mins

  private final String connectorName;
  private final boolean doGroupLookupOnly;
  private final long trustDuration;

  /**
   * Makes a new connector mechanism.
   *
   * @param name The name for the new mechanism.
   * @param connectorName The name of the connector to use.
   * @param doGroupLookupOnly To do only groups lookup and not authentication.
   * @param trustDuration The number of milliseconds for which successfully
   *     verified credentials are trusted.  This must be a non-negative number.
   * @return A new mechanism with the given elements.
   */
  public static AuthnMechConnector make(String name, String connectorName,
      boolean doGroupLookupOnly, long trustDuration) {
    return new AuthnMechConnector(name, connectorName, doGroupLookupOnly, trustDuration);
  }

  /**
   * Makes a new connector mechanism with a default trust duration.
   *
   * @param name The name for the new mechanism.
   * @param connectorName The name of the connector to use.
   * @param doGroupLookupOnly To do only groups lookup and not authentication.
   * @return A new mechanism with the given elements.
   */
  public static AuthnMechConnector make(String name, String connectorName,
      boolean doGroupLookupOnly) {
    return make(name, connectorName, doGroupLookupOnly, getDefaultTrustDuration());
  }

  private AuthnMechConnector(String name, String connectorName, boolean doGroupLookupOnly,
      long trustDuration) {
    super(name);
    this.connectorName = checkString(connectorName);
    this.doGroupLookupOnly = doGroupLookupOnly;
    this.trustDuration = checkTrustDuration(trustDuration);
  }

  /**
   * Makes a new unconfigured connector mechanism.
   */
  public static AuthnMechConnector makeEmpty() {
    return new AuthnMechConnector();
  }

  private AuthnMechConnector() {
    super();
    this.connectorName = null;
    this.doGroupLookupOnly = false;
    this.trustDuration = getDefaultTrustDuration();
  }

  @Override
  public String getTypeName() {
    return TYPE_NAME;
  }

  /**
   * Does this connector implement group lookup but not verification?
   *
   * @return True only if so.
   */
  public boolean doGroupLookupOnly() {
    return doGroupLookupOnly;
  }

  /**
   * Gets the default trust-duration value.
   */
  public static long getDefaultTrustDuration() {
    return DEFAULT_TRUST_DURATION;
  }

  @Override
  public List<CredentialTransform> getCredentialTransforms() {
    return ImmutableList.of(
        doGroupLookupOnly()
        ? CredentialTransform.make(
            CredentialTypeSet.VERIFIED_PRINCIPAL,
            CredentialTypeSet.VERIFIED_GROUPS)
        : CredentialTransform.make(
            CredentialTypeSet.PRINCIPAL_AND_PASSWORD,
            CredentialTypeSet.VERIFIED_PRINCIPAL_PASSWORD_AND_GROUPS));
  }

  @Override
  public AuthnMechanism copyWithNewName(String name) {
    return make(name, getConnectorName(), doGroupLookupOnly(), getTrustDuration());
  }

  @Override
  public long getTrustDuration() {
    return trustDuration;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof AuthnMechConnector)) { return false; }
    AuthnMechConnector mech = (AuthnMechConnector) object;
    return super.equals(mech)
        && Objects.equal(getConnectorName(), mech.getConnectorName())
        && Objects.equal(getTrustDuration(), mech.getTrustDuration());
  }

  @Override
  public int hashCode() {
    return super.hashCode(getConnectorName(), getTrustDuration());
  }

  /**
   * Gets the name of this connector.
   *
   * @return The connector name, never null or empty.
   */
  public String getConnectorName() {
    return connectorName;
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(AuthnMechConnector.class,
        ProxyTypeAdapter.make(AuthnMechConnector.class, LocalProxy.class));
  }

  private static final class LocalProxy extends MechanismProxy<AuthnMechConnector> {
    String connectorName;
    boolean doGroupLookupOnly;
    long trustDuration = DEFAULT_TRUST_DURATION;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(AuthnMechConnector mechanism) {
      super(mechanism);
      connectorName = mechanism.getConnectorName();
      doGroupLookupOnly = mechanism.doGroupLookupOnly();
      trustDuration = mechanism.getTrustDuration();
    }

    @Override
    public AuthnMechConnector build() {
      return make(name, connectorName, doGroupLookupOnly, trustDuration);
    }
  }
}
