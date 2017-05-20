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

package com.google.enterprise.adaptor.testing;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;

import com.google.enterprise.adaptor.AbstractDocIdPusher;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.ExceptionHandler;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A fake implementation of {@link DocIdPusher} that simply records
 * the values it receives.
 */
public class RecordingDocIdPusher extends AbstractDocIdPusher {
  private List<DocId> ids = new ArrayList<DocId>();
  private List<Record> records = new ArrayList<Record>();
  private Map<DocId, Acl> namedResources = new TreeMap<DocId, Acl>();
  private Map<GroupPrincipal, Collection<Principal>> groups
      = new TreeMap<GroupPrincipal, Collection<Principal>>();

  @Override
  public Record pushRecords(Iterable<Record> records, ExceptionHandler handler)
      throws InterruptedException {
    for (Record record : records) {
      ids.add(record.getDocId());
      this.records.add(record);
    }
    return null;
  }

  @Override
  public DocId pushNamedResources(Map<DocId, Acl> resources,
      ExceptionHandler handler) throws InterruptedException {
    namedResources.putAll(resources);
    return null;
  }

  @Override
  public GroupPrincipal pushGroupDefinitions(
      Map<GroupPrincipal, ? extends Collection<Principal>> defs,
      boolean caseSensitive, ExceptionHandler handler)
      throws InterruptedException {
    for (GroupPrincipal key : defs.keySet()) {
      Collection<Principal> members = defs.get(key);
      if (members instanceof List) {
        groups.put(key, unmodifiableList(new ArrayList<Principal>(members)));
      } else if (members instanceof Set) {
        groups.put(key, unmodifiableSet(new LinkedHashSet<Principal>(members)));
      } else {
        groups.put(key, members);
      }
    }
    return null;
  }

  public List<DocId> getDocIds() {
    return Collections.unmodifiableList(ids);
  }

  public List<Record> getRecords() {
    return Collections.unmodifiableList(records);
  }

  public Map<DocId, Acl> getNamedResources() {
    return Collections.unmodifiableMap(namedResources);
  }

  public Map<GroupPrincipal, Collection<Principal>> getGroupDefinitions() {
    return Collections.unmodifiableMap(groups);
  }

  public void reset() {
    ids.clear();
    records.clear();
    namedResources.clear();
    groups.clear();
  }
}
