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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link CommandStreamParser}.
 */
public class CommandStreamParserTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testReadDocIds() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\nid=123\nid=456\n" +
        "id-list\n10\n20\n30\n\nid=789\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    ArrayList<DocIdPusher.Record> docInfoList = parser.readFromLister();
    assertEquals("123", docInfoList.get(0).getDocId().getUniqueId());
    assertEquals("456", docInfoList.get(1).getDocId().getUniqueId());
    assertEquals("10", docInfoList.get(2).getDocId().getUniqueId());
    assertEquals("20", docInfoList.get(3).getDocId().getUniqueId());
    assertEquals("30", docInfoList.get(4).getDocId().getUniqueId());
    assertEquals("789", docInfoList.get(5).getDocId().getUniqueId());
  }

  @Test
  public void testInvalidHeaderString() throws IOException {
    String source = "GSA Adaptor Data Ver 1 [\n]\nid=123\nid=456\nid-list\n10\n20\n30\n\nid=789" +
        "\nend-message";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    thrown.expect(IOException.class);
    parser.readFromLister();

  }

  @Test
  public void testInvalidVersion() throws IOException {
    String source = "GSA Adaptor Data Version 1a [\n]\nid=123\nid=456\nid-list\n10\n20\n30\n\n" +
        "id=789\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    thrown.expect(IOException.class);
    parser.readFromLister();

  }

  @Test
  public void testEmptyDelimiter() throws IOException {
    String source = "GSA Adaptor Data Version 1 []\nid=123\nid=456\nid-list\n10\n20\n30\n\nid=789" +
        "\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    thrown.expect(IOException.class);
    parser.readFromLister();

  }

  void checkDelimiter(String delimiter, boolean isValid) throws IOException {
    String source = "GSA Adaptor Data Version 1 [" + delimiter + "]" + delimiter + "id=123";
    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    if (!isValid) {
      thrown.expect(IOException.class);
    }
    ArrayList<DocIdPusher.Record> docInfoList = parser.readFromLister();

    if (isValid) {
      assertEquals(new DocId("123"), docInfoList.get(0).getDocId()); //.getUniqueId());
    }

  }

  @Test
  public void testUnsupportedDelimiterCharacters() throws IOException {
    checkDelimiter("A", false);
    checkDelimiter("K", false);
    checkDelimiter("Z", false);

    checkDelimiter("a", false);
    checkDelimiter("k", false);
    checkDelimiter("z", false);

    checkDelimiter("0", false);
    checkDelimiter("5", false);
    checkDelimiter("9", false);

    checkDelimiter(":", false);
    checkDelimiter("/", false);
    checkDelimiter("-", false);
    checkDelimiter("_", false);
    checkDelimiter(" ", false);
    checkDelimiter("=", false);
    checkDelimiter("+", false);
    checkDelimiter("[", false);
    checkDelimiter("]", false);

    checkDelimiter("<+>", false);
    checkDelimiter("/n /n", false);
  }

  @Test
  public void testSupportedDelimiterCharacters() throws IOException {
    checkDelimiter("\0", true);
    checkDelimiter("~!#$%^&*(){}", true);
    checkDelimiter("ĀÁÂḀⒶ", true);
    checkDelimiter("ტყაოსანი", true);
  }

  @Test
  public void testRetriever() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\n" +
        "id=123\n" +
        "up-to-date\n" +
        "UNKNOWN_COMMAND=abcdefghi\n" +
        "meta-name=project\nmeta-value=plexi\ncontent\n2468";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    int version = parser.getVersionNumber();
    assertEquals(1, version);

    CommandStreamParser.RetrieverInfo info = parser.readFromRetriever();
    assertEquals("123", info.getDocId().getUniqueId());
    assertTrue(info.isUpToDate());
    assertArrayEquals("2468".getBytes(), info.getContents());
    Map<String, String> metadata = info.getMetadata();
    assertEquals(1, metadata.size());
    assertEquals("plexi", metadata.get("project"));
  }

  @Test
  public void testReadContentAllBytes() throws IOException {
    String commandSource = "GSA Adaptor Data Version 1 [\n]\nid=5\ncontent\n";

    byte[] byteSource = new byte[256];

    byte value = -128;
    for (int i = 0; i <= 255; i++) {
      byteSource[i] = value++;
    }

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byteArrayOutputStream.write(commandSource.getBytes("UTF-8"));
    byteArrayOutputStream.write(byteSource);
    byte[] source = byteArrayOutputStream.toByteArray();

    InputStream inputStream = new ByteArrayInputStream(source);
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    CommandStreamParser.RetrieverInfo info = parser.readFromRetriever();
    assertArrayEquals(byteSource, info.getContents());
  }

  @Test
   public void testReadBytes() throws IOException {
     String commandSource = "GSA Adaptor Data Version 1 [\n]\nid=5\ncontent\n";

     byte[] byteSource = new byte[256];

     byte value = -128;
     for (int i = 0; i <= 255; i++) {
       byteSource[i] = value++;
     }

     ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
     byteArrayOutputStream.write(commandSource.getBytes("UTF-8"));
     byteArrayOutputStream.write(byteSource);
     byte[] source = byteArrayOutputStream.toByteArray();

     InputStream inputStream = new ByteArrayInputStream(source);
     CommandStreamParser parser = new CommandStreamParser(inputStream);

     CommandStreamParser.RetrieverInfo info = parser.readFromRetriever();
     assertArrayEquals(byteSource, info.getContents());
   }

  @Test
  public void testRepositoryUnavailable() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\nrepository-unavailable";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    thrown.expect(IOException.class);
    Map<DocId, AuthzStatus> result = parser.readFromAuthorizer();
 }

  @Test
  public void testAuthorizorNoData() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    Map<DocId, AuthzStatus> result = parser.readFromAuthorizer();
    assertEquals(0, result.size());
 }

  @Test
  public void testAuthorizorDataStartsWithDocId() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\nauthz-status=PERMIT\n" +
        "id=001\nauthz-status=DENY\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    thrown.expect(IOException.class);
    Map<DocId, AuthzStatus> result = parser.readFromAuthorizer();
 }

  @Test
  public void testAuthorizor() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\nid=001\nauthz-status=PERMIT\n" +
        "id=002\nauthz-status=DENY\nid=003\nauthz-status=INDETERMINATE";
    Map<DocId, AuthzStatus> expected = new HashMap<DocId, AuthzStatus>();

    expected.put(new DocId("001"), AuthzStatus.PERMIT);
    expected.put(new DocId("002"), AuthzStatus.DENY);
    expected.put(new DocId("003"), AuthzStatus.INDETERMINATE);

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    Map<DocId, AuthzStatus> result = parser.readFromAuthorizer();
    assertEquals(expected, result);
 }

  @Test
  public void testListerNoData() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    ArrayList<DocIdPusher.Record> result = parser.readFromLister();
    assertEquals(0, result.size());
 }

  @Test
  public void testListerDataStartsWithDocId() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\nlock\n" +
        "id=001\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    thrown.expect(IOException.class);
    ArrayList<DocIdPusher.Record> result = parser.readFromLister();
 }

  @Test
  public void testLister() throws IOException, URISyntaxException {
    String source = "GSA Adaptor Data Version 1 [\n]\n" +
        "id=001\nlock\ncrawl-once\ndelete\ncrawl-immediately\n" +
        "last-modified=1292805597\n" +
        "result-link=http://docs.google.com/myfolder/mydoc.pdf\n" +
        "lock\ndelete\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    DocIdPusher.Record expected = new DocIdPusher.Record.Builder(new DocId("001"))
        .setCrawlImmediately(true)
        .setCrawlOnce(true)
        .setDeleteFromIndex(true)
        .setLastModified(new java.util.Date(1292805597000L))
        .setResultLink(new URI("http://docs.google.com/myfolder/mydoc.pdf"))
        .setLock(true)
        .build();

    ArrayList<DocIdPusher.Record> result = parser.readFromLister();
    assertEquals(expected, result.get(0));

 }



}
