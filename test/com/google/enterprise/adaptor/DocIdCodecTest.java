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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URI;

/**
 * Test cases for {@link DocIdCodec}.
 */
public class DocIdCodecTest {
  private URI baseUri = URI.create("http://localhost/doc/");
  private DocIdCodec codec = new DocIdCodec(baseUri, false);

  @Test
  public void testPrettyUri() {
    final URI golden = URI.create("http://localhost/doc/some/docid1");
    assertEquals(golden, codec.encodeDocId(new DocId("some/docid1")));
  }

  @Test
  public void testRelativeDot() {
    String docId = ".././hi/.h/";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertFalse(uriStr.contains("/../"));
    assertFalse(uriStr.contains("/./"));
    assertTrue(uriStr.contains("/hi/.h/"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDot() {
    String docId = ".";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("...."));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleDot() {
    String docId = "..";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("....."));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testNotToBeConfusedDots() {
    String docId = "....";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("......."));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testNotToBeChanged() {
    String docId = "..safe../.h/h./..h/h..";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains(docId));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleSlash() {
    String docId = "//";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("/.../"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleSlash2() {
    String docId = "//drop/table/now";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("/.../"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleSlash3() {
    String docId = "//drop///table/now//";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("/.../.../"));
    assertTrue(uriStr.endsWith("/.../"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleSlashAfterColon() {
    String docId = "//drop///table/n:ow//";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.endsWith("/.../drop/.../.../table/n:ow/.../"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleSlashAfterColon2() {
    String docId = "//drop:///table////NOW";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("p://.../t"));
    assertFalse(uriStr.contains("////"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleSlashAfterColon3() {
    String docId = "//d:////t//://NOW://.//";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.endsWith("/.../d://.../.../t/.../://NOW://..../.../"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testIndexEscaping() {
    String docId = "index.html";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.endsWith("/_index.html"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testIndexEscaping2() {
    String docId = "index.htm";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.endsWith("/_index.htm"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testIndexEscaping3() {
    String docId = "__index.html";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.endsWith("/___index.html"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testIndexEscaping4() {
    String docId = "____index.htm";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.endsWith("/_____index.htm"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testIndexEscaping5() {
    String docId = "ooga//____index.htm";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.endsWith("ooga/.../_____index.htm"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testIndexEscaping6() {
    String docId = "index.htm/";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.endsWith("/index.htm/"));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  private void decodeAndEncode(String id) {
    URI uri = codec.encodeDocId(new DocId(id));
    assertEquals(id, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testAssortedNonDotIds() {
    decodeAndEncode("simple-id");
    decodeAndEncode("harder-id/");
    decodeAndEncode("harder-id/./");
    decodeAndEncode("");
    decodeAndEncode(" ");
    decodeAndEncode(" \n\t  ");
    decodeAndEncode("/");
    decodeAndEncode("drop/table/now");
    decodeAndEncode("/drop/table/now");
  }

  @Test
  public void testDocIdIsUrl() throws Exception {
    final String docIdStr = "https://something:12/somewhere";
    final DocId docId = new DocId(docIdStr);
    final URI uri = new URI(docIdStr);

    codec = new DocIdCodec(baseUri, true);
    assertEquals(uri, codec.encodeDocId(docId));
    assertEquals(docId, codec.decodeDocId(uri));
  }
}
