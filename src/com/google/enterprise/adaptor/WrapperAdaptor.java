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

import com.google.enterprise.adaptor.MetadataTransform.TransmissionDecision;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    @Override
    public void setForcedTransmissionDecision(TransmissionDecision decision) {
      response.setForcedTransmissionDecision(decision);
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
   * Counterpart of {@link GetContentsRequest} that allows easy calling of an
   * {@link Adaptor}. It does not support {@link #respondNotModified}. Be sure
   * to check {@link #isNotFound()}.
   */
  public static class GetContentsResponse implements Response {
    private OutputStream os;
    private String contentType;
    private Date lastModified;
    private Metadata metadata = new Metadata();
    private Acl acl;
    private boolean secure;
    private List<URI> anchorUris = new ArrayList<URI>();
    private List<String> anchorTexts = new ArrayList<String>();
    private boolean notFound;
    private boolean notModified;
    private boolean noContent;
    private boolean noIndex;
    private boolean noFollow;
    private boolean noArchive;
    private URI displayUrl;
    private boolean crawlOnce;
    private boolean lock;
    private TransmissionDecision forcedTransmissionDecision;
    private Map<String, Acl> fragments = new TreeMap<String, Acl>();

    public GetContentsResponse(OutputStream os) {
      this.os = os;
    }

    @Override
    public void respondNotModified() {
      notModified = true;
    }

    @Override
    public void respondNotFound() {
      notFound = true;
    }
   
    @Override
    public void respondNoContent() {
      noContent = true;
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
    public void setLastModified(Date lastModified) {
      this.lastModified = lastModified;
    }

    @Override
    public void addMetadata(String key, String value) {
      this.metadata.add(key, value);
    }

    @Override
    public void setAcl(Acl acl) {
      this.acl = acl;
    }

    @Override
    public void putNamedResource(String fname, Acl facl) {
      this.fragments.put(fname, facl);
    }

    @Override
    public void setSecure(boolean secure) {
      this.secure = secure;
    }

    @Override
    public void addAnchor(URI uri, String text) {
      anchorUris.add(uri);
      anchorTexts.add(text);
    }

    @Override
    public void setNoIndex(boolean noIndex) {
      this.noIndex = noIndex;
    }

    @Override
    public void setNoFollow(boolean noFollow) {
      this.noFollow = noFollow;
    }

    @Override
    public void setNoArchive(boolean noArchive) {
      this.noArchive = noArchive;
    }

    @Override
    public void setDisplayUrl(URI displayUrl) {
      this.displayUrl = displayUrl;
    }

    @Override
    public void setCrawlOnce(boolean crawlOnce) {
      this.crawlOnce = crawlOnce;
    }

    @Override
    public void setLock(boolean lock) {
      this.lock = lock;
    }

    @Override
    public void setForcedTransmissionDecision(TransmissionDecision decision) {
      this.forcedTransmissionDecision = decision;
    }

    public String getContentType() {
      return contentType;
    }

    public Date getLastModified() {
      return lastModified;
    }

    /** Returns reference to modifiable accumulated metadata. */
    public Metadata getMetadata() {
      return metadata;
    }

    public Acl getAcl() {
      return acl;
    }

    public boolean isSecure() {
      return secure;
    }

    public List<URI> getAnchorUris() {
      return anchorUris;
    }

    public List<String> getAnchorTexts() {
      return anchorTexts;
    }

    public boolean isNotFound() {
      return notFound;
    }

    public boolean isNotModified() {
      return notModified;
    }
    
    public boolean isNoContent() {
      return noContent;
    }

    public boolean isNoIndex() {
      return noIndex;
    }

    public boolean isNoFollow() {
      return noFollow;
    }

    public boolean isNoArchive() {
      return noArchive;
    }

    public URI getDisplayUrl() {
      return displayUrl;
    }

    public boolean isCrawlOnce() {
      return crawlOnce;
    }

    public boolean isLock() {
      return lock;
    }

    public TransmissionDecision getForcedTransmissionDecision() {
      return forcedTransmissionDecision;
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
