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

package com.google.enterprise.adaptor.examples;

import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import java.util.HashMap;
import java.util.Map;

/** Example transform adding type metadata based on extension. */
public class AddContentTypeToMetadata implements MetadataTransform {
  private final String metaKey;
  private final Map<String, String> typeMap = new HashMap<String, String>() {{
    put("application/vnd.openxmlformats-officedocument.wordprocessingml"
        + ".document", "Word");
    put("application/msword", "Word");
    put("application/vnd.ms-word.document.macroEnabled.12", "Word");
    put("application/vnd.openxmlformats-officedocument.wordprocessingml"
        + ".template", "Word");
    put("application/vnd.ms-word.template.macroEnabled.12", "Word");
    put("application/vnd.ms-word.document.12", "Word");
    put("application/vnd.ms-word.12", "Word");
    put("application/vnd.ms-excel", "Excel");
    put("application/excel", "Excel");
    put("application/x-excel", "Excel");
    put("application/x-msexcel", "Excel");
    put("application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "Excel");
    put("application/vnd.ms-excel.template.macroEnabled.12", "Excel");
    put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Excel");
    put("application/vnd.ms-excel.sheet.macroEnabled.12", "Excel");
    put("application/vnd.ms-excel.sheet.binary.macroEnabled.12", "Excel");
    put("application/vnd.ms-excel.addin.macroEnabled.12", "Excel");
    put("application/vnd.ms-excel.sheet.12", "Excel");
    put("application/vnd.ms-excel.12", "Excel");
    put("application/vnd.ms-powerpoint", "PowerPoint");
    put("application/mspowerpoint", "PowerPoint");
    put("application/powerpoint", "PowerPoint");
    put("application/x-mspowerpoint", "PowerPoint");
    put("application/vnd.openxmlformats-officedocument.presentationml"
        + ".presentation", "PowerPoint");
    put("application/vnd.ms-powerpoint.presentation.macroEnabled.12",
        "PowerPoint");
    put("application/vnd.openxmlformats-officedocument.presentationml"
        + ".template", "PowerPoint");
    put("application/vnd.ms-powerpoint.template.macroEnabled.12",
        "PowerPoint");
    put("application/vnd.ms-powerpoint.addin.macroEnabled.12", "PowerPoint");
    put("application/vnd.openxmlformats-officedocument.presentationml"
        + ".slideshow", "PowerPoint");
    put("application/vnd.ms-powerpoint.slideshow.macroEnabled.12",
        "PowerPoint");
    put("application/vnd.openxmlformats-officedocument.presentationml.slide",
        "PowerPoint");
    put("application/vnd.ms-powerpoint.slide.macroEnabled.12", "PowerPoint");
    put("application/vnd.ms-powerpoint.presentation.12", "PowerPoint");
    put("application/vnd.ms-powerpoint.12", "PowerPoint");
    put("application/visio", "Visio");
    put("application/x-visio", "Visio");
    put("application/vnd.visio", "Visio");
    put("application/x-visiotech", "Visio");
    put("application/visio.drawing", "Visio");
    put("text/html", "Web");
    put("text/pdf", "PDF");
    put("application/pdf", "PDF");
    put("text/plain", "Text");
    put("text/rtf", "Text");
    put("application/rtf", "Text");
    put("application/x-rtf", "Text");
    put("text/richtext", "Text");
    put("application/xml", "XML");
    put("text/xml", "XML");
    put("text/vcard", "vCard");
    put("text/x-vcard", "vCard");
    put("application/vcard", "vCard");
    put("application/zip", "Compressed");
    put("application/x-compressed-zip", "Compressed");
    put("application/x-zip-compressed", "Compressed");
    put("application/vnd.ms-cab-compressed", "Compressed");
    put("application/x-7z-compressed", "Compressed");
    put("application/x-tar", "Compressed");
    put("application/x-rar-compressed", "Compressed");
    put("application/x-bzip2", "Compressed");
    put("application/x-compressed", "Compressed");
    put("application/x-ace", "Compressed");
    put("application/x-gzip", "Compressed");
  }};

  public AddContentTypeToMetadata(String metadataKey) {
    if (null == metadataKey) {
      metaKey = "myFileType";
    } else {
      metaKey = metadataKey;
    }
  }

  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    String ct = params.get(MetadataTransform.KEY_CONTENT_TYPE);
    String type = typeMap.get(ct);
    if (null == type) {
      type = "Other";
    }
    metadata.set(metaKey, type);
  }

  @Override
  public String toString() {
    return "ContentTypeToMetadataTransform(" + metaKey + ", " + typeMap + ")";
  }

  public static AddContentTypeToMetadata create(Map<String, String> cfg) {
    return new AddContentTypeToMetadata(cfg.get("metaKey"));
  }
}
