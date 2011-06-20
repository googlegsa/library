// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.lang.InterruptedException;
import java.lang.ProcessBuilder;
import java.lang.Process;
import java.util.Map;

/**
 *
 * @author brandoni@google.com (Brandon Iles)
 */
public class CommandLineTransform extends DocumentTransform {

  private final int STDERR_BUFFER_SIZE = 51200; // 50 kB

  public CommandLineTransform(String name) {
    super(name);
  }

  @Override
  public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                        ByteArrayOutputStream metaDataIn, OutputStream metaDataOut,
                        Map<String, String> params) throws TransformException, IOException {
    // Java Processes can only take input on 1 channel, stdin. If we want
    // to have two separate channels, we would need to either:
    // - Have two separate commands that get called for content and meta
    // - Write each to separate files and pass filenames as params, or
    // - Send them both in through stdin with some separator.
    //
    // We don't yet support sending metadata at serve time, so this version of
    // CommandLineTransform only handles the content. In the case of HTML, the
    // metadata is baked in anyway.

    StringBuilder commandBuilder = new StringBuilder(transformCommand);
    if (commandAcceptsParameters) {
      for(Map.Entry<String, String> param : params.entrySet()) {
        commandBuilder.append(" -");
        commandBuilder.append(param.getKey());
        commandBuilder.append(" ");
        commandBuilder.append(param.getValue());
      }
    }

    ProcessBuilder pb = new ProcessBuilder(commandBuilder.toString());
    pb.redirectErrorStream(false);  // We want 2 streams to come out for stdin and stderr
    if (workingDirectory != null && !workingDirectory.isEmpty())
      pb.directory(new File(workingDirectory));

    Process p = pb.start();
    OutputStream stdin = p.getOutputStream();
    InputStream stdout = p.getInputStream();
    InputStream stderr = p.getErrorStream();

    try {
      // Run it
      contentIn.writeTo(stdin);
      int exitCode = p.waitFor();

      // Handle stderr
      if (exitCode != 0 || stderr.available() > 0) {
        byte[] buf = new byte[STDERR_BUFFER_SIZE];
        stderr.read(buf);
        throw new TransformException(new String(buf));
      }

      // Copy stdout
      byte[] buf = new byte[8192]; // 8kB chunks
      int len = -1;
      while ((len = stdout.read(buf)) >= 0)
        contentOut.write(buf, 0, len);
    }
    catch(InterruptedException e) {
      throw new TransformException(e);
    }
    finally {
      stdin.close();
      stdout.close();
      stderr.close();
    }
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
   * Transform parameters are sent on the command line as flags.
   *    e.g. /path/to/command -param1 value1 -param2 value2 ...
   *
   * Errors should be printed to stderr. If anything is printed to stderr, it
   * will cause a failure for this transform operation.
   */
  public void transformCommand(String transformCommand) {
    this.transformCommand = transformCommand;
  }

  /**
   * Sets the working directory. Must be valid.
   * @returns true on success.
   */
  public boolean workingDirectory(String dir) {
    File file = new File(dir);
    if(file.isDirectory()) {
      workingDirectory = dir;
      return true;
    }
    return false;
  }

  /**
   * @returns The working directory for the command line process.
   */
  public String workingDirectory() {
    return workingDirectory;
  }

  private boolean commandAcceptsParameters = true;
  private String transformCommand = "";
  private String workingDirectory = null;
}
