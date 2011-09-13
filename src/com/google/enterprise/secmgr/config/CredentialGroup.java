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

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeAdapters;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.Collection;
import java.util.List;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A "credential group" is a set of authentication mechanisms that are defined
 * to share a common username and password.
 */
@Immutable
public class CredentialGroup {

  public static final String DEFAULT_NAME = "Default";

  private final String name;
  private final AuthnAuthority authority;
  private final String displayName;
  private final boolean requiresUsername;   // Not satisfied until a username is known.
  private final boolean requiresPassword;   // Not satisfied until a password is known.
  private final boolean isOptional;         // Can be satisfied given blank username & password.
  private final ImmutableList<AuthnMechanism> mechanisms;

  private CredentialGroup(String name, String displayName, boolean requiresUsername,
      boolean requiresPassword, boolean isOptional, List<AuthnMechanism> mechanisms) {
    Preconditions.checkArgument(name != null && AuthnMechanism.isValidConfigName(name),
        "Invalid credential-group name: %s", name);
    this.name = name;
    authority = AuthnAuthority.make(name);
    this.displayName = (displayName != null) ? displayName : name;
    this.requiresUsername = requiresUsername;
    this.requiresPassword = requiresPassword;
    this.isOptional = isOptional;
    this.mechanisms = ImmutableList.copyOf(mechanisms);
  }

  /**
   * Get a credential-group builder.
   *
   * @param name The name of the group; used in ACLs.
   * @param displayName The name of the group as presented to end users.
   * @param requiresUsername Does the group require a verified username?
   * @param requiresPassword Does the group require a verified password?
   * @param isOptional False if the credentials for this group must be supplied.
   * @return A credential-group builder with given initial settings and no mechanisms.
   */
  public static Builder builder(String name, String displayName, boolean requiresUsername,
        boolean requiresPassword, boolean isOptional) {
    return new Builder(name, displayName, requiresUsername, requiresPassword, isOptional);
  }

  /**
   * Get a credential-group builder.
   *
   * @param group A credential group to get the initial settings from.
   * @return A credential-group builder initialized with the given credential group.
   */
  public static Builder builder(CredentialGroup group) {
    Builder newBuilder = new Builder(group.getName(), group.getDisplayName(),
        group.getRequiresUsername(), group.getRequiresPassword(), group.getIsOptional());
    newBuilder.addMechanisms(group.getMechanisms());
    return newBuilder;
  }

  /**
   * Get a credential-group builder.
   *
   * @return A credential-group builder with default initial settings and no mechanisms.
   */
  public static Builder builder() {
    return builder(DEFAULT_NAME, DEFAULT_NAME, true, false, false);
  }

  /**
   * A builder factor for credential-group instances.
   */
  @NotThreadSafe
  public static final class Builder {

    private String name;
    private String displayName;
    private boolean requiresUsername;
    private boolean requiresPassword;
    private boolean isOptional;
    private List<AuthnMechanism> mechanisms;

    private Builder(String name, String displayName, boolean requiresUsername,
        boolean requiresPassword, boolean isOptional) {
      this.name = name;
      this.displayName = displayName;
      this.requiresUsername = requiresUsername;
      this.requiresPassword = requiresPassword;
      this.isOptional = isOptional;
      this.mechanisms = Lists.newArrayList();
    }

