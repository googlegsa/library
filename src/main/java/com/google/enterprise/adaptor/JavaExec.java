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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.adaptor.prebuilt.StreamingCommand;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Creates a Java process with similar settings to the current process.
 */
class JavaExec {
  /**
   * Property names whose value refers to a file. These properties' values may
   * need modification to point to the correct file, since the working
   * directories can be different.
   */
  private static final Set<String> FILE_PROPERTIES
      = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
          "javax.net.ssl.keyStore", "javax.net.ssl.trustStore",
          "java.util.logging.config.file")));

  @VisibleForTesting
  static RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

  // Prevent instantiation.
  private JavaExec() {}

  /**
   * Execute {@code jar} as a subprocess with working directory {@code
   * workingDir} with application arguments {@code args}. {@code jar} is
   * relative to {@code workingDir}. If {@code workingDir} is {@code null}, then
   * the current working directory is used.
   */
  public static int exec(File jar, File workingDir, List<String> args)
      throws IOException, InterruptedException {
    Properties props = System.getProperties();
    String javaHome = props.getProperty("java.home");
    String javaExe = props.getProperty("os.name").startsWith("Windows")
        ? "java.exe" : "java";
    File java = new File(new File(new File(javaHome), "bin"), javaExe);
    if (!java.exists()) {
      throw new IOException("Could not find java executable at "
          + java.getPath());
    }
    List<String> command = new ArrayList<String>();
    command.add(java.getPath());
    command.addAll(computeJvmArgs(workingDir));
    command.add("-jar");
    command.add(jar.getPath());
    command.addAll(args);
    return StreamingCommand.exec(command.toArray(new String[0]), workingDir,
        StreamingCommand.streamInputSource(
            new java.io.ByteArrayInputStream(new byte[0])),
        StreamingCommand.streamOutputSink(System.out),
        StreamingCommand.streamOutputSink(System.err));
  }

  private static List<String> computeJvmArgs(File workingDir) {
    List<String> curArgs = runtimeMxBean.getInputArguments();
    List<String> newArgs = new ArrayList<String>(curArgs.size());
    for (String arg : curArgs) {
      if (arg.startsWith("-D")) {
        String[] parts = arg.substring(2).split("=", 2);
        if (FILE_PROPERTIES.contains(parts[0])) {
          // Make path to value absolute, so working directory won't impact its
          // meaning.
          arg = "-D" + parts[0] + "=" + new File(parts[1]).getAbsolutePath();
        }
      }
      newArgs.add(arg);
    }
    return newArgs;
  }
}
