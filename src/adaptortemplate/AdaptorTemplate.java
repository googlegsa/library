package adaptortemplate;

import adaptorlib.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;

/**
 * Demonstrates what code is necessary for putting public
 * content onto a GSA.  The key operations are A) providing
 * document ids and B) providing document bytes given a
 * document id.  TODO(pjo): Link to more advanced templates.
 */
class AdaptorTemplate extends Adaptor {
  private static final Logger log
      = Logger.getLogger(AdaptorTemplate.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  /** Replace with code that lists your repository. */
  public List<DocId> getDocIds() {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    mockDocIds.add(new DocId("1001"));
    mockDocIds.add(new DocId("1002"));
    return mockDocIds;
  }

  /** Gives the bytes of a document referenced with id. */
  public byte[] getDocContent(DocId id) throws IOException {
    if ("1001".equals(id.getUniqueId())) {
      return "Document 1001 says hello and apple orange".getBytes(encoding);
    } else if ("1002".equals(id.getUniqueId())) {
      return "Document 1002 says hello and banana strawberry"
          .getBytes(encoding);
    } else {
      throw new FileNotFoundException(id.getUniqueId());
    }
  }

  /** An example main for an adaptor that:<br>
   * <ol><li> enables serving doc contents
   *   <li> sends docs ids at program start
   *   <li> and sends doc ids on schedule.</ol>
   */
  public static void main(String a[]) throws InterruptedException {
    Config config = new Config();
    config.autoConfig(a);
    Adaptor adaptor = new AdaptorTemplate();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content.
    try {
      gsa.beginListeningForContentRequests();
      log.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    // Push once at program start.
    gsa.pushDocIds();

    // Schedule pushing of doc ids once per day.
    gsa.beginPushingDocIds(
        new ScheduleOncePerDay(/*hour*/3, /*minute*/0, /*second*/0));
    log.info("doc id pushing has been put on schedule");
  }
}
