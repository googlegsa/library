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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Utility class for providing useful methods when handling streams or other
 * forms of I/O.
 */
public class IOHelper {
  // Prevent construction
  private IOHelper() {}

  /**
   * Copy contents of {@code in} to {@code out}.
   * @param in is source of bytes
   * @param out is destination for bytes
   * @throws IOException if reading or writing fails
   */
  public static void copyStream(InputStream in, OutputStream out)
      throws IOException {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    out.flush();
  }

  /**
   * Read the contents of {@code is} into a byte array.
   * @param instream to be read
   * @return byte[] entire instream bytes
   * @throws IOException when reading fails
   */
  public static byte[] readInputStreamToByteArray(InputStream instream)
      throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    copyStream(instream, os);
    return os.toByteArray();
  }

  /**
   * Form a string from the contents of {@code instream} with charset
   * {@code charset}.
   * @param instream to be read
   * @param charset is encoding of input stream's bytes
   * @return String entire instream contents
   * @throws IOException when reading fails
   */
  public static String readInputStreamToString(InputStream instream,
      Charset charset) throws IOException {
    return new String(readInputStreamToByteArray(instream), charset);
  }

  /**
   * Write contents of {@code in} to a temporary file. Caller is responsible for
   * deleting the temporary file after use.
   * @param in written into file
   * @return File handle for written file
   * @throws IOException when writing file fails
   */
  public static File writeToTempFile(InputStream in) throws IOException {
    File tmpFile = File.createTempFile("adaptorlib", ".tmp");
    try {
      OutputStream os = new FileOutputStream(tmpFile);
      try {
        copyStream(in, os);
      } finally {
        os.close();
      }
    } catch (IOException ex) {
      tmpFile.delete();
      throw ex;
    }
    return tmpFile;
  }

  /**
   * Write contents of {@code string} to a temporary file, encoded using {@code
   * charset}. Caller is responsible for deleting the temporary file after use.
   * @param string written into file
   * @param charset is encoding of written file
   * @return File handle for written file
   * @throws IOException when writing file fails
   */
  public static File writeToTempFile(String string, Charset charset)
      throws IOException {
    byte[] bytes = string.getBytes(charset);
    return IOHelper.writeToTempFile(new ByteArrayInputStream(bytes));
  }

  /**
   * Reads a specified number of bytes from a stream.
   * Fewer bytes will only be returned if the end of stream has been reached.
   *
   * @param      is     the stream from which the bytes will be read.
   * @param      bytes     the buffer into which the data is read.
   * @param      off   the start offset in array <code>b</code>
   *                   at which the data is written.
   * @param      len   the number of bytes to read unless end of
   *                   stream is encountered
   * @return     the total number of bytes read into the buffer, or
   *             <code>-1</code> if there is no more data because the end of
   *             the stream has been reached.
   * @exception  IOException If the first byte cannot be read for any reason
   *             other than reaching end-of-stream, or if the input stream
   *             has been closed, or if some other I/O error occurs.
   * @exception  NullPointerException If <code>b</code> is <code>null</code>.
   * @exception  IndexOutOfBoundsException If <code>off</code> is negative,
   *             <code>len</code> is negative, or <code>len</code> is
   *             greater than <code>b.length - off</code>
   */
  public static int readFully(InputStream is, byte[] bytes, int off, int len)
      throws IOException {
    int bytesRead = 0;
    int result = 0;
    if (len == 0) {
      return 0;
    }
    while ((result != -1) && (bytesRead < len)) {
      result = is.read(bytes, off + bytesRead, len - bytesRead);
      if (result != -1) {
        bytesRead += result;
      }
    }
    if (bytesRead == 0) {
      return -1;
    } else {
      return bytesRead;
    }
  }
}
