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

import com.google.enterprise.adaptor.testing.UnsupportedRequest;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A complete implementation of {@link Request}.
 *
 * @see UnsupportedRequest
 */
public class DocRequest implements Request {
  private static final Logger log
      = Logger.getLogger(DocRequest.class.getName());

  private final DocId docId;
  private final Date lastAccessTime;
  private final boolean isNoContentSupported;

  /**
   * Constructs a request with a null access time that supports HTTP
   * 204 responses. A null access times implies that the document has
   * never been crawled.
   *
   * @param docId the requested document ID
   */
  public DocRequest(DocId docId) {
    this(docId, null);
  }

  /**
   * Constructs a request that supports HTTP 204 responses.
   *
   * @param docId the requested document ID
   * @param lastAccessTime the last time the document was crawled
   */
  public DocRequest(DocId docId, Date lastAccessTime) {
    this(docId, lastAccessTime, true);
  }

  /** Constructs a request.
   *
   * @param docId the requested document ID
   * @param lastAccessTime the last time the document was crawled
   * @param isNoContentSupported {@code true} if an HTTP 204 response
   *     is allowed, and {@code false} otherwise
   */
  public DocRequest(DocId docId, Date lastAccessTime,
      boolean isNoContentSupported) {
    this.docId = docId;
    this.lastAccessTime = lastAccessTime;
    this.isNoContentSupported = isNoContentSupported;
  }

  @Override
  public String toString() {
    return "Request(docId=" + docId + ",lastAccessTime=" + lastAccessTime + ")";
  }

  @Override
  public DocId getDocId() {
    return docId;
  }

  @Override
  public Date getLastAccessTime() {
    return lastAccessTime;
  }

  @Override
  public boolean hasChangedSinceLastAccess(Date lastModified) {
    if (lastAccessTime == null) {
      return true;
    }
    if (lastModified == null) {
      throw new NullPointerException("last modified is null");
    }
    // Adjust last modified date time by stripping milliseconds part as
    // last access time will not have milliseconds part.
    Date lastModifiedAdjusted
        = new Date(1000 * (lastModified.getTime() / 1000));
    log.log(Level.FINEST, "Last modified date time value {0} adjusted to {1}",
        new Object[] {lastModified.getTime(), lastModifiedAdjusted.getTime()});
    return lastAccessTime.before(lastModifiedAdjusted);
  }

  /**
   * @return {@code true} if the docuemnt has not changed and we are
   *     either talking to GSA version 7.4 or higher, or a web browser,
   *     and {@code false} otherwise
   */
  // Note: for web browser startSending will convert 204 to 304
  @Override
  public boolean canRespondWithNoContent(Date lastModified) {
    return isNoContentSupported && !hasChangedSinceLastAccess(lastModified);
  }
}
