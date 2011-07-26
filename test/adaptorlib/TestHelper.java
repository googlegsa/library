package adaptorlib;

import static org.junit.Assume.*;

/**
 * Utility methods for tests.
 */
public class TestHelper {
  // Prevent instantiation
  private TestHelper() {}

  private static boolean isRunningOnWindows() {
    String osName = System.getProperty("os.name");
    boolean isWindows = osName.toLowerCase().startsWith("windows");
    return isWindows;
  }

  public static void assumeOsIsNotWindows() {
    assumeTrue(!isRunningOnWindows());
  }

  public static void assumeOsIsWindows() {
    assumeTrue(isRunningOnWindows());
  }

}
