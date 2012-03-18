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

import java.io.*;
import java.util.logging.*;

/**
 * Logging Handler that keeps a circular buffer of recent log messages for later
 * outputting to a stream or other Handler. It does not clear the buffer after
 * outputting the messages. This class is thread-safe.
 */
class CircularBufferHandler extends Handler {
  private static final int DEFAULT_SIZE = 1000;
  private LogRecord[] buffer;
  private int head, tail;

  public CircularBufferHandler() {
    this(DEFAULT_SIZE);
  }

  public CircularBufferHandler(int size) {
    buffer = new LogRecord[size];
  }

  @Override
  public synchronized void flush() {}

  @Override
  public synchronized void close() {
    buffer = null;
  }

  @Override
  public synchronized void publish(LogRecord record) {
    if (!isLoggable(record)) {
      return;
    }
    buffer[tail] = record;
    tail = (tail + 1) % buffer.length;
    if (head == tail) {
      head = (head + 1) % buffer.length;
    }
  }

  public String writeOut() {
    return writeOut(new SimpleFormatter());
  }

  public synchronized String writeOut(Formatter formatter) {
    StringBuilder sb = new StringBuilder();
    for (int i = head; i != tail; i = (i + 1) % buffer.length) {
      sb.append(formatter.format(buffer[i]));
    }
    return sb.toString();
  }
}
