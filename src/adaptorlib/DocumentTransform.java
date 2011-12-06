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
 *
 * <p>Implementations should also typically have a static factory method with a
 * single {@code Map<String, String>} argument for creating instances based on
 * configuration. Implementations are encouraged to accept "name" and
 * "required" as configuration keys.
 */
public interface DocumentTransform {
  /**
   * Read data from {@code contentIn}, transform it, and write it to {@code
   * contentOut}. Any changes to {@code metadata} and {@code params} will be
   * passed on to subsequent transforms. This method must be thread-safe.
   *
   * @throws TransformException
   * @throws IOException
   */
  public void transform(ByteArrayOutputStream contentIn,
                        OutputStream contentOut,
                        Map<String, String> metadata,
                        Map<String, String> params)
      throws TransformException, IOException;

  /**
   * The name of this transform instance, typically provided by the user. It
   * should not be {@code null}. Using the class name as a default is reasonable
   * if no name has been provided.
   */
  public String getName();

  /**
   * If this property is true, a failure of this transform will cause the entire
   * transform pipeline to abort. This is useful in the case where a particular
   * transform is required in order to server data. For example, a transform
   * tasked with redacting or filtering document content.
   *
   * If this is false and a error occurs, this transform is treated as a
   * identity transform.
   */
  public boolean isRequired();
}
