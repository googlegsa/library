package adaptorlib;

import java.util.*;

/**
 * Abstract Adaptor that provides reasonable default implementations of many
 * {@link Adaptor} methods.
 */
public abstract class AbstractAdaptor implements Adaptor {
  /**
   * {@inheritDoc}
   *
   * <p>This implementation does nothing.
   */
  @Override
  public void setDocIdPusher(DocIdPusher pusher) {}

  /**
   * {@inheritDoc}
   *
   * <p>This implementation provides {@link AuthzStatus#PERMIT} for all {@code
   * DocId}s in an unmodifiable map.
   */
  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
                                                  Collection<DocId> ids) {
    Map<DocId, AuthzStatus> result
        = new HashMap<DocId, AuthzStatus>(ids.size() * 2);
    for (DocId id : ids) {
      result.put(id, AuthzStatus.PERMIT);
    }
    return Collections.unmodifiableMap(result);
  }
}
