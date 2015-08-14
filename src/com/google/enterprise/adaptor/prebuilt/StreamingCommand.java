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

import com.google.enterprise.adaptor.IOHelper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Exec helper that allows easy handling of stdin, stdout, and stderr. Normally
 * you have to worry about deadlock when dealing with those streams (as
 * mentioned briefly in {@link Process}), so this class handles that for you.
 * This class is very similar to {@link Command}, except it allows streaming
 * stdin, stdout, and stderr, instead of buffering them.
 */
public class StreamingCommand {
  private static final InputSource NO_INPUT_SOURCE = new NoInputSource();
  private static final OutputSink DROP_OUTPUT_SINK = new DropOutputSink();

  // Prevent instantiation.
  private StreamingCommand() {}

  /**
   * Same as {@code exec(command, null, stdin, stdout, stderr)}.
   *
   * @see #exec(String[], File, InputSource, OutputSink, OutputSink)
   *
   * @param command command
   * @param stdin stdin
   * @param stdout stdout
   * @param stderr stderr
   * @return Process return code
   * @throws IOException if creating process fails
   * @throws InterruptedException if interrupted
   */
  public static int exec(String[] command, InputSource stdin, OutputSink stdout,
      OutputSink stderr) throws IOException, InterruptedException {
    return exec(command, null, stdin, stdout, stderr);
  }

  /**
   * Create process {@code command} starting in the {@code workingDir} and
   * providing {@code stdin} as input. This method blocks until the process
   * exits. Stdout and stderr must be consumed via the {@link OutputSink}s. If
   * {@code stdin}, {@code stdout}, or {@code stderr} is {@code null}, then a
   * bare implemention will be used.
   *
   * @param command command
   * @param workingDir workingDir
   * @param stdin stdin
   * @param stdout stdout
   * @param stderr stderr
   * @return Process return code
   * @throws IOException if creating process fails
   * @throws InterruptedException if interrupted
   */
  public static int exec(String[] command, File workingDir, InputSource stdin,
      OutputSink stdout, OutputSink stderr)
      throws IOException, InterruptedException {
    if (stdin == null) {
      stdin = NO_INPUT_SOURCE;
    }
    if (stdout == null) {
      stdout = DROP_OUTPUT_SINK;
    }
    if (stderr == null) {
      stderr = DROP_OUTPUT_SINK;
    }

    Process proc = Runtime.getRuntime().exec(command, null, workingDir);
    Thread in, out, err;
    in = new Thread(new InputSourceRunnable(stdin, proc.getOutputStream()));
    in.setDaemon(true);
    in.start();

    out = new Thread(new OutputSinkRunnable(proc.getInputStream(), stdout));
    out.setDaemon(true);
    out.start();

    err = new Thread(new OutputSinkRunnable(proc.getErrorStream(), stderr));
    err.setDaemon(true);
    err.start();

    int returnCode;
    try {
      in.join();
      out.join();
      err.join();

      returnCode = proc.waitFor();
    } catch (InterruptedException ex) {
      // Our threads should stop once the process closes.
      // This destroy() is quite rude to the subprocess, but there is not any
      // way to inform it to abort.
      proc.destroy();
      throw ex;
    }

    return returnCode;
  }

  /**
   * {@link InputStream} to {@link InputSource} adaptor.
   *
   * @param in input stream
   * @return input source
   */
  public static InputSource streamInputSource(InputStream in) {
    return new StreamInputSource(in);
  }

  /**
   * {@link OutputStream} to {@link OutputSink} adaptor.
   *
   * @param out output stream
   * @return output sink
   */
  public static OutputSink streamOutputSink(OutputStream out) {
    return new StreamOutputSink(out);
  }

  private static void silentClose(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException ex) {
      // ignore
    }
  }

  /**
   * Content source that generates content at the rate it can be consumed.
   */
  public static interface InputSource {
    /**
     * Generate content and write it to {@code out}.
     * @param out out
     * @throws IOException ioe
     */
    public void source(OutputStream out) throws IOException;
  }

  /**
   * Content sink that consumes content as soon as it becomes available.
   */
  public static interface OutputSink {
    /**
     * Consume content from {@code in}.
     * @param in in
     * @throws IOException ioe
     */
    public void sink(InputStream in) throws IOException;
  }

  private static class NoInputSource implements InputSource {
    public void source(OutputStream out) {
      // Don't write anything out.
    }
  }

  private static class DropOutputSink implements OutputSink {
    public void sink(InputStream in) throws IOException {
      byte[] buffer = new byte[1024];
      while (in.read(buffer) != -1) {
        // Do nothing with input.
      }
    }
  }

  private static class StreamInputSource implements InputSource {
    private final InputStream in;

    public StreamInputSource(InputStream in) {
      this.in = in;
    }

    @Override
    public void source(OutputStream out) throws IOException {
      IOHelper.copyStream(in, out);
    }
  }

  private static class StreamOutputSink implements OutputSink {
    private final OutputStream out;

    public StreamOutputSink(OutputStream out) {
      this.out = out;
    }

    @Override
    public void sink(InputStream in) throws IOException {
      IOHelper.copyStream(in, out);
    }
  }

  private static class InputSourceRunnable implements Runnable {
    private final InputSource source;
    private final OutputStream out;

    public InputSourceRunnable(InputSource source, OutputStream out) {
      this.source = source;
      this.out = out;
    }

    @Override
    public void run() {
      try {
        source.source(out);
      } catch (IOException ex) {
        // Ignore, but stop thread.
      } finally {
        silentClose(out);
      }
    }
  }

  private static class OutputSinkRunnable implements Runnable {
    private final InputStream in;
    private final OutputSink sink;

    public OutputSinkRunnable(InputStream in, OutputSink sink) {
      this.in = in;
      this.sink = sink;
    }

    @Override
    public void run() {
      try {
        sink.sink(in);
      } catch (IOException ex) {
        // Ignore, but stop thread.
      } finally {
        silentClose(in);
      }
    }
  }
}
