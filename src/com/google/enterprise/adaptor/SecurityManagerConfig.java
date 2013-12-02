// Copyright 2011 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor;

import com.google.enterprise.secmgr.authncontroller.ExportedState;
import com.google.enterprise.secmgr.config.ConfigSingleton;
import com.google.gson.GsonBuilder;

class SecurityManagerConfig {
  static {
    ConfigSingleton.setGsonRegistrations(
        new ConfigSingleton.GsonRegistrations() {
          @Override
          public void register(GsonBuilder builder) {
            ExportedState.registerTypeAdapters(builder);
          }
        });
  }

  /**
   * Does nothing, but provides a straight-forward way of initializing the class
   * (for static functionality to run).
   */
  public static void load() {}
}
