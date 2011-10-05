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

package adaptorlib;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Map;

/** */
public class TransformPipeline extends AbstractList<DocumentTransform> {

  /**
   * ContentIn and metadataIn are guaranteed to remain unchanged.
   */
  public void transform(byte[] contentIn,
                        byte[] metadataIn,
                        OutputStream contentOut,
                        OutputStream metadataOut,
                        Map<String, String> params) throws TransformException, IOException {
    if (transformList.isEmpty()) {
      contentOut.write(contentIn);
      metadataOut.write(metadataIn);
      return;
    }

    ByteArrayOutputStream contentInTransit = new ByteArrayOutputStream(contentIn.length);
    ByteArrayOutputStream metaInTransit = new ByteArrayOutputStream(metadataIn.length);
    ByteArrayOutputStream contentOutTransit = new ByteArrayOutputStream(contentIn.length);
    ByteArrayOutputStream metaOutTransit = new ByteArrayOutputStream(metadataIn.length);
    contentInTransit.write(contentIn);
    metaInTransit.write(metadataIn);
    for (int i = 0; i < transformList.size(); i++) {
      DocumentTransform transform = transformList.get(i);
      try {
        transform.transform(contentInTransit, metaInTransit, contentOutTransit, metaOutTransit,
                            params);
      } catch (TransformException e) {
        // TODO(brandoni): Log error
        if (transform.errorHaltsPipeline()) {
          throw e;
        } else {
          if (i < transformList.size() - 1) {
            contentOutTransit.reset();
            metaOutTransit.reset();
            continue;
          } else {
            contentInTransit.writeTo(contentOut);
            metaInTransit.writeTo(metadataOut);
            return;
          }
        }
      }
      if (i < transformList.size() - 1) {
        // Swap input and output.
        ByteArrayOutputStream tmp = contentInTransit;
        contentInTransit = contentOutTransit;
        contentOutTransit = tmp;
        tmp = metaInTransit;
        metaInTransit = metaOutTransit;
        metaOutTransit = tmp;

        contentOutTransit.reset();
        metaOutTransit.reset();
      }
    }
    contentOutTransit.writeTo(contentOut);
    metaOutTransit.writeTo(metadataOut);
  }

  @Override
  public void add(int index, DocumentTransform transform) {
    transformList.add(index, transform);
  }

  @Override
  public DocumentTransform get(int index) {
    return transformList.get(index);
  }

  @Override
  public DocumentTransform set(int index, DocumentTransform transform) {
    return transformList.set(index, transform);
  }

  @Override
  public DocumentTransform remove(int index) {
    return transformList.remove(index);
  }

  @Override
  public int size() {
    return transformList.size();
  }

  private ArrayList<DocumentTransform> transformList = new ArrayList<DocumentTransform>();
}
