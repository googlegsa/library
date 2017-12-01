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

package com.google.enterprise.adaptor.testing;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform.TransmissionDecision;
import com.google.enterprise.adaptor.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A fake implementation of {@link Response} that simply records the
 * values it receives, and implements the interface semantics of the
 * methods that must be called last. This implementation is not
 * thread-safe.
 */
public class RecordingResponse implements Response {
  /**
   * Response states based on calls to the {@code respondXXX} and
   * {@code getOutputStream} methods.
   */
  public enum State { SETUP, NOT_MODIFIED, NOT_FOUND, NO_CONTENT, SEND_BODY };

  private final OutputStream os;

  private State state = State.SETUP;
  private String contentType;
  private Date lastModified;
  private final Metadata metadata = new Metadata();
  private Acl acl;
  private final Map<String, Acl> namedResources = new TreeMap<String, Acl>();
  private boolean secure;
  private final List<Map.Entry<String, URI>> anchors =
      new ArrayList<Map.Entry<String, URI>>();
  private boolean noIndex;
  private boolean noFollow;
  private boolean noArchive;
  private URI displayUrl;
  private boolean crawlOnce;
  private boolean lock;
  private TransmissionDecision forcedTransmissionDecision;
  private final Map<String, String> params = new TreeMap<String, String>();

  /**
   * Constructs a mock {@code Response} with a {@code ByteArrayOutputStream}.
   */
  public RecordingResponse() {
    this(new ByteArrayOutputStream());
  }

  /**
   * Constructs a mock {@code Response} with the given {@code OutputStream}.
   *
   * @param os the output stream that will be returned from
   *     {@link #getOutputStream}
   */
  public RecordingResponse(OutputStream os) {
    this.os = os;
  }

  @Override
  public void respondNotModified() throws IOException {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    state = State.NOT_MODIFIED;
  }

  @Override
  public void respondNotFound() throws IOException {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    state = State.NOT_FOUND;
  }

  @Override
  public void respondNoContent() throws IOException {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    state = State.NO_CONTENT;
  }

  @Override
  public OutputStream getOutputStream() {
    switch (state) {
      case SETUP:
        state = State.SEND_BODY;
        return os;
      case SEND_BODY:
        return os;
      default:
        throw new IllegalStateException("Already responded " + state);
    }
  }

  @Override
  public void setContentType(String contentType) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.contentType = contentType;
  }

  @Override
  public void setLastModified(Date lastModified) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.lastModified = lastModified;
  }

  @Override
  public void addMetadata(String key, String value) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    metadata.add(key, value);
  }

  @Override
  public void setAcl(Acl acl) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.acl = acl;
  }

  @Override
  public void putNamedResource(String fragment, Acl acl) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    namedResources.put(fragment, acl);
  }

  @Override
  public void setSecure(boolean secure) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.secure = secure;
  }

  @Override
  public void addAnchor(URI uri, String text) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    if (uri == null) {
      throw new NullPointerException();
    }
    anchors.add(new SimpleEntry<String, URI>(text, uri));
  }

  @Override
  public void setNoIndex(boolean noIndex) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.noIndex = noIndex;
  }

  @Override
  public void setNoFollow(boolean noFollow) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.noFollow = noFollow;
  }

  @Override
  public void setNoArchive(boolean noArchive) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.noArchive = noArchive;
  }

  @Override
  public void setDisplayUrl(URI displayUrl) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.displayUrl = displayUrl;
  }

  @Override
  public void setCrawlOnce(boolean crawlOnce) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.crawlOnce = crawlOnce;
  }

  @Override
  public void setLock(boolean lock) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.lock = lock;
  }

  // TODO(bmj): @Override
  public void setForcedTransmissionDecision(TransmissionDecision decision) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    this.forcedTransmissionDecision = decision;
  }

  @Override
  public void setParam(String key, String value) {
    if (state != State.SETUP) {
      throw new IllegalStateException("Already responded " + state);
    }
    params.put(key, value);
  }

  public State getState() {
    return state;
  }

  public String getContentType() {
    return contentType;
  }

  public Date getLastModified() {
    return lastModified;
  }

  /**
   * Gets an unmodifiable view of the accumulated {@code Metadata}.
   *
   * @return an unmodifiable view of the accumulated {@code Metadata}
   */
  public Metadata getMetadata() {
    return metadata.unmodifiableView();
  }

  public Acl getAcl() {
    return acl;
  }

  public Map<String, Acl> getNamedResources() {
    return unmodifiableMap(namedResources);
  }

  public boolean isSecure() {
    return secure;
  }

  /**
   * Gets an unmodifiable list of the accumulated anchors.
   *
   * @return a unmodifiable list of the accumulated anchors
   */
  public List<Map.Entry<String, URI>> getAnchors() {
    return unmodifiableList(anchors);
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

  /**
   * Gets an unmodifiable map of the accumulated params.
   *
   * @return an unmodifiable map of the accumulated params
   */
  public Map<String, String> getParams() {
    return unmodifiableMap(params);
  }
}
