package adaptorlib;

/**
 * Default handler of errors during a push of {@code DocId}s to the GSA.
 */
public class DefaultPushErrorHandler implements Adaptor.PushErrorHandler {
  private int maximumTries;
  private long sleepTimeMillis;

  /**
   * Same as {@code DefaultPushErrorHandler(12, 5000)}.
   *
   * @see #DefaultPushErrorHandler(int, long)
   */
  public DefaultPushErrorHandler() {
    this(12, 5000);
  }

  /**
   * Create a default error handler that gives up after {@code maximumTries} and
   * sleeps {@code sleepTimeMillis * numberOfTries} before retrying.
   */
  public DefaultPushErrorHandler(int maximumTries, long sleepTimeMillis) {
    this.maximumTries = maximumTries;
    this.sleepTimeMillis = sleepTimeMillis;
  }

  /**
   * {@inheritDoc}
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  @Override
  public boolean handleFailedToConnect(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * {@inheritDoc}
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  @Override
  public boolean handleFailedWriting(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * {@inheritDoc}
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  @Override
  public boolean handleFailedReadingReply(Exception ex, int ntries)
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
