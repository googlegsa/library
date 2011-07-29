package adaptorlib;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

/**
 * Wraps all methods of the provided Adaptor to allow modification of behavior
 * via chaining.
 */
abstract class WrapperAdaptor implements Adaptor {
  private Adaptor adaptor;

  public WrapperAdaptor(Adaptor adaptor) {
    this.adaptor = adaptor;
  }

  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    adaptor.getDocContent(req, resp);
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws IOException,
         InterruptedException {
    adaptor.getDocIds(pusher);
  }

  @Override
  public void setDocIdPusher(DocIdPusher pusher) {
    adaptor.setDocIdPusher(pusher);
  }

  /**
   * Passes through all operations to wrapped {@code Request}.
   */
  public static class WrapperRequest implements Request {
    private Request request;

    public WrapperRequest(Request request) {
      this.request = request;
    }

    @Override
    public boolean needDocumentContent() {
      return request.needDocumentContent();
    }

    @Override
    public boolean hasChangedSinceLastAccess(Date lastModified) {
      return request.hasChangedSinceLastAccess(lastModified);
    }

    @Override
    public Date getLastAccessTime() {
      return request.getLastAccessTime();
    }

    @Override
    public DocId getDocId() {
      return request.getDocId();
    }
  }

  /**
   * Passes through all operations to wrapped {@code Response}.
   */
  public static class WrapperResponse implements Response {
    private Response response;

    public WrapperResponse(Response response) {
      this.response = response;
    }

    @Override
    public void respondNotModified() {
      response.respondNotModified();
    }

    @Override
    public OutputStream getOutputStream() {
      return response.getOutputStream();
    }

    @Override
    public void setContentType(String contentType) {
      response.setContentType(contentType);
    }

    @Override
    public void setDocReadPermissions(DocReadPermissions acl) {
      response.setDocReadPermissions(acl);
    }
  }

  /**
   * Request mimicking a client GET request where no cache is involved. This
   * means that the client must write to the response or throw
   * {@link java.io.FileNotFoundException}.
   */
  public static class GetContentsRequest implements Request {
    private DocId docId;

    public GetContentsRequest(DocId docId) {
      this.docId = docId;
    }

    @Override
    public boolean needDocumentContent() {
      return true;
    }

    @Override
    public boolean hasChangedSinceLastAccess(Date lastModified) {
      return true;
    }

    @Override
    public Date getLastAccessTime() {
      return null;
    }

    @Override
    public DocId getDocId() {
      return docId;
    }
  }

  /**
   * Counterpart of {@link GetContentsRequest} that allows easy calling of an
   * {@link Adaptor}. It does not support {@link #respondNotModified}.
   */
  public static class GetContentsResponse implements Response {
    private OutputStream os;
    private String contentType;
    private DocReadPermissions acl;

    public GetContentsResponse(OutputStream os) {
      this.os = os;
    }

    @Override
    public void respondNotModified() {
      throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getOutputStream() {
      return os;
    }

    @Override
    public void setContentType(String contentType) {
      this.contentType = contentType;
    }

    @Override
    public void setDocReadPermissions(DocReadPermissions acl) {
      this.acl = acl;
    }

    public String getContentType() {
      return contentType;
    }

    public DocReadPermissions getDocReadPermissions() {
      return acl;
    }
  }

  /**
   * Passes through all operations to wrapped {@code DocIdPusher}.
   */
  public static class WrapperDocIdPusher implements DocIdPusher {
    private DocIdPusher pusher;

    public WrapperDocIdPusher(DocIdPusher pusher) {
      this.pusher = pusher;
    }

    @Override
    public DocId pushDocIds(Iterable<DocId> docIds)
        throws InterruptedException {
      return pushDocIds(docIds, null);
    }

    @Override
    public DocId pushDocIds(Iterable<DocId> docIds, PushErrorHandler handler)
        throws InterruptedException {
      return pusher.pushDocIds(docIds, handler);
    }
  }
}
