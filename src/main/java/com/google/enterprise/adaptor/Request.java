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

/**
 * Interface provided to {@link Adaptor#getDocContent
 * Adaptor.getDocContent(Request, Response)} for describing the action that
 * should be taken.
 *
 * <p>Avoid implementing this interface in adaptor unit tests because
 * new methods may be added in the future. Instead use
 * {@link DocRequest}, {@link UnsupportedRequest}, or an automated mock
 * generator like Mockito or {@code java.lang.reflect.Proxy}.
 */
public interface Request {
  /**
   * Returns {@code true} if the GSA or other client's current copy of the
   * document was retrieved after the {@code lastModified} date; {@code false}
   * otherwise. {@code lastModified} must be in GMT.
   *
   * <p>If {@code false}, the client does not need to be re-sent the data, since
   * what they have cached is the most recent version. In this case, you should
   * then call {@link Response#respondNotModified}.
   *
   * @param lastModified is actual time document was last modified
   * @return whether updated content should be sent 
   */
  public boolean hasChangedSinceLastAccess(Date lastModified);
  /**
   * Returns {@code true} if the GSA or other client's current copy of the
   * document was retrieved after the {@code lastModified} date and GSA can 
   * handle HTTP 204 response; {@code false} otherwise. {@code lastModified} 
   * must be in GMT.
   *
   * <p>If {@code true}, the client does not need to be re-sent the file content
   * , since what they have cached is the most recent version. In this case, 
   * you should then call {@link Response#respondNoContent}.
   *
   * @param lastModified is actual time document was last modified
   * @return whether sending headers (metadata, ACLs, etc) is enough
   */
  public boolean canRespondWithNoContent(Date lastModified);
  /**
   * Returns the last time a GSA or other client retrieved the data, or {@code
   * null} if none was provided by the client. The returned {@code Date} is in
   * GMT.
   *
   * <p>This is useful for determining if the client needs to be re-sent the
   * data since what they have cached may be the most recent version. If the
   * client is up-to-date, then call {@link Response#respondNotModified}.
   *
   * @return date in GMT client last accessed the DocId or {@code null}
   */
  public Date getLastAccessTime();

  /**
   * Provides the document ID for the document that is being requested. {@code
   * DocId} was not necessarily provided previously by the Adaptor; <b>it is
   * client-provided and must not be trusted</b>. If the document does not
   * exist, then {@link Adaptor#getDocContent} must call {@link
   * Response#respondNotFound}.
   *
   * @return id being requested
   */
  public DocId getDocId();
}
