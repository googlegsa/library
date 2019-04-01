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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

/**
 * Tests for {@link IOHelper}.
 */
public class IOHelperTest {
  private static final Charset charset = Charset.forName("ASCII");
  @Test
  public void testReadFullySuccess() throws Exception {
    final String golden = "Testing";
    int expected = golden.length();
    byte[] in = new byte[expected + 1];
    int read = IOHelper.readFully(
        new ByteArrayInputStream(golden.getBytes(charset)), in, 1, expected);
    assertEquals(expected, read);
    assertEquals(golden, new String(in, 1, read, charset));
  }

  @Test
  public void testReadFullyReachEof() throws Exception {
    final String golden = "Testing";
    byte[] in = new byte[golden.length() + 1];
    int read = IOHelper.readFully(
        new ByteArrayInputStream(golden.getBytes(charset)), in, 0, in.length);
    assertEquals(in.length - 1, read);
    assertEquals(golden, new String(in, 0, read, charset));
  }

  @Test
  public void testReadFullyImmediateEof() throws Exception {
    byte[] in = new byte[1];
    int read = IOHelper.readFully(
        new ByteArrayInputStream(new byte[0]), in, 0, in.length);
    assertEquals(-1, read);
  }

  @Test
  public void testReadFullyZeroLengthBuffer() throws Exception {
    byte[] in = new byte[0];
    int read = IOHelper.readFully(
        new ByteArrayInputStream(new byte[1]), in, 0, 0);
    assertEquals(0, read);
  }
}
