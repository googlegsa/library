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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link CommandStreamParser}.
 */
public class CommandStreamParserTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testReadDocIds() throws Exception {
    String source = "GSA Adaptor Data Version 1 [\n]\nid=123\nid=456\n" +
        "id-list\n10\n20\n30\n\nid=789\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    parser.readFromLister(pusher, null);
    assertEquals("123", pusher.getDocIds().get(0).getUniqueId());
    assertEquals("456", pusher.getDocIds().get(1).getUniqueId());
    assertEquals("10",  pusher.getDocIds().get(2).getUniqueId());
    assertEquals("20",  pusher.getDocIds().get(3).getUniqueId());
    assertEquals("30",  pusher.getDocIds().get(4).getUniqueId());
    assertEquals("789", pusher.getDocIds().get(5).getUniqueId());
  }

  @Test
  public void testInvalidHeaderString() throws Exception {
    String source = "GSA Adaptor Data Ver 1 [\n]\nid=123\nid=456\nid-list\n10\n20\n30\n\nid=789" +
        "\nend-message";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();

    thrown.expect(IOException.class);
    parser.readFromLister(pusher, null);

  }

  @Test
  public void testInvalidVersion() throws Exception {
    String source = "GSA Adaptor Data Version 1a [\n]\nid=123\nid=456\nid-list\n10\n20\n30\n\n" +
        "id=789\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();

    thrown.expect(IOException.class);
    parser.readFromLister(pusher, null);

  }

  @Test
  public void testEmptyDelimiter() throws Exception {
    String source = "GSA Adaptor Data Version 1 []\nid=123\nid=456\nid-list\n10\n20\n30\n\nid=789" +
        "\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();

    thrown.expect(IOException.class);
    parser.readFromLister(pusher, null);

  }

  void checkDelimiter(String delimiter, boolean isValid) throws Exception {
    String source = "GSA Adaptor Data Version 1 [" + delimiter + "]" + delimiter + "id=123";
    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    if (!isValid) {
      thrown.expect(IOException.class);
    }
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    parser.readFromLister(pusher, null);

    if (isValid) {
      assertEquals(new DocId("123"), pusher.getDocIds().get(0)); //.getUniqueId());
    }

  }

  @Test
  public void testUnsupportedDelimiterCharacters() throws Exception {
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
  public void testSupportedDelimiterCharacters() throws Exception {
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
        "meta-name=project\nmeta-value=plexi\n" +
        "last-modified=15\n" +
        "secure=true\n" +
        "anchor-uri=http://example.com/doc\nanchor-text=It is an example\n" +
        "no-index=true\n" +
        "no-follow=true\n" +
        "no-archive=true\n" +
        "display-url=http://example.com/thisDoc\n" +
        "crawl-once=true\n" +
        "lock=true\n" +
        "content\n2468";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    WrapperAdaptor.GetContentsResponse response
        = new WrapperAdaptor.GetContentsResponse(outputStream);
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    int version = parser.getVersionNumber();
    assertEquals(1, version);

    parser.readFromRetriever(new DocId("123"), response);
    assertTrue(response.isNotModified());
    assertArrayEquals("2468".getBytes(), outputStream.toByteArray());
    Metadata metadata = response.getMetadata();
    assertEquals(1, metadata.getKeys().size());
    assertEquals(new Date(15 * 1000), response.getLastModified());
    assertEquals(true, response.isSecure());
    assertEquals(Arrays.asList(URI.create("http://example.com/doc")), response.getAnchorUris());
    assertEquals(Arrays.asList("It is an example"), response.getAnchorTexts());
    assertEquals(true, response.isNoIndex());
    assertEquals(true, response.isNoFollow());
    assertEquals(true, response.isNoArchive());
    assertEquals(URI.create("http://example.com/thisDoc"), response.getDisplayUrl());
    assertEquals(true, response.isCrawlOnce());
    assertEquals(true, response.isLock());
    assertEquals("plexi", metadata.getOneValue("project"));
  }

  @Test
  public void testRetrieverMultipleMetadataValuesSameKey() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\n" +
        "id=123\n" +
        "up-to-date\n" +
        "UNKNOWN_COMMAND=abcdefghi\n" +
        "meta-name=project\nmeta-value=plexi\n" +
        "meta-name=project\nmeta-value=klexa\ncontent\n2468";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    WrapperAdaptor.GetContentsResponse response
        = new WrapperAdaptor.GetContentsResponse(outputStream);
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    int version = parser.getVersionNumber();
    assertEquals(1, version);

    parser.readFromRetriever(new DocId("123"), response);
    assertTrue(response.isNotModified());
    assertArrayEquals("2468".getBytes(), outputStream.toByteArray());
    Metadata metadata = response.getMetadata();
    assertEquals(1, metadata.getKeys().size());
    Set<String> projectNames = new HashSet<String>();
    projectNames.add("plexi");
    projectNames.add("klexa");
    assertEquals(projectNames, metadata.getAllValues("project"));
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
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    WrapperAdaptor.GetContentsResponse response
        = new WrapperAdaptor.GetContentsResponse(outputStream);
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    parser.readFromRetriever(new DocId("5"), response);
    assertArrayEquals(byteSource, outputStream.toByteArray());
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
     ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
     WrapperAdaptor.GetContentsResponse response
         = new WrapperAdaptor.GetContentsResponse(outputStream);
     CommandStreamParser parser = new CommandStreamParser(inputStream);

     parser.readFromRetriever(new DocId("5"), response);
     assertArrayEquals(byteSource, outputStream.toByteArray());
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
  public void testListerNoData() throws Exception {
    String source = "GSA Adaptor Data Version 1 [\n]\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    parser.readFromLister(pusher, null);
    assertEquals(0, pusher.getDocIds().size());
 }

  @Test
  public void testListerDataStartsWithDocId() throws Exception {
    String source = "GSA Adaptor Data Version 1 [\n]\nlock\n" +
        "id=001\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    thrown.expect(IOException.class);
    parser.readFromLister(pusher, null);
 }

  @Test
  public void testLister() throws Exception {
    String source = "GSA Adaptor Data Version 1 [\n]\n" +
        "id=001\nlock\ncrawl-once\ndelete\ncrawl-immediately\n" +
        "last-modified=1292805597\n" +
        "result-link=http://docs.google.com/myfolder/mydoc.pdf\n" +
        "lock\ndelete\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    DocIdPusher.Record expected = new DocIdPusher.Record.Builder(new DocId("001"))
        .setCrawlImmediately(true)
        .setCrawlOnce(true)
        .setDeleteFromIndex(true)
        .setLastModified(new java.util.Date(1292805597000L))
        .setResultLink(new URI("http://docs.google.com/myfolder/mydoc.pdf"))
        .setLock(true)
        .build();

    parser.readFromLister(pusher, null);
    assertEquals(expected, pusher.getRecords().get(0));

 }

  @Test
  public void testManyRecords() throws Exception {
    final List<DocId> goldenIds;
    {
      final int idsToGenerate = 30001;
      List<DocId> ids = new ArrayList<DocId>(idsToGenerate);
      for (int i = 0; i < idsToGenerate; i++) {
        ids.add(new DocId("id " + i));
      }
      goldenIds = ids;
    }
    String source;
    {
      StringBuilder sb = new StringBuilder("GSA Adaptor Data Version 1 [\n]\n");
      for (DocId id : goldenIds) {
        sb.append("id=");
        sb.append(id.getUniqueId());
        sb.append("\n");
      }
      source = sb.toString();
    }

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();

    parser.readFromLister(pusher, null);
    assertEquals(goldenIds, pusher.getDocIds());

  }
}
