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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Abstract class providing most methods required for a {@code DocIdPusher}.
 */
abstract class AbstractDocIdPusher implements DocIdPusher {
  /**
   * Calls {@code pushDocIds(docIds, null)}.
   */
  @Override
  public DocId pushDocIds(Iterable<DocId> docIds)
      throws InterruptedException {
    return pushDocIds(docIds, null);
  }

  /**
   * Calls {@link #pushRecords(Iterable, Adaptor.ExceptionHandler)} with empty
   * metadata for each {@code Record}.
   */
  @Override
  public DocId pushDocIds(Iterable<DocId> docIds,
                          ExceptionHandler handler)
      throws InterruptedException {
    List<Record> records = new ArrayList<Record>();
    for (DocId docId : docIds) {
      records.add(new Record.Builder(docId).build());
    }
    Record record = pushRecords(records, handler);
    return record == null ? null : record.getDocId();
  }

  /**
   * Calls {@code pushRecords(records, null)}.
   */
  @Override
  public Record pushRecords(Iterable<Record> records)
      throws InterruptedException {
    return pushRecords(records, null);
  }

  /**
   * Calls {@code pushNamedResources(resources, null)}.
   */
  @Override
  public DocId pushNamedResources(Map<DocId, Acl> resources)
      throws InterruptedException {
    return pushNamedResources(resources, null);
  }

  /**
   * Calls {@code pushGroupDefinitions(defs, caseSensitive, true, null, null)}.
   */
  @Override
  public GroupPrincipal pushGroupDefinitions(
      Map<GroupPrincipal, ? extends Collection<Principal>> defs,
      boolean caseSensitive) throws InterruptedException {
    return pushGroupDefinitions(defs, caseSensitive, true, null, null);
  }

  /**
   * Calls
   * {@code pushGroupDefinitions(defs, caseSensitive, true, null, handler)}.
   */
  @Override
  public GroupPrincipal pushGroupDefinitions(
      Map<GroupPrincipal, ? extends Collection<Principal>> defs,
      boolean caseSensitive, ExceptionHandler handler)
      throws InterruptedException {
    return pushGroupDefinitions(defs, caseSensitive, true, null, handler);
  }
}
