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
    TransformPipeline pipeline = new TransformPipeline(Collections.<DocumentTransform>emptyList());
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    pipeline.transform(new byte[0], contentOut, metadata, params);

    assertEquals(0, contentOut.size());
    assertEquals(new Metadata(), metadata);
    assertEquals(Collections.emptyMap(), params);
  }

  @Test
  public void testNoOpWithInput() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline(Collections.<DocumentTransform>emptyList());
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    Metadata metadata = new Metadata();
    metadata.add("key1", "value1");
    Map<String, String> params = new HashMap<String, String>();
    params.put("key2", "value2");
    String testString = "Here is some input";
    pipeline.transform(testString.getBytes(), contentOut, metadata, params);

    assertEquals(testString, contentOut.toString());
    Metadata goldenMetadata = new Metadata();
    goldenMetadata.add("key1", "value1");
    assertEquals(goldenMetadata, metadata);
    assertEquals(Collections.singletonMap("key2", "value2"), params);
  }

  @Test
  public void testAddMetadataAndParams() throws IOException, TransformException {
    Metadata metadata = new Metadata();
    metadata.add("key1", "value1");
    Map<String, String> params = new HashMap<String, String>();
    params.put("key2", "value2");

    List<DocumentTransform> transforms = new ArrayList<DocumentTransform>();
    transforms.add(new AbstractDocumentTransform() {
        @Override
        public void transform(ByteArrayOutputStream cIn, OutputStream cOut, Metadata m,
                              Map<String, String> p) throws TransformException, IOException {
          m.set("newMeta", "metaValue");
          p.put("newKey", "newValue");
        }
      });
    TransformPipeline pipeline = new TransformPipeline(transforms);
    pipeline.transform(new byte[0], new ByteArrayOutputStream(), metadata, params);

    assertEquals("value1", metadata.getOneValue("key1"));
    assertEquals("metaValue", metadata.getOneValue("newMeta"));
    assertEquals(2, metadata.getKeys().size());
    assertEquals("value2", params.get("key2"));
    assertEquals("newValue", params.get("newKey"));
    assertEquals(2, params.size());
  }

  private static class IncrementTransform extends AbstractDocumentTransform {
    @Override
    public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                          Metadata metadata, Map<String, String> p)
        throws TransformException, IOException {
      byte[] content = contentIn.toByteArray();
      for (int i = 0; i < content.length; i++) {
        content[i]++;
      }
      contentOut.write(content);
      metadata.set("int", "" + (Integer.parseInt(metadata.getOneValue("int")) + 1));
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
                          Metadata metadata, Map<String, String> p)
        throws TransformException, IOException {
      byte[] content = contentIn.toByteArray();
      for (int i = 0; i < content.length; i++) {
        content[i] *= factor;
      }
      contentOut.write(content);
      metadata.set("int", "" + (Integer.parseInt(metadata.getOneValue("int")) * factor));
      p.put("int", "" + (Integer.parseInt(p.get("int")) * factor));
    }
  }

  private static class ErroringTransform extends AbstractDocumentTransform {
    public ErroringTransform(boolean required) {
      super(null, required);
    }
    @Override
    public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                          Metadata metadata, Map<String, String> p)
        throws TransformException, IOException {
      // Do some work, but don't complete.
      contentOut.write(new byte[] {1});
      metadata.set("trash", "value");
      p.put("more trash", "values");
      throw new TransformException("test exception");
    }
  }

  @Test
  public void testTransform() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline(Arrays.asList(new IncrementTransform()));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Metadata metadata = new Metadata();
    metadata.add("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    Metadata goldenMetadata = new Metadata();
    goldenMetadata.add("int", "1");
    assertEquals(goldenMetadata, metadata);
    assertEquals(Collections.singletonMap("int", "2"), params);
  }

  @Test
  public void testMultipleTransforms() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline(Arrays.asList(
        new IncrementTransform(), new ProductTransform(2)));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Metadata metadata = new Metadata();
    metadata.set("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);

    assertArrayEquals(new byte[] {4, 6, 8}, out.toByteArray());
    Metadata goldenMetadata = new Metadata();
    goldenMetadata.set("int", "2");
    assertEquals(goldenMetadata, metadata);
    assertEquals(Collections.singletonMap("int", "4"), params);
  }

  @Test
  public void testNotLastTransformError() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline(Arrays.asList(
        new IncrementTransform(), new ErroringTransform(false)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Metadata metadata = new Metadata();
    metadata.set("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    Metadata goldenMetadata = new Metadata();
    goldenMetadata.add("int", "1");
    assertEquals(goldenMetadata, metadata);
    assertEquals(Collections.singletonMap("int", "2"), params);
  }

  @Test
  public void testLastTransformError() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline(Arrays.asList(
        new ErroringTransform(false), new IncrementTransform()));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Metadata metadata = new Metadata();
    metadata.set("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);

    assertArrayEquals(new byte[] {2, 3, 4}, out.toByteArray());
    Metadata goldenMetadata = new Metadata();
    goldenMetadata.set("int", "1");
    assertEquals(goldenMetadata, metadata);
    assertEquals(Collections.singletonMap("int", "2"), params);
  }

  @Test
  public void testTransformErrorFatal() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline(Arrays.asList(
        new IncrementTransform(), new ErroringTransform(true)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Metadata metadata = new Metadata();
    metadata.set("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    thrown.expect(TransformException.class);
    try {
      pipeline.transform(new byte[] {1, 2, 3}, out, metadata, params);
    } finally {
      assertArrayEquals(new byte[] {}, out.toByteArray());
      Metadata goldenMetadata = new Metadata();
      goldenMetadata.set("int", "0");
      assertEquals(goldenMetadata, metadata);
      assertEquals(Collections.singletonMap("int", "1"), params);
    }
  }

  @Test
  public void testResetTransform() throws Exception {
    List<DocumentTransform> transforms = new ArrayList<DocumentTransform>();
    transforms.add(new AbstractDocumentTransform() {
      @Override
      public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                            Metadata metadata, Map<String, String> p)
          throws IOException {
        // Modifying contentIn is not allowed.
        contentIn.reset();
      }
    });
    TransformPipeline pipeline = new TransformPipeline(transforms);
    thrown.expect(UnsupportedOperationException.class);
    pipeline.transform(new byte[] {1, 2, 3}, new ByteArrayOutputStream(),
        new Metadata(), new HashMap<String, String>());
  }

  @Test
  public void testWriteTransform1() throws Exception {
    List<DocumentTransform> transforms = new ArrayList<DocumentTransform>();
    transforms.add(new AbstractDocumentTransform() {
      @Override
      public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                            Metadata metadata, Map<String, String> p)
          throws IOException {
        // Modifying contentIn is not allowed.
        contentIn.write(new byte[1], 0, 1);
      }
    });
    TransformPipeline pipeline = new TransformPipeline(transforms);
    thrown.expect(UnsupportedOperationException.class);
    pipeline.transform(new byte[] {1, 2, 3}, new ByteArrayOutputStream(),
        new Metadata(), new HashMap<String, String>());
  }

  @Test
  public void testWriteTransform2() throws Exception {
    List<DocumentTransform> transforms = new ArrayList<DocumentTransform>();
    transforms.add(new AbstractDocumentTransform() {
      @Override
      public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                            Metadata metadata, Map<String, String> p)
          throws IOException {
        // Modifying contentIn is not allowed.
        contentIn.write(0);
      }
    });
    TransformPipeline pipeline = new TransformPipeline(transforms);
    thrown.expect(UnsupportedOperationException.class);
    pipeline.transform(new byte[] {1, 2, 3}, new ByteArrayOutputStream(),
        new Metadata(), new HashMap<String, String>());
  }

  @Test
  public void testWriteTransform3() throws Exception {
    List<DocumentTransform> transforms = new ArrayList<DocumentTransform>();
    transforms.add(new AbstractDocumentTransform() {
      @Override
      public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                            Metadata metadata, Map<String, String> p)
          throws IOException {
        // Modifying contentIn is not allowed.
        contentIn.write(new byte[1]);
      }
    });
    TransformPipeline pipeline = new TransformPipeline(transforms);
    thrown.expect(UnsupportedOperationException.class);
    pipeline.transform(new byte[] {1, 2, 3}, new ByteArrayOutputStream(),
        new Metadata(), new HashMap<String, String>());
  }
}
