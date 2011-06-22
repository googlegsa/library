package templateadaptor;
import adaptorlib.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
/** Demonstrates what code is necessary
 *  for putting public content onto a GSA.
 *  The key operations are A) pushing document ids
 *  and B) providing the bytes of documents. */
// TODO: Link to configuration steps.
class TemplateAdaptor extends Adaptor {
  private static Logger LOG = Logger.getLogger(Adaptor.class.getName());

  /** Acquires and pushes document ids to GSA.  It's OK to push
   *  the same document ids multiple times because the operation
   *  is fast. */
  public void pushDocIds() {
    /* Called on schedule. */
    LOG.info("about to get doc ids");
    List<DocId> handles = getDocIds();
    LOG.info("about to push " + handles.size() + " doc ids");
    GsaCommunicationHandler.pushDocIds("testfeed", handles);
    LOG.info("done pushing doc ids");
  }

  /** Provides a couple mock ids. */
  private List<DocId> getDocIds() {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    mockDocIds.add(new DocId("1001"));
    mockDocIds.add(new DocId("1002"));
    return mockDocIds;
  }

  /** Gives the bytes of a document referenced with id. Returns
   *  null if such a document doesn't exist. */
  public byte []getDocContent(DocId id) {
    try {
      if ("1001".equals(id.getUniqueId())) {
        return "Document 1001 says hello and apple orange"
            .getBytes(Config.getGsaCharacterEncoding());
      } else if ("1002".equals(id.getUniqueId())) {
        return "Document 1002 says hello and banana strawberry"
            .getBytes(Config.getGsaCharacterEncoding());
      }
    } catch (UnsupportedEncodingException e) {
      // Expected to never happen.
      throw new RuntimeException(e);
    }
    return null;
  }

  /** An example main for an adaptor.  This mains controls
   *  the event loop pushing the document ids. */
  public static void main(String a[]) {
    final Adaptor adaptor = new TemplateAdaptor();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor);
    try {
      gsa.beginListeningForContentRequests();
    } catch (IOException e) {
      throw new RuntimeException("could not start", e);
    }
    // TODO: Consider that pushDocIds() is static and begin() is not.
    Scheduler pushScheduler = new Scheduler();

    // If want to push on program start uncomment next line:
    // adaptor.pushDocIds();

    // A once a day scheduled push of doc ids:
    ScheduleOncePerDay atNite = new ScheduleOncePerDay(/*hour*/3,
        /*minute*/0, /*second*/0);
    pushScheduler.schedule(new Scheduler.Task() {
      public void run() {
        adaptor.pushDocIds();
      }
    }, atNite);
    LOG.info("doc id pushing has been put on schedule");
  }
}
// TODO: Write SecuredByAclTemplateAdaptor.
// TODO: Write SecuredByRequestTemplateAdaptor.
