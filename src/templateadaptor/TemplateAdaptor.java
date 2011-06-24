package templateadaptor;
import adaptorlib.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
/** Demonstrates what code is necessary
 *  for putting public content onto a GSA.
 *  The key operations are A) pushing document ids
 *  and B) providing document bytes given a document id. */
// TODO: Link to configuration steps.
class TemplateAdaptor extends Adaptor {
  private static Logger LOG = Logger.getLogger(Adaptor.class.getName());

  /** Acquires and pushes document ids to GSA.  It's OK to push
   *  the same document ids multiple times because the operation
   *  is fast. */
  public void pushDocIds() {
    LOG.info("about to get doc ids");
    List<DocId> handles = getDocIds();
    LOG.info("about to push " + handles.size() + " doc ids");
    GsaCommunicationHandler.pushDocIds("testfeed", handles);
    LOG.info("done pushing doc ids");
  }

  /** Provides a couple mock ids.  Replace with code 
   *  that lists your repository. */
  private List<DocId> getDocIds() {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    mockDocIds.add(new DocId("1001"));
    mockDocIds.add(new DocId("1002"));
    return mockDocIds;
  }

  /** Gives the bytes of a document referenced with id. Returns
   *  null if such a document doesn't exist. */
  public byte[] getDocContent(DocId id) {
    if ("1001".equals(id.getUniqueId())) {
      return "Document 1001 says hello and apple orange"
          .getBytes(Config.getGsaCharacterEncoding());
    } else if ("1002".equals(id.getUniqueId())) {
      return "Document 1002 says hello and banana strawberry"
          .getBytes(Config.getGsaCharacterEncoding());
    } else {
      return null;
    }
  }

  /** An example main for an adaptor. */
  public static void main(String a[]) {
    Adaptor adaptor = new TemplateAdaptor();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor);

    // Setup providing content:
    try {
      gsa.beginListeningForContentRequests();
      LOG.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    // Uncomment next line to push once at program start.
    // adaptor.pushDocIds();

    // Setup regular pushing of doc ids:
    ScheduleIterator everyNite = new ScheduleOncePerDay(/*hour*/3,
        /*minute*/0, /*second*/0);
    gsa.beginPushingDocIds(everyNite);
    LOG.info("doc id pushing has been put on schedule");
  }
}

// TODO: Write SecuredByAclTemplateAdaptor.
// TODO: Write SecuredByRequestTemplateAdaptor.
