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

package com.google.enterprise.adaptor.secmgr.modules;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.enterprise.adaptor.secmgr.common.AuthzStatus;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Instances of this class are the return value from an authorization process.
 * At the moment this is just some fancy constructors that create a delegated
 * map.
 */
@Immutable
public final class AuthzResult implements Map<String, AuthzStatus> {
  private final ImmutableMap<String, AuthzStatus> statusMap;
  private final ImmutableSet<String> cacheResults;

  private AuthzResult(ImmutableMap<String, AuthzStatus> statusMap,
      ImmutableSet<String> determinedByCache) {
    this.statusMap = statusMap;
    this.cacheResults = determinedByCache;
  }

  /**
   * Get an {@link AuthzResult} builder that has a minimum set of resources.
   * This builder generates an AuthzResult instance that's guaranteed to have a
   * status for every resource in {@code resources}; those that aren't
   * explicitly set in the builder will map to {@link
   * AuthzStatus#INDETERMINATE}.
   *
   * @param resources The minimum set of resources to use for the returned
   *     builder.
   * @return An {@link AuthzResult} builder.
   */
  public static Builder builder(Iterable<String> resources) {
    Preconditions.checkNotNull(resources);
    return new Builder(resources);
  }

  /**
   * Get an {@link AuthzResult} builder that expands on a previous result.
   *
   * @param map The previous result.
   * @return An {@link AuthzResult} builder.
   */
  public static Builder builder(Map<String, AuthzStatus> map) {
    Preconditions.checkNotNull(map);
    return new Builder(map);
  }

  /**
   * Get an {@link AuthzResult} builder that has a no minimum set of resources;
   * this is equivalent to passing an empty set of resources as an argument.
   *
   * @return An {@link AuthzResult} builder.
   */
  public static Builder builder() {
    ImmutableList<String> resources = ImmutableList.of();
    return new Builder(resources);
  }

  /**
   * Find out if a resource's determination (PERMIT or DENY) came from
   * UserCacheConnector.
   *
   * @return True if resource's determiantion came from UserCacheConnector.
   */
  public boolean wasDeterminedByCache(String resource) {
    return cacheResults.contains(resource);
  }

  /**
   * A builder class for AuthzResult instances.
   */
  @NotThreadSafe
  public static final class Builder {

    private final Map<String, AuthzStatus> map;
    private final ImmutableSet.Builder<String> determinedByCache; // PERMIT or DENY.

    private Builder(Iterable<String> resources) {
      map = Maps.newHashMap();
      for (String resource : resources) {
        map.put(resource, AuthzStatus.INDETERMINATE);
      }
      determinedByCache = ImmutableSet.builder();
    }

    private Builder(Map<String, AuthzStatus> map) {
      this.map = Maps.newHashMap(map);
      determinedByCache = ImmutableSet.builder();
    }

    /**
     * Add an entry to the builder.
     *
     * @param resource The resource for the new entry.
     * @param status The status for the new entry.
     * @return The builder, for convenience.
     */
    public Builder put(String resource, AuthzStatus status) {
      Preconditions.checkNotNull(resource);
      Preconditions.checkNotNull(status);
      map.put(resource, status);
      return this;
    }

    /**
     * Add PERMIT or DENY from cache to the builder.
     *
     * @param resource The resource for the new entry.
     * @param status The status for the new entry.
     * @return The builder, for convenience.
     */
    public Builder putStatusFromCache(String resource, AuthzStatus status) {
      Preconditions.checkNotNull(resource);
      Preconditions.checkNotNull(status);
      map.put(resource, status);
      if (AuthzStatus.INDETERMINATE != status) {
        determinedByCache.add(resource);
      }
      return this;
    }

    /**
     * Add an entry to the builder.
     *
     * @param entry The entry to add.
     * @return The builder, for convenience.
     */
    public Builder add(Map.Entry<String, AuthzStatus> entry) {
      Preconditions.checkNotNull(entry);
      return put(entry.getKey(), entry.getValue());
    }

