// Copyright 2017 Google Inc. All Rights Reserved.
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

/** Unit tests for {@link RegexDecisionFilter}. */
public class RegexDecisionFilterTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static RegexDecisionFilter defaultFilter() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "leaveMe");
    return RegexDecisionFilter.create(config);
  }

  // tests on create calls (various errors) and toString results
  @Test
  public void testToString_defaultFilter() {
    MetadataTransform transform = defaultFilter();
    assertEquals("RegexDecisionFilter(skipMe, \\A, true, as-is, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testCreate_noKey() {
    thrown.expect(NullPointerException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
  }

  @Test
  public void testCreate_emptyKey() {
    thrown.expect(NullPointerException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
  }

  @Test
  public void testCreate_invalidPattern() {
    thrown.expect(PatternSyntaxException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("pattern", "[");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
  }

  @Test
  public void testCreate_emptyPatternSameAsNull() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("pattern", "");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(skipMe, \\A, true, do-not-index, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testCreate_noDecisionDefaultsToAsIs() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "foo");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(foo, \\A, true, as-is, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testCreate_invalidDecision() {
    thrown.expect(IllegalArgumentException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "foo");
    config.put("decision", "maybe");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
  }

  @Test
  public void testCreate_DoNotIndexDecision() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "foo");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(foo, \\A, true, do-not-index, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testCreate_DoNotIndexContentDecision() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "foo");
    config.put("decision", "do-not-index-content");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(foo, \\A, true, do-not-index-content, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testCreate_AsIsDecision() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "foo");
    config.put("decision", "as-is");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(foo, \\A, true, as-is, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testToString_decideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("decideOnMatch", "false");
    config.put("decision", "do-not-index");
    MetadataTransform transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(skipMe, \\A, false, do-not-index, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testToString_CorporaMetadata() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("corpora", "metadata");
    config.put("decision", "do-not-index");
    MetadataTransform transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(skipMe, \\A, true, do-not-index, "
        + "metadata)", transform.toString());
  }

  @Test
  public void testToString_CorporaParams() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("corpora", "params");
    config.put("decision", "do-not-index");
    MetadataTransform transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(skipMe, \\A, true, do-not-index, params)",
        transform.toString());
  }

  @Test
  public void testCreate_CorporaBogus() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("corpora", "bogus");
    config.put("decision", "do-not-index");
    MetadataTransform transform = RegexDecisionFilter.create(config);
    assertEquals("RegexDecisionFilter(skipMe, \\A, true, do-not-index, "
        + "metadata or params)", transform.toString());
  }

  // tests on transform behavior when pattern is blank

  @Test
  public void testTransform_SkipKeyNotFoundWhenDecideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("decideOnMatch", "false");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("found", "someValue");
    params.put(MetadataTransform.KEY_DOC_ID, "docId01");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_KeyFoundInMetadataWhenDecideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "found");
    config.put("decideOnMatch", "false");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId02");
    Metadata metadata = new Metadata();
    metadata.add("found", "someValue");
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_KeyFoundInParamsWhenDecideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "found");
    config.put("decideOnMatch", "false");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId03");
    params.put("found", "someValue");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_SkipKeyFoundInMetadataWhenDecideOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "found");
    config.put("decideOnMatch", "true");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId04");
    Metadata metadata = new Metadata();
    metadata.add("found", "someValue");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_SkipKeyFoundInParamsWhenDecideOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "found");
    config.put("decideOnMatch", "true");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId05");
    params.put("found", "someValue");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_KeyNotFoundWhenDecideOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("decideOnMatch", "true");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId06");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  // tests on transform behavior when pattern is a regex

  @Test
  public void testTransform_SkipKeyNotMatchedWhenDecideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "property");
    config.put("pattern", "match.*");
    config.put("decideOnMatch", "false");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId07");
    Metadata metadata = new Metadata();
    metadata.add("property", "lighter"); // not a match
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_KeyMatchedInMetadataWhenDecideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "property");
    config.put("pattern", "match.*");
    config.put("decideOnMatch", "false");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId08");
    Metadata metadata = new Metadata();
    metadata.add("property", "matchbox 20");
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_KeyMatchedInParamsWhenDecideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "property");
    config.put("pattern", "match.*");
    config.put("decideOnMatch", "false");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId09");
    params.put("property", "pictures of matchstick men");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_SkipKeyMatchedInMetadataWhenDecideOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "property");
    config.put("pattern", "match.*");
    config.put("decideOnMatch", "true");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId10");
    Metadata metadata = new Metadata();
    metadata.add("property", "matchmaker, matchmaker, make me a match");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_SkipKeyMatchedInParamsWhenDecideOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "property");
    config.put("pattern", "match.*");
    config.put("decideOnMatch", "true");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId11");
    params.put("property", "another match");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_KeyNotMatchedWhenDecideOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "property");
    config.put("pattern", "match.*");
    config.put("decideOnMatch", "true");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId12");
    params.put("property", "find me a find");
    Metadata metadata = new Metadata();
    metadata.add("property", "catch me a catch");
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  // tests on corpora=params (skipping Metadata)

  @Test
  public void testTransform_SkipCorporaParamsWhenDecideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("decideOnMatch", "false");
    config.put("corpora", "params");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId13");
    Metadata metadata = new Metadata();
    metadata.add("skipMe", "this value skipped");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_CorporaParamsWhenDecideOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("decideOnMatch", "true");
    config.put("corpora", "params");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId14");
    Metadata metadata = new Metadata();
    metadata.add("skipMe", "this value skipped");
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  // tests on corpora=Metadata (skipping params)

  @Test
  public void testTransform_SkipCorporaMetadataWhenDecideOnMatchFalse() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("decideOnMatch", "false");
    config.put("corpora", "metadata");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put(MetadataTransform.KEY_DOC_ID, "docId15");
    params.put("skipMe", "this value skipped");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_CorporaMetadataWhenDecideOnMatchTrue() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "skipMe");
    config.put("decideOnMatch", "true");
    config.put("corpora", "metadata");
    config.put("decision", "do-not-index");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    // no docId, intentionally
    params.put("skipMe", "this value skipped");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  // tests other decisions

  @Test
  public void testTransform_DoNotIndexContentDecision() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "property");
    config.put("decideOnMatch", "true");
    config.put("decision", "do-not-index-content");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    Metadata metadata = new Metadata();
    metadata.add("property", "someValue");
    transform.transform(metadata, params);
    assertEquals("do-not-index-content", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AsIsDecision() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "property");
    config.put("decideOnMatch", "true");
    config.put("decision", "as-is");
    RegexDecisionFilter transform = RegexDecisionFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("Transmission-Decision", "do-not-index");
    Metadata metadata = new Metadata();
    metadata.add("property", "someValue");
    transform.transform(metadata, params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }
}
