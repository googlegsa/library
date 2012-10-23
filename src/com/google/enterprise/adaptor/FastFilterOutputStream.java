// Copyright 2012 Google Inc. All Rights Reserved.
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

/**
 * {@link FilterOutputStream} replacement that uses {@link
 * #write(byte[],int,int)} for all writes. This class is not thread-safe.
 */
class FastFilterOutputStream extends OutputStream {
  private byte[] singleByte = new byte[1];
  // Protected to mimic FilterOutputStream.
  protected OutputStream out;

  /**
   * Construct instance with {@code out = null}. Extending class must ensure
   * that {@code out} is handled correctly.
   */
  protected FastFilterOutputStream() {}

  /**
   * Construct instance with provided {@code out}. {@code out} is not permitted
   * to be {@code null} via this constructor.
   */
  public FastFilterOutputStream(OutputStream out) {
    if (out == null) {
      throw new NullPointerException();
    }
    this.out = out;
  }

  /**
   * Calls {@code out.close()}.
   */
  @Override
  public void close() throws IOException {
    out.close();
  }

  /**
   * Calls {@code out.flush()}.
   */
  @Override
  public void flush() throws IOException {
    out.flush();
  }

  /**
   * Calls {@code out.write(b, off, len)}.
   */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    out.write(b, off, len);
  }

  /**
   * Calls {@link #write(byte[],int,int)}. This does not call {@code out}'s
   * write, but instead this class's write. There is no reason to override this
   * method.
   */
  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  /**
   * Calls {@link #write(byte[],int,int)}. This does not call {@code out}'s
   * write, but instead this class's write. There is no reason to override this
   * method.
   */
  @Override
  public void write(int b) throws IOException {
    singleByte[0] = (byte) b;
    write(singleByte, 0, 1);
  }
}
