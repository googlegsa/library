// Copyright 2014 Google Inc. All Rights Reserved.
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
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link JavaExec}. */
public class JavaExecTest {
  // Relative to WORKING_DIR
  private static final File CHILD_JAR
      = new File("../test/com/google/enterprise/adaptor/JavaExecTestChild.jar");
  private static final File WORKING_DIR = new File("build/");

  private PrintStream savedStdout;
  private ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
  private PrintStream stdout = new PrintStream(stdoutBytes);
  private PrintStream savedStderr;
  private ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
  private PrintStream stderr = new PrintStream(stderrBytes);
  private RuntimeMXBean savedRuntimeMxBean;

  @Before
  public void swapStdout() {
    savedStdout = System.out;
    System.setOut(stdout);
    savedStderr = System.err;
    System.setErr(stderr);
  }

  @Before
  public void saveRuntimeMxBean() {
    savedRuntimeMxBean = JavaExec.runtimeMxBean;
  }

  @After
  public void swapStdoutBack() {
    System.setOut(savedStdout);
    System.setErr(savedStderr);
  }

  @After
  public void restoreRuntimeMxBean() {
    JavaExec.runtimeMxBean = savedRuntimeMxBean;
  }

  @Test
  public void testExec() throws Exception {
    assertEquals(0,
        JavaExec.exec(CHILD_JAR, WORKING_DIR, Arrays.asList("testExec")));
    assertEquals("child run", bytesToString(stdoutBytes));
    assertEquals("", bytesToString(stderrBytes));
  }

  @Test
  public void testExit1() throws Exception {
    assertEquals(1,
        JavaExec.exec(CHILD_JAR, WORKING_DIR, Arrays.asList("testExit1")));
    assertEquals("stdout", bytesToString(stdoutBytes));
    assertEquals("stderr", bytesToString(stderrBytes));
  }

  @Test(expected = InterruptedException.class)
  public void testInterrupt() throws Exception {
    Thread.currentThread().interrupt();
    JavaExec.exec(CHILD_JAR, WORKING_DIR, Arrays.asList("testInterrupt"));
  }

  @Test
  public void testJvmArgs() throws Exception {
    JavaExec.runtimeMxBean = new MockRuntimeMxBean(Arrays.asList(
          "-Xmx123m", "-Djava.util.logging.config.file=logging-config-file",
          "-Djava.util.logging.not-file=not-file"));
    assertEquals(0,
        JavaExec.exec(CHILD_JAR, WORKING_DIR, Arrays.asList("testJvmArgs")));
    String[] resp = bytesToString(stdoutBytes).split(" ");
    assertEquals(3, resp.length);
    assertEquals("[-Xmx123m,", resp[0]);
    assertTrue(resp[1].startsWith("-Djava.util.logging.config.file="));
    assertTrue(resp[1].endsWith("/logging-config-file,"));
    assertEquals("-Djava.util.logging.not-file=not-file]", resp[2]);
    assertEquals("", bytesToString(stderrBytes));
  }

  private String bytesToString(ByteArrayOutputStream baos) throws Exception {
    return new String(baos.toByteArray(), "UTF-8").replace("\r\n", "\n");
  }

  /** Class to house logic that is run in the child process. */
  public static class Child {
    public static void main(String[] args) {
      if (args.length < 1) {
        System.out.print("No test code specified");
        System.out.flush();
        System.exit(100);
      }
      if ("testExec".equals(args[0])) {
        System.out.print("child run");
      } else if ("testExit1".equals(args[0])) {
        System.out.print("stdout");
        System.err.print("stderr");
        System.exit(1);
      } else if ("testInterrupt".equals(args[0])) {
        // Sleep forever.
        while (true) {
          try {
            Thread.sleep(100 * 1000);
          } catch (InterruptedException ex) {
            // Ignore.
          }
        }
      } else if ("testJvmArgs".equals(args[0])) {
        System.out.print(
            ManagementFactory.getRuntimeMXBean().getInputArguments());
      } else {
        System.out.println("Could not find test method: " + args[0]);
        System.exit(101);
      }
    }
  }

  private static class MockRuntimeMxBean extends UnsupportedRuntimeMxBean {
    private final List<String> inputArguments;

    public MockRuntimeMxBean(List<String> inputArguments) {
      this.inputArguments = Collections.unmodifiableList(new ArrayList<String>(
          inputArguments));
    }

    @Override
    public List<String> getInputArguments() {
      return inputArguments;
    }
  }

  private static class UnsupportedRuntimeMxBean implements RuntimeMXBean {
    @Override
    public String getBootClassPath() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getClassPath() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getInputArguments() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getLibraryPath() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getManagementSpecVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getSpecName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getSpecVendor() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getSpecVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getStartTime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getSystemProperties() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getUptime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getVmName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getVmVendor() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getVmVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBootClassPathSupported() {
      throw new UnsupportedOperationException();
    }
  }
}
