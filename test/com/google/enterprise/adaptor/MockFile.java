// Copyright 2013 Google Inc. All Rights Reserved.
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

/** Mock File for testing file-related code paths. */
class MockFile extends File {
  private String fileContents = "";
  private long lastModified;
  private boolean exists = true;

  public MockFile(String name) {
    super(name);
  }

  public Reader createReader() {
    if (!exists) {
      throw new IllegalStateException("File does not exist");
    }
    return new StringReader(fileContents);
  }

  public void setFileContents(String fileContents) {
    this.fileContents = fileContents;
  }

  @Override
  public long lastModified() {
    if (!exists) {
      return 0;
    }
    return lastModified;
  }

  @Override
  public boolean setLastModified(long time) {
    this.lastModified = time;
    return exists;
  }

  @Override
  public boolean exists() {
    return exists;
  }

  public void setExists(boolean exists) {
    this.exists = exists;
  }

  @Override
  public boolean isFile() {
    // This only mocks files, not directories and the like.
    return exists;
  }
}
