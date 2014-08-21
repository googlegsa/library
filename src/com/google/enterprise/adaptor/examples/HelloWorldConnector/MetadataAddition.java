// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.google.enterprise.adaptor.DocumentTransform;
import com.google.enterprise.adaptor.Metadata;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example transform which will add values to metadata "taste" if the document
 * has this metadata.
 */
public class MetadataAddition implements DocumentTransform {
  private static final Logger log = Logger.getLogger(MetadataAddition.class
      .getName());
  private static String metaTaste = "taste";
  private Set<String> valuesToAdd = null;

  private MetadataAddition(String values) {
    if (null == values) {
      throw new NullPointerException();
    }
    String valueArray[] = values.split(",");
    valuesToAdd = new HashSet<String>(Arrays.asList(valueArray));
}

  /** Makes transform from config file with "taste". */
  public static MetadataAddition load(Map<String, String> cfg) {
    return new MetadataAddition(cfg.get(metaTaste));
  }

  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    Set<String> values = metadata.getAllValues(metaTaste);
    if (values.isEmpty()) {
      log.log(Level.INFO, "no metadata {0}. Skipping", metaTaste);
    } else {
      log.log(Level.INFO,
              "adding values {1} for existing metadata {0}  ",
              new Object[] { metaTaste, valuesToAdd });      
      metadata.set(metaTaste, combine(values, valuesToAdd));
    }
  }

  private Set<String> combine(Set<String> s1, Set<String> s2) {
    Set<String> combined = new HashSet<String>(s1);
    combined.addAll(s2);
    return combined;
  }

  @Override
  public String toString() {
    return "MetadataAddition(meta_taste=" + metaTaste + ")";
  }
}
