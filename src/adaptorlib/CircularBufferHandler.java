package adaptorlib;

import java.io.*;
import java.util.logging.*;

/**
 * Logging Handler that keeps a circular buffer of recent log messages for later
 * outputting to a stream or other Handler. It does not clear the buffer after
 * outputting the messages. This class is thread-safe.
 */
public class CircularBufferHandler extends Handler {
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
