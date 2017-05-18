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

package com.google.enterprise.adaptor.prebuilt;

import static java.util.Locale.US;

import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;
import com.google.enterprise.adaptor.MetadataTransform.TransmissionDecision;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Transform that makes a {@link TransmissionDecision}, based on a document's
 * Metadata properties. The document metadata key used to make the transmission
 * decision are defined by {@code "key"} configuration entry. If the config has
 * {@code pattern} that is a regular expression, then whether any values of the
 * {@code key} metadata property matches the regular expression is determined.
 * If {@code pattern} is not set, then whether any key named {@code key} is
 * present determines the match. The key {@code when} determines
 * whether the {@code decision} is made for matching documents (if that key is
 * set to {@code found}) or made for non-matching documents (if that key is set
 * to {@code not-found}). The config key {@code keyset} may be set to
 * {@code metadata} or to {@code params} to restrict the search to
 * {@code Metadata} or {@code params}, respectively. If {@code keyset} is not
 * specified, it defaults to {@code metadata}.
 * Most keys/values of interest will normally be specified in the document's
 * {@code Metadata}, but some key/values of interest (e.g. ContentType, DocId)
 * exist in the document's {@code params}.
 *
 * <p>Example: skip documents that have a {@code NoIndex} metadata key or params
 * key, regardless of value:
 * <pre><code>
 * metadata.transform.pipeline=regexFilter
 * metadata.transform.pipeline.regexFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.RegexFilter.create
 * metadata.transform.pipeline.regexFilter.key=NoIndex
 * metadata.transform.pipeline.regexFilter.decision=do-not-index
 * </code></pre>
 *
 * <p>Example: drop the content of documents that have a {@code ContentLength}
 * greater than or equal to 100 megabytes:
 * <pre><code>
 * metadata.transform.pipeline=regexFilter
 * metadata.transform.pipeline.regexFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.RegexFilter.create
 * metadata.transform.pipeline.regexFilter.key=ContentLength
 * metadata.transform.pipeline.regexFilter.keyset=params
 * metadata.transform.pipeline.regexFilter.pattern=0*[1-9][0-9]{8,}
 * metadata.transform.pipeline.regexFilter.decision=do-not-index-content
 * </code></pre>
 *
 * <p>Example: skips documents whose Metadata {@code Classification} property
 * is neither {@code PUBLIC} nor {@code DECLASSIFIED}:
 * <pre><code>
 * metadata.transform.pipeline=regexFilter
 * metadata.transform.pipeline.regexFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.RegexFilter.create
 * metadata.transform.pipeline.regexFilter.key=Classification
 * metadata.transform.pipeline.regexFilter.keyset=metadata
 * metadata.transform.pipeline.regexFilter.pattern=(PUBLIC)|(DECLASSIFIED)
 * metadata.transform.pipeline.regexFilter.when=not-found
 * metadata.transform.pipeline.regexFilter.decision=do-not-index
 * </code></pre>
 */
// @since TODO(bmj)
public class RegexFilter implements MetadataTransform {
  /**
   * Make decision based upon whether the regular expression matches or not.
   */
  private static enum When {
    /**
     * The decision is set if a match to the regular expression is found.
     * The name of this value is {@code found}.
     */
    FOUND("found"),
    /**
     * The decision is set if a match to the regular expression is not found.
     * The name of this value is {@code not-found}.
     */
    NOT_FOUND("not-found");

    private final String name;

    private When(String name) {
      this.name = name;
    }

    /**
     * Returns a {@code When} value with the given {@code name}, or the
     * default value of {@code FOUND} if name is {@code null}.
     * @param name the name of the When value
     * @throws IllegalArgumentException if there is no When value with
     *         {@code name}.
     */
    public static When from(String name) {
      if (null == name) {
        return FOUND;
      }
      return When.valueOf(name.replace('-', '_').toUpperCase(US));
    }

    /** Returns the name of this {@code When} value. */
    @Override
    public String toString() {
      return name;
    }
  };

  // TODO (bmj): remove this and use the one in MetadataTransforms.
  private enum Keyset {
    METADATA("metadata"),
    PARAMS("params");

    private final String name;

    private Keyset(String name) {
      this.name = name;
    }

    public static Keyset from(String val) {
      return (val == null) ? METADATA : Keyset.valueOf(val.toUpperCase());
    }

    @Override
    public String toString() {
      return name;
    }
  };

  private static final Logger log
      = Logger.getLogger(RegexFilter.class.getName());

  /** The name of the key (either Metadata key or params key) to match. */
  private final String key;

  /**
   * If {@code METADATA}, search the metadata for the specified key;
   * if {@code PARAMS}, search the params for the specified key;
   */
  private final Keyset keyset;

