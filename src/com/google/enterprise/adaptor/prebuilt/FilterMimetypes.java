// Copyright 2015 Google Inc. All Rights Reserved.
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
import static java.util.Arrays.asList;

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transform causing exclusion of certain mime-types. 
 *
 * <p> The order of checking for known mimetypes is:
 * <ol>
 *   <li> Check explicitly supported types. If type is 
 *       explicitly supported then content, metadata, 
 *       headers, everything is sent.
 *   <li> Check if explicitly unsupported. If type is 
 *       explicitly unsupported we will send headers 
 *       and metadata but will not send content.
 *   <li> Check if type is explicitly excluded. If type 
 *       is explicitly excluded we do not send any info
 *       about the doc. Instead we say to drop the entire
 *       document contents, headers, et cetera and return
 *       a 404 not found code.
 *   <li> Check if supported by matching a supported pattern
 *       that has a wildcard (*).
 *   <li> Check if unsupported by matching an usupported
 *       pattern that has a wildcard (*).
 *   <li> Check if excluded by matching an excluded pattern
 *       that has a wildcard (*).
 * </ol>
 */
public class FilterMimetypes implements MetadataTransform {
  private static final Logger log
      = Logger.getLogger(FilterMimetypes.class.getName());

  private Set<String> supportedExplicit = new TreeSet<String>();
  private Set<String> unsupportedExplicit = new TreeSet<String>();
  private Set<String> excludedExplicit = new TreeSet<String>();
  private Set<String> supportedGlobs = new TreeSet<String>();
  private Set<String> unsupportedGlobs = new TreeSet<String>();
  private Set<String> excludedGlobs = new TreeSet<String>();
  private Map<String, String> decided;

  private synchronized String lookupDecision(String ct) {
    return decided.get(ct);
  }

  private synchronized void insertDecision(String ct, String d) {
    decided.put(ct, d);
  }

  private FilterMimetypes(Set<String> s, Set<String> u, Set<String> e) {
    if (null == s || null == u || null == e) {
      throw new NullPointerException();
    }
    split(supportedGlobs, supportedExplicit, s);
    split(unsupportedGlobs, unsupportedExplicit, u);
    split(excludedGlobs, excludedExplicit, e);
    supportedExplicit = Collections.unmodifiableSet(supportedExplicit);
    unsupportedExplicit = Collections.unmodifiableSet(unsupportedExplicit);
    excludedExplicit = Collections.unmodifiableSet(excludedExplicit);
    supportedGlobs = Collections.unmodifiableSet(supportedGlobs);
    unsupportedGlobs = Collections.unmodifiableSet(unsupportedGlobs);
    excludedGlobs = Collections.unmodifiableSet(excludedGlobs);
    decided = new HashMap<String, String>();
  }

  private void split(Set<String> globs, Set<String> explicit, Set<String> src) {
    for (String pat : src) {
      if (-1 == pat.indexOf('*')) {
        explicit.add(pat);  
      } else {
        globs.add(pat);  
      }
    }
  }

  public Set<String> getSupportedMimetypes() {
    return addTogether(supportedExplicit, supportedGlobs);
  }

  public Set<String> getUnsupportedMimetypes() {
    return addTogether(unsupportedExplicit, unsupportedGlobs);
  }

  public Set<String> getExcludedMimetypes() {
    return addTogether(excludedExplicit, excludedGlobs);
  }

