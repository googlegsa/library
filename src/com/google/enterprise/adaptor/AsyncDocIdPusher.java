// Copyright 2014 Google Inc. All Rights Reserved.
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

/**
 * Interface that allows asynchronous at-will pushing of {@code DocId}s
 * to the GSA.
 */
public interface AsyncDocIdPusher {
  /**
   * Push a {@code DocId} asynchronously to the GSA. The {@code DocId} is
   * enqueued and sent in the next batch to the GSA. If the queue is full,
   * then the item will be dropped and a warning will be logged.
   *
   * @return {@code true} if the DocId was accepted, {@code false} otherwise
   */
  public boolean pushDocId(DocId docId);

  /**
   * Push a {@code Record} asynchronously to the GSA. The {@code Record}
   * is enqueued and sent in the next batch to the GSA. If the queue is full,
   * then the item will be dropped and a warning will be logged.
   *
   * @return {@code true} if the Record was accepted, {@code false} otherwise
   */
  public boolean  pushRecord(DocIdPusher.Record record);

  /**
   * Push a named resource asynchronously to the GSA. The named resource is
   * enqueued and sent in the next batch to the GSA. If the queue is full,
   * then the item will be dropped and a warning will be logged.
   *
   * <p>Named resources are {@code DocId}s without any content or metadata,
   * that only exist for ACL inheritance. These {@code DocId} will never be
   * visible to the user and have no meaning outside of ACL processing.
   *
   * @return {@code true} if the NamedResource was accepted, {@code false}
   *   otherwise
   */
  public boolean pushNamedResource(DocId docId, Acl acl);
}
