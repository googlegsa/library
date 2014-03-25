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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link FastFilterOutputStream}.
 */
public class FastFilterOutputStreamTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullOutputStream() {
    thrown.expect(NullPointerException.class);
    new FastFilterOutputStream(null);
  }

  @Test
  public void testEmptyConstructor() {
    boolean isOutNull = new FastFilterOutputStream() {
      public boolean isOutNull() {
        return out == null;
      }
    }.isOutNull();
    assertTrue(isOutNull);
  }

  @Test
  public void testClose() throws Exception {
    final AtomicBoolean closed = new AtomicBoolean();
    OutputStream os = new FastFilterOutputStream(new UnsupportedOutputStream() {
      @Override
      public void close() {
        closed.set(true);
      }
    });
    os.close();
    assertTrue(closed.get());
  }

  @Test
  public void testFlush() throws Exception {
    final AtomicBoolean flushed = new AtomicBoolean();
    OutputStream os = new FastFilterOutputStream(new UnsupportedOutputStream() {
      @Override
      public void flush() {
        flushed.set(true);
      }
    });
    os.flush();
    assertTrue(flushed.get());
  }

  @Test
  public void testWrite() throws Exception {
    final AtomicBoolean written = new AtomicBoolean();
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    OutputStream os = new FastFilterOutputStream(new UnsupportedOutputStream() {
      @Override
      public void write(byte[] b, int off, int len) {
        baos.write(b, off, len);
        written.set(true);
      }
    });
    os.write(new byte[] {1, 0}, 1, 1);
    assertTrue(written.get());

    written.set(false);
    os.write(new byte[] {1});
    assertTrue(written.get());

    written.set(false);
    os.write(2);
    assertTrue(written.get());

    assertArrayEquals(new byte[] {0, 1, 2}, baos.toByteArray());
  }

  private static class UnsupportedOutputStream extends OutputStream {
    @Override
    public void close() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void flush() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void write(byte[] b) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void write(int b) throws IOException {
      throw new UnsupportedOperationException();
    }
  }
}
