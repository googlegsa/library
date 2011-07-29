package adaptorlib;

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
  public void setDocIdPusher(DocIdPusher pusher) {}
}