    /**
     * Add a map of entries to the builder.
     *
     * @param map The map to add.
     * @return The builder, for convenience.
     */
    public Builder addAll(Map<String, AuthzStatus> map) {
      Preconditions.checkNotNull(map);
      for (Map.Entry<String, AuthzStatus> entry : map.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
      return this;
    }

    /**
     * @return The {@link AuthzResult} instance that's been constructed by this
     *     builder.  Calling this method more than once on the same builder
     *     instance is not guaranteed to work.
     */
    public AuthzResult build() {
      return new AuthzResult(ImmutableMap.copyOf(map), determinedByCache.build());
    }
  }

  /**
   * Make an {@link AuthzResult} in which all of the resources have
   * indeterminate status.
   *
   * @param resources The resources in the result.
   * @return An AuthzResult instance.
   */
  public static AuthzResult makeIndeterminate(Iterable<String> resources) {
    return builder(resources).build();
  }

  /**
   * @return An empty {@link AuthzResult}.
   */
  public static AuthzResult of() {
    return builder().build();
  }

  /**
   * Get an {@link AuthzResult} with a single entry.
   *
   * @param r1 The single resource.
   * @param s1 The status for that resource.
   * @return The result instance.
   */
  public static AuthzResult of(String r1, AuthzStatus s1) {
    return AuthzResult.builder()
        .put(r1, s1)
        .build();
  }

  /**
   * Get an {@link AuthzResult} with two entries.
   *
   * @param r1 The first resource.
   * @param s1 The status for the first resource.
   * @param r2 The second resource.
   * @param s2 The status for the second resource.
   * @return The result instance.
   */
  public static AuthzResult of(String r1, AuthzStatus s1, String r2, AuthzStatus s2) {
    return AuthzResult.builder()
        .put(r1, s1)
        .put(r2, s2)
        .build();
  }

  /**
   * Get an {@link AuthzResult} with three entries.
   *
   * @param r1 The first resource.
   * @param s1 The status for the first resource.
   * @param r2 The second resource.
   * @param s2 The status for the second resource.
   * @param r3 The third resource.
   * @param s3 The status for the third resource.
   * @return The result instance.
   */
  public static AuthzResult of(String r1, AuthzStatus s1, String r2, AuthzStatus s2,
      String r3, AuthzStatus s3) {
    return AuthzResult.builder()
        .put(r1, s1)
        .put(r2, s2)
        .put(r3, s3)
        .build();
  }

  /**
   * Get an {@link AuthzResult} with four entries.
   *
   * @param r1 The first resource.
   * @param s1 The status for the first resource.
   * @param r2 The second resource.
   * @param s2 The status for the second resource.
   * @param r3 The third resource.
   * @param s3 The status for the third resource.
   * @param r4 The fourth resource.
   * @param s4 The status for the fourth resource.
   * @return The result instance.
   */
  public static AuthzResult of(String r1, AuthzStatus s1, String r2, AuthzStatus s2,
      String r3, AuthzStatus s3, String r4, AuthzStatus s4) {
    return AuthzResult.builder()
        .put(r1, s1)
        .put(r2, s2)
        .put(r3, s3)
        .put(r4, s4)
        .build();
  }

  @Override
  public Set<String> keySet() {
    return statusMap.keySet();
  }

  @Override
  public Set<Map.Entry<String, AuthzStatus>> entrySet() {
    return statusMap.entrySet();
  }

  @Override
  public int size() {
    return statusMap.size();
  }

  @Override
  public AuthzStatus get(Object resource) {
    return statusMap.get(resource);
  }

  @Override
  public boolean containsKey(Object key) {
    return statusMap.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return statusMap.containsValue(value);
  }

  @Override
  public boolean isEmpty() {
    return statusMap.isEmpty();
  }

  @Override
  public Collection<AuthzStatus> values() {
    return statusMap.values();
  }

  @Override
  public boolean equals(Object object) {
    return statusMap.equals(object);
  }

  @Override
  public int hashCode() {
    return statusMap.hashCode();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AuthzStatus put(String key, AuthzStatus value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(Map<? extends String, ? extends AuthzStatus> m) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AuthzStatus remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    boolean firstElt = true;
    builder.append("{");
    for (String resource : Ordering.natural().sortedCopy(statusMap.keySet())) {
      if (!firstElt) {
        builder.append(',');
      } else {
        firstElt = false;
      }
      builder.append("\n  \"");
      builder.append(resource);
      builder.append("\", ");
      builder.append(statusMap.get(resource));
    }
    builder.append("\n}");
    return builder.toString();
  }
}
