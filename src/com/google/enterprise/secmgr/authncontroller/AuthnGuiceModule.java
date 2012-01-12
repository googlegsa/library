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

package com.google.enterprise.secmgr.authncontroller;

import com.google.gson.GsonBuilder;
/*import com.google.inject.AbstractModule;*/

/**
 * Guice configuration for this package.
 */
public final class AuthnGuiceModule /*extends AbstractModule*/ {

  /*@Override
  protected void configure() {
    bind(AuthnController.class);
    bind(AuthnSessionManager.class).to(AuthnSessionManagerImpl.class);
    requestStaticInjection(AuthnSession.class);
  }*/

  public static void registerTypeAdapters(GsonBuilder builder) {
    AuthnSessionState.registerTypeAdapters(builder);
    ExportedState.registerTypeAdapters(builder);
  }
}
