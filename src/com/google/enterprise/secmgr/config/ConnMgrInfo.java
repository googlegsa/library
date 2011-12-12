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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.secmgr.json.TypeAdapters;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * A structure describing a set of configured connector managers.
 */
@Immutable
@ParametersAreNonnullByDefault
public final class ConnMgrInfo {

  /**
   * A structure describing a single connector manager.
   */
  @Immutable
  @ParametersAreNonnullByDefault
  public static final class Entry {
    @Nonnull private final String name;
    @Nonnull private final String url;

    private Entry(String name, String url) {
      this.name = name;
      this.url = url;
    }

    /**
     * Makes a new connector-manager entry.
     *
     * @param name The name of the connector manager.
     * @param url The URL for the connector manager.
     * @return An entry containing the above information.
     */
    @Nonnull
    public static Entry make(String name, String url) {
      Preconditions.checkNotNull(name);
      Preconditions.checkNotNull(url);
      return new Entry(name, url);
    }

    /**
     * Gets the name of the connector manager represented by this entry.
     *
     * @return The connector-manager name.
     */
    @Nonnull
    public String getName() {
      return name;
    }

    /**
     * Gets the URL of the connector manager represented by this entry.
     *
     * @return The connector-manager URL as a string.
     */
    @Nonnull
    public String getUrl() {
      return url;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) { return true; }
      if (!(object instanceof Entry)) { return false; }
      Entry other = (Entry) object;
      return Objects.equal(getName(), other.getName())
          && Objects.equal(getUrl(), other.getUrl());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(getName(), getUrl());
    }

    @Override
    public String toString() {
      return ConfigSingleton.getGson().toJson(this);
    }
  }

  @Nonnull private final ImmutableSet<Entry> entries;

  private ConnMgrInfo(ImmutableSet<Entry> entries) {
    this.entries = entries;
  }

  /**
   * Makes a new connector-manager info object.
   *
   * @param entries The per-connector-manager entries.
   * @return A new set with the given entries.
   */
  @Nonnull
  public static ConnMgrInfo make(Iterable<Entry> entries) {
    return new ConnMgrInfo(ImmutableSet.copyOf(entries));
  }

  /**
   * Gets the per-connector-manager entries for this object.
   *
   * @return The entries as an immutable set.
   */
  @Nonnull
  public ImmutableSet<Entry> getEntries() {
    return entries;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof ConnMgrInfo)) { return false; }
    ConnMgrInfo other = (ConnMgrInfo) object;
    return Objects.equal(getEntries(), other.getEntries());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getEntries());
  }

  @Override
  public String toString() {
    return ConfigSingleton.getGson().toJson(this);
  }

  /**
   * Decodes string-encoded connector-manager info.
   *
   * @param string The encoded info.
   * @return The decoded info.
   */
  public static ConnMgrInfo valueOf(String string) {
    return ConfigSingleton.getGson().fromJson(string, ConnMgrInfo.class);
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(new TypeToken<ImmutableSet<Entry>>() {}.getType(),
        TypeAdapters.immutableSet());
  }
}
