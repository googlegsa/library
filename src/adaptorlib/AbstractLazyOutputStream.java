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

package adaptorlib;

import java.io.*;

/**
 * OutputStream that passes all calls to the {@code OutputStream} provided by
 * {@link #retrieveOs}, but calls {@code retrieveOs()} only once needed.
 */
abstract class AbstractLazyOutputStream extends OutputStream {
  private OutputStream os;

  @Override
  public void close() throws IOException {
    loadOs();
    os.close();
  }

  @Override
  public void flush() throws IOException {
    loadOs();
    os.flush();
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    loadOs();
    os.write(b, off, len);
  }

  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(int b) throws IOException {
    loadOs();
    os.write(b);
  }

  protected void loadOs() throws IOException {
    if (os == null) {
      os = retrieveOs();
    }
  }

  /**
   * Retrieve the real {@code OutputStream}. Will only be called once.
   */
  protected abstract OutputStream retrieveOs() throws IOException;
}
