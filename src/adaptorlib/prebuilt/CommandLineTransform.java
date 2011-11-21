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

package adaptorlib.prebuilt;

import adaptorlib.DocumentTransform;
import adaptorlib.IOHelper;
import adaptorlib.TransformException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.*;

/**
 * A conduit that allows a simple way to create a document transform based on
 * a command line program.
 */
public class CommandLineTransform extends DocumentTransform {
  private static final Logger log
      = Logger.getLogger(CommandLineTransform.class.getName());
  private static final int STDERR_BUFFER_SIZE = 51200; // 50 kB

  private final Charset charset = Charset.forName("UTF-8");

  public CommandLineTransform(String name) {
    super(name);
  }

  public CommandLineTransform(Map<String, String> config) {
    super("CommandLineTransform");
    List<String> cmd = new ArrayList<String>();
    for (Map.Entry<String, String> me : config.entrySet()) {
      String key = me.getKey();
      String value = me.getValue();
      if ("cmd".equals(key)) {
        cmd.add(value);
      } else if ("workingDirectory".equals(key)) {
        workingDirectory(new File(value));
      } else if ("errorHaltsPipeline".equals(key)) {
        errorHaltsPipeline(Boolean.parseBoolean(value));
      }
    }
    if (cmd.size() == 0) {
      throw new RuntimeException("cmd configuration property must be set");
    }
    for (int i = 1;; i++) {
      String value = config.get("arg" + i);
      if (value == null) {
        break;
      }
      cmd.add(value);
    }
    transformCommand = cmd;
  }

  @Override
  public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                        Map<String, String> metadata, Map<String, String> params)
      throws TransformException, IOException {
    if (transformCommand == null) {
      throw new NullPointerException("transformCommand must not be null");
    }
    File metadataFile = null;
    File paramsFile = null;
    try {
      String[] commandLine;
      if (commandAcceptsParameters) {
        metadataFile = writeMapToTempFile(metadata);
        paramsFile = writeMapToTempFile(params);

        commandLine = new String[transformCommand.size() + 2];
        transformCommand.toArray(commandLine);
        commandLine[transformCommand.size()] = metadataFile.getAbsolutePath();
        commandLine[transformCommand.size() + 1] = paramsFile.getAbsolutePath();
      } else {
        commandLine = transformCommand.toArray(new String[0]);
      }

      Command command = new Command();
      try {
        command.exec(commandLine, workingDirectory, contentIn.toByteArray());
      } catch (InterruptedException ex) {
        throw new TransformException(ex);
      }

      int exitCode = command.getReturnCode();

      // Handle stderr
      if (exitCode != 0) {
        String errorOutput = new String(command.getStderr(), charset);
        throw new TransformException("Exit code " + exitCode + ". Stderr: " + errorOutput);
      }

      if (command.getStderr().length > 0) {
        String errorOutput = new String(command.getStderr(), charset);
        log.log(Level.INFO, "Stderr: {0}", new Object[] {errorOutput});
      }

      contentOut.write(command.getStdout());
      if (commandAcceptsParameters) {
        metadata.clear();
        metadata.putAll(readMapFromFile(metadataFile));
        params.clear();
        params.putAll(readMapFromFile(paramsFile));
      }
    } finally {
      if (metadataFile != null) {
        metadataFile.delete();
      }
      if (paramsFile != null) {
        paramsFile.delete();
      }
    }
  }

  private File writeMapToTempFile(Map<String, String> map) throws IOException, TransformException {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> me : map.entrySet()) {
      if (me.getKey().contains("\0")) {
        throw new TransformException("Key cannot contain the null character: " + me.getKey());
      }
      if (me.getValue().contains("\0")) {
        throw new TransformException("Value for key '" + me.getKey() + "' cannot contain the null "
                                   + "character: " + me.getKey());
      }
      sb.append(me.getKey()).append('\0');
      sb.append(me.getValue()).append('\0');
    }
    return IOHelper.writeToTempFile(sb.toString(), charset);
  }

  private Map<String, String> readMapFromFile(File file) throws IOException {
    InputStream is = new FileInputStream(file);
    String str;
    try {
      str = IOHelper.readInputStreamToString(is, charset);
    } finally {
      is.close();
    }

    String[] list = str.split("\0");
    Map<String, String> map = new HashMap<String, String>(list.length);
    for (int i = 0; i + 1 < list.length; i += 2) {
      map.put(list[i], list[i + 1]);
    }
    return map;
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
   *    e.g. /path/to/command metadataFile paramsFile
   *
   * Errors should be printed to stderr. If anything is printed to stderr, it
   * will cause a failure for this transform operation.
   */
  public void transformCommand(List<String> transformCommand) {
    this.transformCommand = new ArrayList<String>(transformCommand);
  }

  public List<String> transformCommand() {
    return Collections.unmodifiableList(transformCommand);
  }

  /**
   * Sets the working directory. Must be valid.
   *
   * @throws IllegalArgumentException if {@code dir} is not a directory
   */
  public void workingDirectory(File dir) {
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException("File must be a directory");
    }
    workingDirectory = dir;
  }

  /**
   * @return The working directory for the command line process.
   */
  public File workingDirectory() {
    return workingDirectory;
  }

  private boolean commandAcceptsParameters = true;
  private List<String> transformCommand;
  private File workingDirectory;
}
