package adaptorlib;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Base class for all user-specific implementation details of an Adaptor.
 *
 * @see adaptortemplate.AdaptorTemplate
 */
public abstract class Adaptor {
  private static final Logger log = Logger.getLogger(Adaptor.class.getName());

  /** Provides bytes of particular document. */
  public abstract byte[] getDocContent(DocId id) throws IOException;

  /** Provides doc ids that are to be indexed. */
  public abstract List<DocId> getDocIds() throws IOException;

  /* Default implementations. */

  public boolean isAllowedAccess(DocId id, String username) {
    return true;
  }

  /**
   * {@link GsaCommunicationHandler#pushDocIds} had a failure connecting with
   * GSA to send a batch. The thrown exception is provided as well the number of
   * times that this batch was attempted to be sent. Return true to retry,
   * perhaps after a Thread.sleep() of some time.
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  public boolean handleFailedToConnect(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * {@link GsaCommunicationHandler#pushDocIds} had a failure writing to the GSA
   * while sending a batch.  The thrown exception is provided as well the number
   * of times that this batch was attempted to be sent. Return true to retry,
   * perhaps after a Thread.sleep() of some time.
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  public boolean handleFailedWriting(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * {@link GsaCommunicationHandler#pushDocIds} had a failure reading response
   * from GSA. The thrown exception is provided as well the number of times that
   * this batch was attempted to be sent. Return true to retry, perhaps after a
   * Thread.sleep() of some time.
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  public boolean handleFailedReadingReply(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * {@link GsaCommunicationHandler#pushDocIds} had a failure reading from this
   * adaptor's {@link Adaptor#getDocIds}. The thrown exception is provided as
   * well as the number of times that this batch was attempted to be sent.
   * Return true to retry, perhaps after a Thread.sleep() of some time.
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  public boolean handleFailedToGetDocIds(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * Common handle method for generic error handling.
   */
  protected boolean handleGeneric(Exception ex, int ntries)
      throws InterruptedException {
    if (ntries > 12) {
      return false;
    }
    Thread.sleep(5000 * ntries);
    return true;
  }
}
