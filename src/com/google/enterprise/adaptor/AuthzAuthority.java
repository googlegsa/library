// Copyright 2013 Google Inc. All Rights Reserved.
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

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Interface for adaptors capable of authorizing users.
 *
 * <p>Instances of this interface are typically registered with {@link
 * AdaptorContext#setAuthzAuthority}.
 */
public interface AuthzAuthority {
  /**
   * Determines whether the user identified is allowed to access the {@code
   * DocId}s. The user is either anonymous or assumed to be previously
   * authenticated. If an anonymous user is denied access to a document, then
   * the caller may prompt the user to go through an authentication process and
   * then try again.
   *
   * <p>Returns {@link AuthzStatus#PERMIT} for {@link DocId}s the user is
   * allowed to access. Retutrns {@link AuthzStatus#DENY} for {@code DocId}s the
   * user is not allowed to access. If the document exists, {@link
   * AuthzStatus#INDETERMINATE} will not be returned for that {@code DocId}.
   *
   * <p>If the document doesn't exist, then there are several possibilities. If
   * the repository is fully-public then it will return {@code PERMIT}. This
   * will allow the caller to provide a cached version of the file to the user
   * or call {@link Adaptor#getDocContent} which should call {@link
   * Response#respondNotFound}. If the adaptor is not sensitive to users knowing
   * that certain documents do not exist, then it will return {@code
   * INDETERMINATE}. This will be interpreted as the document does not exist; no
   * cached copy will be provided to the user but the user may be informed the
   * document doesn't exist. Highly sensitive repositories may return {@code
   * DENY}.
   *
   * <p>If you experience a fatal error, feel free to throw an {@link
   * IOException} or {@link RuntimeException}. In the case of an error, the
   * users will be denied access to the resources.
   *
   * @param userIdentity user to authorize, or {@code null} for anonymous
   *        users
   * @param ids Collection of {@code DocId}s that need to be checked
   * @return an {@code AuthzStatus} for each {@code DocId} provided in {@code
   *         ids}
   */
  public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity userIdentity,
      Collection<DocId> ids) throws IOException;
}