  private static Set<String> addTogether(Set<String> a, Set<String> b) {
    Set<String> union = new TreeSet<String>(a);
    union.addAll(b);
    return union; 
  }

  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    String ct = params.get(MetadataTransform.KEY_CONTENT_TYPE);
    if (ct == null) {
      return;
    }
    // parse Content-Type per RFC 2616's Section 3.7 Media Types
    int semicolonIndex = ct.indexOf(";");
    if (-1 != semicolonIndex) {
      ct = ct.substring(0, semicolonIndex);
    }
    ct = ct.trim().toLowerCase();
    String decision = lookupDecision(ct);
    if (null != decision) {
      params.put("Transmission-Decision", decision);
      return;
    }
    if (supportedExplicit.contains(ct)) {
      log.log(Level.FINE, ct + "is explicitly supported");
      insertDecision(ct, TransmissionDecision.AS_IS.toString());
      params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
          TransmissionDecision.AS_IS.toString());
    } else if (unsupportedExplicit.contains(ct)) {
      log.log(Level.FINE, ct + "is explicitly unsupported");
      insertDecision(ct, TransmissionDecision.DO_NOT_INDEX_CONTENT.toString());
      params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
          TransmissionDecision.DO_NOT_INDEX_CONTENT.toString());
    } else if (excludedExplicit.contains(ct)) {
      log.log(Level.FINE, ct + "is explicitly excluded");
      insertDecision(ct, TransmissionDecision.DO_NOT_INDEX.toString());
      params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
          TransmissionDecision.DO_NOT_INDEX.toString());
    } else if (matches(supportedGlobs, ct, "supported by glob")) {
      insertDecision(ct, TransmissionDecision.AS_IS.toString());
      params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
          TransmissionDecision.AS_IS.toString());
    } else if (matches(unsupportedGlobs, ct, "unsupported by glob")) {
      insertDecision(ct, TransmissionDecision.DO_NOT_INDEX_CONTENT.toString());
      params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
          TransmissionDecision.DO_NOT_INDEX_CONTENT.toString());
    } else if (matches(excludedGlobs, ct, "excluded by glob")) {
      insertDecision(ct, TransmissionDecision.DO_NOT_INDEX.toString());
      params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
          TransmissionDecision.DO_NOT_INDEX.toString());
    } else {
      log.info("unknown mime-type: " + ct);
    }
  }

  /**
   *  The glob has wildcards. No other characters are special;
   *  not periods and not question marks. Only widlcards are
   *  special.
   */
  @VisibleForTesting
  static boolean wildcardmatch(String glob, String str) {
    String parts[] = glob.split("\\*", -1);
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      regex.append(java.util.regex.Pattern.quote(parts[i]));
      if ((i + 1) != parts.length) {
        regex.append(".*");
      }
    }
    return str.matches(regex.toString());
  }

  private static boolean matches(Set<String> pats, String ct, String label) {
    for (String pat : pats) {
      if (wildcardmatch(pat, ct)) {
        log.log(Level.FINE, "{0} matches {1} and is {2}",
            new Object[] {ct, pat, label});
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FilterMimetypes(");
    sb.append("" + getSupportedMimetypes());
    sb.append(", ");
    sb.append("" + getUnsupportedMimetypes());
    sb.append(", ");
    sb.append("" + getExcludedMimetypes());
    sb.append(")");
    return "" + sb;
  }

  public static FilterMimetypes create(Map<String, String> cfg) {
    final Set<String> supported;
    final Set<String> unsupported;
    final Set<String> excluded;

    {
      String value = cfg.get("supportedMimetypes"); 
      if (null == value) {
        value = SUPPORTED;
      }
      supported = new TreeSet<String>(asList(value.split("\\s+", 0)));
      supported.remove("");
    }
    {
      String value = cfg.get("unsupportedMimetypes"); 
      if (null == value) {
        value = UNSUPPORTED;
      }
      unsupported = new TreeSet<String>(asList(value.split("\\s+", 0)));
      unsupported.remove("");
    }
    {
      String value = cfg.get("excludedMimetypes"); 
      if (null == value) {
        value = EXCLUDED;
      } 
      excluded = new TreeSet<String>(asList(value.split("\\s+", 0)));
      excluded.remove("");
    }

    if (cfg.containsKey("supportedMimetypesAddon")) {
      for (String re : cfg.get("supportedMimetypesAddon")
          .split("\\s+", 0)) {
        if (!re.isEmpty()) {
          supported.add(re);
        }
      }
    }
    if (cfg.containsKey("unsupportedMimetypesAddon")) {
      for (String re : cfg.get("unsupportedMimetypesAddon")
          .split("\\s+", 0)) {
        if (!re.isEmpty()) {
          unsupported.add(re);
        }
      }
    }
    if (cfg.containsKey("excludedMimetypesAddon")) {
      for (String re : cfg.get("excludedMimetypesAddon")
          .split("\\s+", 0)) {
        if (!re.isEmpty()) {
          excluded.add(re);
        }
      }
    }
    log.log(Level.INFO, "Setting supported mime types to: {0}", supported);
    log.log(Level.INFO, "Setting unsupported mime types to: {0}", unsupported);
    log.log(Level.INFO, "Setting excluded mime types to: {0}", excluded);
    return new FilterMimetypes(supported, unsupported, excluded);
  }

  private static final String SUPPORTED
      /*
        Sets the preferred/supported mime types to index.
        We send content and metadata for these types.
        These mime types may require some preprocessing or
        file format conversion. Some information may be lost
        or discarded.
      */ 
      = " application/excel"
      + " application/mspowerpoint"
      + " application/msword"
      + " application/mswrite"
      + " application/pdf"
      + " application/plain"
      + " application/postscript"
      + " application/powerpoint"
      + " application/rdf+xml"
      + " application/rtf"
      + " application/vnd.framemaker"
      + " application/vnd.kde.kpresenter"
      + " application/vnd.kde.kspread"
      + " application/vnd.kde.kword"
      + " application/vnd.lotus-1-2-3"
      + " application/vnd.lotus-freelance"
      + " application/vnd.lotus-notes"
      + " application/vnd.lotus-wordpro"
      + " application/vnd.mif"
      + " application/vnd.ms-excel"
      + " application/vnd.ms-excel.*"
      + " application/vnd.ms-htmlhelp"
      + " application/vnd.ms-outlook"
      + " application/vnd.ms-powerpoint"
      + " application/vnd.ms-powerpoint.*"
      + " application/vnd.ms-powerpoint.macroEnabled.*"
      + " application/vnd.ms-powerpoint.presentation.*"
      + " application/vnd.ms-powerpoint.presentation.macroEnabled.*"
      + " application/vnd.ms-project"
      + " application/vnd.ms-word.*"
      + " application/vnd.ms-word.document.*"
      + " application/vnd.ms-word.document.macroEnabled.*"
      + " application/vnd.ms-word.macroEnabled.*"
      + " application/vnd.ms-works"
      + " application/vnd.ms-xpsdocument"
      + " application/vnd.oasis.opendocument.presentation*"
      + " application/vnd.oasis.opendocument.spreadsheet*"
      + " application/vnd.oasis.opendocument.text*"
      + " application/vnd.openxmlformats-officedocument.presentationml.presentation"
      + " application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      + " application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      + " application/vnd.quark.quarkxpress"
      + " application/vnd.scibus"
      + " application/vnd.visio"
      + " application/vnd.wordperfect"
      + " application/wordperfect"
      + " application/wordperfect*"
      + " application/x-bzip"
      + " application/x-bzip2"
      + " application/x-compressed"
      + " application/x-excel"
      + " application/x-freelance"
      + " application/x-gtar"
      + " application/x-gzip"
      + " application/xhtml+xml"
      + " application/x-latex"
      + " application/xml"
      + " application/x-msexcel"
      + " application/x-mspublisher"
      + " application/x-msschedule"
      + " application/x-mswrite"
      + " application/x-pagemaker"
      + " application/x-project"
      + " application/x-rtf"
      + " application/x-tar"
      + " application/x-tex"
      + " application/x-texinfo"
      + " application/x-troff"
      + " application/x-visio"
      + " application/x-zip"
      + " application/x-zip-compressed"
      + " application/zip"
      + " message/http"
      + " message/news"
      + " message/s-http"
      + " multipart/appledouble"
      + " multipart/mixed"
      + " multipart/x-zip"
      + " text/*"
      + " text/calendar"
      + " text/csv"
      + " text/html"
      + " text/plain"
      + " text/richtext"
      + " text/rtf"
      + " text/sgml"
      + " text/tab-separated-values"
      + " text/troff"
      + " text/xhtml"
      + " text/xml"
      + " text/x-sgml";

  private static final String UNSUPPORTED
      /*
         Sets the unsupported mime types whose content should not be indexed.
         These mime types provide little or no textual content, or are data
         formats that are either unknown or do not have a format converter.
         The connector may still provide meta-data describing the content,
         but the content itself should not be pushed.
      */
      = " application/binhex"
      + " application/binhex4"
      + " application/gnutar"
      + " application/macbinary"
      + " application/mac-binhex"
      + " application/mac-binhex40"
      + " application/octet-stream"
      + " application/sea"
      + " application/x-binary"
      + " application/x-binhex"
      + " application/x-binhex40"
      + " application/x-lzh"
      + " application/x-sea"
      + " application/x-sit"
      + " application/x-stuffit"
      + " audio/*"
      + " chemical/*"
      + " image/*"
      + " i-world/*"
      + " message/*"
      + " model/*"
      + " multipart/*"
      + " music/*"
      + " video/*"
      + " world/*"
      + " x-music/*"
      + " x-world/*";

  private static final String EXCLUDED
      /*
         Sets the mime types whose document should not be indexed.
         The connector should skip the document, providing neither meta-data,
         nor the content.
      */
      = " application/annodex"
      + " application/internet-property-stream"
      + " application/mime"
      + " application/pgp-signature"
      + " application/solids"
      + " application/vnd.acucorp"
      + " application/vnd.ibm.modcap"
      + " application/vnd.koan"
      + " application/x-aim"
      + " application/x-koan"
      + " application/x-msaccess"
      + " application/x-msdownload"
      + " application/x-world"
      + " message/rfc822"
      + " text/asp"
      + " text/vnd.abc"
      + " text/x-audiosoft-intra";
}
