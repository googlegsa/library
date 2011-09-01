// Copyright 2011 Google Inc.
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

package adaptorlib.prebuilt;

import adaptorlib.TestHelper;

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
    // Only sets flag, does not immediately throw InterruptedException.
    Thread.currentThread().interrupt();
    thrown.expect(InterruptedException.class);
    command.exec(new String[] {"sleep", "10"});
  }

  @Test
  public void testInterruptedWindows() throws java.io.IOException,
         InterruptedException {
    TestHelper.assumeOsIsWindows();
    // Only sets flag, does not immediately throw InterruptedException.
    Thread.currentThread().interrupt();
    thrown.expect(InterruptedException.class);
    // Use a 10 second ping as substitute for unix sleep.
    command.exec(new String[] {"ping", "-n", "10", "localhost"});
  }

}
