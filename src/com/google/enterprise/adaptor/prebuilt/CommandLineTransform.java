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

import static java.util.AbstractMap.SimpleEntry;

import com.google.enterprise.adaptor.IOHelper;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A conduit that allows a simple way to create a document transform based on
 * a command line program.
 */
public class CommandLineTransform implements MetadataTransform {
  private static final Logger log
      = Logger.getLogger(CommandLineTransform.class.getName());
  private static final int STDERR_BUFFER_SIZE = 51200; // 50 kB

  private final Charset charset = Charset.forName("UTF-8");
  private boolean commandAcceptsParameters = true;
  private List<String> transformCommand;
  private File workingDirectory;

  public CommandLineTransform() {}

  /**
   * Accepts keys {@code "cmd"}, {@code "workingDirectory"}, and {@code "arg?"}.
   * The {@code "arg?"} configuration values should be numerically increasing
   * starting from one: {@code "arg1"}, {@code "arg2"}, {@code "arg3}, ...
   *
   * @param config configuration
   * @return transform
   */
  public static CommandLineTransform create(Map<String, String> config) {
    CommandLineTransform transform = new CommandLineTransform();

    List<String> cmdList = new ArrayList<String>();
    String cmd = config.get("cmd");
    if (cmd != null) {
      cmdList.add(cmd);
    } else {
      throw new RuntimeException("'cmd' not defined in configuration");
    }

    String workingDirectory = config.get("workingDirectory");
    if (workingDirectory != null) {
      transform.setWorkingDirectory(new File(workingDirectory));
    }

    String cmdAcceptsParameters = config.get("cmdAcceptsParameters");
    if (cmdAcceptsParameters != null) {
      transform.commandAcceptsParameters
          = Boolean.parseBoolean(cmdAcceptsParameters);
    }

    for (int i = 1;; i++) {
      String value = config.get("arg" + i);
      if (value == null) {
        break;
      }
      cmdList.add(value);
    }
    transform.transformCommand = cmdList;
    return transform;
  }

  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    if (transformCommand == null) {
      throw new NullPointerException("transformCommand must not be null");
    }
    File metadataFile = null;
    File paramsFile = null;
    try {
      String[] commandLine;
      if (commandAcceptsParameters) {
        metadataFile = writeIterableToTempFile(metadata);
        paramsFile = writeMapToTempFile(params);

        commandLine = new String[transformCommand.size() + 2];
        transformCommand.toArray(commandLine);
        commandLine[transformCommand.size()] = metadataFile.getAbsolutePath();
        commandLine[transformCommand.size() + 1] = paramsFile.getAbsolutePath();
      } else {
        commandLine = transformCommand.toArray(new String[0]);
      }

      Command.Result result;
      try {
        result = Command.exec(commandLine, workingDirectory);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(ex);
      }

      int exitCode = result.getReturnCode();

      // Handle stderr
      if (exitCode != 0) {
        String errorOutput = new String(result.getStderr(), charset);
        throw new RuntimeException("Exit code " + exitCode + ". Stderr: "
                                   + errorOutput);
      }

      if (result.getStderr().length > 0) {
        String errorOutput = new String(result.getStderr(), charset);
        log.log(Level.INFO, "Stderr: {0}", new Object[] {errorOutput});
      }

      if (commandAcceptsParameters) {
        metadata.set(readSetFromFile(metadataFile));
        params.clear();
        params.putAll(readMapFromFile(paramsFile));
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    } finally {
      if (metadataFile != null) {
        metadataFile.delete();
      }
      if (paramsFile != null) {
        paramsFile.delete();
      }
    }
  }

  private File writeMapToTempFile(Map<String, String> map)
      throws IOException {
    return writeIterableToTempFile(map.entrySet());
  }

  private File writeIterableToTempFile(Iterable<Map.Entry<String, String>> it)
      throws IOException {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> me : it) {
      if (me.getKey().contains("\0")) {
        throw new RuntimeException("Key cannot contain the null character: "
                                     + me.getKey());
      }
      if (me.getValue().contains("\0")) {
        throw new RuntimeException("Value for key '" + me.getKey()
            + "' cannot contain the null " + "character: " + me.getKey());
      }
      sb.append(me.getKey()).append('\0');
      sb.append(me.getValue()).append('\0');
    }
    return IOHelper.writeToTempFile(sb.toString(), charset);
  }

  private List<Map.Entry<String, String>> readListFromFile(File file) throws IOException {
    InputStream is = new FileInputStream(file);
    String str;
    try {
      str = IOHelper.readInputStreamToString(is, charset);
    } finally {
      is.close();
    }

    String[] list = str.split("\0", -1);
    List<Map.Entry<String, String>> all = new ArrayList<Map.Entry<String, String>>();
    for (int i = 0; i + 1 < list.length; i += 2) {
      all.add(new SimpleEntry<String, String>(list[i], list[i + 1]));
    }
    return all;
  }

  private Set<Map.Entry<String, String>> readSetFromFile(File file) throws IOException {
    List<Map.Entry<String, String>> all = readListFromFile(file);
    Set<Map.Entry<String, String>> set = new HashSet<Map.Entry<String, String>>(all);
    return set;
  }

  private Map<String, String> readMapFromFile(File file) throws IOException {
    Map<String, String> map = new HashMap<String, String>();
    for (Map.Entry<String, String> e : readListFromFile(file)) {
      map.put(e.getKey(), e.getValue());
    }
    return map;
  }

  /**
   * This controls whether the input parameters to the transform call are passed
   * along to the actual call to the command. This is useful in the case where a
   * binary might return erros when unexpected command line flags are passed in.
   *
   * @param commandAcceptsParameters param passing boolean
   */
  public void setCommandAcceptsParameters(boolean commandAcceptsParameters) {
    this.commandAcceptsParameters = commandAcceptsParameters;
  }

  public boolean getCommandAcceptsParameters() {
    return commandAcceptsParameters;
  }

  /**
   * Sets the command that is in charge of transforming the document content.
   * This command should take input on stdin, and print the output to stdout.
   *    e.g. /path/to/command metadataFile paramsFile
   *
   * Errors should be printed to stderr. If anything is printed to stderr, it
   * will cause a failure for this transform operation.
   *
   * @param transformCommand transform
   */
  public void setTransformCommand(List<String> transformCommand) {
    this.transformCommand = new ArrayList<String>(transformCommand);
  }

  public List<String> getTransformCommand() {
    return Collections.unmodifiableList(transformCommand);
  }

  /**
   * Sets the working directory. Must be valid.
   *
   * @param dir directory
   * @throws IllegalArgumentException if {@code dir} is not a directory
   */
  public void setWorkingDirectory(File dir) {
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException("File must be a directory");
    }
    workingDirectory = dir;
  }

  /**
   * @return The working directory for the command line process.
   */
  public File getWorkingDirectory() {
    return workingDirectory;
  }
}
