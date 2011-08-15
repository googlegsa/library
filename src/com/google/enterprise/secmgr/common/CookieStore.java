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

import java.util.Collection;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A cookie store is an object that holds a collection of cookies.  The store
 * never holds two cookies with the same key.  When a cookie is added to a
 * store, if there's already a cookie in the store with that key, the new cookie
 * replaces the old one.
 * <p>
 * Because of this, the store may contain multiple cookies with the same name,
 * as long as the other parts of their keys differ.
 */
@ParametersAreNonnullByDefault
public interface CookieStore extends Collection<GCookie> {
  /**
   * Removes all cookies with expiration times earlier than a given time.
   *
   * @param timeStamp A time to use for expirations.
   */
  public void expireCookies(@Nonnegative long timeStamp);

  /**
   * Removes all cookies with expiration times earlier than the current time.
   */
  public void expireCookies();

  /**
   * Does this store contain a cookie with a given name?
   *
   * @param name The name to test for.
   * @return True only if there's at least one cookie with that name in the
   *     store.
   */
  @CheckReturnValue
  public boolean contains(String name);

  /**
   * If this store contains a cookie with a given name, gets it.  If there are
   * multiple cookies with that name, one of them is arbitrarily chosen.
   *
   * @param name The name to look for.
   * @return A cookie with that name, or {@code null} if there are none.
   */
  @CheckReturnValue
  @Nullable
  public GCookie get(String name);

  /**
   * Does this store contain a cookie with a given key?
   *
   * @param key The key to test for.
   * @return True only if there's a cookie with that key in the store.
   */
  @CheckReturnValue
  public boolean contains(GCookie.Key key);

  /**
   * If this store contains a cookie with a given key, gets it.
   *
   * @param key The key to look for.
   * @return The cookie with that key, or {@code null} if there isn't one.
   */
  @CheckReturnValue
  @Nullable
  public GCookie get(GCookie.Key key);
}
