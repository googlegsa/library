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

/**
 * Most of the code here is a modification to the code of SkipDocumentFilter
 * in package com.google.enterprise.connector.util.filter, from the v3 Connector
 * code (Hence the "2011" in the Copyright line).  It has now been ported to
 * the v4 Adaptor Library.  Constructors of a {@code SkipDocumentFilter}
 * instance should take notice that {@code propertyName}, {@code pattern}, and
 * {@code skipOnMatch} are now passed in as keys to a {@code Map}, not as
 * {@code Bean} properties.
 */
package com.google.enterprise.adaptor.prebuilt;

import static com.google.enterprise.adaptor.MetadataTransform.TransmissionDecision;

import com.google.common.base.Strings;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Transform causing exclusion of certain Documents, based on that document's
 * Metadata properties.  The key to determine whether or not to skip the
 * document is passed in to the configuration under the configuration key
 * {@code propertyName} (the name comes from Connectors v3, where Documents had
 * {@code Properties}).  If the config has {@code pattern} that is a regular
 * expression, then whether any value of the {@code propertyName} property
 * matches the regular expression is determined.  If {@code pattern} is not set,
 * then whether any key named {@code propertyName} is present determines the
 * match.  The key {@code skipOnMatch} determines whether to skip the matching
 * documents (if that key is set to {@code true}) or to skip all <i>but</i> the
 * matching documents (if that key is set to {@code false}).  By default, both
 * Document {@code Metadata} and {@code params} are searched for the matching
 * {@code propertyName}; the config key {@code corpora} may be set to
 * {@code metadata} or to {@code params} to restrict the search to only
 * {@code Metadata} or {@code params}, respectively.  Most keys/values of
 * interest will normally be specified in the document's {@code Metadata}, but
 * some key/values of interest (e.g. ContentType, DocId) exist in the document's
 * {@code params}.
 *
 * <p>Example: skip documents that have a {@code NoIndex} metadata key or params
 * key, regardless of value:
 * <pre><code>
   metadata.transform.pipeline=skipDocumentFilter
   metadata.transform.pipeline.skipDocumentFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.SkipDocumentFilter.create
   metadata.transform.pipeline.skipDocumentFilter.propertyName=NoIndex
   </code></pre>
 *
 * <p>Example 2: skips documents whose Metadata {@code Classification} property
 * is neither {@code PUBLIC} nor {@code DECLASSIFIED}:
 * <pre><code>
   metadata.transform.pipeline=skipDocumentFilter
   metadata.transform.pipeline.skipDocumentFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.SkipDocumentFilter.create
   metadata.transform.pipeline.skipDocumentFilter.propertyName=Classification
   metadata.transform.pipeline.skipDocumentFilter.pattern=(PUBLIC)|(DECLASSIFIED)
   metadata.transform.pipeline.skipDocumentFilter.skipOnMatch=false
   metadata.transform.pipeline.skipDocumentFilter.corpora=metadata
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
   * If {@code true}, skip the document on a match;
   * if {@code false}, skip the document on a failed match.
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
      boolean decideOnMatch, Corpora corpora) {
    this.key = key;
    this.pattern = pattern;
    this.decideOnMatch = decideOnMatch;
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
   * Conditionally adds a single {@code Map.Entry} to the {@code params Map}:
   * key {@code Transmission-Decision}, value
   * {@code TransmissionDecision.DO_NOT_INDEX} to indicate that the document is
   * to be skipped.  The decision is based on settings of the
   * {@code key}, {@code pattern}, {@code decideOnMatch}, and
   * {@code corpora} configuration variables (as discussed above).
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
    if (null == docId || docId.isEmpty()) {
      docId = "with no docId";
    }
    // determine the Transmission Decision based on skipMatch
    if (decideOnMatch) {
      if (found) {
        log.info("Skipping document " + docId + ", because we found a match in "
            + corpora);
        params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
            decision.toString());
      } else {
        log.fine("Not skipping document " + docId + ", because we did not find "
            + "a match in " + corpora);
      }
    } else {
      if (found) {
        log.fine("Not skipping document " + docId + ", because we found a match"
            + " in " + corpora);
      } else {
        log.info("Skipping document " + docId + ", because we did not find a "
            + "match in " + corpora);
        params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
            decision.toString());
      }
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("RegexDecisionFilter(");
    sb.append(key);
    sb.append(", ");
    sb.append(pattern == null ? "[null]" : pattern.toString());
    sb.append(", ");
    sb.append(decideOnMatch);
    sb.append(", ");
    sb.append(decision);
    sb.append(", ");
    sb.append(corpora);
    sb.append(")");
    return "" + sb;
  }

  public static RegexDecisionFilter create(Map<String, String> cfg) {
    String key;
    Pattern pattern = null;
    boolean decideOnMatch = true;
    TransmissionDecision decision;
    Corpora corpora;

    key = cfg.get("key");
    if (Strings.isNullOrEmpty(key)) {
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

    String decideOnMatchString = cfg.get("decideOnMatch");
    if (decideOnMatchString != null) {
      decideOnMatchString = decideOnMatchString.trim();
    }
    if (!Strings.isNullOrEmpty(decideOnMatchString)) {
      decideOnMatch = Boolean.parseBoolean(decideOnMatchString);
    }
    log.config("decideOnMatch set to " + decideOnMatch);

    decision = TransmissionDecision.from(cfg.get("decision"));
    log.config("decision = " + decision);

    corpora = Corpora.from(cfg.get("corpora"));
    log.config("corpora set to " + corpora);

    return new RegexDecisionFilter(key, pattern, decideOnMatch, corpora);
  }
}
