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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Pair;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeAdapters;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A class that holds a complete security manager configuration.
 */
@ThreadSafe
public final class SecurityManagerConfig {

  static final int CURRENT_VERSION = 5;

  private final int version;
  @GuardedBy("this") private ImmutableList<CredentialGroup> credentialGroups;
  @GuardedBy("this") private ImmutableList<AuthnMechanism> mechanisms;
  @GuardedBy("this") private ConfigParams params;
  @GuardedBy("this") private FlexAuthorizer flexAuthorizer;

  private SecurityManagerConfig(int version, ImmutableList<CredentialGroup> credentialGroups,
      ConfigParams params, FlexAuthorizer flexAuthorizer) {
    this.version = version;
    setCredentialGroupsInternal(credentialGroups);
    this.params = params;
    this.flexAuthorizer = flexAuthorizer;
  }

  /**
   * Set a configuration's credential groups.  The security manager uses this
   * only for testing.
   *
   * @param credentialGroups The new credential groups.
   * @throws IllegalArgumentException if there's a problem with the argument.
   */
  public void setCredentialGroupsInternal(ImmutableList<CredentialGroup> credentialGroups) {
    ImmutableList.Builder<AuthnMechanism> mechanismsBuilder = ImmutableList.builder();
    for (CredentialGroup credentialGroup : credentialGroups) {
      mechanismsBuilder.addAll(credentialGroup.getMechanisms());
    }
    ImmutableList<AuthnMechanism> mechanisms = mechanismsBuilder.build();
    synchronized (this) {
      this.credentialGroups = credentialGroups;
      this.mechanisms = mechanisms;
    }
  }

  /**
   * Make a security manager configuration.
   *
   * @param credentialGroups The configuration's credential groups.
   * @param params The configuration's parameters.
   * @param flexAuthorizer The flex authorization configs
   * @return A security manager configuration.
   */
  public static SecurityManagerConfig make(Iterable<CredentialGroup> credentialGroups,
      ConfigParams params, FlexAuthorizer flexAuthorizer) {
    Preconditions.checkArgument(params != null);
    Preconditions.checkArgument(flexAuthorizer != null);
    return new SecurityManagerConfig(CURRENT_VERSION, checkCredentialGroups(credentialGroups),
        params, flexAuthorizer);
  }

  @VisibleForTesting
  public static SecurityManagerConfig make(Iterable<CredentialGroup> credentialGroups) {
    return new SecurityManagerConfig(CURRENT_VERSION, checkCredentialGroups(credentialGroups),
        ConfigParams.makeDefault(),
        FlexAuthorizerImpl.makeDefault());
  }

  static SecurityManagerConfig makeInternal(int version, Iterable<CredentialGroup> credentialGroups,
      ConfigParams params, FlexAuthorizer flexAuthorizer) {
    Preconditions.checkArgument(version > 0 && version <= CURRENT_VERSION);
    return new SecurityManagerConfig(
        version,
        checkCredentialGroups(credentialGroups),
        (params != null) ? params : ConfigParams.makeDefault(),
        (flexAuthorizer != null) ? flexAuthorizer : FlexAuthorizerImpl.makeDefault());
  }

  private static ImmutableList<CredentialGroup> checkCredentialGroups(
      Iterable<CredentialGroup> credentialGroups) {
    Preconditions.checkNotNull(credentialGroups);
    ImmutableList<CredentialGroup> copy = ImmutableList.copyOf(credentialGroups);
    Collection<String> names = Lists.newArrayList();
    for (CredentialGroup group : copy) {
      checkConfigName(group.getName(), names);
      for (AuthnMechanism mech : group.getMechanisms()) {
        checkConfigName(mech.getName(), names);
      }
    }
    return copy;
  }

  private static void checkConfigName(String name, Collection<String> names) {
    if (name != null) {
      name = name.toLowerCase(Locale.US);
      Preconditions.checkArgument(!names.contains(name),
          "Configuration name appears more than once: %s", name);
      names.add(name);
    }
  }

  /**
   * @return A default security manager configuration.
   */
  public static SecurityManagerConfig makeDefault() {
    return SecurityManagerConfig.make(
        makeDefaultCredentialGroups(),
        ConfigParams.makeDefault(),
        FlexAuthorizerImpl.makeDefault());
  }

  public static ImmutableList<CredentialGroup> makeDefaultCredentialGroups() {
    return ImmutableList.of(CredentialGroup.builder().build());
  }

  /**
   * @return The configuration's version.
   */
  int getVersion() {
    return version;
  }

