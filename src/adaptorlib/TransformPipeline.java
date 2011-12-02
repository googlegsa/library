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
import java.io.UnsupportedEncodingException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modify content and metadata using multiple serial transforms. The transforms
 * are arranged into a serial pipeline where the output of one becomes the
 * input for the next in the series.
 */
public class TransformPipeline extends AbstractList<DocumentTransform> {
  private static final Logger log
      = Logger.getLogger(TransformPipeline.class.getName());

  /**
   * Transform {@code contentIn} and {@code metadata}. {@code ContentIn} is
   * guaranteed to remain unchanged; the rest of the parameters are expected to
   * change.
   */
  public void transform(byte[] contentIn,
                        OutputStream contentOut,
                        Map<String, String> metadata,
                        Map<String, String> params) throws TransformException, IOException {
    if (transformList.isEmpty()) {
      contentOut.write(contentIn);
      return;
    }

    ByteArrayOutputStream contentInTransit = new ByteArrayOutputStream(contentIn.length);
    ByteArrayOutputStream contentOutTransit = new ByteArrayOutputStream(contentIn.length);
    Map<String, String> metadataInTransit = Collections.checkedMap(
        new HashMap<String, String>(metadata.size() * 2), String.class, String.class);
    Map<String, String> metadataOutTransit = Collections.checkedMap(
        new HashMap<String, String>(metadata.size() * 2), String.class, String.class);
    Map<String, String> paramsInTransit = Collections.checkedMap(
        new HashMap<String, String>(params.size() * 2), String.class, String.class);
    Map<String, String> paramsOutTransit = Collections.checkedMap(
        new HashMap<String, String>(params.size() * 2), String.class, String.class);

    contentInTransit.write(contentIn);
    metadataInTransit.putAll(metadata);
    paramsInTransit.putAll(params);

    for (DocumentTransform transform : transformList) {
      contentOutTransit.reset();
      metadataOutTransit.clear();
      metadataOutTransit.putAll(metadataInTransit);
      paramsOutTransit.clear();
      paramsOutTransit.putAll(paramsInTransit);

      try {
        transform.transform(new UnmodifiableWrapperByteArrayOutputStream(contentInTransit),
                            contentOutTransit, metadataOutTransit, paramsOutTransit);
      } catch (TransformException e) {
        if (transform.errorHaltsPipeline()) {
          log.log(Level.WARNING, "Transform Exception. Aborting '" + transform.name() + "'", e);
          throw e;
        } else {
          log.log(Level.WARNING,
                  "Transform Exception. Ignoring transform '" + transform.name() + "'", e);
          continue;
        }
      }
      // Swap input and output. The input is reused as the output for effeciency.
      ByteArrayOutputStream tmp = contentInTransit;
      contentInTransit = contentOutTransit;
      contentOutTransit = tmp;
      Map<String, String> tmpMap = metadataInTransit;
      metadataInTransit = metadataOutTransit;
      metadataOutTransit = tmpMap;
      tmpMap = paramsInTransit;
      paramsInTransit = paramsOutTransit;
      paramsOutTransit = tmpMap;
    }
    contentInTransit.writeTo(contentOut);
    metadata.clear();
    metadata.putAll(metadataInTransit);
    params.clear();
    params.putAll(paramsInTransit);
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

  private static class UnmodifiableWrapperByteArrayOutputStream extends ByteArrayOutputStream {
    private ByteArrayOutputStream os;

    public UnmodifiableWrapperByteArrayOutputStream(ByteArrayOutputStream os) {
      this.os = os;
    }

    @Override
    public void reset() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
      return os.size();
    }

    @Override
    public byte[] toByteArray() {
      return os.toByteArray();
    }

    @Override
    public String toString() {
      return os.toString();
    }

    @Override
    public String toString(String charsetName) throws UnsupportedEncodingException {
      return os.toString(charsetName);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void write(int b) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
      os.writeTo(out);
    }
  }
}
