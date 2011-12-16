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

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Tests for {@link TransformPipeline}.
 */
public class TransformPipelineTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNoOpEmpty() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    Map<String, String> metadata = new HashMap<String, String>();
    Map<String, String> params = new HashMap<String, String>();
    pipeline.transform(new byte[0], contentOut, metadata, params);

    assertEquals(0, contentOut.size());
    assertEquals(Collections.emptyMap(), metadata);
    assertEquals(Collections.emptyMap(), params);
  }

  @Test
  public void testNoOpWithInput() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    Map<String, String> metadata = new HashMap<String, String>();
    metadata.put("key1", "value1");
    Map<String, String> params = new HashMap<String, String>();
    params.put("key2", "value2");
    String testString = "Here is some input";
    pipeline.transform(testString.getBytes(), contentOut, metadata, params);

    assertEquals(testString, contentOut.toString());
    assertEquals(Collections.singletonMap("key1", "value1"), metadata);
    assertEquals(Collections.singletonMap("key2", "value2"), params);
  }

  @Test
  public void testAddMetadataAndParams() throws IOException, TransformException {
    Map<String, String> metadata = new HashMap<String, String>();
    metadata.put("key1", "value1");
    Map<String, String> params = new HashMap<String, String>();
    params.put("key2", "value2");

    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new AbstractDocumentTransform() {
        @Override
        public void transform(ByteArrayOutputStream cIn, OutputStream cOut, Map<String, String> m,
                              Map<String, String> p) throws TransformException, IOException {
          m.put("newMeta", "metaValue");
          p.put("newKey", "newValue");
        }
      });
    pipeline.transform(new byte[0], new ByteArrayOutputStream(), metadata, params);

    assertEquals("value1", metadata.get("key1"));
    assertEquals("metaValue", metadata.get("newMeta"));
    assertEquals(2, metadata.size());
    assertEquals("value2", params.get("key2"));
    assertEquals("newValue", params.get("newKey"));
    assertEquals(2, params.size());
  }

  @Test
  public void testTransform() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new IncrementTransform());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Map<String, String> metadata = new HashMap<String, String>();
    metadata.put("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    assertEquals(Collections.singletonMap("int", "1"), metadata);
    assertEquals(Collections.singletonMap("int", "2"), params);
  }

  @Test
  public void testMultipleTransforms() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new IncrementTransform());
    pipeline.add(new ProductTransform(2));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Map<String, String> metadata = new HashMap<String, String>();
    metadata.put("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);

    assertArrayEquals(new byte[] {4, 6, 8}, out.toByteArray());
    assertEquals(Collections.singletonMap("int", "2"), metadata);
    assertEquals(Collections.singletonMap("int", "4"), params);
  }

  @Test
  public void testNotLastTransformError() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new IncrementTransform());
    pipeline.add(new ErroringTransform(false));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Map<String, String> metadata = new HashMap<String, String>();
    metadata.put("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    assertEquals(Collections.singletonMap("int", "1"), metadata);
    assertEquals(Collections.singletonMap("int", "2"), params);
  }

  @Test
  public void testLastTransformError() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new ErroringTransform(false));
    pipeline.add(new IncrementTransform());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Map<String, String> metadata = new HashMap<String, String>();
    metadata.put("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    assertEquals(Collections.singletonMap("int", "1"), metadata);
    assertEquals(Collections.singletonMap("int", "2"), params);
  }

  @Test
  public void testTransformErrorFatal() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new IncrementTransform());
    pipeline.add(new ErroringTransform(true));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Map<String, String> metadata = new HashMap<String, String>();
    metadata.put("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    thrown.expect(TransformException.class);
    try {
      pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);
    } finally {
      assertArrayEquals(new byte[] {}, out.toByteArray());
      assertEquals(Collections.singletonMap("int", "0"), metadata);
      assertEquals(Collections.singletonMap("int", "1"), params);
    }
  }

  private static class IncrementTransform extends AbstractDocumentTransform {
    @Override
    public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                          Map<String, String> metadata, Map<String, String> p)
        throws TransformException, IOException {
      byte[] content = contentIn.toByteArray();
      for (int i = 0; i < content.length; i++) {
        content[i]++;
      }
      contentOut.write(content);
      metadata.put("int", "" + (Integer.parseInt(metadata.get("int")) + 1));
      p.put("int", "" + (Integer.parseInt(p.get("int")) + 1));
    }
  }

  private static class ProductTransform extends AbstractDocumentTransform {
    private int factor;

    public ProductTransform(int factor) {
      this.factor = factor;
    }

    @Override
    public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                          Map<String, String> metadata, Map<String, String> p)
        throws TransformException, IOException {
      byte[] content = contentIn.toByteArray();
      for (int i = 0; i < content.length; i++) {
        content[i] *= factor;
      }
      contentOut.write(content);
      metadata.put("int", "" + (Integer.parseInt(metadata.get("int")) * factor));
      p.put("int", "" + (Integer.parseInt(p.get("int")) * factor));
    }
  }

  private static class ErroringTransform extends AbstractDocumentTransform {
    public ErroringTransform(boolean required) {
      super(null, required);
    }

    @Override
    public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                          Map<String, String> metadata, Map<String, String> p)
        throws TransformException, IOException {
      // Do some work, but don't complete.
      contentOut.write(new byte[] {1});
      metadata.put("trash", "value");
      p.put("more trash", "values");
      throw new TransformException("test exception");
    }
  }
}
