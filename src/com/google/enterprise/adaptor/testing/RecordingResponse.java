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
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A fake implementation of {@link Response} that simply records the
 * values it receives, and implements the interface semantics of the
 * methods that must be called last. This implementation is not
 * thread-safe.
 * <p>
 * Methods that return collections all return unmodifiable views of
 * the recorded values. The collections cannot be changed directly,
 * but they will reflect changes to the recorded values that are made
 * through the {@code Response} interface.
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
    anchors.add(new SimpleImmutableEntry<String, URI>(text, uri));
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

  @Override
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

  /**
   * Gets an unmodifiable view of the named resources as a {@code Map}
   * from URI fragments to ACLs.
   *
   * @return an unmodifiable view of the named resources
   */
  public Map<String, Acl> getNamedResources() {
    return unmodifiableMap(namedResources);
  }

  public boolean isSecure() {
    return secure;
  }

  /**
   * Gets an unmodifiable view of the accumulated anchors as a multimap.
   *
   * @return a unmodifiable view of the accumulated anchors
   */
  public AnchorMap getAnchors() {
    return new AnchorMap(anchors);
  }

  /**
   * An unmodifiable view of the accumulated anchors as a multimap
   * that supports duplicate, ordered key-value pairs. The anchor
   * texts are the keys, and the anchor URIs are the values.
   * <p>
   * This class is consistent with Guava's {@code LinkedListMultimap},
   * although it does not extend that class, or implement the
   * {@code ListMultimap} interface or all of its methods.
   */
  public static class AnchorMap {
    private final List<Map.Entry<String, URI>> anchors;

    private AnchorMap(List<Map.Entry<String, URI>> anchors) {
      this.anchors = unmodifiableList(anchors);
    }

    @Override
    public String toString() {
      return anchors.toString();
    }

    public boolean isEmpty() {
      return anchors.isEmpty();
    }

    public int size() {
      return anchors.size();
    }

    /**
     * Gets an unmodifiable list of the anchors. The list is in
     * insertion order, and may be empty, but will never be
     * {@code null}. The keys (anchor texts) may be {@code null}.
     * The values (anchor URIs) will not be {@code null}.
     *
     * @return an unmodifiable list of the accumulated anchors
     */
    public List<Map.Entry<String, URI>> entries() {
      return anchors;
    }

    /**
     * Gets an unmodifiable list of the anchor texts. The list is in
     * insertion order and may contain duplicates and nulls.
     *
     * @return a unmodifiable list of the accumulated anchor texts
     */
    public List<String> keyList() {
      List<String> texts = new ArrayList<String>(anchors.size());
      for (Map.Entry<String, URI> entry : anchors) {
        texts.add(entry.getKey());
      }
      return unmodifiableList(texts);
    }

    /**
     * Gets an unmodifiable set of the unique anchor texts. The set is
     * in insertion order based on the first appearance of each key,
     * and may contain {@code null}.
     *
     * @return a unmodifiable set of the accumulated anchor texts
     */
    public Set<String> keySet() {
      Set<String> texts = new LinkedHashSet<String>(anchors.size());
      for (Map.Entry<String, URI> entry : anchors) {
        texts.add(entry.getKey());
      }
      return unmodifiableSet(texts);
    }

    /**
     * Gets an unmodifiable list of the accumulated anchor URIs
     * that match the given text. The text may be {@code null}. The
     * list is in insertion order, and may be empty, but will
     * never be {@code null}. The URIs will not be {@code null}.
     *
     * @param text the anchor text to get the URIs for
     * @return a unmodifiable list of the accumulated anchor URIs
     */
    public List<URI> get(String text) {
      List<URI> uris = new ArrayList<URI>();
      for (Map.Entry<String, URI> entry : anchors) {
        // TODO(jlacey): This is just Objects.equals in Java 7.
        if ((text == null && entry.getKey() == null)
            || (text != null && text.equals(entry.getKey()))) {
          uris.add(entry.getValue());
        }
      }
      return unmodifiableList(uris);
    }
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
   * Gets an unmodifiable view of the accumulated params as a {@code Map}.
   *
   * @return an unmodifiable view of the accumulated params
   */
  public Map<String, String> getParams() {
    return unmodifiableMap(params);
  }
}
