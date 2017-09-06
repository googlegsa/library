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

import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.Request;

import java.util.Date;

/**
 * An implementation of {@link Request} that throws an
 * {@code UnsupportedOperationException} if any method is called.
 *
 * <p>This class is intended to be extended for unit testing, rather
 * than implementing the {@link Request} interface directly.
 */
public class UnsupportedRequest implements Request {
  /** @throws UnsupportedOperationException always */
  @Override
  public boolean hasChangedSinceLastAccess(Date lastModified) {
    throw new UnsupportedOperationException("UnsupportedRequest was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public boolean canRespondWithNoContent(Date lastModified) {
    throw new UnsupportedOperationException("UnsupportedRequest was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public Date getLastAccessTime() {
    throw new UnsupportedOperationException("UnsupportedRequest was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public DocId getDocId() {
    throw new UnsupportedOperationException("UnsupportedRequest was called");
  }
}
