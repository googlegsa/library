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

package adaptorlib;

import java.util.*;

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
   * Calls {@link #pushDocInfos(Iterable, Adaptor.PushErrorHandler)} with empty
   * metadata for each {@code DocInfo}.
   */
  @Override
  public DocId pushDocIds(Iterable<DocId> docIds,
                          PushErrorHandler handler)
      throws InterruptedException {
    List<DocInfo> docInfos = new ArrayList<DocInfo>();
    for (DocId docId : docIds) {
      docInfos.add(new DocInfo(docId, PushAttributes.DEFAULT));
    }
    DocInfo record = pushDocInfos(docInfos, handler);
    return record == null ? null : record.getDocId();
  }

  /**
   * Calls {@code pushDocInfos(docInfos, null)}.
   */
  @Override
  public DocInfo pushDocInfos(Iterable<DocInfo> docInfos)
      throws InterruptedException {
    return pushDocInfos(docInfos, null);
  }
}
