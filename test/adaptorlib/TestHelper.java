package adaptorlib;

import static org.junit.Assume.*;

/**
 * Utility methods for tests.
 */
public class TestHelper {
  // Prevent instantiation
  private TestHelper() {}

  public static void assumeOsIsNotWindows() {
    String osName = System.getProperty("os.name");
    boolean isWindows = osName.toLowerCase().startsWith("windows");
    assumeTrue(!isWindows);
  }
}
