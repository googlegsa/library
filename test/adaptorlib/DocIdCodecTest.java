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

import java.net.*;

/**
 * Test cases for {@link DocIdCodec}.
 */
public class DocIdCodecTest {
  private Config config = new Config();
  private DocIdCodec codec = new DocIdCodec(config);

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
    assertTrue(uriStr.contains("..."));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleDot() {
    String docId = "..";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("...."));
    assertEquals(docId, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testNotToBeConfusedDots() {
    String docId = "...";
    URI uri = codec.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("....."));
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

  private void decodeAndEncode(String id) {
    URI uri = codec.encodeDocId(new DocId(id));
    assertEquals(id, codec.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testAssortedNonDotIds() {
    decodeAndEncode("simple-id");
    decodeAndEncode("harder-id/");
    decodeAndEncode("harder-id/./");
    decodeAndEncode("harder-id///&?///");
    decodeAndEncode("");
    decodeAndEncode(" ");
    decodeAndEncode(" \n\t  ");
    decodeAndEncode("/");
    decodeAndEncode("//");
    decodeAndEncode("drop/table/now");
    decodeAndEncode("/drop/table/now");
    decodeAndEncode("//drop/table/now");
    decodeAndEncode("//d&op/t+b+e/n*w");
  }

  @Test
  public void testDocIdIsUrl() throws Exception {
    final String docIdStr = "https://something:12/somewhere";
    final DocId docId = new DocId(docIdStr);
    final URI uri = new URI(docIdStr);

    config.setValue("docId.isUrl", "true");
    assertEquals(uri, codec.encodeDocId(docId));
    assertEquals(docId, codec.decodeDocId(uri));
  }
}
