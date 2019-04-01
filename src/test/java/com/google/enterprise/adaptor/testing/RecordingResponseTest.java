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

package com.google.enterprise.adaptor.testing;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Tests for {@link RecordingResponse}.
 */
public class RecordingResponseTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testAnchors_empty() {
    RecordingResponse response = new RecordingResponse();
    RecordingResponse.AnchorMap anchors = response.getAnchors();
    assertEquals(emptyList(), anchors.entries());
    assertEquals(emptyList(), anchors.keyList());
    assertEquals(emptySet(), anchors.keySet());
    assertEquals(emptyList(), anchors.get("foo"));
  }

  @Test
  public void testAnchors_nullUri() {
    thrown.expect(NullPointerException.class);
    new RecordingResponse().addAnchor(null, "hello");
  }

  @Test
  public void testAnchors_nullText() {
    RecordingResponse response = new RecordingResponse();
    response.addAnchor(URI.create("http://localhost/"), null);
    RecordingResponse.AnchorMap anchors = response.getAnchors();
    assertEquals(
        singletonList(
            new SimpleImmutableEntry<String, URI>(null,
                URI.create("http://localhost/"))),
        anchors.entries());
    assertEquals(singletonList((String) null), anchors.keyList());
    assertEquals(singleton((String) null), anchors.keySet());
    assertEquals(singletonList(URI.create("http://localhost/")),
        anchors.get(null));
    assertEquals(emptyList(), anchors.get("foo"));
  }

  @Test
  public void testAnchors_duplicates() {
    RecordingResponse response = new RecordingResponse();
    response.addAnchor(URI.create("http://localhost/a"), "a");
    response.addAnchor(URI.create("http://localhost/b"), null);
    response.addAnchor(URI.create("http://localhost/c"), "a");
    response.addAnchor(URI.create("http://localhost/b"), "d");
    RecordingResponse.AnchorMap anchors = response.getAnchors();
    assertEquals(
        ImmutableList.of(
            new SimpleImmutableEntry<String, URI>("a",
                URI.create("http://localhost/a")),
            new SimpleImmutableEntry<String, URI>(null,
                URI.create("http://localhost/b")),
            new SimpleImmutableEntry<String, URI>("a",
                URI.create("http://localhost/c")),
            new SimpleImmutableEntry<String, URI>("d",
                URI.create("http://localhost/b"))),
        anchors.entries());
    assertEquals(asList("a", null, "a", "d"), anchors.keyList());
    assertEquals(
        new HashSet<String>(asList("d", "a", null)), // Out of order as a test.
        anchors.keySet());
    // The keySet() should be ordered the same as the keyList() or entries().
    assertEquals(
        asList("a", null, "d"),
        new ArrayList<String>(anchors.keySet()));
    assertEquals(
        asList(
            URI.create("http://localhost/a"),
            URI.create("http://localhost/c")),
        anchors.get("a"));
    assertEquals(
        asList(URI.create("http://localhost/b")),
        anchors.get(null));
  }
}
