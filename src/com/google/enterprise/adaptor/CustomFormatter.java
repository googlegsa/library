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

package com.google.enterprise.adaptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Custom log formatter for ease of development. It is specifically targeted to
 * use in console output and is able to produce color output on Unix terminals.
 */
public class CustomFormatter extends Formatter {
  private Date date = new Date();
  // The format for a color is '\x1b[30;40m' where 30 and 40 are the foreground
  // and background colors, respectively. The ';' isn't required. If you don't
  // provide a foreground or background, it will be set to the default.
  private MessageFormat formatter = new MessageFormat(
      "\u001b[3{5}m{0,date,MM-dd HH:mm:ss.SSS}"
      + " \u001b[3{6}m{1}\u001b[3{5}m {2} {3}:\u001b[m {4}");
  /** Is identical to {@link #formatter} except for colors */
  private MessageFormat noColorFormatter = new MessageFormat(
      "{0,date,MM-dd HH:mm:ss.SSS} {1} {2} {3}: {4}");
  private StringBuffer buffer = new StringBuffer();
  private PrintWriter writer = new PrintWriter(new StringBufferWriter(buffer));
  /**
   * Flag for whether color escapes should be used. Defaults to false on
   * Windows and true on all other platforms. Can be overridden with the
   * {@code com.google.enterprise.adaptor.CustomFormatter.useColor} logging
   * configuration property.
   */
  private boolean useColor = !System.getProperty("os.name").contains("Windows");
  /**
   * Default highlight color is a cyan on my terminal. This color needs to be
   * readable on both light-on-dark and dark-on-light setups.
   */
  private int highlightColor = 6;
  /** Colors range from 30-37 for foreground and 40-47 for background */
  private final int numberOfColors = 8;

  public CustomFormatter() {
    LogManager manager = LogManager.getLogManager();
    String className = getClass().getName();

    String value = manager.getProperty(className + ".useColor");
    if (value != null) {
      setUseColor(Boolean.parseBoolean(value));
    }
  }

  public synchronized String format(LogRecord record) {
    buffer.delete(0, buffer.length());

    date.setTime(record.getMillis());
    String threadName;
    if (record.getThreadID() == Thread.currentThread().getId()) {
      threadName = Thread.currentThread().getName();
    } else {
      threadName = "" + record.getThreadID();
    }
    // We know that one of the colors is used for the background. The default
    // for terminals is 40, so we avoid using color 30 here.
    int threadColor = (record.getThreadID() % (numberOfColors - 1)) + 1;
    String method = record.getSourceClassName() + "."
        + record.getSourceMethodName() + "()";
    if (method.length() > 30) {
      method = method.substring(method.length() - 30);
    }
    getActiveFormat().format(
        new Object[] {date, threadName, method,
          record.getLevel().getLocalizedName(), formatMessage(record),
          highlightColor, threadColor
        }, buffer, null);
    buffer.append(System.getProperty("line.separator"));
    if (record.getThrown() != null) {
      record.getThrown().printStackTrace(writer);
    }

    String formatted = buffer.toString();
    buffer.delete(0, buffer.length());
    return formatted;
  }

  private MessageFormat getActiveFormat() {
    return useColor ? formatter : noColorFormatter;
  }

  public boolean isUseColor() {
    return useColor;
  }

  public void setUseColor(boolean useColor) {
    this.useColor = useColor;
  }

  private static class StringBufferWriter extends Writer {
    private StringBuffer sb;

    public StringBufferWriter(StringBuffer sb) {
      this.sb = sb;
    }

    public void close() {
      sb = null;
    }

    public void flush() throws IOException {
      if (sb == null) {
        throw new IOException("Writer closed");
      }
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
      if (sb == null) {
        throw new IOException("Writer closed");
      }
      sb.append(cbuf, off, len);
    }
  }
}
