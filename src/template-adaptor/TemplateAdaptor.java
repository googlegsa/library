package templateadaptor;
import adaptorlib.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.ArrayList;
/** The bodies of getDocIds(), getDocContent() and main() are
  the responsibility of the connector developer.
*/
class RepositoryConnector implements DocContentRetriever {
  // TODO: Extend comments.

  static List<DocId> getDocIds() {
    // TODO: Provide binary calling implementation.
    // TODO: Test with large document counts.
    // TODO: Test with variety of schedules.
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    DocReadPermissions indexPerms
        = new DocReadPermissions("bob, alice", "bowlers, golfers");
    mockDocIds.add(new DocId("1001", indexPerms));
    DocReadPermissions cssPerms
        = new DocReadPermissions("carol, chat", "  ");
    mockDocIds.add(new DocId("1002", cssPerms));
    // mockDocIds.add(new DeletedDocId("1005"));
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

  /** Answers whether particular user is allowed access to 
    referenced document. */
  boolean isAllowedAccess(DocId id, String username) {
    // TODO: Consider and enrich credentials structure.
    return true;
  }

  public static void main(String a[]) {
    int port = Config.getLocalPort();
    DocContentRetriever retr = new RepositoryConnector();
    try {
      new GsaCommunicationHandler(port, retr).beginListeningForConnections();
    } catch (IOException e) {
      throw new RuntimeException("Could not listen on " + port, e);
    }
    // TODO: Replace single push with schedule from Config.
    List<DocId> handles = getDocIds();
    GsaCommunicationHandler.pushDocIds("testFeed", handles);
  }
}
