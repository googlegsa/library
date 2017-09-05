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

import static com.google.enterprise.adaptor.DocIdPusher.FeedType.INCREMENTAL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This class provides an implementation of the forwarding methods of
 * the {@link DocIdPusher} interface.
 */
public abstract class AbstractDocIdPusher implements DocIdPusher {
  /** {@inheritDoc} */
  @Override
  public DocId pushDocIds(Iterable<DocId> docIds)
      throws InterruptedException {
    return pushDocIds(docIds, null);
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public Record pushRecords(Iterable<Record> records)
      throws InterruptedException {
    return pushRecords(records, null);
  }

  /** {@inheritDoc} */
  @Override
  public DocId pushNamedResources(Map<DocId, Acl> resources)
      throws InterruptedException {
    return pushNamedResources(resources, null);
  }

  /** {@inheritDoc} */
  @Override
  public GroupPrincipal pushGroupDefinitions(
      Map<GroupPrincipal, ? extends Collection<Principal>> defs,
      boolean caseSensitive) throws InterruptedException {
    return pushGroupDefinitions(defs, caseSensitive, INCREMENTAL, null, null);
  }

  /** {@inheritDoc} */
  @Override
  public GroupPrincipal pushGroupDefinitions(
      Map<GroupPrincipal, ? extends Collection<Principal>> defs,
      boolean caseSensitive, ExceptionHandler handler)
      throws InterruptedException {
    return pushGroupDefinitions(defs, caseSensitive, INCREMENTAL, null,
        handler);
  }
}
