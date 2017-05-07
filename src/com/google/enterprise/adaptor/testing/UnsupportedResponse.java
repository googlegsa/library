// Copyright 2017 Google Inc. All Rights Reserved.
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
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.Response2;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Date;

/**
 * An implementation of {@link Response2} that throws an
 * {@code UnsupportedOperationException} if any method is called.
 *
 * <p>This class is intended to be extended for unit testing, rather
 * than implementing {@link Response} or {@link Response2} directly.
 */
public class UnsupportedResponse implements Response2 {
  /** @throws UnsupportedOperationException always */
  public void respondNotModified() throws IOException {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void respondNotFound() throws IOException {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void respondNoContent() throws IOException {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  public OutputStream getOutputStream() throws IOException {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setContentType(String contentType) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setLastModified(Date lastModified) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void addMetadata(String key, String value) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setAcl(Acl acl) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void putNamedResource(String fragment, Acl acl) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setSecure(boolean secure) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void addAnchor(URI uri, String text) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setNoIndex(boolean noIndex) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setNoFollow(boolean noFollow) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setNoArchive(boolean noArchive) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setDisplayUrl(URI displayUrl) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setCrawlOnce(boolean crawlOnce) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void setLock(boolean lock) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  public void addParam(String key, String value) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }
}
