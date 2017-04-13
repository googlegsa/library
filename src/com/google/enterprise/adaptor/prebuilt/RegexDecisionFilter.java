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

import static com.google.enterprise.adaptor.MetadataTransform.TransmissionDecision;

import com.google.common.base.Strings;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

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
 * present determines the match. The key {@code decideOnMatch} determines
 * whether the {@code decision} is made for matching documents (if that key is
 * set to {@code true}) or made for non-matching documents (if that key is set
 * to {@code false}). By default, both Document {@code Metadata} and
 * {@code params} are searched for the matching {@code key}; the config key
 * {@code corpora} may be set to {@code metadata} or to {@code params} to
 * restrict the search to only {@code Metadata} or {@code params}, respectively.
 * Most keys/values of interest will normally be specified in the document's
 * {@code Metadata}, but some key/values of interest (e.g. ContentType, DocId)
 * exist in the document's {@code params}.
 *
 * <p>Example: skip documents that have a {@code NoIndex} metadata key or params
 * key, regardless of value:
 * <pre><code>
   metadata.transform.pipeline=regexDecisionFilter
   metadata.transform.pipeline.regexDecisionFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.SkipDocumentFilter.create
   metadata.transform.pipeline.regexDecisionFilter.key=NoIndex
   metadata.transform.pipeline.regexDecisionFilter.decision=do-not-index
   </code></pre>
 *
 * <p>Example: drop the content of documents that have a {@code ContentLength}
 * greater than or equal to 100 megabytes:
 * <pre><code>
   metadata.transform.pipeline=regexDecisionFilter
   metadata.transform.pipeline.regexDecisionFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.RegexDecisionFilter.create
   metadata.transform.pipeline.regexDecisionFilter.key=ContentLength
   metadata.transform.pipeline.regexDecisionFilter.pattern=0*[1-9][0-9]{8,}
   metadata.transform.pipeline.regexDecisionFilter.decision=do-not-index-content
   </code></pre>
 *
 * <p>Example: skips documents whose Metadata {@code Classification} property
 * is neither {@code PUBLIC} nor {@code DECLASSIFIED}:
 * <pre><code>
   metadata.transform.pipeline=regexDecisionFilter
   metadata.transform.pipeline.regexDecisionFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.RegexDecisionFilter.create
   metadata.transform.pipeline.regexDecisionFilter.key=Classification
   metadata.transform.pipeline.regexDecisionFilter.pattern=(PUBLIC)|(DECLASSIFIED)
   metadata.transform.pipeline.regexDecisionFilter.decideOnMatch=false
   metadata.transform.pipeline.regexDecisionFilter.decision=do-not-index
   metadata.transform.pipeline.regexDecisionFilter.corpora=metadata
   </code></pre>
 */
public class RegexDecisionFilter implements MetadataTransform {
  /**
   * Which collections of keys/values to search.  Metadata, params, or both.
   */
  private enum Corpora {
    METADATA("metadata"),
    PARAMS("params"),
    METADATA_OR_PARAMS("metadata or params");

    private final String name;

    private Corpora(String n) {
      name = n;
    }

    public static Corpora from(String val) {
      if ("metadata".equalsIgnoreCase(val)) {
        return Corpora.METADATA;
      }
      if ("params".equalsIgnoreCase(val)) {
        return Corpora.PARAMS;
      }
      return METADATA_OR_PARAMS;
    }

    public String toString() {
      return name;
    }
  };

  private static final Logger log
      = Logger.getLogger(RegexDecisionFilter.class.getName());

  /** The name of the key (either Metadata key or params key) to match. */
  private String key;

  /**
   * The regex pattern to match in the property value (can be null to indicate
   * that any value is considered a match).
   */
  private Pattern pattern;

  /**
   * If {@code true}, make a transmission decision on a match;
   * if {@code false}, make a transmission decision on a failed match.
   */
  private boolean decideOnMatch = true;

  /**
   * The {@code TransmissionDecision} to be made.
   */
  private TransmissionDecision decision;

