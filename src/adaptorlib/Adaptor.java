package adaptorlib;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Adaptor {
  private static final Logger log = Logger.getLogger(Adaptor.class.getName());

  /** Provides bytes of particular document. */
  abstract public byte[] getDocContent(DocId id);

  /** Provides doc ids that are to be indexed. */
  abstract public List<DocId> getDocIds();

  /* Default implementations. */

  public boolean isAllowedAccess(DocId id, String username) {
    return true;
  }

  /**
   * GsaCommunicationHandler.pushDocIds had a failure connecting with GSA to
   * send a batch. The thrown exception is provided as well the number of times
   * that this batch was attempted to be sent. Return true to retry, perhaps
   * after a Thread.sleep() of some time.
   */
  public boolean handleFailedToConnect(GsaFeedFileSender.FailedToConnect ftc,
                                       int ntries) {
    if (ntries > 12) {
      throw new RuntimeException(ftc);
    }
    try {
      Thread.sleep(5000 * ntries);
      return true;
    } catch (InterruptedException e) {
      log.log(Level.WARNING, "", e);
      return false;
    }
  }

  /**
   * GsaCommunicationHandler.pushDocIds had a failure writing to the GSA while
   * sending a batch.  The thrown exception is provided as well the number of
   * times that this batch was attempted to be sent. Return true to retry,
   * perhaps after a Thread.sleep() of some time.
   */
  public boolean handleFailedToConnect(GsaFeedFileSender.FailedWriting fw,
                                       int ntries) {
    if (ntries > 12) {
      throw new RuntimeException(fw);
    }
    try {
      Thread.sleep(5000 * ntries);
      return true;
    } catch (InterruptedException e) {
      log.log(Level.WARNING, "", e);
      return false;
    }
  }

  /**
   * GsaCommunicationHandler.pushDocIds had a failure reading response from GSA.
   * The thrown exception is provided as well the number of times that this
   * batch was attempted to be sent. Return true to retry, perhaps after a
   * Thread.sleep() of some time.
   */
  public boolean handleFailedToConnect(GsaFeedFileSender.FailedReadingReply fr,
                                       int ntries) {
    if (ntries > 12) {
      throw new RuntimeException(fr);
    }
    try {
      Thread.sleep(5000 * ntries);
      return true;
    } catch (InterruptedException e) {
      log.log(Level.WARNING, "", e);
      return false;
    }
  }
}
