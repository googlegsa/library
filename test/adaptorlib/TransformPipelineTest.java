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

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link TransformPipeline}.
 */
public class TransformPipelineTest {
  @Test
  public void testNoOp() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    pipeline.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(0, contentIn.size());
    assertEquals(0, contentOut.size());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testNoOpWithInput() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    String testString = "Here is some input";
    contentIn.write(testString.getBytes());
    pipeline.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(testString, contentIn.toString());
    assertEquals(testString, contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testModifyParams() throws IOException, TransformException {
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new DocumentTransform("Param Transform") {
        @Override
        public void transform(ByteArrayOutputStream cIn, ByteArrayOutputStream mIn,
                              OutputStream cOut, OutputStream mOut,
                              Map<String, String> p) throws TransformException, IOException {
          p.put("newKey", "newValue");
        }
      });
    pipeline.transform(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                       new ByteArrayOutputStream(), new ByteArrayOutputStream(), params);

    assertEquals("value1", params.get("key1"));
    assertEquals("newValue", params.get("newKey"));
    assertEquals(2, params.keySet().size());
  }
}
