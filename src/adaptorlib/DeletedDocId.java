// Copyright 2011 Google Inc.
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
 * A DocId that when sent to GSA
 * results in quickly removing referenced document
 * from crawling and index.
 * <p> Please note that GSA will figure out a document
 * is deleted on its own and sending a DeletedDocId is
 * optional.  Sending the GSA DeletedDocId
 * instances will be faster than waiting for GSA to
 * realize a document has been deleted.
 * <p> @see DocId for more details.
 */
public class DeletedDocId extends DocId {
  public DeletedDocId(String id) {
    super(id);
  }

  /** Provides delete for action attribute value. */
  String getFeedFileAction() {
    return "delete";
  } 

  /** "DeletedDocId(" + getUniqueId() + ")" */
  public String toString() {
    return "DeletedDocId(" + getUniqueId() + ")";
  }
}
