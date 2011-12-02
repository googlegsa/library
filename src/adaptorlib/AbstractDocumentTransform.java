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
import java.util.Map;

/**
 * Represents an individual transform in the transform pipeline.
 * Subclass this to add your own custom behavior.
 */
public abstract class AbstractDocumentTransform implements DocumentTransform {
  private String name = getClass().getName();
  private boolean errorHaltsPipeline = true;

  public AbstractDocumentTransform() {}

  /**
   * If {@code name} is {@code null}, the default is used.
   */
  public AbstractDocumentTransform(String name, boolean errorHaltsPipeline) {
    if (name != null) {
      this.name = name;
    }
    this.errorHaltsPipeline = errorHaltsPipeline;
  }

  public AbstractDocumentTransform(Map<String, String> config) {
    String name = config.get("name");
    if (name != null) {
      this.name = name;
    }

    String errorHaltsPipeline = config.get("errorHaltsPipeline");
    if (errorHaltsPipeline != null) {
      this.errorHaltsPipeline = Boolean.parseBoolean(errorHaltsPipeline);
    }
  }

  /**
   * Override this function to do the actual data transformation.
   * Read data from the ByteArrayOutputStream instances holding the incoming data,
   * and write them to the OutputStreams. Any changes to the params map will be
   * passed on the subsequent transforms.
   *
   * @throws TransformException
   * @throws IOException
   */
  public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                        Map<String, String> metadata, Map<String, String> params)
      throws TransformException, IOException {
    // Defaults to identity transform
    contentIn.writeTo(contentOut);
  }

  public void name(String name) {
    if (name == null) {
      throw new NullPointerException();
    }
    this.name = name;
  }
  public String name() { return name; }

  /**
   * If this property is true, a failure of this transform will cause the entire
   * transform pipeline to abort. This is useful in the case where a particular
   * transform is required in order to server data. For example, a transform
   * tasked with redacting or filtering document content.
   *
   * If this is false and a error occurs, this transform is treated as a
   * identity transform.
   */
  public void errorHaltsPipeline(boolean errorHaltsPipeline) {
    this.errorHaltsPipeline = errorHaltsPipeline;
  }

  public boolean errorHaltsPipeline() {
    return errorHaltsPipeline;
  }
}
