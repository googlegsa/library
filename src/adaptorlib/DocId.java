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
 * DocId refers to a unique document in repository.
 * You give the adaptorlib a DocId to have it insert your
 * document for crawl and index.
 * The adaptorlib provides the DocId when it asks your code
 * for some information about a particular document in
 * repository.  For example when the adaptorlib wants the bytes
 * of a particular document or when it wants to find
 * out if a particular user has read permissions for it.
 */
public class DocId {
  private final String uniqId;  // Not null.

  public DocId(String id) {
    if (id == null) {
      throw new NullPointerException();
    }
    this.uniqId = id;
  }

  public String getUniqueId() {
    return uniqId;
  }

  /** "DocId(" + uniqId + ")" */
  @Override
  public String toString() {
    return "DocId(" + uniqId + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (null == o || !getClass().equals(o.getClass())) {
      return false;
    }
    DocId d = (DocId) o;
    return this.uniqId.equals(d.uniqId);
  }

  @Override
  public int hashCode() {
    return this.uniqId.hashCode();
  }
}