  /**
   * Gets the credential groups contained in this configuration.
   *
   * @return The credential groups as an immutable list.  The order is the same
   *     as was given when this configuration was created.
   */
  public synchronized ImmutableList<CredentialGroup> getCredentialGroups() {
    return credentialGroups;
  }

  /**
   * Set a configuration's credential groups.  The security manager uses this
   * only for testing.
   *
   * @param credentialGroups The new credential groups.
   * @throws IllegalArgumentException if there's a problem with the argument.
   */
  public void setCredentialGroups(Iterable<CredentialGroup> credentialGroups) {
    setCredentialGroupsInternal(checkCredentialGroups(credentialGroups));
    ConfigSingleton.setChanged();
  }

  /**
   * Gets the authentication mechanisms contained in this configuration.
   *
   * @return The mechanisms as an immutable list.  The order is the same as was
   *     given when this configuration was created.
   */
  public synchronized ImmutableList<AuthnMechanism> getMechanisms() {
    return mechanisms;
  }

  /**
   * Gets the credential group for a given mechanism.
   *
   * @param mechanism The mechanism to get the credential group for.
   * @return The credential group for the given mechanism.
   * @throws IllegalArgumentException if the mechanism isn't contained in this
   *     configuration.
   */
  public CredentialGroup getCredentialGroup(AuthnMechanism mechanism) {
    for (CredentialGroup credentialGroup : getCredentialGroups()) {
      if (credentialGroup.getMechanisms().contains(mechanism)) {
        return credentialGroup;
      }
    }
    throw new IllegalArgumentException("Unknown mechanism: " + mechanism);
  }

  /**
   * Gets the credential group with a given name.
   *
   * @param name The credential-group name to search for.
   * @return The credential group with that name.
   * @throws IllegalArgumentException if there's no credential group with that name.
   */
  public CredentialGroup getCredentialGroup(String name) {
    Preconditions.checkNotNull(name);
    for (CredentialGroup credentialGroup : getCredentialGroups()) {
      if (name.equalsIgnoreCase(credentialGroup.getName())) {
        return credentialGroup;
      }
    }
    throw new IllegalArgumentException("No credential group with this name: " + name);
  }

  /**
   * Gets an authority predicate for a given credential group.
   *
   * @param credentialGroup A credential group to get the predicate for.
   * @return The authority predicate for the credential group.
   * @throws IllegalArgumentException if the credential group isn't contained in
   *     this configuration.
   */
  public Predicate<AuthnAuthority> getAuthorityPredicate(CredentialGroup credentialGroup) {
    ImmutableSet.Builder<AuthnAuthority> builder = ImmutableSet.builder();
    builder.add(credentialGroup.getAuthority());
    for (AuthnMechanism mechanism : credentialGroup.getMechanisms()) {
      builder.add(mechanism.getAuthority());
    }
    return Predicates.in(builder.build());
  }

  public synchronized FlexAuthorizer getFlexAuthorizer() {
    return flexAuthorizer;
  }

  /**
   * Sets this configurations's flex authorizer.  The security manager uses this
   * only for testing.
   */
  public void setFlexAuthorizer(FlexAuthorizer flexAuthorizer) {
    synchronized (this) {
      this.flexAuthorizer = flexAuthorizer;
    }
    ConfigSingleton.setChanged();
  }

  /**
   * @return The configuration parameters.
   */
  public synchronized ConfigParams getParams() {
    return params;
  }

  /**
   * Gets a list of credential group name and mechanism name pair for the mechanism.
   *
   * @param mechanismType the authentication mechanism type
   * @return a list of credential group name and mechanism name pair for the mechanism.
   */
  public <T extends AuthnMechanism> List<Pair<String, String>> getMechanism(
      final Class<T> mechanismType) {
    List<Pair<String, String>> mechanisms = Lists.newArrayList();
    for (CredentialGroup group : getCredentialGroups()) {
      for (AuthnMechanism mech : group.getMechanisms()) {
        if (mechanismType.isInstance(mech)) {
          mechanisms.add(Pair.of(group.getName(), mech.getName()));
        }
      }
    }
    return mechanisms;
  }

  /**
   * Checks if the mechanism is configured.
   *
   * @param mechanismType the authentication mechanism type
   * @return true if the mechanism is already configured.
   */
  public <T extends AuthnMechanism> boolean hasMechanism(final Class<T> mechanismType) {
    return !getMechanism(mechanismType).isEmpty();
  }

  /**
   * Sets a configuration's parameters.  The security manager uses this only for
   * testing.
   *
   * @param params The new parameters.
   */
  public void setParams(ConfigParams params) {
    Preconditions.checkNotNull(params);
    synchronized (this) {
      this.params = params;
    }
    ConfigSingleton.setChanged();
  }

