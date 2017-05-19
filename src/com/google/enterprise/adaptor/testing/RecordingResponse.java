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
 * values it receives.
 */
public class RecordingResponse implements Response {
  private final OutputStream os;

  private boolean notModified;
  private boolean notFound;
  private boolean noContent;
  private String contentType;
  private Date lastModified;
  private Metadata metadata = new Metadata();
  private Acl acl;
  private Map<String, Acl> namedResources = new TreeMap<String, Acl>();
  private boolean secure;
  private List<Map.Entry<String, URI>> anchors =
      new ArrayList<Map.Entry<String, URI>>();
  private boolean noIndex;
  private boolean noFollow;
  private boolean noArchive;
  private URI displayUrl;
  private boolean crawlOnce;
  private boolean lock;
  private TransmissionDecision forcedTransmissionDecision;
  private Map<String, String> params = new TreeMap<String, String>();

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
    notModified = true;
  }

  @Override
  public void respondNotFound() throws IOException {
    notFound = true;
  }

  @Override
  public void respondNoContent() throws IOException {
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
    metadata.add(key, value);
  }

  @Override
  public void setAcl(Acl acl) {
    this.acl = acl;
  }

  @Override
  public void putNamedResource(String fragment, Acl acl) {
    namedResources.put(fragment, acl);
  }

  @Override
  public void setSecure(boolean secure) {
    this.secure = secure;
  }

  @Override
  public void addAnchor(URI uri, String text) {
    anchors.add(new SimpleEntry<String, URI>(text, uri));
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

  // TODO(bmj): @Override
  public void setForcedTransmissionDecision(TransmissionDecision decision) {
    this.forcedTransmissionDecision = decision;
  }

  @Override
  public void setParam(String key, String value) {
    params.put(key, value);
  }

  public boolean isNotModified() {
    return notModified;
  }

  public boolean isNotFound() {
    return notFound;
  }

  public boolean isNoContent() {
    return noContent;
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

  public TransmissionDecision getForcedTransmissionDecision() {
    return forcedTransmissionDecision;
  }

  public Map<String, String> getParams() {
    return params;
  }
}
