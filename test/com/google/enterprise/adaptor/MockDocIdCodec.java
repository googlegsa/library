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

import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdDecoder;
import com.google.enterprise.adaptor.DocIdEncoder;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Mock of {@link DocIdDecoder}.
 */
class MockDocIdCodec implements DocIdDecoder, DocIdEncoder {
  @Override
  public DocId decodeDocId(URI uri) {
    return new DocId(uri.getPath().substring(1));
  }

  @Override
  public URI encodeDocId(DocId docId) {
    URI base = URI.create("http://localhost/");
    URI resource;
    try {
      resource = new URI(null, null, "/" + docId.getUniqueId(), null);
    } catch (URISyntaxException ex) {
      throw new AssertionError();
    }
    return base.resolve(resource);
  }
}
