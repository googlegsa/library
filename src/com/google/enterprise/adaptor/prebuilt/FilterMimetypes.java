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

import static java.util.Arrays.asList;

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
/** Transform causing exclusion of certain mime-types. */
public class FilterMimetypes implements MetadataTransform {
  private static final Logger log
      = Logger.getLogger(FilterMimetypes.class.getName());

  private final Set<String> supported;
  private final Set<String> unsupported;
  private final Set<String> excluded;

  private FilterMimetypes(Set<String> s, Set<String> u, Set<String> e) {
    if (null == s || null == u || null == e) {
      throw new NullPointerException();
    }
    supported = s;
    unsupported = u;
    excluded = e;
  }

  public Set<String> getSupportedMimetypes() {
    return Collections.unmodifiableSet(supported);
  }

  public Set<String> getUnsupportedMimetypes() {
    return Collections.unmodifiableSet(unsupported);
  }

  public Set<String> getExcludedMimetypes() {
    return Collections.unmodifiableSet(excluded);
  }

  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    String ct = params.get("Content-Type");
    if (ct == null) {
      return;
    }
    // parse Content-Type per RFC 2616's Section 3.7 Media Types
    int semicolonIndex = ct.indexOf(";");
    if (-1 != semicolonIndex) {
      ct = ct.substring(0, semicolonIndex);
    }
    ct = ct.trim().toLowerCase();
    if (matches(supported, ct, "supported")) {
      params.put("Transmission-Decision", "as-is");
    } else if (matches(unsupported, ct, "unsupported")) {
      params.put("Transmission-Decision", "do-not-index-content");
    } else if (matches(excluded, ct, "excluded")) {
      params.put("Transmission-Decision", "do-not-index");
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

  private static boolean matches(Set<String> globs, String ct, String label) {
    for (String glob : globs) {
      boolean matched = false;
      if (-1 != glob.indexOf('*')) {
        matched = wildcardmatch(glob, ct);
      } else if (glob.equals(ct)) {
        matched = true;
      }
      if (matched) {
        log.log(Level.FINE, "{0} matches {1} and is {2}",
            new Object[] {ct, glob, label});
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FilterMimetypes(");
    sb.append("" + supported);
    sb.append(", ");
    sb.append("" + unsupported);
    sb.append(", ");
    sb.append("" + excluded);
    sb.append(")");
    return "" + sb;
  }

  public static FilterMimetypes create(Map<String, String> cfg) {
    final Set<String> supported;
    final Set<String> unsupported;
    final Set<String> excluded;

    {
      String value = cfg.get("supportedMimetypeGlobs"); 
      if (null == value) {
        supported = new TreeSet<String>(SUPPORTED);
      } else if (value.isEmpty()) {
        supported = new TreeSet<String>();
      } else {
        supported = new TreeSet<String>(asList(value.split("\\s+", 0)));
      }
    }
    {
      String value = cfg.get("unsupportedMimetypeGlobs"); 
      if (null == value) {
        unsupported = new TreeSet<String>(UNSUPPORTED);
      } else if (value.isEmpty()) {
        unsupported = new TreeSet<String>();
      } else {
        unsupported = new TreeSet<String>(asList(value.split("\\s+", 0)));
      }
    }
    {
      String value = cfg.get("excludedMimetypeGlobs"); 
      if (null == value) {
        excluded = new TreeSet<String>(EXCLUDED);
      } else if (value.isEmpty()) {
        excluded = new TreeSet<String>();
      } else {
        excluded = new TreeSet<String>(asList(value.split("\\s+", 0)));
      }
    }

    if (cfg.containsKey("supportedMimetypeGlobsAddon")) {
      for (String re : cfg.get("supportedMimetypeGlobsAddon")
          .split("\\s+", 0)) {
        if (!re.isEmpty()) {
          supported.add(re);
        }
      }
    }
    if (cfg.containsKey("unsupportedMimetypeGlobsAddon")) {
      for (String re : cfg.get("unsupportedMimetypeGlobsAddon")
          .split("\\s+", 0)) {
        if (!re.isEmpty()) {
          unsupported.add(re);
        }
      }
    }
    if (cfg.containsKey("excludedMimetypeGlobsAddon")) {
      for (String re : cfg.get("excludedMimetypeGlobsAddon")
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

  private static final Set<String> SUPPORTED = new TreeSet<String>() {{
      /*
        Sets the preferred/supported mime types to index.
        We send content and metadata for these types.
        These mime types may require some preprocessing or
        file format conversion. Some information may be lost
        or discarded.
      */ 
      add("text/calendar");
      add("text/csv");
      add("text/plain");
      add("text/html");
      add("text/sgml");
      add("text/x-sgml");
      add("text/tab-separated-values");
      add("text/xhtml");
      add("text/xml");
      add("text/*");
      add("application/plain");
      add("application/rdf+xml");
      add("application/xhtml+xml");
      add("application/xml");
      add("message/http");
      add("message/s-http");
      add("message/news");
      add("text/richtext");
      add("text/rtf");
      add("application/rtf");
      add("application/x-rtf");
      add("text/troff");
      add("application/x-troff");
      add("application/pdf");
      add("application/postscript");
      add("application/vnd.framemaker");
      add("application/vnd.mif");
      add("application/vnd.kde.kpresenter");
      add("application/vnd.kde.kspread");
      add("application/vnd.kde.kword");
      add("application/vnd.lotus-1-2-3");
      add("application/vnd.lotus-freelance");
      add("application/x-freelance");
      add("application/vnd.lotus-notes");
      add("application/vnd.lotus-wordpro");
      add("application/excel");
      add("application/vnd.ms-excel");
      add("application/x-excel");
      add("application/x-msexcel");
      add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      add("application/vnd.ms-excel.*");
      add("application/vnd.ms-htmlhelp");
      add("application/vnd.ms-outlook");
      add("application/mspowerpoint");
      add("application/powerpoint");
      add("application/vnd.ms-powerpoint");
      add("application/vnd.openxmlformats-officedocument.presentationml.presentation");
      add("application/vnd.ms-powerpoint.macroEnabled.*");
      add("application/vnd.ms-powerpoint.presentation.macroEnabled.*");
      add("application/vnd.ms-powerpoint.presentation.*");
      add("application/vnd.ms-powerpoint.*");
      add("application/vnd.ms-project");
      add("application/x-project");
      add("application/x-mspublisher");
      add("application/x-msschedule");
      add("application/msword");
      add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
      add("application/vnd.ms-word.document.macroEnabled.*");
      add("application/vnd.ms-word.document.*");
      add("application/vnd.ms-word.macroEnabled.*");
      add("application/vnd.ms-word.*");
      add("application/vnd.ms-works");
      add("application/mswrite");
      add("application/x-mswrite");
      add("application/vnd.ms-xpsdocument");
      add("application/vnd.oasis.opendocument.presentation*");
      add("application/vnd.oasis.opendocument.spreadsheet*");
      add("application/vnd.oasis.opendocument.text*");
      add("application/vnd.quark.quarkxpress");
      add("application/vnd.scibus");
      add("application/vnd.wordperfect");
      add("application/wordperfect");
      add("application/wordperfect*");
      add("application/vnd.visio");
      add("application/x-visio");
      add("application/x-latex");
      add("application/x-tex");
      add("application/x-texinfo");
      add("application/x-pagemaker");
      add("mulitpart/appledouble");
      add("mulitpart/mixed");
  }};
  
  private static final Set<String> UNSUPPORTED = new TreeSet<String>() {{
      /*
         Sets the unsupported mime types whose content should not be indexed.
         These mime types provide little or no textual content, or are data
         formats that are either unknown or do not have a format converter.
         The connector may still provide meta-data describing the content,
         but the content itself should not be pushed.
      */
      add("audio/*");
      add("image/*");
      add("music/*");
      add("x-music/*");
      add("video/*");
      add("application/octet-stream");
      add("application/macbinary");
      add("application/x-binary");
      add("application/binhex");
      add("application/binhex4");
      add("application/gnutar");
      add("application/mac-binhex");
      add("application/mac-binhex40");
      add("application/sea");
      add("application/x-binhex");
      add("application/x-binhex40");
      add("application/x-bzip");
      add("application/x-bzip2");
      add("application/x-compressed");
      add("application/x-gtar");
      add("application/x-gzip");
      add("application/x-lzh");
      add("application/x-sea");
      add("application/x-sit");
      add("application/x-stuffit");
      add("application/x-tar");
      add("application/x-zip");
      add("application/x-zip-compressed");
      add("application/zip");
      add("multipart/x-zip");
      add("chemical/*");
      add("message/*");
      add("model/*");
      add("mulitpart/*");
      add("world/*");
      add("i-world/*");
      add("x-world/*");
      // add("application/");
  }};

  private static final Set<String> EXCLUDED = new TreeSet<String>() {{
      /*
         Sets the mime types whose document should not be indexed.
         The connector should skip the document, providing neither meta-data,
         nor the content.
      */
      add("application/annodex");
      add("application/internet-property-stream");
      add("application/mime");
      add("application/pgp-signature");
      add("application/solids");
      add("application/vnd.acucorp");
      add("application/vnd.koan");
      add("application/vnd.ibm.modcap");
      add("application/x-aim");
      add("application/x-koan");
      add("application/x-msaccess");
      add("application/x-msdownload");
      add("application/x-world");
      add("message/rfc822");
      add("text/asp");
      add("text/vnd.abc");
      add("text/x-audiosoft-intra");
  }};
}
