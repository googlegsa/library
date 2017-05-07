// Copyright 2017 Google Inc. All Rights Reserved.
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

import com.google.enterprise.adaptor.testing.UnsupportedResponse;

/**
 * Extended interface provided to {@link Adaptor#getDocContent
 * Adaptor.getDocContent(Request, Response)} for performing the actions needed
 * to satisfy a request.
 *
 * <p>This interface should not be implemented directly for unit testing.
 * Instead, use a mocking framework, such as Mockito, a
 * {@code java.lang.reflect.Proxy}, or extend the {@link UnsupportedResponse}
 * class.
 */
public interface Response2 extends Response {
  /**
   * Adds a parameter to a map for use by {@link MetadataTransform
   * MetadataTransforms} when making transforms or decisions. Params
   * are data associated with the document, but might not be indexed
   * and searchable. The {@code params} include the document's {@link
   * DocId}, and values from {@link #setLock setLock}, {@link
   * #setCrawlOnce setCrawlOnce}, {@link #setDisplayUrl
   * setDisplayUrl}, {@link #setContentType setContentType}, and
   * {@link #setLastModified setLastModified}.
   *
   * @param key a key for a Map entry
   * @param value the value for the Map entry for key
   */
  // TODO (bmj): Supply Params to ContentTransforms.
  public void addParam(String key, String value);
}
