// Copyright 2013 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor.prebuilt;

import static org.junit.Assert.assertEquals;

import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link PrebuiltTransfors}. */
public class PrebuiltTransformsTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testCopy() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.to", "color");
    config.put("2.from", "author");
    config.put("2.to", "contributors");
    config.put("3.from", "color");
    config.put("3.to", "favorite");
    config.put("4.from", "missing");
    config.put("4.to", "favorite");
    config.put("trash.from", "colour");
    config.put("trash.to", "not used");
    config.put("5.from", "colour");
    config.put("6.to", "colour");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.copyMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("colour", "black");
      golden.add("color", "black");
      golden.add("favorite", "black");
      golden.add("author", "Fred");
      golden.add("contributors", "Mary");
      golden.add("contributors", "George");
      golden.add("contributors", "Fred");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    metadata.add("author", "Fred");
    metadata.add("contributors", "Mary");
    metadata.add("contributors", "George");
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testCopyToString() {
    Map<String, String> config = new HashMap<String, String>();
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.copyMetadata(config);
    assertEquals("CopyTransform(copies=[],overwrite=false,move=false)",
        transform.toString());
  }

  @Test
  public void testMove() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("overwrite", "true");
    config.put("1.from", "colour");
    config.put("1.to", "color");
    config.put("2.from", "author");
    config.put("2.to", "contributors");
    config.put("3.from", "color");
    config.put("3.to", "favorite");
    config.put("4.from", "missing");
    config.put("4.to", "favorite");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.moveMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("favorite", "black");
      golden.add("contributors", "Fred");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    metadata.add("author", "Fred");
    metadata.add("contributors", "Mary");
    metadata.add("contributors", "George");
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testMoveToString() {
    Map<String, String> config = new HashMap<String, String>();
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.moveMetadata(config);
    assertEquals("CopyTransform(copies=[],overwrite=false,move=true)",
        transform.toString());
  }

  @Test
  public void testDelete() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key1", "missing");
    config.put("key3", "author");
    config.put("keyy", "contributors");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.deleteMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("colour", "black");
      golden.add("contributors", "Mary");
      golden.add("contributors", "George");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    metadata.add("author", "Fred");
    metadata.add("contributors", "Mary");
    metadata.add("contributors", "George");
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testDeleteToString() {
    Map<String, String> config = new HashMap<String, String>();
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.deleteMetadata(config);
    assertEquals("DeleteTransform(keys=[])", transform.toString());
  }

  @Test
  public void testReplacePattern() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key1", "colour");
    config.put("key2", "missing");
    config.put("key4", "contributors");
    config.put("pattern", "[aeiou]");
    config.put("replacement", "$0$0");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("colour", "blaack");
      golden.add("author", "Fred");
      golden.add("contributors", "Maary");
      golden.add("contributors", "Geeoorgee");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    metadata.add("author", "Fred");
    metadata.add("contributors", "Mary");
    metadata.add("contributors", "George");
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testReplaceString() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("overwrite", "false");
    config.put("key1", "colour");
    config.put("key2", "missing");
    config.put("key4", "contributors");
    config.put("string", "[test]");
    config.put("replacement", "$0");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("colour", "black [test]");
      golden.add("colour", "black $0");
      golden.add("author", "Fred [test]");
      golden.add("contributors", "Ma[test]ry[test]");
      golden.add("contributors", "Ma$0ry$0");
      golden.add("contributors", "George");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "black [test]");
    metadata.add("author", "Fred [test]");
    metadata.add("contributors", "Ma[test]ry[test]");
    metadata.add("contributors", "George");
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testReplaceToString() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("string", "tofind");
    config.put("replacement", "replace$0");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.replaceMetadata(config);
    assertEquals("ReplaceTransform(keys=[],toMatch=\\Qtofind\\E,"
        + "replacement=replace\\$0,overwrite=true)", transform.toString());
  }

  @Test
  public void testReplaceMissingStringAndPattern() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("replacement", "replace$0");
    config = Collections.unmodifiableMap(config);

    thrown.expect(IllegalArgumentException.class);
    MetadataTransform transform
        = PrebuiltTransforms.replaceMetadata(config);
  }

  @Test
  public void testReplaceBothStringAndPattern() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("string", "tofind");
    config.put("pattern", "tofind");
    config.put("replacement", "replace$0");
    config = Collections.unmodifiableMap(config);

    thrown.expect(IllegalArgumentException.class);
    MetadataTransform transform 
        = PrebuiltTransforms.replaceMetadata(config);
  }

  @Test
  public void testReplaceMissingReplacement() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("string", "tofind");
    config = Collections.unmodifiableMap(config);

    thrown.expect(IllegalArgumentException.class);
    MetadataTransform transform  
        = PrebuiltTransforms.replaceMetadata(config);
  }

  @Test
  public void testDegenerateMove() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("1.from", "color");
    config.put("1.to", "color");
    config = Collections.unmodifiableMap(config);
    MetadataTransform transform 
         = PrebuiltTransforms.moveMetadata(config);
    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("color", "black");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("color", "black");
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testDegenerateCopy() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("1.from", "color");
    config.put("1.to", "color");
    config = Collections.unmodifiableMap(config);
    MetadataTransform transform 
        = PrebuiltTransforms.copyMetadata(config);
    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("color", "black");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("color", "black");
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }
}
