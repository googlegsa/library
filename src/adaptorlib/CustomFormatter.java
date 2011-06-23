package adaptorlib;

import java.io.*;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.*;

public class CustomFormatter extends Formatter {
  private static final String newline = System.getProperty("line.separator");
  private Date date = new Date();
  private MessageFormat formatter = new MessageFormat(
      "\u001b[3{5}m{0,date,HH:mm:ss.SSS} {1}\u001b[3{5}m {2} {3}:\u001b[m {4}");
  private StringBuffer buffer = new StringBuffer();
  private PrintWriter writer = new PrintWriter(new StringBufferWriter(buffer));
  private int highlightColor = 6;
  private final int numberOfColors = 8;

  public synchronized String format(LogRecord record) {
    buffer.delete(0, buffer.length());

    date.setTime(record.getMillis());
    String threadName;
    if (record.getThreadID() == Thread.currentThread().getId())
      threadName = Thread.currentThread().getName();
    else
      threadName = "" + record.getThreadID();
    int backgroundColor = (record.getThreadID() % (numberOfColors - 1)) + 1;
    threadName = "\u001b[3" + backgroundColor + "m" + threadName;
    String method = record.getSourceClassName() + "."
        + record.getSourceMethodName() + "()";
    if (method.length() > 30)
      method = method.substring(method.length() - 30);
    formatter.format(
        new Object[] {date, threadName, method,
          record.getLevel().getLocalizedName(), formatMessage(record),
          highlightColor
        }, buffer, null);
    buffer.append(newline);
    if (record.getThrown() != null)
      record.getThrown().printStackTrace(writer);

    String formatted = buffer.toString();
    buffer.delete(0, buffer.length());
    return formatted;
  }
}

class StringBufferWriter extends Writer {
  private StringBuffer sb;

  public StringBufferWriter(StringBuffer sb) {
    this.sb = sb;
  }

  public void close() {
    sb = null;
  }

  public void flush() throws IOException {
    if (sb == null)
      throw new IOException("Writer closed");
  }

  public void write(char[] cbuf, int off, int len) throws IOException {
    if (sb == null)
      throw new IOException("Writer closed");
    sb.append(cbuf, off, len);
  }
}