  /**
   * If {@code METADATA}, only search the metadata for the specified key;
   * if {@code PARAMS}, only search the params for the specified key;
   * if {@code METADATA_OR_PARAMS}, search both.
   */
  private Corpora corpora = Corpora.METADATA_OR_PARAMS;

  private RegexDecisionFilter(String key, Pattern pattern,
      boolean decideOnMatch, TransmissionDecision decision, Corpora corpora) {
    this.key = key;
    this.pattern = pattern;
    this.decideOnMatch = decideOnMatch;
    this.decision = decision;
    this.corpora = corpora;
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
      found = pattern.matcher(params.get(key)).find();
    }
    log.fine((found ? "Did" : "Did not") + " find matching pattern for key `"
        + key + "' in params.");
    return found;
  }

  /**
   * Conditionally adds a {@code Transmission-Decision} entry to the
   * {@code params Map}. The decision is based on settings of the
   * {@code key}, {@code pattern}, {@code decideOnMatch}, {@code decision},
   * and {@code corpora} configuration variables (as discussed above).
   */
  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    boolean found;
    switch (corpora) {
      case METADATA:
        found = foundInMetadata(metadata);
        break;
      case PARAMS:
        found = foundInParams(params);
        break;
      case METADATA_OR_PARAMS:
      default:
        found = foundInMetadata(metadata) || foundInParams(params);
    }

    String docId = params.get(MetadataTransform.KEY_DOC_ID);
    if (Strings.isNullOrEmpty(docId)) {
      docId = "with no docId";
    }
    // Determine the TransmissionDecision.
    if (decideOnMatch) {
      if (found) {
        log.log(Level.INFO, "Transmission decision of {0} for document {1}, "
            + "because we found a match in {2}",
            new Object[] { decision, docId, corpora });
        params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
            decision.toString());
      } else {
        log.log(Level.FINE, "No transmission decision for document {0}, "
            + "because we did not find a match in {1}",
            new Object[] { docId, corpora });
      }
    } else {
      if (found) {
        log.log(Level.FINE, "No transmission decision for document {0}, "
            + "because we found a match in {1}",
            new Object[] { docId, corpora });
      } else {
        log.log(Level.INFO, "Transmission decision of {0} for document {1}, "
            + "because we did not find a match in {2}",
            new Object[] { decision, docId, corpora });
        params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
            decision.toString());
      }
    }
  }

  @Override
  public String toString() {
    return new StringBuilder("RegexDecisionFilter(")
        .append(key).append(", ")
        .append(pattern == null ? "[null]" : pattern.toString()).append(", ")
        .append(decideOnMatch).append(", ")
        .append(decision).append(", ")
        .append(corpora).append(")")
        .toString();
  }

  public static RegexDecisionFilter create(Map<String, String> cfg) {
    String key;
    Pattern pattern = null;
    boolean decideOnMatch = true;
    TransmissionDecision decision;
    Corpora corpora;

    key = getTrimmedValue(cfg, "key");
    if (key == null) {
      throw new NullPointerException("key may not be null or empty");
    }
    log.config("key = " + key);

    String patternString = cfg.get("pattern");
    if (Strings.isNullOrEmpty(patternString)) {
      log.config("pattern left null");
      pattern = Pattern.compile("\\A"); // matches any value
    } else {
      pattern = Pattern.compile(patternString);
      log.config("pattern set to " + patternString);
    }

    String decideOnMatchString = getTrimmedValue(cfg, "decideOnMatch");
    if (decideOnMatchString != null) {
      decideOnMatch = Boolean.parseBoolean(decideOnMatchString);
    }
    log.config("decideOnMatch set to " + decideOnMatch);

    decision = TransmissionDecision.from(getTrimmedValue(cfg, "decision"));
    log.config("decision = " + decision);

    corpora = Corpora.from(getTrimmedValue(cfg, "corpora"));
    log.config("corpora set to " + corpora);

    return new RegexDecisionFilter(key, pattern, decideOnMatch, decision,
        corpora);
  }

  private static String getTrimmedValue(Map<String, String> cfg, String key) {
    String value = cfg.get(key);
    return (value == null) ? value : Strings.emptyToNull(value.trim());
  }
}
