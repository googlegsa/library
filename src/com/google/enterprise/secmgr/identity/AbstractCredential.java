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

package com.google.enterprise.secmgr.identity;

import com.google.common.base.Predicate;
import com.google.enterprise.secmgr.config.ConfigSingleton;

/**
 * A base class for all credentials.
 *
 * @see Credential
 */
public abstract class AbstractCredential implements Credential {

  /**
   * Get a predicate for a given credential subtype.  This predicate is true
   * only of credentials of that type.
   *
   * @param clazz The class of the credential subtype to test for.
   * @return The requested predicate.
   */
  public static final Predicate<Credential> getTypePredicate(
      final Class<? extends Credential> clazz) {
    return new Predicate<Credential>() {
      public boolean apply(Credential credential) {
        return clazz.isInstance(credential);
      }
    };
  }

  @Override
  public String toString() {
    return ConfigSingleton.getGson().toJson(this);
  }
}
