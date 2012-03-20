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

import java.io.*;
import java.util.*;

/**
 * Mock of {@link Config}.
 */
public class MockConfig extends Config {
  protected Properties config = new Properties();

  public MockConfig() {
  }

  public Set<String> getAllKeys() {
    return config.stringPropertyNames();
  }

  public String getValue(String key) {
    String value = config.getProperty(key);
    if (value == null) {
      throw new IllegalStateException("unknown key");
    }
    return value;
  }

  public void load(Reader configFile) throws IOException  {
    throw new UnsupportedOperationException();
  }

  public void loadDefaultConfigFile() {}

  public String[] autoConfig(String[] args) {
    return args;
  }

  public void addKey(String key, String defaultValue) {
  }

  /** Normal way to add things to this Mock */
  public void setKey(String key, String value) {
    config.setProperty(key, value);
  }
}
