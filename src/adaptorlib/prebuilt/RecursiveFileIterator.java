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

package adaptorlib.prebuilt;

import adaptorlib.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterate over all files within a folder, including files in subdirectories.
 */
public class RecursiveFileIterator implements Iterator<File>, Iterable<File> {
  /**
   * List of all directory contents we have encountered and not returned or
   * descended into. We only return the File at the front of the list. If a
   * directory is at the front of the list, then we descend into it, remove it
   * from the list, and add its children to the front of the list.
   */
  private List<File> traversalStateStack = new LinkedList<File>();

  /**
   * @param rootFile directory to recursively list contents
   */
  public RecursiveFileIterator(File rootFile) {
    traversalStateStack.add(rootFile);
  }

  /**
   * Returns {@code this} to allow using with foreach loops.
   */
  public Iterator<File> iterator() {
    return this;
  }

  /**
   * Make {@code traversalStateStack.get(0)} be the file that would be returned
   * by {@link #next}, or have traversalStateStack be empty. If things are
   * already in the right place, then no action is performed.
   */
  private void setPositionToNextFile() throws IOException {
    while (!traversalStateStack.isEmpty()
           && traversalStateStack.get(0).isDirectory()) {
      File dir = traversalStateStack.remove(0);
      File[] files = dir.listFiles();
      if (files == null) {
        throw new IOException("Exception while getting directory listing for: "
                              + dir.getName());
      }
      traversalStateStack.addAll(0, Arrays.asList(files));
    }
  }

  /**
   * Returns {@code true} if the iteration has more elements. Even if this
   * method throws a WrappedIOException, the iterator can continue to list
   * files. However, each exception would note a directory that could not be
   * descended into.
   *
   * @throws WrappedIOException if there was an IOException
   */
  public boolean hasNext() {
    try {
      setPositionToNextFile();
    } catch (IOException ex) {
      throw new WrappedIOException(ex);
    }
    return !traversalStateStack.isEmpty();
  }

  /**
   * Returns the next file in the iteratior. Even if this method throws a
   * WrappedIOException, the iterator can continue to list files. However, each
   * exception would note a directory that could not be descended into.
   *
   * @throws WrappedIOException if there was an IOException
   */
  public File next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    return traversalStateStack.remove(0);
  }

  /**
   * Unsupported.
   *
   * @throws UnsupportedOperationException always
   */
  public void remove() {
    throw new UnsupportedOperationException();
  }

  /**
   * Allows throwing IOExceptions and allowing the caller to unpack and rethrow
   * them with certainty.
   */
  public class WrappedIOException extends RuntimeException {
    public WrappedIOException(IOException ex) {
      super(ex);
    }

    public IOException getCause() {
      return (IOException) super.getCause();
    }
  }
}
