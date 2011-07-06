package adaptorlib;

import java.io.*;

/**
 * Exec helper that allows easy handling of stdin, stdout, and stderr. Normally
 * you have to worry about deadlock when dealing with those streams, so this
 * class handles that for you.
 */
public class Command {
  private int returnCode;
  private byte[] stdout;
  private byte[] stderr;

  public Command() {}

  public int exec(String[] command) throws IOException,
         InterruptedException {
    return exec(command, null, new byte[0]);
  }

  public int exec(String[] command, File workingDir) throws IOException,
         InterruptedException {
    return exec(command, workingDir, new byte[0]);
  }

  public int exec(String[] command, byte[] stdin) throws IOException,
         InterruptedException {
    return exec(command, null, stdin);
  }

  /**
   * @throws IOException if creating process fails
   */
  public int exec(String[] command, File workingDir, byte[] stdin)
      throws IOException, InterruptedException {
    // Clear so that if the object is reused, and the second use has an
    // InterruptedException, they don't accidentally use the wrong data.
    stdout = null;
    stderr = null;

    Process proc = Runtime.getRuntime().exec(command, null, workingDir);
    Thread in, out, err;
    in = new Thread(new StreamCopyRunnable(new ByteArrayInputStream(stdin),
                                           proc.getOutputStream(), true));
    in.setDaemon(true);
    in.start();

    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    out = new Thread(new StreamCopyRunnable(proc.getInputStream(), outBuffer,
                                            true));
    out.setDaemon(true);
    out.start();

    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    err = new Thread(new StreamCopyRunnable(proc.getErrorStream(), errBuffer,
                                            true));
    err.setDaemon(true);
    err.start();

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

    stdout = outBuffer.toByteArray();
    stderr = errBuffer.toByteArray();

    return returnCode;
  }

  public int getReturnCode() {
    return returnCode;
  }

  /**
   * Returns internal byte array without copying.
   */
  public byte[] getStdout() {
    return stdout;
  }

  /**
   * Returns internal byte array without copying.
   */
  public byte[] getStderr() {
    return stderr;
  }

  public static void main(String[] args) throws IOException,
         InterruptedException {
    Command command = new Command();
    if (true) {
      command.exec(new String[] {"cat"}, "hello".getBytes());
    } else {
      final Thread mainThread = Thread.currentThread();
      Thread interruptThread = new Thread() {
        public void run() {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException ex) {
            // ignore, but stop sleep
          }
          mainThread.interrupt();
        }
      };
      interruptThread.setDaemon(true);
      interruptThread.start();
      command.exec(new String[] {"sleep", "1m"});
    }
    System.out.println("" + command.getReturnCode() + "\n"
                       + new String(command.getStderr()) + "\n"
                       + new String(command.getStdout()) + "\n");
  }

  private static class StreamCopyRunnable implements Runnable {
    private InputStream is;
    private OutputStream os;
    private boolean autoClose;

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
        // Ignore, but stop thread
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
