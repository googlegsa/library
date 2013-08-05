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

import java.net.*;

/**
 * Codec for encoding and decoding {@code DocId}s to {@code URI}s.
 */
class DocIdCodec implements DocIdEncoder, DocIdDecoder {
  private final URI baseDocUri;
  private final boolean isDocIdUrl;

  public DocIdCodec(URI baseDocUri, boolean isDocIdUrl) {
    if (baseDocUri == null) {
      throw new NullPointerException();
    }
    if (baseDocUri.getPath() == null) {
      throw new NullPointerException("Provided URI must have a non-null path");
    }
    this.baseDocUri = baseDocUri;
    this.isDocIdUrl = isDocIdUrl;
  }

  public URI encodeDocId(DocId docId) {
    if (isDocIdUrl) {
      return URI.create(docId.getUniqueId());
    } else {
      URI resource;
      String uniqueId = docId.getUniqueId();
      // Add two dots to any sequence of only dots. This is to allow "/../" and
      // "/./" within DocIds.
      uniqueId = uniqueId.replaceAll("(^|/)(\\.+)(?=$|/)", "$1$2..");
      try {
        resource = new URI(null, null, baseDocUri.getPath() + uniqueId, null);
      } catch (URISyntaxException ex) {
        throw new IllegalStateException(ex);
      }
      return baseDocUri.resolve(resource);
    }
  }

  /** Given a URI that was used in feed file, convert back to doc id. */
  public DocId decodeDocId(URI uri) {
    if (isDocIdUrl) {
      return new DocId(uri.toString());
    } else {
      String basePath = baseDocUri.getPath();
      if (!uri.getPath().startsWith(basePath)) {
        throw new IllegalArgumentException("URI does not refer to a DocId");
      }
      String id = uri.getPath().substring(basePath.length());
      // Remove two dots from any sequence of only dots. This is to remove the
      // addition we did in {@link #encodeDocId}.
      id = id.replaceAll("(^|/)(\\.+)\\.\\.(?=$|/)", "$1$2");
      return new DocId(id);
    }
  }
}
