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

/**
 * Refers to a unique document in repository. It is a thin wrapper of {@link
 * String} that adds meaning to the String and prevents the string from being
 * confused with others that do not refer to documents.
 *
 * <p>You provide a {@code DocId} to methods like {@link DocIdPusher#pushDocIds}
 * to inform the GSA about the document's existance, so that it can crawl and
 * index it. When the GSA requests that document's contents, {@link
 * Request#getDocId} will have the same unique id as {@code DocId} you provided.
 * However, just because a {@code DocId} is provided, does not mean the value it
 * represents exists or ever existed. The GSA can query for documents that have
 * been deleted and users can query for documents that never existed.
 */
public class DocId implements Comparable<DocId> {
  private final String uniqId;  // Not null.

  /**
   * Construct an identifier based on {@code id}.
   *
   * @param id non-{@code null} document identifier
   */
  public DocId(String id) {
    if (id == null) {
      throw new NullPointerException();
    }
    this.uniqId = id;
  }

  /**
   * Returns the string identifier provided to the constructor.
   * @return String id
   */
  public String getUniqueId() {
    return uniqId;
  }

  /** Generates a string useful for debugging that contains the unique id. */
  @Override
  public String toString() {
    return "DocId(" + uniqId + ")";
  }

  /**
   * Determines equality based on the unique id string.
   */
  @Override
  public boolean equals(Object o) {
    if (null == o || !getClass().equals(o.getClass())) {
      return false;
    }
    DocId d = (DocId) o;
    return this.uniqId.equals(d.uniqId);
  }

  /**
   * Generates a hash code based on the unique id string.
   */
  @Override
  public int hashCode() {
    return this.uniqId.hashCode();
  }

  /**
   * Provides comparison for ids based on the unique id string.
   */
  @Override
  public int compareTo(DocId docId) {
    return uniqId.compareTo(docId.uniqId);
  }
}
