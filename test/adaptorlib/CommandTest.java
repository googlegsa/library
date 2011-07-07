package adaptorlib;

import static org.junit.Assert.*;

import org.junit.Test;

public class CommandTest {
  private Command command = new Command();

  @Test
  public void testStdinStdout() throws java.io.IOException,
         InterruptedException {
    final String value = "hello";
    final String encoding = "US-ASCII";
    command.exec(new String[] {"cat"}, value.getBytes(encoding));
    assertEquals(0, command.getReturnCode());
    assertEquals(value, new String(command.getStdout(), encoding));
    assertEquals(0, command.getStderr().length);
  }

  @Test(expected=InterruptedException.class)
  public void testInterrupted() throws java.io.IOException,
         InterruptedException {
    // Only sets flag, does not immediately throw InterruptedException
    Thread.currentThread().interrupt();
    command.exec(new String[] {"sleep", "10"});
  }
}
