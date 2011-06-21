package templateadaptor;
import adaptorlib.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
/**
 * Demonstrates the functions that are necessary
 * for putting public content onto a GSA.
 * The key operations are A) pushing document ids
 * and B) providing the bytes of documents.
 */
class TemplateAdaptor extends Adaptor {
  private static Logger LOG = Logger.getLogger(Adaptor.class.getName());

  public void pushDocIds() {
    /* Called on schedule. */
    LOG.info("about to get doc ids");
    List<DocId> handles = getDocIds();
    LOG.info("about to push doc ids");
    GsaCommunicationHandler.pushDocIds("testfeed", handles);
    LOG.info("done pushing doc ids");
  }

  private List<DocId> getDocIds() {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    DocReadPermissions indexPerms
        = new DocReadPermissions("bob, alice", "bowlers, golfers");
    mockDocIds.add(new DocId("1001", indexPerms));
    DocReadPermissions cssPerms
        = new DocReadPermissions("carol, chat", "  ");
    mockDocIds.add(new DocId("1002", cssPerms));
    return mockDocIds;
  }

  /** Gives the bytes of a document referenced with id. Returns
    null if such a document doesn't exist.  */
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
      throw new RuntimeException(e);
    }
    return null;
  }

  public static void main(String a[]) {
    Adaptor mock = new TemplateAdaptor();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(mock);
    try {
      gsa.begin();
    } catch (IOException e) {
      throw new RuntimeException("could not start", e);
    }
  }
}
