// Copyright 2016 Google Inc. All Rights Reserved.
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

import java.util.HashMap;
import java.util.Map;

import java.util.regex.PatternSyntaxException;

/** Unit tests for {@link SkipDocumentFilter}. */
public class SkipDocumentFilterTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static SkipDocumentFilter defaultFilter() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    return SkipDocumentFilter.create(config);
  }

  // tests on create calls (various errors) and toString results
  @Test
  public void testToString_defaultFilter() {
    MetadataTransform transform = defaultFilter();
    assertEquals("SkipDocumentFilter(skipMe, \\A, true, metadata or params)",
        transform.toString());
  }

  @Test
  public void testCreate_noPropertyName() {
    thrown.expect(NullPointerException.class);
    Map<String, String> config = new HashMap<String, String>();
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
  }

  @Test
  public void testCreate_emptyPropertyName() {
    thrown.expect(NullPointerException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
  }

  @Test
  public void testCreate_invalidPattern() {
    thrown.expect(PatternSyntaxException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("pattern", "[");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
  }

  @Test
  public void testCreate_emptyPatternSameAsNull() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("pattern", "");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    assertEquals("SkipDocumentFilter(skipMe, \\A, true, metadata or params)",
        transform.toString());
  }

  @Test
  public void testToString_skipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("skipOnMatch", "false");
    MetadataTransform transform = SkipDocumentFilter.create(config);
    assertEquals("SkipDocumentFilter(skipMe, \\A, false, metadata or params)",
        transform.toString());
  }

  @Test
  public void testToString_CorporaMetadata() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("corpora", "metadata");
    MetadataTransform transform = SkipDocumentFilter.create(config);
    assertEquals("SkipDocumentFilter(skipMe, \\A, true, metadata)",
        transform.toString());
  }

  @Test
  public void testToString_CorporaParams() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("corpora", "params");
    MetadataTransform transform = SkipDocumentFilter.create(config);
    assertEquals("SkipDocumentFilter(skipMe, \\A, true, params)",
        transform.toString());
  }

  @Test
  public void testCreate_CorporaBogus() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("corpora", "bogus");
    MetadataTransform transform = SkipDocumentFilter.create(config);
    assertEquals("SkipDocumentFilter(skipMe, \\A, true, metadata or params)",
        transform.toString());
  }

  // tests on transform behavior when pattern is blank

  @Test
  public void testTransform_SkipKeyNotFoundWhenSkipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("skipOnMatch", "false");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("found", "someValue");
    params.put(MetadataTransform.KEY_DOC_ID, "docId01");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsKeyFoundInMetadataWhenSkipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "found");
    config.put("skipOnMatch", "false");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId02");
    Metadata metadata = new Metadata();
    metadata.add("found", "someValue");
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsKeyFoundInParamsWhenSkipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "found");
    config.put("skipOnMatch", "false");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId03");
    params.put("found", "someValue");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_SkipKeyFoundInMetadataWhenSkipOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "found");
    config.put("skipOnMatch", "true");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId04");
    Metadata metadata = new Metadata();
    metadata.add("found", "someValue");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_SkipKeyFoundInParamsWhenSkipOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "found");
    config.put("skipOnMatch", "true");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId05");
    params.put("found", "someValue");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsKeyNotFoundWhenSkipOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("skipOnMatch", "true");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId06");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  // tests on transform behavior when pattern is a regex

  @Test
  public void testTransform_SkipKeyNotMatchedWhenSkipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "property");
    config.put("pattern", "match.*");
    config.put("skipOnMatch", "false");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId07");
    Metadata metadata = new Metadata();
    metadata.add("property", "lighter"); // not a match
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsKeyMatchedInMetadataWhenSkipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "property");
    config.put("pattern", "match.*");
    config.put("skipOnMatch", "false");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId08");
    Metadata metadata = new Metadata();
    metadata.add("property", "matchbox 20");
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsKeyMatchedInParamsWhenSkipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "property");
    config.put("pattern", "match.*");
    config.put("skipOnMatch", "false");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId09");
    params.put("property", "pictures of matchstick men");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_SkipKeyMatchedInMetadataWhenSkipOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "property");
    config.put("pattern", "match.*");
    config.put("skipOnMatch", "true");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId10");
    Metadata metadata = new Metadata();
    metadata.add("property", "matchmaker, matchmaker, make me a match");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_SkipKeyMatchedInParamsWhenSkipOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "property");
    config.put("pattern", "match.*");
    config.put("skipOnMatch", "true");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId11");
    params.put("property", "another match");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsKeyNotMatchedWhenSkipOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "property");
    config.put("pattern", "match.*");
    config.put("skipOnMatch", "true");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId12");
    params.put("property", "find me a find");
    Metadata metadata = new Metadata();
    metadata.add("property", "catch me a catch");
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  // tests on corpora=params (skipping Metadata)

  @Test
  public void testTransform_SkipCorporaParamsWhenSkipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("skipOnMatch", "false");
    config.put("corpora", "params");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId13");
    Metadata metadata = new Metadata();
    metadata.add("skipMe", "this value skipped");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsCorporaParamsWhenSkipOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("skipOnMatch", "true");
    config.put("corpora", "params");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId14");
    Metadata metadata = new Metadata();
    metadata.add("skipMe", "this value skipped");
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  // tests on corpora=Metadata (skipping params)

  @Test
  public void testTransform_SkipCorporaMetadataWhenSkipOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("skipOnMatch", "false");
    config.put("corpora", "metadata");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId15");
    params.put("skipMe", "this value skipped");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsCorporaMetadataWhenSkipOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("propertyName", "skipMe");
    config.put("skipOnMatch", "true");
    config.put("corpora", "metadata");
    SkipDocumentFilter transform = SkipDocumentFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    // no docId, intentionally
    params.put("skipMe", "this value skipped");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }
}
