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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modify metadata using multiple serial transforms. The transforms
 * are arranged into a serial pipeline where the output of one becomes the
 * input for the next in the series.
 *
 * <p>This class is thread-safe.
 */
public class TransformPipeline {
  private static final Logger log
      = Logger.getLogger(TransformPipeline.class.getName());

  private final List<DocumentTransform> transformList;

  public TransformPipeline(List<? extends DocumentTransform> transforms) {
    this.transformList = Collections.unmodifiableList(new ArrayList<DocumentTransform>(transforms));
  }

  /**
   * Transform {@code metadata}.
   */
  public void transform(Metadata metadata, Map<String, String> params)
      throws TransformException {
    if (transformList.isEmpty()) {
      return;
    }

    Metadata metadataInTransit = new Metadata(metadata);
    Map<String, String> paramsInTransit = Collections.checkedMap(
        new HashMap<String, String>(params), String.class, String.class);

    for (DocumentTransform transform : transformList) {
      try {
        transform.transform(metadataInTransit, paramsInTransit);
      } catch (TransformException e) {
        throw new TransformException("Aborting " + transform.getName(), e);
      }
    }

    metadata.set(metadataInTransit);
    params.clear();
    params.putAll(paramsInTransit);
  }

  /**
   * Retrieve transforms in the order they are processed in the pipeline.
   */
  public List<DocumentTransform> getDocumentTransforms() {
    return transformList;
  }
}