    public String getName() {
      return name;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public Builder setDisplayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder setRequiresUsername(boolean requiresUsername) {
      this.requiresUsername = requiresUsername;
      return this;
    }

    public Builder setRequiresPassword(boolean requiresPassword) {
      this.requiresPassword = requiresPassword;
      return this;
    }

    public Builder setIsOptional(boolean isOptional) {
      this.isOptional = isOptional;
      return this;
    }

    public List<AuthnMechanism> getMechanisms() {
      return mechanisms;
    }

    public Builder addMechanism(AuthnMechanism mechanism) {
      mechanisms.add(mechanism);
      return this;
    }

    public Builder addMechanism(int index, AuthnMechanism mechanism) {
      mechanisms.add(index, mechanism);
      return this;
    }

    public Builder addMechanisms(Collection<AuthnMechanism> mechanisms) {
      this.mechanisms.addAll(mechanisms);
      return this;
    }

    public AuthnMechanism findMechanism(String name) {
      for (AuthnMechanism mechanism : mechanisms) {
        if (name.equalsIgnoreCase(mechanism.getName())) {
          return mechanism;
        }
      }
      return null;
    }

    public Builder replaceMechanism(AuthnMechanism mechanism) {
      AuthnMechanism other = findMechanism(mechanism.getName());
      Preconditions.checkArgument(other != null,
          "No existing mechanism with this name: %s", mechanism.getName());
      mechanisms.remove(other);
      mechanisms.add(mechanism);
      return this;
    }

    public CredentialGroup build() {
      return new CredentialGroup(name, displayName, requiresUsername, requiresPassword, isOptional,
          mechanisms);
    }
  }

  public String getName() {
    return name;
  }

  public AuthnAuthority getAuthority() {
    return authority;
  }

  public String getDisplayName() {
    return displayName;
  }

  public ImmutableList<AuthnMechanism> getMechanisms() {
    return mechanisms;
  }

  public boolean hasMechanismOfType(Class<? extends AuthnMechanism> type) {
    for (AuthnMechanism mech : mechanisms) {
      if (type.isInstance(mech)) {
        return true;
      }
    }
    return false;
  }

  public boolean getRequiresUsername() {
    return requiresUsername;
  }

  public boolean getRequiresPassword() {
    return requiresPassword;
  }

  public boolean getIsOptional() {
    return isOptional;
  }

  @Override
  public String toString() {
    return ConfigSingleton.getGson().toJson(this);
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof CredentialGroup)) { return false; }
    CredentialGroup other = (CredentialGroup) object;
    return Objects.equal(name, other.name)
        && Objects.equal(displayName, other.displayName)
        && Objects.equal(requiresUsername, other.requiresUsername)
        && Objects.equal(requiresPassword, other.requiresPassword)
        && Objects.equal(isOptional, other.isOptional)
        && Objects.equal(mechanisms, other.mechanisms);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, displayName, requiresUsername, requiresPassword, isOptional,
        mechanisms);
  }

  public boolean isDefault() {
    return DEFAULT_NAME.equalsIgnoreCase(name);
  }

  public boolean canUseUlfCredentials() {
    for (AuthnMechanism mech : mechanisms) {
      if (mech.canUseUlfCredentials()) {
        return true;
      }
    }
    return false;
  }

  public static final Predicate<CredentialGroup> DEFAULT_PREDICATE =
      new Predicate<CredentialGroup>() {
        public boolean apply(CredentialGroup credentialGroup) {
          return credentialGroup.isDefault();
        }
      };

  public static final Function<Builder, CredentialGroup> BUILD_FUNCTION =
      new Function<Builder, CredentialGroup>() {
        public CredentialGroup apply(Builder builder) {
          return builder.build();
        }
      };

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(CredentialGroup.class,
        ProxyTypeAdapter.make(CredentialGroup.class, LocalProxy.class));
    builder.registerTypeAdapter(new TypeToken<ImmutableList<AuthnMechanism>>() {}.getType(),
        TypeAdapters.immutableList());
  }

  private static final class LocalProxy implements TypeProxy<CredentialGroup> {
    String name;
    String displayName;
    boolean requiresUsername;
    boolean requiresPassword;
    boolean isOptional;
    ImmutableList<AuthnMechanism> mechanisms;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(CredentialGroup credentialGroup) {
      name = credentialGroup.name;
      displayName = credentialGroup.displayName;
      requiresUsername = credentialGroup.requiresUsername;
      requiresPassword = credentialGroup.requiresPassword;
      isOptional = credentialGroup.isOptional;
      mechanisms = credentialGroup.mechanisms;
    }

    @Override
    public CredentialGroup build() {
      return new CredentialGroup(name, displayName, requiresUsername, requiresPassword, isOptional,
          mechanisms);
    }
  }
}
