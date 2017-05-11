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

package com.google.enterprise.adaptor;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

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
  public void getDocContent(Request req, Response resp) throws IOException,
      InterruptedException {
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
    public boolean canRespondWithNoContent(Date lastModified) {
      return request.canRespondWithNoContent(lastModified);
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
    public void respondNotModified() throws IOException {
      response.respondNotModified();
    }

    @Override
    public void respondNotFound() throws IOException {
      response.respondNotFound();
    }
   
    @Override
    public void respondNoContent() throws IOException {
      response.respondNoContent();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      return response.getOutputStream();
    }

    @Override
    public void setContentType(String contentType) {
      response.setContentType(contentType);
    }

    @Override
    public void setLastModified(Date lastModified) {
      response.setLastModified(lastModified);
    }

    @Override
    public void addMetadata(String key, String value) {
      response.addMetadata(key, value);
    }

    @Override
    public void setAcl(Acl acl) {
      response.setAcl(acl);
    }

    @Override
    public void putNamedResource(String fname, Acl facl) {
      response.putNamedResource(fname, facl);
    }

    @Override
    public void setSecure(boolean secure) {
      response.setSecure(secure);
    }

    @Override
    public void addAnchor(URI uri, String text) {
      response.addAnchor(uri, text);
    }

    @Override
    public void setNoIndex(boolean noIndex) {
      response.setNoIndex(noIndex);
    }

    @Override
    public void setNoFollow(boolean noFollow) {
      response.setNoFollow(noFollow);
    }

    @Override
    public void setNoArchive(boolean noArchive) {
      response.setNoArchive(noArchive);
    }

    @Override
    public void setDisplayUrl(URI displayUrl) {
      response.setDisplayUrl(displayUrl);
    }

    @Override
    public void setCrawlOnce(boolean crawlOnce) {
      response.setCrawlOnce(crawlOnce);
    }

    @Override
    public void setLock(boolean lock) {
      response.setLock(lock);
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
    public boolean canRespondWithNoContent(Date lastModified) {
      return false;
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
   * Passes through all operations to wrapped {@code DocIdPusher}.
   */
  public static class WrapperDocIdPusher extends AbstractDocIdPusher {
    private DocIdPusher pusher;

    public WrapperDocIdPusher(DocIdPusher pusher) {
      this.pusher = pusher;
    }

    @Override
    public DocIdPusher.Record pushRecords(
        Iterable<DocIdPusher.Record> records, ExceptionHandler handler)
        throws InterruptedException {
      return pusher.pushRecords(records, handler);
    }

    @Override
    public DocId pushNamedResources(Map<DocId, Acl> resources,
        ExceptionHandler handler) throws InterruptedException {
      return pusher.pushNamedResources(resources, handler);
    }
    
    @Override
    public GroupPrincipal pushGroupDefinitions(
        Map<GroupPrincipal, ? extends Collection<Principal>> defs,
        boolean caseSensitive, ExceptionHandler exceptionHandler)
        throws InterruptedException {
      return pusher.pushGroupDefinitions(defs, caseSensitive, exceptionHandler);
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
    public AsyncDocIdPusher getAsyncDocIdPusher() {
      return context.getAsyncDocIdPusher();
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
    public void setGetDocIdsFullErrorHandler(ExceptionHandler handler) {
      context.setGetDocIdsFullErrorHandler(handler);
    }

    @Override
    public ExceptionHandler getGetDocIdsFullErrorHandler() {
      return context.getGetDocIdsFullErrorHandler();
    }

    @Override
    public void setGetDocIdsIncrementalErrorHandler(
        ExceptionHandler handler) {
      context.setGetDocIdsIncrementalErrorHandler(handler);
    }

    @Override
    public ExceptionHandler getGetDocIdsIncrementalErrorHandler() {
      return context.getGetDocIdsIncrementalErrorHandler();
    }

    @Override
    public SensitiveValueDecoder getSensitiveValueDecoder() {
      return context.getSensitiveValueDecoder();
    }

    @Override
    public HttpContext createHttpContext(String path, HttpHandler handler) {
      return context.createHttpContext(path, handler);
    }

    @Override
    public Session getUserSession(HttpExchange ex, boolean create) {
      return context.getUserSession(ex, create);
    }

    @Override
    public void setPollingIncrementalLister(PollingIncrementalLister lister) {
      context.setPollingIncrementalLister(lister);
    }

    @Override
    public void setAuthnAuthority(AuthnAuthority authnAuthority) {
      context.setAuthnAuthority(authnAuthority);
    }

    @Override
    public void setAuthzAuthority(AuthzAuthority authzAuthority) {
      context.setAuthzAuthority(authzAuthority);
    }
  }
}
