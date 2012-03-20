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

package com.google.enterprise.adaptor.prebuilt;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.util.Iterator;

/**
 * Tests for {@link RecursiveFileIterator}.
 */
public class RecursiveFileIteratorTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testIteration() {
    File dir = new MockFile("parent", new File[] {
      new MockFile("file2"),
      new MockFile("dir1", new File[] {
        new MockFile("dir2", new File[0]),
        new MockFile("file3"),
      }),
      new MockFile("file1"),
      new MockFile("dir3", new File[] {
        new MockFile("file4"),
      }),
    });
    Iterator<File> iter = new RecursiveFileIterator(dir);
    assertTrue(iter.hasNext());
    assertEquals("file2", iter.next().getName());
    assertTrue(iter.hasNext());
    assertEquals("file3", iter.next().getName());
    assertEquals("file1", iter.next().getName());
    assertEquals("file4", iter.next().getName());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testIOExceptionNext() {
    Iterator<File> iter = new RecursiveFileIterator(new File("trash") {
      @Override
      public File[] listFiles() {
        // Indicates error
        return null;
      }

      @Override
      public boolean isDirectory() {
        return true;
      }
    });
    thrown.expect(RecursiveFileIterator.WrappedIOException.class);
    iter.next();
  }

  @Test
  public void testIOExceptionHasNext() {
    Iterator<File> iter = new RecursiveFileIterator(new File("trash") {
      @Override
      public File[] listFiles() {
        // Indicates error
        return null;
      }

      @Override
      public boolean isDirectory() {
        return true;
      }
    });
    thrown.expect(RecursiveFileIterator.WrappedIOException.class);
    iter.hasNext();
  }

  private static class MockFile extends File {
    private boolean isDirectory;
    private String name;
    private File[] children;

    /** Constructor for mock files */
    public MockFile(String name) {
      super("trash");
      isDirectory = false;
      this.name = name;
    }

    /** Constructor for mock directories */
    public MockFile(String name, File[] children) {
      super("trash");
      isDirectory = true;
      this.name = name;
      this.children = children;
    }

    @Override
    public boolean isDirectory() {
      return isDirectory;
    }

    @Override
    public File[] listFiles() {
      return children;
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
