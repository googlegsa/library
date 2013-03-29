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

import java.io.*;
import java.net.URI;
import java.util.Date;

/**
 * Interface provided to {@link Adaptor#getDocContent
 * Adaptor.getDocContent(Request, Response)} for performing the actions needed
 * to satisfy a request.
 *
 * <p>There are several ways that a request can be processed. In the simplest
 * case an Adaptor always sets different pieces of metadata, calls {@link
 * #getOutputStream}, and writes the document contents. If the document does not
 * exist, it should call {@link #respondNotFound} instead.
 *
 * <p>For improved efficiency during recrawl by the GSA, an Adaptor should check
 * {@link Request#hasChangedSinceLastAccess} and call {@link
 * #respondNotModified} when it is {@code true}. This prevents the Adaptor from
 * ever needing to retrieve the document contents and metadata.
 */
public interface Response {
  /**
   * Respond to the GSA or other client that it already has the latest version
   * of a file and its metadata. If you have called other methods on this object
   * to provide various metadata, the effects of those methods will be ignored.
   *
   * <p>If called, this must be the last call to this interface. Once you call
   * this method, for the rest of the processing, exceptions may no longer be
   * communicated to clients cleanly.
   */
  public void respondNotModified() throws IOException;

  /**
   * Respond to the GSA or other client that the request document does not
   * exist. If you have called other methods on this object, the effects of
   * those methods will be ignored.
   *
   * <p>If called, this must be the last call to this interface. Once you call
   * this method, for the rest of the processing, exceptions may no longer be
   * communicated to the clients cleanly.
   */
  public void respondNotFound() throws IOException;

  /**
   * Get stream to write document contents to. There is no need to flush or
   * close the {@code OutputStream} when done.
   *
   * <p>If called, this must be the last call to this interface (although, for
   * convenience, you may call this method multiple times). Once you call this
   * method, for the rest of the processing, exceptions may no longer be
   * communicated to clients cleanly.
   */
  public OutputStream getOutputStream() throws IOException;

  /**
   * Describe the content type of the document.
   */
  public void setContentType(String contentType);

  /**
   * Provide the last modification time of the document.
   */
  public void setLastModified(Date lastModified);

  /**
   * Add metadata element that applies to the document. Providing multiple
   * values for the same key is supported; simply repeat the call once for each
   * value.
   *
   * @param key the key of metadata element
   * @param value the value of metadata element
   * @throws NullPointerException if {@code key} or {@code value}
   *     is {@code null}
   */
  public void addMetadata(String key, String value);

  /**
   * Provide the document's ACLs for early-binding security on the GSA. By
   * default, the document's ACL will be {@code null}, which means the document
   * is public if the document isn't marked as secure via {@link #setSecure}.
   */
  public void setAcl(Acl acl);

  /**
   * Mark the document as secure, for use with late-binding security. By
   * default, the secure setting will be {@code false}, which means the document
   * is public if there are no ACLs. ACLs should be used, if possible, instead
   * of setting this option to {@code true}. When {@code true}, the GSA needs to
   * be correctly configured to issue a SAML request to the Adaptor.
   * Setting ACLs to non-null will override setSecure and send secure indicator
   * to GSA.
   */
  public void setSecure(boolean secure);

  /**
   * Add a hyperlink for the GSA to follow without modifying the document
   * contents. This is equivalent to the following HTML: {@code
   * <a href='$uri'>$text</a>}. If you want to link to a {@link DocId}, then you
   * may use the {@link DocIdEncoder} provided by {@link
   * AdaptorContext#getDocIdEncoder} to produce an appropriate URI.
   *
   * @param uri the URI of the anchor
   * @param text the text of the anchor, or {@code null}
   * @throws NullPointerException if {@code uri} is {@code null}
   */
  public void addAnchor(URI uri, String text);

  /**
   * Whether the GSA should index the content for searching. When {@code true},
   * the document will not be visible in search results. This does not change
   * the GSA's behavior of following links within the document to find other
   * documents. By default, the GSA will index the document (a value of {@code
   * false}).
   *
   * @param noIndex {@code true} when the GSA shouldn't index this document
   */
  public void setNoIndex(boolean noIndex);

  /**
   * Whether the GSA should follow the links within the document to find other
   * documents. By default, the GSA will follow links (a value of {@code
   * false}).
   *
   * @param noFollow {@code true} when the GSA shouldn't follow links from this
   *     document to find other documents
   */
  public void setNoFollow(boolean noFollow);

  /**
   * Whether the GSA should show the "Cached" link in search results for this
   * document. By default, the GSA will show the "Cached" link (a value of
   * {@code false}).
   *
   * @param noArchive {@code true} when the GSA shouldn't show the "Cached"
   *     link in search results
   */
  public void setNoArchive(boolean noArchive);
}
