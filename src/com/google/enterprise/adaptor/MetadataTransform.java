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

import static java.util.Locale.US;

import java.util.Map;

/**
 * Represents an individual transform in the transform pipeline.
 *
 * <p>Implementations should also typically have a static factory method with a
 * single {@code Map<String, String>} argument for creating instances based on
 * configuration.
 */
public interface MetadataTransform {

  /** The key for the document's unique ID supplied in the {@code params}. */
  public static final String KEY_DOC_ID = "DocId";

  /** The key for the document's display URL supplied in the {@code params}. */
  public static final String KEY_DISPLAY_URL = "Display-URL";

  /** The key for the document's content type supplied in the {@code params}. */
  public static final String KEY_CONTENT_TYPE = "Content-Type";

  /**
   * The key for the document's crawl once flag supplied in the {@code params}.
   */
  public static final String KEY_CRAWL_ONCE = "Crawl-Once";

  /** The key for the document's lock flag supplied in the {@code params}. */
  public static final String KEY_LOCK = "Lock";

  /**
   * The key for the document's last modified timestamp supplied in the
   * {@code params}.
   */
  public static final String KEY_LAST_MODIFIED_MILLIS_UTC
      = "Last-Modified-Millis-UTC";

  /**
   * The key for a {@link TransmissionDecision} that may be added to the
   * {@code params}.
   */
  public static final String KEY_TRANSMISSION_DECISION 
      = "Transmission-Decision";
  public static final String KEY_FORCED_TRANSMISSION_DECISION
      = "Forced-Transmission-Decision";

  /**
   * Identifies the location a configured key/value pair resides,
   * either in the {@link Metadata}, or the map of {@code params}
   * supplied to the {@link #transform transform} method of a
   * {@code MetadataTransform}.
   * <p>
   * Suppose a transform wishes to examine or modify the display URL, which is
   * stored in the {@code params}. The transform's configuration might include:
   * <pre><code>
   * key=Display-URL
   * keyset=params
   * </code></pre>
   * If unspecified, the default value is {@code METADATA}.
   *
   * @since 4.1.4
   */
  public enum Keyset {
    /**
     * The key/value(s) reside in the {@link Metadata}.
     * This value's name is {@code metadata}.
     */
    METADATA("metadata"),
    /**
     * The key/value pair resides in the {@code Map} of {@code params}.
     * This value's name is {@code params}.
     */
    PARAMS("params");

    private final String name;

    private Keyset(String name) {
      this.name = name;
    }

    /**
     * Returns a {@code Keyset} value with the given {@code name}, or the
     * default value of {@code METADATA} if name is {@code null}.
     * @param name the name of the Keyset value
     * @return a {@code Keyset} corresponding to {@code name}
     * @throws IllegalArgumentException if there is no Keyset value with
     *         {@code name}.
     */
    public static Keyset from(String name) {
      return (name == null) ? METADATA : Keyset.valueOf(name.toUpperCase(US));
    }

    /** Returns the name of this {@code Keyset} value. */
    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * Transforms can cancel sending a document, or cancel sending its contents by
   * adding a {@code Transmission-Decision} to the {@code Map} of {@code params}
   * supplied to the {@link #transform transform} method of the
   * {@code MetadataTransform}.
   * If no {@code Transmission-Decision} is set in the params, the default value
   * of {@code AS_IS} is used.
   */
  public enum TransmissionDecision {
    /**
     * The document's metadata and content are to remain intact.
     * This value's name is {@code as-is}.
     */
    AS_IS("as-is"),
    /**
     * The document's metadata and content are discarded.
     * This value's name is {@code do-not-index}.
     */
    DO_NOT_INDEX("do-not-index"),
    /**
     * The document's content is discarded, but its metadata remains intact.
     * This value's name is {@code do-not-index-content}.
     */
    DO_NOT_INDEX_CONTENT("do-not-index-content");

    private final String name;

    private TransmissionDecision(String n) {
      name = n;
    }

    /**
     * Returns a {@code TransmissionDecision} value with the given {@code name},
     * or the default value of {@code AS_IS} if name is {@code null}.
     * @param name the name of the TransmissionDecision value
     * @return a {@code TransmissionDecision} corresponding to {@code name}
     * @throws IllegalArgumentException if there is no TransmissionDecision
     *         value with {@code name}.
     */
    public static TransmissionDecision from(String name) {
      if (null == name) {
        return AS_IS;
      }
      return
          TransmissionDecision.valueOf(name.replace('-', '_').toUpperCase(US));
    }

    /** Returns the name of this {@code TransmissionDecision} value. */
    @Override
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
