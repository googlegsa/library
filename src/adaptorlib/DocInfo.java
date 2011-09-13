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

/**
 * DocId and Metadata pair for communicating with {@link Adaptor.DocIdPusher}.
 */
public class DocInfo {
  private final DocId docId;
  private final Metadata metadata;

  public DocInfo(DocId docId, Metadata metadata) {
    if (docId == null || metadata == null) {
      throw new NullPointerException();
    }
    this.docId = docId;
    this.metadata = metadata;
  }

  public DocId getDocId() {
    return docId;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }
    DocInfo docRecord = (DocInfo) o;
    return docId.equals(docRecord.docId) && metadata.equals(docRecord.metadata);
  }

  @Override
  public int hashCode() {
    return docId.hashCode() ^ metadata.hashCode();
  }

  @Override
  public String toString() {
    return "DocInfo(" + docId + "," + metadata + ")";
  }
}
