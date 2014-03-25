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

package com.google.enterprise.adaptor.prebuilt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Exec helper that allows easy handling of stdin, stdout, and stderr. Normally
 * you have to worry about deadlock when dealing with those streams (as
 * mentioned briefly in {@link Process}), so this class handles that for you.
 */
public class Command {
  // Prevent instantiation.
  private Command() {}

  /**
   * Same as {@code exec(command, null, new byte[0])}.
   *
   */
  public static Result exec(String[] command) throws IOException,
         InterruptedException {
    return exec(command, null, new byte[0]);
  }

  /**
   * Same as {@code exec(command, workingDir, new byte[0])}.
   *
   * @see #exec(String[], File, byte[])
   */
  public static Result exec(String[] command, File workingDir)
      throws IOException, InterruptedException {
    return exec(command, workingDir, new byte[0]);
  }

  /**
   * Same as {@code exec(command, null, stdin)}.
   *
   * @see #exec(String[], File, byte[])
   */
  public static Result exec(String[] command, byte[] stdin) throws IOException,
         InterruptedException {
    return exec(command, null, stdin);
  }

  /**
   * Create process {@code command} starting in the {@code workingDir} and
   * providing {@code stdin} as input. This method blocks until the process
   * exits. Stdout and stderr are available via {@link Result#getStdout} and
   * {@link Result#getStderr}. Before using them, however, you should generally
   * make sure that the process exited with a return code of zero, as other
   * return codes typically indicate an error.
   *
   * @throws IOException if creating process fails
   */
  public static Result exec(String[] command, File workingDir, byte[] stdin)
      throws IOException, InterruptedException {
    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    int returnCode = StreamingCommand.exec(command, workingDir,
        StreamingCommand.streamInputSource(new ByteArrayInputStream(stdin)),
        StreamingCommand.streamOutputSink(outBuffer),
        StreamingCommand.streamOutputSink(errBuffer));

    byte[] stdout = outBuffer.toByteArray();
    byte[] stderr = errBuffer.toByteArray();

    return new Result(returnCode, stdout, stderr);
  }

  /**
   * Result data from an invocation
   */
  public static class Result {
    private final int returnCode;
    private final byte[] stdout;
    private final byte[] stderr;

    /**
     * Construct a result. In normal usage, this is unnecessary, but it can be
     * helpful in the tests of classes that use {@code Command}.
     */
    public Result(int returnCode, byte[] stdout, byte[] stderr) {
      if (stdout == null || stderr == null) {
        throw new NullPointerException();
      }
      this.returnCode = returnCode;
      this.stdout = stdout;
      this.stderr = stderr;
    }

    public int getReturnCode() {
      return returnCode;
    }

    /**
     * Returns internal byte array without copying.
     */
    public byte[] getStdout() {
      return stdout;
    }

    /**
     * Returns internal byte array without copying.
     */
    public byte[] getStderr() {
      return stderr;
    }
  }
}
