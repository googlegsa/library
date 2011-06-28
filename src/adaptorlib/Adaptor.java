package adaptorlib;
import java.util.List;
public abstract class Adaptor {

  /** Provides bytes of particular document. */
  abstract public byte[] getDocContent(DocId id);

  /** Provides doc ids that are to be indexed. */
  abstract public List<DocId> getDocIds();


  /* Default implementations. */

  public boolean isAllowedAccess(DocId id, String username) {
    return true;
  }
}
