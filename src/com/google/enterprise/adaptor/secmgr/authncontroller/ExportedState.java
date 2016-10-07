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

package com.google.enterprise.adaptor.secmgr.authncontroller;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.secmgr.config.ConfigSingleton;
import com.google.enterprise.adaptor.secmgr.identity.Group;
import com.google.enterprise.adaptor.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.adaptor.secmgr.json.TypeAdapters;
import com.google.enterprise.adaptor.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * This is the state exported from the security manager to its clients after a
 * successful authentication. It is also the format that the security manager
 * accepts from client SAML providers if they choose to provide this
 * information.
 */
@Immutable
@ParametersAreNonnullByDefault
public final class ExportedState {
  @Nonnull public static final String ATTRIBUTE_NAME = "SecurityManagerState";
  @Nonnegative public static final int CURRENT_VERSION = 1;
  @Nonnegative public static final int MIN_VERSION = 1;
  @Nonnegative public static final int MAX_VERSION = 1;

  /**
   * This class represents simple credentials without the complex structure used
   * by the security manager.
   */
  @Immutable
  @ParametersAreNonnullByDefault
  public static final class Credentials {
    @Nonnull public static final Credentials EMPTY
        = new Credentials(null, null, null, null, ImmutableSet.<Group>of());

    @Nullable private final String username;
    @Nullable private final String domain;
    @Nullable private final String password;
    @Nullable private final String namespace;
    @Nonnull private final ImmutableSet<Group> groups;

    private Credentials(@Nullable String username, @Nullable String domain,
        @Nullable String password, @Nullable String namespace, ImmutableSet<Group> groups) {
      this.username = username;
      this.domain = domain;
      this.password = password;
      this.namespace = namespace;
      this.groups = groups;
    }

    /**
     * Gets a new credentials instance.
     *
     * @param username The username credential or {@code null}.
     * @param domain The domain credential or {@code null}.
     * @param password The password credential or {@code null}.
     * @param namespace The namespace credential or {@code null}.
     * @param groups The group credentials.
     * @return An immutable structure of the given credentials.
     */
    @CheckReturnValue
    @Nonnull
    public static Credentials make(@Nullable String username, @Nullable String domain,
        @Nullable String password, @Nullable String namespace, Iterable<Group> groups) {
      return new Credentials(username, domain, password, namespace, ImmutableSet.copyOf(groups));
    }

    /**
     * Gets a new credentials instance with no groups.
     *
     * @param username The username credential or {@code null}.
     * @param domain The domain credential or {@code null}.
     * @param password The password credential or {@code null}.
     * @param namespace The namespace credential or {@code null}.
     * @return An immutable structure of the given credentials.
     */
    @CheckReturnValue
    @Nonnull
    public static Credentials make(@Nullable String username, @Nullable String domain,
        @Nullable String password, @Nullable String namespace) {
      return make(username, domain, password, namespace, ImmutableSet.<Group>of());
    }

    /**
     * Gets this instance's username.
     */
    @CheckReturnValue
    @Nullable
    public String getUsername() {
      return username;
    }

    /**
     * Gets this instance's domain.
     */
    @CheckReturnValue
    @Nullable
    public String getDomain() {
      return domain;
    }

    /**
     * Gets this instance's namespace.
     */
    @Nullable
    public String getNamespace() {
      return namespace;
    }

    /**
     * Gets this instance's password.
     */
    @CheckReturnValue
    @Nullable
    public String getPassword() {
      return password;
    }

