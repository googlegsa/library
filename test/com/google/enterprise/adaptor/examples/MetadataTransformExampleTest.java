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

package com.google.enterprise.adaptor.examples;

import static org.junit.Assert.assertEquals;

import com.google.enterprise.adaptor.DocumentTransform;
import com.google.enterprise.adaptor.Metadata;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link MetadataTransformExample}. */
public class MetadataTransformExampleTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static Map<String, String> makeConfig(String src, String dest) {
    Map<String, String> config = new HashMap<String, String>();
    config.put("src", src);
    config.put("dest", dest);
    return config;
  }

  @Test
  public void testMove() {
    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("color", "orange");
      golden.add("color", "keylime");
      golden.add("color", "oxblood");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("colour", "keylime");
    metadata.add("color", "oxblood");
    metadata.add("colour", "orange");
    DocumentTransform transform 
        = MetadataTransformExample.create(makeConfig("colour", "color"));
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }

  @Test
  public void testDegenerateMove() {
    final Metadata metadataGolden;
    {
      Metadata golden = new Metadata();
      golden.add("color", "orange");
      golden.add("color", "keylime");
      golden.add("color", "oxblood");
      metadataGolden = golden.unmodifiableView();
    }
    Metadata metadata = new Metadata();
    metadata.add("color", "keylime");
    metadata.add("color", "oxblood");
    metadata.add("color", "orange");
    DocumentTransform transform 
        = MetadataTransformExample.create(makeConfig("color", "color"));
    transform.transform(metadata, new HashMap<String, String>());
    assertEquals(metadataGolden, metadata);
  }
}
