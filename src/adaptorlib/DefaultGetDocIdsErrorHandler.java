package adaptorlib;

/**
 * Default handler of errors during {@link Adaptor#getDocIds}.
 */
public class DefaultGetDocIdsErrorHandler
    implements Adaptor.GetDocIdsErrorHandler {
  private int maximumTries;
  private long sleepTimeMillis;

  /**
   * Same as {@code DefaultGetDocIdsErrorHandler(12, 5000)}.
   *
   * @see #DefaultGetDocIdsErrorHandler(int, long)
   */
  public DefaultGetDocIdsErrorHandler() {
    this(12, 5000);
  }

  /**
   * Create a default error handler that gives up after {@code maximumTries} and
   * sleeps {@code sleepTimeMillis * numberOfTries} before retrying.
   */
  public DefaultGetDocIdsErrorHandler(int maximumTries, long sleepTimeMillis) {
    this.maximumTries = maximumTries;
    this.sleepTimeMillis = sleepTimeMillis;
  }

  /**
   * {@inheritDoc}
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  @Override
  public boolean handleFailedToGetDocIds(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * Common handle method for generic error handling. The handler gives up after
   * {@code maximumTries} and sleeps {@code sleepTimeMillis * ntries} before
   * retrying.
   */
  protected boolean handleGeneric(Exception ex, int ntries)
      throws InterruptedException {
    if (ntries > maximumTries) {
      return false;
    }
    Thread.sleep(sleepTimeMillis * ntries);
    return true;
  }
}
