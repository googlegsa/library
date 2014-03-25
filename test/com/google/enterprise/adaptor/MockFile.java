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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Arrays;

/** Mock File for testing file-related code paths. */
class MockFile extends File {
  private static final Charset CHARSET = Charset.forName("UTF-8");

  private String fileContents = "";
  private long lastModified;
  private boolean exists = true;
  private boolean isFile = true;
  private File[] children;

  public MockFile(String name) {
    super(name);
  }

  public Reader createReader() {
    if (!exists || !isFile) {
      throw new IllegalStateException("File does not exist");
    }
    return new StringReader(fileContents);
  }

  public InputStream createInputStream() {
    if (!exists || !isFile) {
      throw new IllegalStateException("File does not exist");
    }
    return new ByteArrayInputStream(fileContents.getBytes(CHARSET));
  }

  public MockFile setFileContents(String fileContents) {
    this.fileContents = fileContents;
    return this;
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

  public MockFile setExists(boolean exists) {
    this.exists = exists;
    return this;
  }

  @Override
  public boolean isFile() {
    return exists && isFile;
  }

  @Override
  public boolean isDirectory() {
    return exists && !isFile;
  }

  /** Marks the file and a directory, with the provided children. */
  public MockFile setChildren(File[] children) {
    isFile = false;
    this.children = children;
    return this;
  }

  @Override
  public File[] listFiles() {
    if (!exists || isFile) {
      return null;
    }
    return Arrays.copyOf(children, children.length);
  }
}
