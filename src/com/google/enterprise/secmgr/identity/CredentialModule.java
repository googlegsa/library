/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.enterprise.secmgr.identity;

import com.google.common.collect.ImmutableList;
import com.google.enterprise.secmgr.json.TypeAdapters;
import com.google.gson.GsonBuilder;

/**
 * A module for top-level configuration of this package.
 */
public final class CredentialModule {

  // Don't instantiate.
  private CredentialModule() {
    throw new UnsupportedOperationException();
  }

  public static void registerTypeAdapters(GsonBuilder builder) {
    AuthnPrincipal.registerTypeAdapters(builder);
    CredPassword.registerTypeAdapters(builder);
    GroupMemberships.registerTypeAdapters(builder);
    Verification.registerTypeAdapters(builder);
    builder.registerTypeAdapter(Credential.class,
        TypeAdapters.dispatch(
            ImmutableList.<Class<? extends Credential>>of(
                AuthnPrincipal.class,
                CredPassword.class,
                GroupMemberships.class)));
  }
}
