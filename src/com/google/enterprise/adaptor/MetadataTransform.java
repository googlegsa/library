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

import java.util.Map;

/**
 * Represents an individual transform in the transform pipeline.
 *
 * <p>Implementations should also typically have a static factory method with a
 * single {@code Map<String, String>} argument for creating instances based on
 * configuration.
 */
public interface MetadataTransform {

  public static final String KEY_DOC_ID = "DocId";
  public static final String KEY_DISPLAY_URL = "Display-URL";
  public static final String KEY_CONTENT_TYPE = "Content-Type";
  public static final String KEY_CRAWL_ONCE = "Crawl-Once";
  public static final String KEY_LOCK = "Lock";
  public static final String KEY_LAST_MODIFIED_MILLIS_UTC
      = "Last-Modified-Millis-UTC";
  public static final String KEY_TRANSMISSION_DECISION 
      = "Transmission-Decision";
  public static final String KEY_FORCED_TRANSMISSION_DECISION
      = "Forced-Transmission-Decision";

  /** Transforms can cancel sending doc, or cancel sending its contents. */
  public enum TransmissionDecision {
    AS_IS("as-is"),
    DO_NOT_INDEX("do-not-index"),
    DO_NOT_INDEX_CONTENT("do-not-index-content");

    private final String name;

    private TransmissionDecision(String n) {
      name = n;
    }

    public static TransmissionDecision from(String val) {
      if (null == val) {
        return AS_IS;
      }
      return TransmissionDecision.valueOf(val.replace('-', '_').toUpperCase());
    }

    public String toString() {
      return name;
    }
  };

  /**
   * Any changes to {@code metadata} and {@code params} will be
   * passed on to subsequent transforms. This method must be thread-safe.
   * @param metadata of document
   * @param params are extra contextual information
   */
  public void transform(Metadata metadata, Map<String, String> params);

  /** 
   * Versions prior to 4.1.1 used class named "DocumentTransform".
   * This wrapper class allows historical classes of that previous 
   * type to work without requiring recompilation.
   */
  @SuppressWarnings("deprecation")
  static class HistoricalWrapper implements MetadataTransform {
    private final DocumentTransform wrapped;
    HistoricalWrapper(DocumentTransform dt) {
      if (null == dt) {
        throw new NullPointerException();
      }
      this.wrapped = dt;
    }
    
    public void transform(Metadata metadata, Map<String, String> params) {
      wrapped.transform(metadata, params);
    }
  }
}
