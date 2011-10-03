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
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    pipeline.transform(new byte[0], new byte[0], contentOut, metadataOut, params);

    assertEquals(0, contentOut.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testNoOpWithInput() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    String testString = "Here is some input";
    pipeline.transform(testString.getBytes(), new byte[0], contentOut, metadataOut, params);

    assertEquals(testString, contentOut.toString());
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
    pipeline.transform(new byte[0], new byte[0],
                       new ByteArrayOutputStream(), new ByteArrayOutputStream(), params);

    assertEquals("value1", params.get("key1"));
    assertEquals("newValue", params.get("newKey"));
    assertEquals(2, params.keySet().size());
  }

  @Test
  public void testTransform() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new IncrementTransform());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream mOut = new ByteArrayOutputStream();

    pipeline.transform(new byte[] {1, 2, 3}, new byte[0], out, mOut, new HashMap<String, String>());

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    assertArrayEquals(new byte[0], mOut.toByteArray());
  }

  @Test
  public void testMultipleTransforms() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new IncrementTransform());
    pipeline.add(new ProductTransform(2));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream mOut = new ByteArrayOutputStream();

    pipeline.transform(new byte[] {1, 2, 3}, new byte[0], out, mOut, new HashMap<String, String>());

    assertArrayEquals(new byte[] {4, 6, 8}, out.toByteArray());
    assertArrayEquals(new byte[0], mOut.toByteArray());
  }

  @Test
  public void testNotLastTransformError() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new IncrementTransform());
    pipeline.add(new ErroringTransform());
    pipeline.get(1).errorHaltsPipeline(false);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream mOut = new ByteArrayOutputStream();

    pipeline.transform(new byte[] {1, 2, 3}, new byte[0], out, mOut, new HashMap<String, String>());

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    assertArrayEquals(new byte[0], mOut.toByteArray());
  }

  @Test
  public void testLastTransformError() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new ErroringTransform());
    pipeline.get(0).errorHaltsPipeline(false);
    pipeline.add(new IncrementTransform());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream mOut = new ByteArrayOutputStream();

    pipeline.transform(new byte[] {1, 2, 3}, new byte[0], out, mOut, new HashMap<String, String>());

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    assertArrayEquals(new byte[0], mOut.toByteArray());
  }

  private static class IncrementTransform extends DocumentTransform {
    public IncrementTransform() {
      super("incrementTransform");
    }

    @Override
    public void transform(ByteArrayOutputStream contentIn, ByteArrayOutputStream metadataIn,
                          OutputStream contentOut, OutputStream metadataOut, Map<String, String> p)
                          throws TransformException, IOException {
      byte[] content = contentIn.toByteArray();
      for (int i = 0; i < content.length; i++) {
        content[i]++;
      }
      contentOut.write(content);
      metadataIn.writeTo(metadataOut);
    }
  }

  private static class ProductTransform extends DocumentTransform {
    private int factor;

    public ProductTransform(int factor) {
      super("productTransform");
      this.factor = factor;
    }

    @Override
    public void transform(ByteArrayOutputStream contentIn, ByteArrayOutputStream metadataIn,
                          OutputStream contentOut, OutputStream metadataOut, Map<String, String> p)
                          throws TransformException, IOException {
      byte[] content = contentIn.toByteArray();
      for (int i = 0; i < content.length; i++) {
        content[i] *= factor;
      }
      contentOut.write(content);
      metadataIn.writeTo(metadataOut);
    }
  }

  private static class ErroringTransform extends DocumentTransform {
    public ErroringTransform() {
      super("erroringTransform");
    }

    @Override
    public void transform(ByteArrayOutputStream contentIn, ByteArrayOutputStream metadataIn,
                          OutputStream contentOut, OutputStream metadataOut,
                          Map<String, String> p) throws TransformException, IOException {
      // Do some work, but don't complete.
      contentOut.write(new byte[] {1});
      throw new TransformException("something went wrong");
    }
  }
}
