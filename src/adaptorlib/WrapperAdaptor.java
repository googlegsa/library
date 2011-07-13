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
  public boolean handleFailedToConnect(Exception ex, int ntries)
      throws InterruptedException {
    return adaptor.handleFailedToConnect(ex, ntries);
  }

  @Override
  public boolean handleFailedWriting(Exception ex, int ntries)
      throws InterruptedException {
    return adaptor.handleFailedWriting(ex, ntries);
  }

  @Override
  public boolean handleFailedReadingReply(Exception ex, int ntries)
      throws InterruptedException {
    return adaptor.handleFailedReadingReply(ex, ntries);
  }

  @Override
  public boolean handleFailedToGetDocIds(Exception ex, int ntries)
      throws InterruptedException {
    return adaptor.handleFailedToGetDocIds(ex, ntries);
  }
}
