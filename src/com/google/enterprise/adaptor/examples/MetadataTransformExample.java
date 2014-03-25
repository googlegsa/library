// Copyright 2013 Google Inc. All Rights Reserved.
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

import com.google.enterprise.adaptor.DocumentTransform;
import com.google.enterprise.adaptor.Metadata;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example transform which will move values from one key to another.
 * <p>
 * For example the values under key "creator" could be moved to 
 * be under the key "author".
 */
public class MetadataTransformExample implements DocumentTransform {
  private static final Logger log
      = Logger.getLogger(MetadataTransformExample.class.getName());
  private final String src;
  private final String dest;
 
  private MetadataTransformExample(String originalKey, String changedKey) {
    if (null == originalKey || null == changedKey) {
      throw new NullPointerException();
    }
    this.src = originalKey;
    this.dest = changedKey;
    if (src.equals(dest)) {
      log.log(Level.WARNING, "original and destination key the same: {0}", src);
    }
  }

  /** Makes transform from config with "src" and "dest" keys. */
  public static MetadataTransformExample create(Map<String, String> cfg) {
    return new MetadataTransformExample(cfg.get("src"), cfg.get("dest"));
  }

  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    if (src.equals(dest)) {
      return;
    }
    Set<String> valuesToMove = metadata.getAllValues(src);
    if (valuesToMove.isEmpty()) {
      log.log(Level.FINE, "no values for {0}. Skipping", src);
    } else {
      log.log(Level.FINE, "moving values from {0} to {1}: {2}",
          new Object[] {src, dest, valuesToMove});
      Set<String> valuesAlreadyThere = metadata.getAllValues(dest);
      metadata.set(dest, combine(valuesToMove, valuesAlreadyThere));
      log.log(Level.FINER, "deleting source {0}", src);
      metadata.set(src, Collections.<String>emptySet());
    }
  }

  private Set<String> combine(Set<String> s1, Set<String> s2) {
    Set<String> combined = new HashSet<String>(s1);
    combined.addAll(s2);
    return combined;
  }

  @Override
  public String toString() {
    return "MetadataTransformExample(src=" + src + ",dest=" + dest + ")";
  }
}