    /**
     * Gets this instance's groups as an immutable set.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableSet<Group> getGroups() {
      return groups;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) {
        return true;
      }
      if (!(object instanceof Credentials)) {
        return false;
      }
      Credentials other = (Credentials) object;
      return Objects.equal(getUsername(), other.getUsername())
          && Objects.equal(getDomain(), other.getDomain())
          && Objects.equal(getNamespace(), other.getNamespace())
          && Objects.equal(getPassword(), other.getPassword())
          && Objects.equal(getGroups(), other.getGroups());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(getUsername(), getDomain(), getNamespace(),
          getPassword(), getGroups());
    }

    @Override
    public String toString() {
      return ConfigSingleton.getGson().toJson(this);
    }

    private static final class LocalProxy implements TypeProxy<Credentials> {
      String username;
      String domain;
      String password;
      String name_space;
      ImmutableSet<Group> groups;

      @SuppressWarnings("unused")
      LocalProxy() {
      }

      @SuppressWarnings("unused")
      LocalProxy(Credentials credentials) {
        username = credentials.getUsername();
        domain = credentials.getDomain();
        name_space = credentials.getNamespace();
        password = credentials.getPassword();
        groups = credentials.getGroups();
      }

      @Override
      public Credentials build() {
        return Credentials.make(username, domain, password, name_space, groups);
      }
    }
  }

  @Nonnegative private final int version;
  @Nonnegative private final long timeStamp;
  @Nonnull private final Credentials pviCredentials;
  @Nonnull private final Credentials basicCredentials;
  @Nonnull private final ImmutableList<Credentials> verifiedCredentials;

  private ExportedState(@Nonnegative int version, @Nonnegative long timeStamp,
      Credentials pviCredentials, Credentials basicCredentials,
      ImmutableList<Credentials> verifiedCredentials) {
    this.version = version;
    this.timeStamp = timeStamp;
    this.pviCredentials = pviCredentials;
    this.basicCredentials = basicCredentials;
    this.verifiedCredentials = verifiedCredentials;
  }

  /**
   * Gets the time at which this state was frozen.
   */
  @CheckReturnValue
  @Nonnegative
  public long getTimeStamp() {
    return timeStamp;
  }

  /**
   * Gets the credentials for the Primary Verified Identity (PVI).
   */
  @CheckReturnValue
  @Nonnull
  public Credentials getPviCredentials() {
    return pviCredentials;
  }

  /**
   * If the security manager is configured for HTTP Basic or NTLM
   * authentication, this gets the credentials for that mechanism.
   */
  @CheckReturnValue
  @Nonnull
  public Credentials getBasicCredentials() {
    return basicCredentials;
  }
  
  /**
   * Returns all non-connector verified credentials from the security manager,
   * or an empty list if there is none.
   */
  @Nonnull
  public ImmutableList<Credentials> getAllVerifiedCredentials() {
    return verifiedCredentials;
  }

  /**
   * Gets a JSON string representation for this object.
   */
  @CheckReturnValue
  @Nonnull
  public String toJsonString() {
    return ConfigSingleton.getGson().toJson(this);
  }

  /**
   * Decodes a JSON string representation into an exported-state object.
   */
  @CheckReturnValue
  @Nonnull
  public static ExportedState fromJsonString(String jsonString) {
    return ConfigSingleton.getGson().fromJson(jsonString, ExportedState.class);
  }

  public static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(Credentials.class,
        ProxyTypeAdapter.make(Credentials.class, Credentials.LocalProxy.class));
    builder.registerTypeAdapter(ExportedState.class,
        ProxyTypeAdapter.make(ExportedState.class, LocalProxy.class));
    builder.registerTypeAdapter(new TypeToken<ImmutableSet<Group>>() {}.getType(),
        TypeAdapters.immutableSet());
    builder.registerTypeAdapter(new TypeToken<ImmutableList<Credentials>>() {}.getType(),
        TypeAdapters.immutableList());
  }

  private static final class LocalProxy implements TypeProxy<ExportedState> {
    int version;
    long timeStamp;
    Credentials pviCredentials;
    Credentials basicCredentials;
    ImmutableList<Credentials> verifiedCredentials;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(ExportedState state) {
      version = state.version;
      timeStamp = state.timeStamp;
      pviCredentials = state.getPviCredentials();
      basicCredentials = state.getBasicCredentials();
      verifiedCredentials = state.getAllVerifiedCredentials();
    }

    @Override
    public ExportedState build() {
      Preconditions.checkArgument(version >= MIN_VERSION && version <= MAX_VERSION);
      Preconditions.checkArgument(timeStamp >= 0);
      return new ExportedState(version, timeStamp, pviCredentials, basicCredentials,
          ImmutableList.copyOf(verifiedCredentials));
    }
  }
}
