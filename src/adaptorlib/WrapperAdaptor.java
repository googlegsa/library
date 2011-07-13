package adaptorlib;

import java.io.IOException;
import java.util.List;

/**
 * Wraps all methods of the provided Adaptor to allow modification of behavior
 * via chaining.
 */
abstract class WrapperAdaptor extends Adaptor {
  private Adaptor adaptor;

  public WrapperAdaptor(Adaptor adaptor) {
    this.adaptor = adaptor;
  }

  @Override
  public byte[] getDocContent(DocId id) throws IOException {
    return adaptor.getDocContent(id);
  }

  @Override
  public List<DocId> getDocIds() throws IOException {
    return adaptor.getDocIds();
  }

  @Override
  public boolean isAllowedAccess(DocId id, String username) {
    return adaptor.isAllowedAccess(id, username);
  }

  @Override
  public boolean handleFailedToConnect(GsaFeedFileSender.FailedToConnect ftc,
                                       int ntries) {
    return adaptor.handleFailedToConnect(ftc, ntries);
  }

  @Override
  public boolean handleFailedToConnect(GsaFeedFileSender.FailedWriting fw,
                                       int ntries) {
    return adaptor.handleFailedToConnect(fw, ntries);
  }

  @Override
  public boolean handleFailedToConnect(GsaFeedFileSender.FailedReadingReply fr,
                                       int ntries) {
    return adaptor.handleFailedToConnect(fr, ntries);
  }
}
