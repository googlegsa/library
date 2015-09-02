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

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link MetadataTransformPipeline}.
 */
public class MetadataTransformPipelineTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNoOpEmpty() throws IOException {
    MetadataTransformPipeline pipeline = new MetadataTransformPipeline(
        Collections.<MetadataTransform>emptyList(),
        Collections.<String>emptyList());
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    pipeline.transform(metadata, params);

    assertEquals(new Metadata(), metadata);
    assertEquals(Collections.emptyMap(), params);
  }

  @Test
  public void testNoOpWithInput() throws IOException {
    MetadataTransformPipeline pipeline = new MetadataTransformPipeline(
        Collections.<MetadataTransform>emptyList(),
        Collections.<String>emptyList());
    Metadata metadata = new Metadata();
    metadata.add("key1", "value1");
    Map<String, String> params = new HashMap<String, String>();
    params.put("key2", "value2");
    pipeline.transform(metadata, params);

    Metadata goldenMetadata = new Metadata();
    goldenMetadata.add("key1", "value1");
    assertEquals(goldenMetadata, metadata);
    assertEquals(Collections.singletonMap("key2", "value2"), params);
  }

  @Test
  public void testAddMetadataAndParams() throws IOException {
    Metadata metadata = new Metadata();
    metadata.add("key1", "value1");
    Map<String, String> params = new HashMap<String, String>();
    params.put("key2", "value2");

    List<MetadataTransform> transforms
        = new ArrayList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
        @Override
        public void transform(Metadata m, Map<String, String> p) {
          m.set("newMeta", "metaValue");
          p.put("newKey", "newValue");
        }
      });
    MetadataTransformPipeline pipeline = new MetadataTransformPipeline(
        transforms, Arrays.asList("t1"));
    pipeline.transform(metadata, params);

    assertEquals("value1", metadata.getOneValue("key1"));
    assertEquals("metaValue", metadata.getOneValue("newMeta"));
    assertEquals(2, metadata.getKeys().size());
    assertEquals("value2", params.get("key2"));
    assertEquals("newValue", params.get("newKey"));
    assertEquals(2, params.size());
  }

  private static class ErroringTransform implements MetadataTransform {
    @Override
    public void transform(Metadata metadata, Map<String, String> p) {
      // Do some work, but don't complete.
      metadata.set("trash", "value");
      p.put("more trash", "values");
      throw new RuntimeException("test exception");
    }
  }

  private static class IncrementTransform implements MetadataTransform {
    @Override
    public void transform(Metadata metadata, Map<String, String> p) {
      metadata.set("int",
          "" + (Integer.parseInt(metadata.getOneValue("int")) + 1));
      p.put("int", "" + (Integer.parseInt(p.get("int")) + 1));
    }
  }

  private static class ProductTransform implements MetadataTransform {
    private int factor;

    public ProductTransform(int factor) {
      this.factor = factor;
    }

    @Override
    public void transform(Metadata metadata, Map<String, String> p) {
      metadata.set("int",
          "" + (Integer.parseInt(metadata.getOneValue("int")) * factor));
      p.put("int", "" + (Integer.parseInt(p.get("int")) * factor));
    }
  }

  @Test
  public void testTransform() throws IOException {
    MetadataTransformPipeline pipeline = new MetadataTransformPipeline(
        Arrays.asList(new IncrementTransform()), Arrays.asList("it"));
    Metadata metadata = new Metadata();
    metadata.add("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(metadata, params);

    Metadata goldenMetadata = new Metadata();
    goldenMetadata.add("int", "1");
    assertEquals(goldenMetadata, metadata);
    assertEquals(Collections.singletonMap("int", "2"), params);
  }

  @Test
  public void testMultipleTransforms() throws IOException {
    MetadataTransformPipeline pipeline
        = new MetadataTransformPipeline(Arrays.asList(
        new IncrementTransform(), new ProductTransform(2)),
        Arrays.asList("it", "pt"));

    Metadata metadata = new Metadata();
    metadata.set("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    pipeline.transform(metadata, params);

    Metadata goldenMetadata = new Metadata();
    goldenMetadata.set("int", "2");
    assertEquals(goldenMetadata, metadata);
    assertEquals(Collections.singletonMap("int", "4"), params);
  }

  @Test
  public void testTransformErrorFatal() throws IOException {
    MetadataTransformPipeline pipeline
        = new MetadataTransformPipeline(Arrays.asList(
        new IncrementTransform(), new ErroringTransform()),
        Arrays.asList("it", "et"));
    Metadata metadata = new Metadata();
    metadata.set("int", "0");
    Map<String, String> params = new HashMap<String, String>();
    params.put("int", "1");

    thrown.expect(RuntimeException.class);
    try {
      pipeline.transform(metadata, params);
    } finally {
      Metadata goldenMetadata = new Metadata();
      goldenMetadata.set("int", "0");
      assertEquals(goldenMetadata, metadata);
      assertEquals(Collections.singletonMap("int", "1"), params);
    }
  }
}
