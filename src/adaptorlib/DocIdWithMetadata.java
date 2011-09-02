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

import java.util.Arrays;

public class DocIdWithMetadata extends DocId {
  private Metadata metabox;

  public DocIdWithMetadata(String id, Metadata metadata) {
    super(id);
    if (null == metadata) {
      throw new IllegalArgumentException("metadata is required");
    }
    this.metabox = metadata;
  }

  Metadata getMetadata() {
    return metabox;
  }

  /** "DocIdWithMetadata(" + getUniqueId() + "," + metadata + ")" */
  public String toString() {
    return "DocIdWithMetadata(" + getUniqueId() + "," + metabox + ")";
  }

  public boolean equals(Object o) {
    boolean same = false;
    if (null != o && getClass().equals(o.getClass())) {
      DocIdWithMetadata d = (DocIdWithMetadata) o;
      same = super.equals(d) && metabox.equals(d.metabox);
    }
    return same;
  }

  public int hashCode() {
    Object parts[] = new Object[] { super.hashCode(), metabox.hashCode() };
    return Arrays.hashCode(parts);
  }
}
