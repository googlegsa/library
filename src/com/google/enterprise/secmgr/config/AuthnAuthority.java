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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.enterprise.secmgr.common.SecurityManagerUtil;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An authentication authority describes an authority that validates credentials.
 */
@ThreadSafe
@ParametersAreNonnullByDefault
public class AuthnAuthority {
  @GuardedBy("itself")
  @Nonnull static final Map<URI, AuthnAuthority> AUTHORITIES = Maps.newHashMap();

  @Nonnull private final URI uri;

  private AuthnAuthority(URI uri) {
    this.uri = uri;
  }

  /**
   * Makes an authority.
   *
   * @param uri The URI for this authority.
   * @return The unique authority with that URI, creating it if necessary.
   * @throws IllegalArgumentException if the URI is invalid.
   */
  @CheckReturnValue
  @Nonnull
  public static AuthnAuthority make(URI uri) {
    checkUri(uri);
    AuthnAuthority authority;
    synchronized (AUTHORITIES) {
      authority = AUTHORITIES.get(uri);
      if (authority == null) {
        authority = new AuthnAuthority(uri);
        AUTHORITIES.put(uri, authority);
      }
    }
    return authority;
  }

  private static void checkUri(URI uri) {
    Preconditions.checkNotNull(uri);
    Preconditions.checkArgument(uri.isAbsolute());
    Preconditions.checkArgument(!uri.isOpaque());
    Preconditions.checkArgument(uri.getQuery() == null);
    Preconditions.checkArgument(uri.getFragment() == null);
  }

  static AuthnAuthority make(String configName) {
    return make(SecurityManagerUtil.smUriBuilder().addSegment(configName).build());
  }

  @VisibleForTesting
  public static AuthnAuthority make() {
    return make(SecurityManagerUtil.smUriBuilder().addRandomSegment(16).build());
  }

  /**
   * Gets a globally-unique URI for this authority.
   *
   * @return The authority's URI.
   */
  @CheckReturnValue
  @Nonnull
  public URI getUri() {
    return uri;
  }

  /**
   * Gets a globally-unique name for this authority.  Equivalent to
   * {@code getUri().toString()}.
   *
   * @return The authority's name.
   */
  @CheckReturnValue
  @Nonnull
  public String getName() {
    return uri.toString();
  }

  /**
   * Gets an authority given its URI.
   *
   * @param uri The URI of the authority to find.
   * @return The corresponding authority, or {@code null} if no authority has that URI.
   */
  @CheckReturnValue
  @Nullable
  public static AuthnAuthority lookupByUri(URI uri) {
    checkUri(uri);
    synchronized (AUTHORITIES) {
      return AUTHORITIES.get(uri);
    }
  }

  /**
   * Gets an authority given its name.
   *
   * @param name The name of the authority to find.
   * @return The corresponding authority, or {@code null} if no authority has that name.
   */
  @CheckReturnValue
  @Nullable
  public static AuthnAuthority lookupByName(String name) {
    return lookupByUri(URI.create(name));
  }

  /**
   * Gets an authority given its name.
   *
   * @param name The name of the authority to find.
   * @return The corresponding authority.
   * @throw IllegalArgumentException if no such authority.
   */
  @CheckReturnValue
  @Nonnull
  public static AuthnAuthority getByName(String name) {
    AuthnAuthority authority = lookupByName(name);
    if (authority == null) {
      throw new IllegalArgumentException("No authority of this name: " + name);
    }
    return authority;
  }

  @VisibleForTesting
  public static void clearAuthorities() {
    synchronized (AUTHORITIES) {
      AUTHORITIES.clear();
    }
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof AuthnAuthority)) { return false; }
    AuthnAuthority other = (AuthnAuthority) object;
    return getUri().equals(other.getUri());
  }

  @Override
  public int hashCode() {
    return getUri().hashCode();
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   * Gets a predicate that is true for an authority with a given URI.
   *
   * @param uri The authority URI to test for.
   * @return The predicate corresponding to the name.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<AuthnAuthority> getUriPredicate(final URI uri) {
    return new Predicate<AuthnAuthority>() {
      public boolean apply(AuthnAuthority authority) {
        return uri.equals(authority.getUri());
      }
    };
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(AuthnAuthority.class, new LocalTypeAdapter());
  }

  private static final class LocalTypeAdapter
      implements JsonSerializer<AuthnAuthority>, JsonDeserializer<AuthnAuthority> {
    LocalTypeAdapter() {
    }

    @Override
    public JsonElement serialize(AuthnAuthority value, Type type,
        JsonSerializationContext context) {
      return context.serialize(value.getName(), String.class);
    }

    @Override
    public AuthnAuthority deserialize(JsonElement elt, Type type,
        JsonDeserializationContext context) {
      String string = context.deserialize(elt, String.class);
      return make(URI.create(string));
    }
  }
}
