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
 */
public interface DocumentTransform {
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
      throws TransformException, IOException;

  public String name();

  /**
   * If this property is true, a failure of this transform will cause the entire
   * transform pipeline to abort. This is useful in the case where a particular
   * transform is required in order to server data. For example, a transform
   * tasked with redacting or filtering document content.
   *
   * If this is false and a error occurs, this transform is treated as a
   * identity transform.
   */
  public boolean errorHaltsPipeline();
}
