package adaptorlib;

import java.io.*;

/**
 * Exec helper that allows easy handling of stdin, stdout, and stderr. Normally
 * you have to worry about deadlock when dealing with those streams, so this
 * class handles that for you.
 *
 * @author ejona@google.com (Eric Anderson)
 */
public class Command {
  private int returnCode;
  private byte[] stdout;
  private byte[] stderr;

  public Command() {}

  public int exec(String[] command) throws IOException {
    return exec(command, null, new byte[0]);
  }

  public int exec(String[] command, File workingDir) throws IOException {
    return exec(command, workingDir, new byte[0]);
  }

  public int exec(String[] command, byte[] stdin) throws IOException {
    return exec(command, null, stdin);
  }

  /**
   * @throws IOException if creating process fails
   */
  public int exec(String[] command, File workingDir, byte[] stdin)
      throws IOException {
    Process proc = Runtime.getRuntime().exec(command, null, workingDir);
    Thread in, out, err;
    in = new Thread(new StreamCopyRunnable(new ByteArrayInputStream(stdin),
                                           proc.getOutputStream(), true));
    in.start();

    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    out = new Thread(new StreamCopyRunnable(proc.getInputStream(), outBuffer,
                                            true));
    out.start();

    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    err = new Thread(new StreamCopyRunnable(proc.getErrorStream(), errBuffer,
                                            true));
    err.start();

    silentJoin(in);
    silentJoin(out);
    silentJoin(err);

    returnCode = silentWaitFor(proc);
    stdout = outBuffer.toByteArray();
    stderr = errBuffer.toByteArray();

    return returnCode;
  }

  private void silentJoin(Thread thread) {
    while (true) {
      try {
        thread.join();
      } catch (InterruptedException ex) {
        continue;
      }
      break;
    }
  }

  private int silentWaitFor(Process proc) {
    int ret;
    while (true) {
      try {
        ret = proc.waitFor();
      } catch (InterruptedException ex) {
        continue;
      }
      break;
    }
    return ret;
  }

  public int getReturnCode() {
    return returnCode;
  }

  public byte[] getStdout() {
    return stdout;
  }

  public byte[] getStderr() {
    return stderr;
  }

  public static void main(String[] args) throws IOException {
    Command command = new Command();
    command.exec(new String[] {"cat"}, "hello".getBytes());
    System.out.println("" + command.getReturnCode() + "\n"
                       + new String(command.getStderr()) + "\n"
                       + new String(command.getStdout()) + "\n");
  }

  private static class StreamCopyRunnable implements Runnable {
    private InputStream is;
    private OutputStream os;
    private boolean autoClose;

    public StreamCopyRunnable(InputStream is, OutputStream os) {
      this(is, os, false);
    }

    public StreamCopyRunnable(InputStream is, OutputStream os,
                              boolean autoClose) {
      this.is = is;
      this.os = os;
      this.autoClose = autoClose;
    }

    public void run() {
      try {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
          os.write(buffer, 0, read);
        }
      } catch (IOException ex) {
        // ignore
      } finally {
        if (autoClose) {
          silentClose(is);
          silentClose(os);
        }
      }
    }

    private static void silentClose(InputStream is) {
      try {
        is.close();
      } catch (IOException ex) {
        // ignore
      }
    }

    private static void silentClose(OutputStream os) {
      try {
        os.close();
      } catch (IOException ex) {
        // ignore
      }
    }
  }
}
