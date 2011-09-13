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
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeAdapters;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.Iterator;

import javax.annotation.concurrent.Immutable;

/**
 * An implementation of Set&lt;String&gt; with a reliable string representation.
 */
@Immutable
public final class StringSet implements Iterable<String> {

  private final ImmutableSet<String> contents;

  private StringSet(ImmutableSet<String> contents) {
    this.contents = contents;
  }

  /**
   * Makes a new string set.
   *
   * @param contents The contents of the new set.
   * @return A new set with the given contents.
   */
  public static StringSet make(Iterable<String> contents) {
    return new StringSet(ImmutableSet.copyOf(contents));
  }

  /**
   * Makes a new string set.
   *
   * @param contents The contents of the new set.
   * @return A new set with the given contents.
   */
  public static StringSet make(String... contents) {
    return new StringSet(ImmutableSet.copyOf(contents));
  }

  @Override
  public Iterator<String> iterator() {
    return contents.iterator();
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) { return true; }
    if (!(object instanceof StringSet)) { return false; }
    StringSet other = (StringSet) object;
    return Objects.equal(contents, other.contents);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(contents);
  }

  @Override
  public String toString() {
    return ConfigSingleton.getGson().toJson(this);
  }

  /**
   * Decodes a string-encoded string set.
   *
   * @param string The encoded string set.
   * @return The decoded string set.
   */
  public static StringSet valueOf(String string) {
    return ConfigSingleton.getGson().fromJson(string, StringSet.class);
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(StringSet.class,
        ProxyTypeAdapter.make(StringSet.class, LocalProxy.class));
    builder.registerTypeAdapter(new TypeToken<ImmutableSet<String>>() {}.getType(),
        TypeAdapters.immutableSet());
  }

  private static final class LocalProxy implements TypeProxy<StringSet> {
    ImmutableSet<String> contents;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(StringSet set) {
      contents = set.contents;
    }

    @Override
    public StringSet build() {
      return make(contents);
    }
  }
}
