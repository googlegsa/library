// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package adaptorlib;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

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
  public void initConfig(Config config) {
    adaptor.initConfig(config);
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    adaptor.init(context);
  }

  @Override
  public void destroy() {
    adaptor.destroy();
  }

  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
      Set<String> groups, Collection<DocId> ids) throws IOException {
    return adaptor.isUserAuthorized(userIdentifier, groups, ids);
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
    public void setMetadata(Metadata m) {
      response.setMetadata(m);
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
    private Metadata metadata;

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
    public void setMetadata(Metadata m) {
      this.metadata = m;
    }

    public String getContentType() {
      return contentType;
    }

    public Metadata getMetadata() {
      return metadata;
    }
  }

  /**
   * Passes through all operations to wrapped {@code DocIdPusher}.
   */
  public static class WrapperDocIdPusher extends AbstractDocIdPusher {
    private DocIdPusher pusher;

    public WrapperDocIdPusher(DocIdPusher pusher) {
      this.pusher = pusher;
    }

    @Override
    public DocIdPusher.DocInfo pushDocInfos(
        Iterable<DocIdPusher.DocInfo> docInfos, PushErrorHandler handler)
        throws InterruptedException {
      return pusher.pushDocInfos(docInfos, handler);
    }
  }

  public static class WrapperAdaptorContext implements AdaptorContext {
    private AdaptorContext context;

    public WrapperAdaptorContext(AdaptorContext context) {
      this.context = context;
    }

    @Override
    public Config getConfig() {
      return context.getConfig();
    }

    @Override
    public DocIdPusher getDocIdPusher() {
      return context.getDocIdPusher();
    }

    @Override
    public DocIdEncoder getDocIdEncoder() {
      return context.getDocIdEncoder();
    }

    @Override
    public void addStatusSource(StatusSource source) {
      context.addStatusSource(source);
    }

    @Override
    public void removeStatusSource(StatusSource source) {
      context.removeStatusSource(source);
    }

    @Override
    public void setGetDocIdsErrorHandler(GetDocIdsErrorHandler handler) {
      context.setGetDocIdsErrorHandler(handler);
    }

    @Override
    public GetDocIdsErrorHandler getGetDocIdsErrorHandler() {
      return context.getGetDocIdsErrorHandler();
    }
  }
}
