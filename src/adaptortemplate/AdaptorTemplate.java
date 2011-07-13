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
 * document id.  TODO: Link to more advanced templates.
 */
class AdaptorTemplate extends Adaptor {
  private final static Logger LOG = Logger.getLogger(Adaptor.class.getName());
  // TODO: Get encoding from config.
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

  /** An example main for an adaptor that enables serving. */
  public static void main(String a[]) {
    Config config = new Config();
    config.autoConfig(a);
    Adaptor adaptor = new AdaptorTemplate();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content.
    try {
      gsa.beginListeningForContentRequests();
      LOG.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }
    
    
    try {
      gsa.pushDocIds("adaptorTemplate", adaptor.getDocIds());
    } catch (IOException e) {
      // TODO(johnfelton): Improve error recording when "journal" is available.
      LOG.severe(e.getMessage());
    }

    // Setup scheduled pushing of doc ids for once per day.
    gsa.beginPushingDocIds(
        new ScheduleOncePerDay(/*hour*/3, /*minute*/0, /*second*/0));
    LOG.info("doc id pushing has been put on schedule");
  }
}
