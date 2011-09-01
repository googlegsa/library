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
 * Utility class for providing useful methods when handling streams or other
 * forms of I/O.
 */
public class IOHelper {
  // Prevent construction
  private IOHelper() {}

  /**
   * Copy contents of {@code in} to {@code out}.
   */
  public static void copyStream(InputStream in, OutputStream out)
      throws IOException {
    byte[] buffer = new byte[1024];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    out.flush();
  }

  /**
   * Read the contents of {@code is} into a byte array.
   */
  public static byte[] readInputStreamToByteArray(InputStream is)
      throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    copyStream(is, os);
    return os.toByteArray();
  }

  /**
   * Write contents of {@code in} to a temporary file. Caller is responsible for
   * deleting the temporary file after use.
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
}
