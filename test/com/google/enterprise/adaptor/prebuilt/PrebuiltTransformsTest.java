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
import java.util.LinkedHashMap;
import java.util.Map;

/** Unit tests for {@link PrebuiltTransfors}. */
public class PrebuiltTransformsTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testCopyMetadataOverwriteFalse() {
    Map<String, String> config = new LinkedHashMap<String, String>();
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
  public void testCopyMetadataOverwriteTrue() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("overwrite", "true");
    config.put("2.from", "author");
    config.put("2.to", "contributors");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "Fred");
      golden.add("contributors", "Fred");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("author", "Fred");
    metadata.add("contributors", "Mary");
    metadata.add("contributors", "George");
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testCopyParamToParam() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "black");
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testCopyParamToExistingParam() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "black");
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    params.put("color", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testCopyParamToParamOverwriteFalse() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("overwrite", "false");
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config.put("2.from", "colour");
    config.put("2.from.keyset", "params");
    config.put("2.to", "hue");
    config.put("2.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "black");
      golden.put("color", "red");
      golden.put("hue", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    params.put("color", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testCopyParamToParamOverwriteTrue() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("overwrite", "true");
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config.put("2.from", "colour");
    config.put("2.from.keyset", "params");
    config.put("2.to", "hue");
    config.put("2.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "black");
      golden.put("color", "black");
      golden.put("hue", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    params.put("color", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testCopyParamToMetadata() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("color", "black");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    params.put("author", "Fred");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testCopyParamToMultivalueMetadata() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("2.from", "author");
    config.put("2.from.keyset", "params");
    config.put("2.to", "contributors");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("contributors", "Mary");
      golden.add("contributors", "George");
      golden.add("contributors", "Fred");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("contributors", "Mary");
    metadata.add("contributors", "George");
    Map<String, String> params = new HashMap<String, String>();
    params.put("author", "Fred");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testCopyMetadataToParam() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    metadata.add("author", "Fred");
    Map<String, String> params = new HashMap<String, String>();
    transform.transform(metadata, params);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testCopyMultivalueMetadataToParam() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    metadata.add("colour", "red");
    Map<String, String> params = new HashMap<String, String>();
    transform.transform(metadata, params);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testCopyMetadataToParamOverwrite() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);

    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    Map<String, String> params = new HashMap<String, String>();
    params.put("color", "red");
    transform.transform(metadata, params);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testCopyToString() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config = Collections.unmodifiableMap(config);
    MetadataTransform transform
        = PrebuiltTransforms.copyMetadata(config);
    assertEquals("CopyTransform(copies=[],overwrite=null,move=false)",
        transform.toString());
  }

  @Test
  public void testCopyToStringWithKeysAndKeysetAndOverwrite() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "metadata");
    config.put("2.from", "author");
    config.put("2.to", "contributors");
    config.put("overwrite", "true");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.copyMetadata(config);
    assertEquals("CopyTransform(copies=["
        + "(from=(key=colour,keyset=params),"
        + "to=(key=color,keyset=metadata)), "
        + "(from=(key=author,keyset=metadata),"
        + "to=(key=contributors,keyset=metadata))],"
        + "overwrite=true,move=false)",
        transform.toString());
  }

  @Test
  public void testMoveMetadataToMetadata() {
    Map<String, String> config = new LinkedHashMap<String, String>();
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
  public void testMoveFromParamsToMetadata() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.moveMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("color", "black");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden
        = Collections.<String, String>emptyMap();

    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testMoveFromParamsToExistingMetadata() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("overwrite", "true");
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.moveMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("color", "black");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden
        = Collections.<String, String>emptyMap();

    Metadata metadata = new Metadata();
    metadata.add("color", "red");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testMoveFromMetadataToParam() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.moveMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    Map<String, String> params = new HashMap<String, String>();
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testMoveFromMetadataToExistingParam() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.moveMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    Map<String, String> params = new HashMap<String, String>();
    params.put("color", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testMoveFromParamToParam() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.moveMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testMoveParamToParamOverwriteFalse() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("overwrite", "false");
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.moveMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("color", "red");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    params.put("color", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testMoveParamToParamOverwriteTrue() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("overwrite", "true");
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.moveMetadata(config);

    final Metadata metadataGolden = new Metadata().unmodifiableView();
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("color", "black");
      paramsGolden = Collections.unmodifiableMap(golden);
    }
    Metadata metadata = new Metadata();
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "black");
    params.put("color", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testMoveToString() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.moveMetadata(config);
    assertEquals("CopyTransform(copies=[],overwrite=null,move=true)",
        transform.toString());
  }

  @Test
  public void testMoveToStringWithKeysAndKeysetAndOverwrite() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("1.from", "colour");
    config.put("1.from.keyset", "params");
    config.put("1.to", "color");
    config.put("1.to.keyset", "metadata");
    config.put("2.from", "author");
    config.put("2.to", "contributors");
    config.put("overwrite", "true");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.moveMetadata(config);
    assertEquals("CopyTransform(copies=["
        + "(from=(key=colour,keyset=params),"
        + "to=(key=color,keyset=metadata)), "
        + "(from=(key=author,keyset=metadata),"
        + "to=(key=contributors,keyset=metadata))],"
        + "overwrite=true,move=true)",
        transform.toString());
  }

  @Test
  public void testDeleteMetadata() {
    Map<String, String> config = new LinkedHashMap<String, String>();
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
  public void testDeleteParams() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "missing");
    config.put("keyset1", "params");
    config.put("key2", "colour");
    config.put("keyset2", "params");
    config.put("key3", "author");
    config.put("keyset3", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.deleteMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("colour", "black");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden
        = Collections.<String, String>emptyMap();

    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    Map<String, String> params = new HashMap<String, String>();
    params.put("author", "Fred");
    params.put("colour", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testDeleteToString() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform
        = PrebuiltTransforms.deleteMetadata(config);
    assertEquals("DeleteTransform(keys=[])", transform.toString());
  }

  @Test
  public void testDeleteToStringWithKeys() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key2", "colour");
    config.put("key3", "author");
    config.put("keyset3", "params");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.deleteMetadata(config);
    assertEquals("DeleteTransform("
        + "keys=[(key=colour,keyset=metadata), (key=author,keyset=params)])",
        transform.toString());
  }

  @Test
  public void testDeleteInvalidKeys() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "");
    config.put("keyy", "author");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.deleteMetadata(config);
    assertEquals("DeleteTransform(keys=[])", transform.toString());
  }

  @Test
  public void testReplacePattern() {
    Map<String, String> config = new LinkedHashMap<String, String>();
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
    Map<String, String> config = new LinkedHashMap<String, String>();
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
  public void testReplacePatternInParams() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "missing");
    config.put("keyset2", "params");
    config.put("pattern", "[aeiou]");
    config.put("replacement", "$0$0");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("colour", "black");
      golden.add("author", "Fred");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "reed");
      golden.put("author", "Fred");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("colour", "black");
    metadata.add("author", "Fred");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "red");
    params.put("author", "Fred");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplaceStringInParams() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "missing");
    config.put("keyset2", "params");
    config.put("string", "[test]");
    config.put("replacement", "herring");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("colour", "black [test]");
      golden.add("author", "Fred [test]");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "red herring");
      golden.put("author", "Fred [test]");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("colour", "black [test]");
    metadata.add("author", "Fred [test]");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "red [test]");
    params.put("author", "Fred [test]");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplacePatternWholeValue() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "author");
    config.put("keyset2", "metadata");
    config.put("pattern", "^.*$");
    config.put("replacement", "test");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "test");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "test");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("author", "J.D. Salinger");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplacePatternEmptyValue() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "author");
    config.put("keyset2", "metadata");
    config.put("pattern", "^$");
    config.put("replacement", "test");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "J.D. Salinger");
      golden.add("author", "test");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "test");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("author", "J.D. Salinger");
    metadata.add("author", "");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplacePatternEmptyReplacement() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "author");
    config.put("keyset2", "metadata");
    config.put("pattern", "[aeiou]");
    config.put("replacement", "");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "J.D. Slngr");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "rd");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("author", "J.D. Salinger");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplacePatternUntrimmed() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "author");
    config.put("keyset2", "metadata");
    // Pattern with leading and trailing whitespace matches 2 or more spaces,
    // replacing them with a single space.
    config.put("pattern", " + ");
    config.put("replacement", " ");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "J. D. Salinger");
      golden.add("author", " J. K. Rowling ");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "burnt orange ");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("author", "J.    D.  Salinger");
    // Metadata and Param values with leading and trailing whitespace that
    // that should match pattern.
    metadata.add("author", "   J.   K.  Rowling   ");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "burnt   orange   ");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplaceStringUntrimmed() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "author");
    config.put("keyset2", "metadata");
    config.put("string", "  ");
    config.put("replacement", " ");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "J. D. Salinger");
      golden.add("author", " J. K. Rowling ");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "burnt orange ");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("author", "J.  D.  Salinger");
    metadata.add("author", "  J.  K.  Rowling  ");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "burnt  orange  ");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  // These next 3 tests are showing the behavior of patterns from the
  // replaceMetadata javadoc.
  @Test
  public void testReplacePatternDotStar() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "author");
    config.put("keyset2", "metadata");
    config.put("pattern", ".*");
    config.put("replacement", "test");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "testtest");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "testtest");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("author", "J.D. Salinger");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplacePatternPrepend() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "author");
    config.put("keyset2", "metadata");
    config.put("pattern", "^");
    config.put("replacement", "test");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "testJ.D. Salinger");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "testred");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("author", "J.D. Salinger");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplacePatternAppend() {
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("key1", "colour");
    config.put("keyset1", "params");
    config.put("key2", "author");
    config.put("keyset2", "metadata");
    config.put("pattern", "$");
    config.put("replacement", "test");
    config = Collections.unmodifiableMap(config);

    MetadataTransform transform = PrebuiltTransforms.replaceMetadata(config);

    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("author", "J.D. Salingertest");
      metadataGolden = golden.unmodifiableView();
    }
    final Map<String, String> paramsGolden;
    {
      Map<String, String> golden = new HashMap<String, String>();
      golden.put("colour", "redtest");
      paramsGolden = Collections.unmodifiableMap(golden);
    }

    Metadata metadata = new Metadata();
    metadata.add("author", "J.D. Salinger");
    Map<String, String> params = new HashMap<String, String>();
    params.put("colour", "red");
    transform.transform(metadata, params);
    assertEquals(metadataGolden, metadata);
    assertEquals(paramsGolden, params);
  }

  @Test
  public void testReplaceToString() {
    Map<String, String> config = new LinkedHashMap<String, String>();
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
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("replacement", "replace$0");
    config = Collections.unmodifiableMap(config);

    thrown.expect(IllegalArgumentException.class);
    MetadataTransform transform
        = PrebuiltTransforms.replaceMetadata(config);
  }

  @Test
  public void testReplaceBothStringAndPattern() {
    Map<String, String> config = new LinkedHashMap<String, String>();
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
    Map<String, String> config = new LinkedHashMap<String, String>();
    config.put("string", "tofind");
    config = Collections.unmodifiableMap(config);

    thrown.expect(IllegalArgumentException.class);
    MetadataTransform transform  
        = PrebuiltTransforms.replaceMetadata(config);
  }

  @Test
  public void testDegenerateMove() {
    Map<String, String> config = new LinkedHashMap<String, String>();
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
    // If the move attempts to write to the unmodifiable view,
    // it would throw UnsupportedOperationException.
    final Metadata unmodifiableMetadata;
    {
      Metadata metadata = new Metadata();
      metadata.add("color", "black");
      unmodifiableMetadata = metadata.unmodifiableView();
    }
    transform.transform(unmodifiableMetadata, new HashMap<String, String>());
    assertEquals(metadataGolden, unmodifiableMetadata);
    assertEquals("CopyTransform(copies=[],overwrite=null,move=true)",
        transform.toString());
  }

  @Test
  public void testDegenerateCopy() {
    Map<String, String> config = new LinkedHashMap<String, String>();
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
    // If the copy attempts to write to the unmodifiable view,
    // it would throw UnsupportedOperationException.
    final Metadata unmodifiableMetadata;
    {
      Metadata metadata = new Metadata();
      metadata.add("color", "black");
      unmodifiableMetadata = metadata.unmodifiableView();
    }
    transform.transform(unmodifiableMetadata, new HashMap<String, String>());
    assertEquals(metadataGolden, unmodifiableMetadata);
    assertEquals("CopyTransform(copies=[],overwrite=null,move=false)",
        transform.toString());

  }
}
