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
