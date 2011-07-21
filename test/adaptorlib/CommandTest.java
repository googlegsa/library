package adaptorlib;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

/**
 * Tests for {@link Command}.
 */
public class CommandTest {
  private Command command = new Command();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testStdinStdout() throws java.io.IOException,
         InterruptedException {
    TestHelper.assumeOsIsNotWindows();

    final String value = "hello";
    final String encoding = "US-ASCII";
    command.exec(new String[] {"cat"}, value.getBytes(encoding));
    assertEquals(0, command.getReturnCode());
    assertEquals(value, new String(command.getStdout(), encoding));
    assertEquals(0, command.getStderr().length);
  }

  @Test
  public void testInterrupted() throws java.io.IOException,
         InterruptedException {
    TestHelper.assumeOsIsNotWindows();

    // Only sets flag, does not immediately throw InterruptedException
    Thread.currentThread().interrupt();
    thrown.expect(InterruptedException.class);
    command.exec(new String[] {"sleep", "10"});
  }
}
