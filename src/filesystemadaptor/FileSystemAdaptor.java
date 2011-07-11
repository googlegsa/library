package filesystemadaptor;

import adaptorlib.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Adaptor serving files from current directory
 */
class FileSystemAdaptor extends Adaptor {
  private static Logger log = Logger.getLogger(FileSystemAdaptor.class.getName());
  private final File serveDir;

  public FileSystemAdaptor(File file) {
    this.serveDir = file.getAbsoluteFile();
  }

  public List<DocId> getDocIds() {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    String parent = serveDir.toString();
    for (File file : new RecursiveFileIterator(serveDir)) {
      String name = file.toString();
      if (!name.startsWith(parent)) {
        throw new IllegalStateException(
            "Internal problem: the file's path begins with its parent.");
      }
      // +1 for slash
      name = name.substring(parent.length() + 1);
      mockDocIds.add(new DocId(name));
    }
    return mockDocIds;
  }

  /** Gives the bytes of a document referenced with id. Returns
   *  null if such a document doesn't exist. */
  public byte[] getDocContent(DocId id) throws IOException {
    File file = new File(serveDir, id.getUniqueId()).getAbsoluteFile();
    if (!isFileDescendantOfServeDir(file)) {
      throw new FileNotFoundException();
    }
    InputStream input = new FileInputStream(file);
    ByteArrayOutputStream output = new ByteArrayOutputStream(
        (int) file.length());
    try {
      copyStream(input, output);
    } finally {
      try {
        input.close();
      } catch (IOException ex) {
        // Ignore. Nothing we can do.
      }
      try {
        output.close();
      } catch (IOException ex) {
        // Ignore. Nothing we can do.
      }
    }
    return output.toByteArray();
  }

  private void copyStream(InputStream input, OutputStream output)
      throws IOException {
    byte[] buffer = new byte[1024];
    int read;
    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
  }

  private boolean isFileDescendantOfServeDir(File file) {
    while (file != null) {
      if (file.equals(serveDir)) {
        return true;
      }
      file = file.getParentFile();
    }
    return false;
  }

  /** An example main for an adaptor. */
  public static void main(String a[]) {
    Config config = new Config();
    config.autoConfig(a);
    String source = config.getValueOrDefault("filesystemadaptor.src", ".");
    Adaptor adaptor = new FileSystemAdaptor(new File(source));
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content:
    try {
      gsa.beginListeningForContentRequests();
      log.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    // Uncomment next line to push once at program start.
    gsa.pushDocIds(config.getFeedName(), adaptor.getDocIds());

    // Setup regular pushing of doc ids for once per day.
    gsa.beginPushingDocIds(
        new ScheduleOncePerDay(/*hour*/3, /*minute*/0, /*second*/0));
    log.info("doc id pushing has been put on schedule");
  }
}
