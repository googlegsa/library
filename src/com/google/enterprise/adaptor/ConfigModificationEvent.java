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

import java.util.*;

/**
 * Represents configuration modification event information.
 */
class ConfigModificationEvent extends EventObject {
  protected Config oldConfig;
  protected Set<String> modifiedKeys;

  public ConfigModificationEvent(Config source, Config oldConfig,
                                 Set<String> modifiedKeys) {
    super(source);
    this.oldConfig = oldConfig;
    this.modifiedKeys = Collections.unmodifiableSet(modifiedKeys);
  }

  public Config getNewConfig() {
    return (Config) source;
  }

  public Config getOldConfig() {
    return oldConfig;
  }

  /**
   * Keys whose values were changed.
   */
  public Set<String> getModifiedKeys() {
    return modifiedKeys;
  }

  @Override
  public String toString() {
    return "ConfigModificationEvent(modifiedKeys: " + modifiedKeys + ")";
  }
}
