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

import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fake implementation of {@link Response} that simply records the
 * values it receives, and implements the interface semantics of the
 * methods that must be called last.
 */
public class RecordingResponse implements Response {
  // TODO(jlacey): Implement Response2.

  /**
   * Response states based on calls to the {@code respondXXX} and
   * {@code getOutputStream} methods.
   */
  public enum State { SETUP, NOT_MODIFIED, NOT_FOUND, NO_CONTENT, SEND_BODY };

  private final OutputStream os;
  private State state = State.SETUP;
  private String contentType;
  private Date lastModified;
  private Metadata metadata = new Metadata();
  private Acl acl;
  private Map<String, Acl> namedResources = new HashMap<String, Acl>();
  private boolean secure;
  private List<Map.Entry<String, URI>> anchors =
      new ArrayList<Map.Entry<String, URI>>();
  private boolean noIndex;
  private boolean noFollow;
  private boolean noArchive;
  private URI displayUrl;
  private boolean crawlOnce;
  private boolean lock;

  /**
   * Constructs a mock {@code Response} with a {@code ByteArrayOutputStream}.
   */
  public RecordingResponse() {
    this(new ByteArrayOutputStream());
  }

  /**
   * Constructs a mock {@code Response} with the given {@code OutputStream}.
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

  public State getState() {
    return state;
  }

  public String getContentType() {
    return contentType;
  }

  public Date getLastModified() {
    return lastModified;
  }

  /** Returns a reference to unmodifiable, accumulated metadata. */
  public Metadata getMetadata() {
    return metadata.unmodifiableView();
  }

  public Acl getAcl() {
    return acl;
  }

  public Map<String, Acl> getNamedResources() {
    return namedResources;
  }

  public boolean isSecure() {
    return secure;
  }

  /** Returns a reference to modifiable, accumulated anchors. */
  public List<Map.Entry<String, URI>> getAnchors() {
    return anchors;
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
}
