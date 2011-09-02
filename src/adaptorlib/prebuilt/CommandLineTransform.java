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

import adaptorlib.DocumentTransform;
import adaptorlib.IOHelper;
import adaptorlib.TransformException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A conduit that allows a simple way to create a document transform based on
 * a command line program.
 */
public class CommandLineTransform extends DocumentTransform {

  private static final int STDERR_BUFFER_SIZE = 51200; // 50 kB

  public CommandLineTransform(String name) {
    super(name);
  }

  @Override
  public void transform(ByteArrayOutputStream contentIn, ByteArrayOutputStream metadataIn,
                        OutputStream contentOut, OutputStream metadataOut,
                        Map<String, String> params) throws TransformException, IOException {
    // Java Processes can only take input on 1 channel, stdin. If we want
    // to have two separate channels, we would need to either:
    // - Have two separate commands that get called for content and metadata
    // - Write metadata to file and send filename as parameter.
    // - Send them both in through stdin with some separator.
    //
    // We don't yet support sending metadata with content, so this version of
    // CommandLineTransform only handles the content. In the case of HTML, the
    // metadata is baked in anyway.

    List<String> command = Arrays.asList(transformCommand.split(" "));
    if (commandAcceptsParameters) {
      for (Map.Entry<String, String> param : params.entrySet()) {
        command.add("-" + param.getKey());
        command.add(param.getValue());
      }
    }

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(false);  // We want 2 streams to come out for stdout and stderr.
    if (workingDirectory != null && !workingDirectory.isEmpty()) {
      pb.directory(new File(workingDirectory));
    }

    Process p = pb.start();

    // Streams can be confusing because an input to one component is an output
    // to another based on the frame of reference.
    // Here, stdin is an OutputStream, because we write to it.
    // stdout and stderr are InputStreams, because we read from them.
    OutputStream stdin = p.getOutputStream();
    InputStream stdout = p.getInputStream();
    InputStream stderr = p.getErrorStream();

    try {
      // Run it
      contentIn.writeTo(stdin);
      stdin.close();  // Necessary for some commands that expect EOF.
      int exitCode = p.waitFor();

      // Handle stderr
      if (exitCode != 0 || stderr.available() > 0) {
        byte[] buf = new byte[STDERR_BUFFER_SIZE];
        stderr.read(buf);
        throw new TransformException(new String(buf));
      }

      // Copy stdout
      IOHelper.copyStream(stdout, contentOut);
    } catch (InterruptedException e) {
      throw new TransformException(e);
    } finally {
      stdin.close();
      stdout.close();
      stderr.close();
      p.destroy();
    }
  }

  /**
   * This controls whether the input parameters to the transform call are passed
   * along to the actual call to the command. This is useful in the case where a
   * binary might return erros when unexpected command line flags are passed in.
   */
  public void commandAcceptsParameters(boolean commandAcceptsParameters) {
    this.commandAcceptsParameters = commandAcceptsParameters;
  }

  public boolean commandAcceptsParameters() {
    return commandAcceptsParameters;
  }

  /**
   * Sets the command that is in charge of transforming the document content.
   * This command should take input on stdin, and print the output to stdout.
   * Transform parameters are sent on the command line as flags.
   *    e.g. /path/to/command -param1 value1 -param2 value2 ...
   *
   * Errors should be printed to stderr. If anything is printed to stderr, it
   * will cause a failure for this transform operation.
   */
  public void transformCommand(String transformCommand) {
    this.transformCommand = transformCommand;
  }

  /**
   * Sets the working directory. Must be valid.
   * @return true on success.
   */
  public boolean workingDirectory(String dir) {
    File file = new File(dir);
    if (file.isDirectory()) {
      workingDirectory = dir;
      return true;
    }
    return false;
  }

  /**
   * @return The working directory for the command line process.
   */
  public String workingDirectory() {
    return workingDirectory;
  }

  private boolean commandAcceptsParameters = true;
  private String transformCommand = "";
  private String workingDirectory = null;
}
