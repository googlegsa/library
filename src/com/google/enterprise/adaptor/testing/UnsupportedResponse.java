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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Date;

/**
 * An implementation of {@link Response} that throws an
 * {@code UnsupportedOperationException} if any method is called.
 *
 * <p>This class is intended to be extended for unit testing, rather
 * than implementing the {@link Response} interface directly.
 */
public class UnsupportedResponse implements Response {
  /** @throws UnsupportedOperationException always */
  @Override
  public void respondNotModified() throws IOException {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void respondNotFound() throws IOException {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void respondNoContent() throws IOException {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public OutputStream getOutputStream() throws IOException {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setContentType(String contentType) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setLastModified(Date lastModified) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void addMetadata(String key, String value) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setAcl(Acl acl) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void putNamedResource(String fragment, Acl acl) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setSecure(boolean secure) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void addAnchor(URI uri, String text) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setNoIndex(boolean noIndex) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setNoFollow(boolean noFollow) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setNoArchive(boolean noArchive) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setDisplayUrl(URI displayUrl) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setCrawlOnce(boolean crawlOnce) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setLock(boolean lock) {
    throw new UnsupportedOperationException("UnsupportedResponse was called");
  }
}