  /**
   * @return The name of the ACL group rules file, never null or empty.
   */
  public String getAclGroupsFilename() {
    return params.get(ParamName.ACL_GROUPS_FILENAME, String.class);
  }

  /**
   * @return The name of the ACL URL rules file, never null or empty.
   */
  public String getAclUrlsFilename() {
    return params.get(ParamName.ACL_URLS_FILENAME, String.class);
  }

  /**
   * @return The name of the certificate-authority certificates file, never null
   *     or empty.
   */
  public String getCertificateAuthoritiesFilename() {
    return params.get(ParamName.CERTIFICATE_AUTHORITIES_FILENAME, String.class);
  }

  /**
   * @return The boolean value whether to check the server certificate during
   *     serving time
   */
  public Boolean getCheckServerCertificate() {
    return params.get(ParamName.CHECK_SERVER_CERTIFICATE, Boolean.class);
  }

  /**
   * @return The URLs of the configured connector managers as an immutable set.
   */
  public StringSet getConnectorManagerUrls() {
    return params.get(ParamName.CONNECTOR_MANAGER_URLS, StringSet.class);
  }

  /**
   * @return The name of the http deny rules file, never null or empty.
   */
  public String getDenyRulesFilename() {
    return params.get(ParamName.DENY_RULES_FILENAME, String.class);
  }

  /**
   * @return The global batch request timeout.
   */
  public Float getGlobalBatchRequestTimeout() {
    return params.get(ParamName.GLOBAL_BATCH_REQUEST_TIMEOUT, Float.class);
  }

  /**
   * @return The global single request timeout.
   */
  public Float getGlobalSingleRequestTimeout() {
    return params.get(ParamName.GLOBAL_SINGLE_REQUEST_TIMEOUT, Float.class);
  }

  /**
   * @return The name of the SAML metadata configuration file, never null or
   *     empty.
   */
  public String getSamlMetadataFilename() {
    return params.get(ParamName.SAML_METADATA_FILENAME, String.class);
  }

  /**
   * @return The name of the security manager's certificate file, never null or
   *     empty.
   */
  public String getServerCertificateFilename() {
    return params.get(ParamName.SERVER_CERTIFICATE_FILENAME, String.class);
  }

  /**
   * @return The name of the certificate file to be used for signing outgoing
   *     messages, never null or empty.
   */
  public String getSigningCertificateFilename() {
    return params.get(ParamName.SIGNING_CERTIFICATE_FILENAME, String.class);
  }

  /**
   * @return The name of the key file to be used for signing outgoing messages,
   *     never null or empty.
   */
  public String getSigningKeyFilename() {
    return params.get(ParamName.SIGNING_KEY_FILENAME, String.class);
  }

  /**
   * @return The port of the stunnel service that is forwarding to the security manager.
   */
  public int getStunnelPort() {
    return params.get(ParamName.STUNNEL_PORT, Integer.class);
  }

  @Override
  public String toString() {
    return ConfigSingleton.getGson().toJson(this);
  }

  @Override
  public synchronized boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof SecurityManagerConfig)) { return false; }
    SecurityManagerConfig other = (SecurityManagerConfig) object;
    return Objects.equal(getVersion(), other.getVersion())
        && Objects.equal(getCredentialGroups(), other.getCredentialGroups())
        && Objects.equal(getParams(), other.getParams())
        && Objects.equal(getFlexAuthorizer(), other.getFlexAuthorizer());
  }

  @Override
  public synchronized int hashCode() {
    return Objects.hashCode(getVersion(), getCredentialGroups(), getParams(), getFlexAuthorizer());
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(SecurityManagerConfig.class,
        ProxyTypeAdapter.make(SecurityManagerConfig.class, LocalProxy.class));
    builder.registerTypeAdapter(new TypeToken<ImmutableList<CredentialGroup>>() {}.getType(),
        TypeAdapters.immutableList());
  }

  private static final class LocalProxy implements TypeProxy<SecurityManagerConfig> {
    int version;
    @SerializedName("CGs") ImmutableList<CredentialGroup> credentialGroups;
    ConfigParams params;
    @SerializedName("flexAuthz") FlexAuthorizer flexAuthorizer;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(SecurityManagerConfig config) {
      version = config.getVersion();
      credentialGroups = config.getCredentialGroups();
      params = config.getParams();
      flexAuthorizer = config.getFlexAuthorizer();
    }

    @Override
    public SecurityManagerConfig build() {
      return makeInternal(version, credentialGroups, params, flexAuthorizer);
    }
  }
}