  /**
   * The regex pattern to match in the property value (can be null to indicate
   * that any value is considered a match).
   */
  private final Pattern pattern;

  /**
   * If {@code found}, make a transmission decision on a match;
   * if {@code not-found}, make a transmission decision on a failed match.
   */
  private final When when;

  /**
   * The {@code TransmissionDecision} to be made.
   */
  private final TransmissionDecision decision;

  private RegexFilter(String key, Keyset keyset, Pattern pattern,
      When when, TransmissionDecision decision) {
    this.key = key;
    this.keyset = keyset;
    this.pattern = pattern;
    this.when = when;
    this.decision = decision;
  }

  /**
   * Search (only) the {@code Metadata} for an instance of the key
   * containing a value that matches the {@code pattern}.  Returns {@code true}
   * if found, {@code false} if not.
   */
  private boolean foundInMetadata(Metadata metadata) {
    boolean found = false;
    for (String value : metadata.getAllValues(key)) {
      if (pattern.matcher(value).find()) {
        found = true;
        break;
      }
    }
    log.fine((found ? "Did" : "Did not") + " find matching pattern for key `"
        + key + "' in metadata.");
    return found;
  }

  /**
   * Search (only) the {@code params} for an instance of the key
   * containing a value that matches the {@code pattern}.  Returns {@code true}
   * if found, {@code false} if not.
   */
  private boolean foundInParams(Map<String, String> params) {
    boolean found = false;
    if (params.containsKey(key)) {
      String value = params.get(key);
      if (value == null) {
        value = "";
      }
      found = pattern.matcher(value).find();
    }
    log.fine((found ? "Did" : "Did not") + " find matching pattern for key `"
        + key + "' in params.");
    return found;
  }

  /**
   * Conditionally adds a {@code Transmission-Decision} entry to the
   * {@code params Map}. The decision is based on settings of the
   * {@code key}, {@code pattern}, {@code decideOnMatch}, {@code decision},
   * and {@code keyset} configuration variables (as discussed above).
   */
  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    boolean found;
    switch (keyset) {
      case METADATA:
        found = foundInMetadata(metadata);
        break;
      case PARAMS:
        found = foundInParams(params);
        break;
      default:
        throw new AssertionError("Invalid keyset: " + keyset);
    }

    String docId = params.get(MetadataTransform.KEY_DOC_ID);
    if (null == docId || docId.isEmpty()) {
      docId = "with no docId";
    }
    // Determine the TransmissionDecision.
    if (when == When.FOUND) {
      if (found) {
        log.log(Level.INFO, "Transmission decision of {0} for document {1}, "
            + "because we found a match in {2}",
            new Object[] { decision, docId, keyset });
        params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
            decision.toString());
      } else {
        log.log(Level.FINE, "No transmission decision for document {0}, "
            + "because we did not find a match in {1}",
            new Object[] { docId, keyset });
      }
    } else {
      if (found) {
        log.log(Level.FINE, "No transmission decision for document {0}, "
            + "because we found a match in {1}",
            new Object[] { docId, keyset });
      } else {
        log.log(Level.INFO, "Transmission decision of {0} for document {1}, "
            + "because we did not find a match in {2}",
            new Object[] { decision, docId, keyset });
        params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
            decision.toString());
      }
    }
  }

  @Override
  public String toString() {
    return new StringBuilder("RegexFilter(")
        .append(key).append(", ")
        .append(keyset).append(", ")
        .append(pattern).append(", ")
        .append(when).append(", ")
        .append(decision).append(")")
        .toString();
  }

  public static RegexFilter create(Map<String, String> cfg) {
    String key;
    Keyset keyset;
    Pattern pattern;
    When when;
    TransmissionDecision decision;

    key = getTrimmedValue(cfg, "key");
    if (key == null) {
      throw new NullPointerException("key may not be null or empty");
    }
    log.config("key = " + key);

    keyset = Keyset.from(getTrimmedValue(cfg, "keyset"));
    log.config("keyset = " + keyset);

    String patternString = cfg.get("pattern");
    if (patternString == null || patternString.isEmpty()) {
      log.config("pattern left null");
      pattern = Pattern.compile("\\A"); // matches any value
    } else {
      pattern = Pattern.compile(patternString);
      log.config("pattern set to " + patternString);
    }

    when = When.from(getTrimmedValue(cfg, "when"));
    log.config("when = " + when);

    decision = TransmissionDecision.from(getTrimmedValue(cfg, "decision"));
    log.config("decision = " + decision);

    return new RegexFilter(key, keyset, pattern, when, decision);
  }

  private static String getTrimmedValue(Map<String, String> cfg, String key) {
    String value = cfg.get(key);
    if (value != null) {
      value = value.trim();
      if (value.length() > 0) {
        return value;
      }
    }
    return null;
  }
}
