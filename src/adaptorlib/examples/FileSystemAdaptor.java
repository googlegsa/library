package adaptorlib.examples;

import adaptorlib.*;
import java.io.*;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Adaptor serving files from current directory
 */
public class FileSystemAdaptor extends AbstractAdaptor {
  private static Logger log = Logger.getLogger(FileSystemAdaptor.class.getName());
  private final File serveDir;

  public FileSystemAdaptor(File file) throws IOException {
    this.serveDir = file.getCanonicalFile();
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws IOException,
         InterruptedException {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    String parent = serveDir.toString();
    try {
      for (File file : new RecursiveFileIterator(serveDir)) {
        String name = file.toString();
        if (!name.startsWith(parent)) {
          throw new IllegalStateException(
              "Internal problem: the file's path does not begin with parent.");
        }
        // +1 for slash
        name = name.substring(parent.length() + 1);
        mockDocIds.add(new DocId(name));
      }
    } catch (RecursiveFileIterator.WrappedIOException ex) {
      throw ex.getCause();
    }
    pusher.pushDocIds(mockDocIds);
  }

  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    File file = new File(serveDir, id.getUniqueId()).getCanonicalFile();
    if (!isFileDescendantOfServeDir(file)) {
      throw new FileNotFoundException();
    }
    InputStream input = new FileInputStream(file);
    try {
      IOHelper.copyStream(input, resp.getOutputStream());
    } finally {
      input.close();
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
  public static void main(String a[]) throws IOException, InterruptedException {
    Config config = new Config();
    config.addKey("filesystemadaptor.src", ".");
    config.autoConfig(a);
    String source = config.getValue("filesystemadaptor.src");
    Adaptor adaptor = new FileSystemAdaptor(new File(source));
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content:
    try {
      gsa.beginListeningForContentRequests();
      log.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    // Push once at program start.
    gsa.pushDocIds();

    // Setup regular pushing of doc ids for once per day.
    gsa.beginPushingDocIds(
        new ScheduleOncePerDay(/*hour*/3, /*minute*/0, /*second*/0));
    log.info("doc id pushing has been put on schedule");
  }
}
