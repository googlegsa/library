package adaptorlib;
import java.util.List;
public abstract class Adaptor {

  /** Provides bytes of particular document. */
  abstract public byte[] getDocContent(DocId id);

  /** Responsible for pushing doc ids to GSA; called on a schedule. */
  abstract public void pushDocIds();


  /* Default implementations. */

  public boolean isAllowedAccess(DocId id, String username) {
    return true;
  }
}
