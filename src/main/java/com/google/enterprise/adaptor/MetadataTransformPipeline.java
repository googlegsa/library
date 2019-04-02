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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modify metadata using multiple serial transforms. The transforms
 * are arranged into a serial pipeline where the output of one becomes the
 * input for the next in the series.
 *
 * <p>This class is thread-safe.
 */
class MetadataTransformPipeline {
  private final List<MetadataTransform> transformList;
  private final List<String> names;

  public MetadataTransformPipeline(
      List<? extends MetadataTransform> transforms,
      List<String> names) {
    if (transforms.size() != names.size()) {
      throw new IllegalArgumentException(
          "Transforms and names must be the same size");
    }
    this.transformList = Collections.unmodifiableList(
        new ArrayList<MetadataTransform>(transforms));
    this.names = Collections.unmodifiableList(new ArrayList<String>(names));
    // Check for null after copying the lists because List.contains(null)
    // is permitted to throw NPE whereas ArrayList is documented not to 
    // throw NPE.
    if (transformList.contains(null)) {
      throw new NullPointerException("Transforms must not contain null values");
    }
    if (names.contains(null)) {
      throw new NullPointerException("Names must not contain null values");
    }
  }

  /**
   * Transform {@code metadata}.
   */
  public void transform(Metadata metadata, Map<String, String> params) {
    if (transformList.isEmpty()) {
      return;
    }

    Metadata metadataInTransit = new Metadata(metadata);
    Map<String, String> paramsInTransit = Collections.checkedMap(
        new HashMap<String, String>(params), String.class, String.class);

    for (int i = 0; i < transformList.size(); i++) {
      MetadataTransform transform = transformList.get(i);
      try {
        transform.transform(metadataInTransit, paramsInTransit);
      } catch (RuntimeException e) {
        throw new RuntimeException(
            "Exception during transform " + names.get(i), e);
      }
    }

    metadata.set(metadataInTransit);
    params.clear();
    params.putAll(paramsInTransit);
  }

  /**
   * Retrieve transforms in the order they are processed in the pipeline.
   */
  public List<MetadataTransform> getMetadataTransforms() {
    return transformList;
  }

  public List<String> getNames() {
    return names;
  }
}
