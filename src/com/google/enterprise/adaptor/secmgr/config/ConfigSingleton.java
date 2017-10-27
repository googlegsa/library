// Copyright 2008 Google Inc.
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

package com.google.enterprise.adaptor.secmgr.config;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A singleton class to access configured parameters.
 */
@ThreadSafe
public class ConfigSingleton {
  private static final Logger LOGGER = Logger.getLogger(ConfigSingleton.class.getName());

  @GuardedBy("class") private static Gson gson;

  private ConfigSingleton() {
  }

  public static synchronized void setGsonRegistrations(GsonRegistrations registrations) {
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    registrations.register(builder);
    gson = builder.create();
  }

  /** A type to use for passing in Gson registrations. */
  public interface GsonRegistrations {
    public void register(GsonBuilder builder);
  }

  public static synchronized Gson getGson() {
    Preconditions.checkNotNull(gson);
    return gson;
  }
}
