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

package com.google.enterprise.secmgr.common;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import org.joda.time.DateTimeUtils;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An implementation of a mutable cookie store that automatically expires
 * cookies whenever a cookie is added or the store is iterated.
 */
@NotThreadSafe
@ParametersAreNonnullByDefault
final class CookieStoreImpl extends AbstractCollection<GCookie>
    implements CookieStore {

  @Nonnull private final Map<GCookie.Key, GCookie> map;

  CookieStoreImpl() {
    map = Maps.newHashMap();
  }

  @Override
  public int size() {
    return map.size();
  }

  // For a given cookie name, a store can contain exactly one partial key with
  // that name, or no partial keys and any number of full keys.
  @Override
  public boolean add(GCookie cookie) {
    GCookie.Key key = cookie.getKey();
    GCookie oldCookie = map.get(key);
    if (cookie.equals(oldCookie)) {
      return false;
    }
    Iterables.removeIf(this, matchingKeyPredicate(key));
    if (oldCookie != null) {
      map.put(key, GCookie.builder(cookie).setCreationTime(oldCookie.getCreationTime()).build());
    } else {
      map.put(key, cookie);
    }
    return true;
  }

  private static Predicate<GCookie> matchingKeyPredicate(final GCookie.Key key) {
    return key.isPartial()
      ? new Predicate<GCookie>() {
          @Override
          public boolean apply(GCookie cookie) {
            GCookie.Key other = cookie.getKey();
            return key.getName().equals(other.getName());
          }
        }
      : new Predicate<GCookie>() {
          @Override
          public boolean apply(GCookie cookie) {
            GCookie.Key other = cookie.getKey();
            return key.getName().equals(other.getName())
                && (other.isPartial() || key.equals(other));
          }
        };
  }

  @Override
  public Iterator<GCookie> iterator() {
    return map.values().iterator();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof Iterable<?>)) { return false; }
    return ImmutableSet.copyOf(map.values()).equals(ImmutableSet.copyOf((Iterable<?>) object));
  }

  @Override
  public int hashCode() {
    return ImmutableSet.copyOf(map.values()).hashCode();
  }

  @Override
  public void expireCookies(@Nonnegative long timeStamp) {
    Iterables.removeIf(this, GCookie.isExpiredPredicate(timeStamp));
  }

  @Override
  public void expireCookies() {
    expireCookies(DateTimeUtils.currentTimeMillis());
  }

  @Override
  public boolean contains(String name) {
    return get(name) != null;
  }

  @Override
  @Nullable
  public GCookie get(String name) {
    Preconditions.checkNotNull(name);
    for (GCookie cookie : map.values()) {
      if (name.equalsIgnoreCase(cookie.getName())) {
        return cookie;
      }
    }
    return null;
  }

  @Override
  public boolean contains(GCookie.Key key) {
    return get(key) != null;
  }

  @Override
  @Nullable
  public GCookie get(GCookie.Key key) {
    Preconditions.checkNotNull(key);
    return map.get(key);
  }
}
