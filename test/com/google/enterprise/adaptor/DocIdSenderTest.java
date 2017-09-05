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

import static com.google.enterprise.adaptor.DocIdPusher.EVERYTHING_CASE_SENSITIVE;
import static com.google.enterprise.adaptor.DocIdPusher.FeedType.FULL;
import static com.google.enterprise.adaptor.DocIdPusher.FeedType.INCREMENTAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.enterprise.adaptor.Journal.CompletionStatus;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test cases for {@link DocIdSender}.
 */
public class DocIdSenderTest {
  private MockGsaFeedFileMaker fileMaker = new MockGsaFeedFileMaker();
  private MockGsaFeedFileSender fileSender = new MockGsaFeedFileSender();
  private MockFeedArchiver fileArchiver = new MockFeedArchiver();
  private Journal journal = new Journal(new MockTimeProvider());
  private Config config = new Config();
  private DocIdsMockAdaptor adaptor = new DocIdsMockAdaptor();
  private DocIdSender docIdSender = new DocIdSender(fileMaker, fileSender, 
      fileArchiver, journal, config, adaptor);
  private ExceptionHandler runtimeExceptionHandler
      = new RuntimeExceptionExceptionHandler();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    config.setValue("gsa.hostname", "localhost");
    config.setValue("gsa.version", "7.2.0-8");
  }

  @Test
  public void testPushDocIdsFromAdaptorNormal() throws Exception {
    adaptor.pushItems = new ArrayList<List<DocIdPusher.Record>>();
    DocIdPusher.Record[] records = new DocIdPusher.Record[6];
    for (int i = 0; i < records.length; i++) {
      DocId id = new DocId("test" + i);
      records[i] = new DocIdPusher.Record.Builder(id).build();
    }
    List<DocIdPusher.Record> infos = new ArrayList<DocIdPusher.Record>();
    infos.add(records[0]);
    infos.add(records[1]);
    infos.add(records[2]);
    infos.add(records[3]);
    infos.add(records[4]);
    adaptor.pushItems.add(infos);
    infos = new ArrayList<DocIdPusher.Record>();
    infos.add(records[5]);
    adaptor.pushItems.add(infos);
    config.setValue("feed.maxUrls", "2");
    config.setValue("feed.name", "testing");

    docIdSender.pushFullDocIdsFromAdaptor(runtimeExceptionHandler);
    assertEquals(4, fileMaker.i);
    assertEquals(Arrays.asList(new String[] {
      "testing", "testing", "testing", "testing",
    }), fileMaker.names);
    assertEquals(Arrays.asList(new List[] {
      Arrays.asList(new DocIdPusher.Record[] {records[0], records[1]}),
      Arrays.asList(new DocIdPusher.Record[] {records[2], records[3]}),
      Arrays.asList(new DocIdPusher.Record[] {records[4]}),
      Arrays.asList(new DocIdPusher.Record[] {records[5]}),
    }), fileMaker.recordses);
    assertEquals(Arrays.asList(new String[] {
      "null", "null", "null", "null", "null", "null",
    }), fileMaker.metadatases);

    assertEquals(Arrays.asList(new String[] {
      "testing", "testing", "testing", "testing",
    }), fileSender.datasources);
    assertEquals(Arrays.asList(new String[] {
      "0", "1", "2", "3",
    }), fileSender.xmlStrings);
    assertEquals(Arrays.asList(new String[] {
      "0", "1", "2", "3",
    }), fileArchiver.feeds);
    assertTrue(fileArchiver.failedFeeds.isEmpty());
  }

  @Test
  public void testPushDocIdsFromAdaptorWithMetadata() throws Exception {
    adaptor.pushItems = new ArrayList<List<DocIdPusher.Record>>();
    DocIdPusher.Record[] records = new DocIdPusher.Record[6];
    for (int i = 0; i < records.length; i++) {
      DocId id = new DocId("test" + i);
      Metadata metadata = new Metadata();
      if (i < 5) { // have empty (but not null) metadata on the last record
        metadata.add("id", "test" + i);
        metadata.add("foo", "bar" + (i * 2));
      }
      records[i] = new DocIdPusher.Record.Builder(id).setMetadata(metadata)
          .build();
    }
    List<DocIdPusher.Record> infos = new ArrayList<DocIdPusher.Record>();
    infos.add(records[0]);
    infos.add(records[1]);
    infos.add(records[2]);
    infos.add(records[3]);
    infos.add(records[4]);
    adaptor.pushItems.add(infos);
    infos = new ArrayList<DocIdPusher.Record>();
    infos.add(records[5]);
    adaptor.pushItems.add(infos);
    config.setValue("feed.maxUrls", "2");
    config.setValue("feed.name", "testing");

    docIdSender.pushFullDocIdsFromAdaptor(runtimeExceptionHandler);
    assertEquals(4, fileMaker.i);
    assertEquals(Arrays.asList(new String[] {
      "testing", "testing", "testing", "testing",
    }), fileMaker.names);
    assertEquals(Arrays.asList(new List[] {
      Arrays.asList(new DocIdPusher.Record[] {records[0], records[1]}),
      Arrays.asList(new DocIdPusher.Record[] {records[2], records[3]}),
      Arrays.asList(new DocIdPusher.Record[] {records[4]}),
      Arrays.asList(new DocIdPusher.Record[] {records[5]}),
    }), fileMaker.recordses);
    assertEquals(Arrays.asList(new String[] {
      "[foo=bar0, id=test0]", "[foo=bar2, id=test1]", "[foo=bar4, id=test2]",
      "[foo=bar6, id=test3]", "[foo=bar8, id=test4]", "[]",
    }), fileMaker.metadatases);

    assertEquals(Arrays.asList(new String[] {
      "testing", "testing", "testing", "testing",
    }), fileSender.datasources);
    assertEquals(Arrays.asList(new String[] {
      "0", "1", "2", "3",
    }), fileSender.xmlStrings);
    assertEquals(Arrays.asList(new String[] {
      "0", "1", "2", "3",
    }), fileArchiver.feeds);
    assertTrue(fileArchiver.failedFeeds.isEmpty());
  }

  @Test
  public void testPushDocIdsNoHandler() throws Exception {
    // Don't send anything.
    adaptor.pushItems = new ArrayList<List<DocIdPusher.Record>>();
    thrown.expect(NullPointerException.class);
    docIdSender.pushFullDocIdsFromAdaptor(null);
  }
  
  @Test
  public void testPushDocIdsIterrupted() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocIds(DocIdPusher pusher) throws InterruptedException {
        throw new InterruptedException();
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);
    thrown.expect(InterruptedException.class);
    docIdSender.pushFullDocIdsFromAdaptor(runtimeExceptionHandler);
  }

  @Test
  public void testPushDocIdsFailureByError() throws Exception {
    class InternalErrorInTest extends Error {}
    MockAdaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocIds(DocIdPusher pusher) throws InterruptedException {
        throw new InternalErrorInTest();
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);

    assertEquals(CompletionStatus.SUCCESS, journal.getLastFullPushStatus());
    thrown.expect(InternalErrorInTest.class);
    try {
      docIdSender.pushFullDocIdsFromAdaptor(runtimeExceptionHandler);
    } finally {
      assertEquals(CompletionStatus.FAILURE, journal.getLastFullPushStatus());
    }
  }

  @Test
  public void testPushDocIdsFailure() throws Exception {
    class FailureAdaptor extends MockAdaptor {
      private int times;

      @Override
      public void getDocIds(DocIdPusher pusher) throws IOException {
        times++;
        throw new IOException();
      }
    }

    class TryTwiceExceptionHandler implements ExceptionHandler {
      @Override
      public boolean handleException(Exception ex, int ntries) {
        return ntries < 2;
      }
    }

    FailureAdaptor adaptor = new FailureAdaptor();
    ExceptionHandler errorHandler = new TryTwiceExceptionHandler();
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);
    docIdSender.pushFullDocIdsFromAdaptor(errorHandler);
    assertEquals(2, adaptor.times);
  }

  @Test
  public void testPushSizedBatchFailed() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String datasource,
                                     String xmlString, boolean useCompression)
          throws IOException {
        throw new IOException();
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);
    List<DocId> ids = Arrays.asList(new DocId[] {new DocId("test")});
    NeverRetryExceptionHandler errorHandler = new NeverRetryExceptionHandler();

    docIdSender.pushDocIds(ids, errorHandler);
    assertEquals(1, errorHandler.failed);
    assertTrue(fileArchiver.feeds.isEmpty());
  }

  public void testPushSizedBatchRetrying() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String datasource,
                                     String xmlString, boolean useCompression)
          throws IOException {
        throw new IOException();
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);
    List<DocId> ids = Arrays.asList(new DocId[] {new DocId("test")});
    NeverRetryExceptionHandler errorHandler = new NeverRetryExceptionHandler() {
      @Override
      public boolean handleException(Exception ex, int ntries) {
        super.handleException(ex, ntries);
        return ntries < 2;
      }
    };

    docIdSender.pushDocIds(ids, errorHandler);
    assertEquals(2, errorHandler.failed);
    assertTrue(fileArchiver.feeds.isEmpty());
  }

  @Test
  public void testPushInterruptedFirstBatch() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String datasource,
                                     String xmlString, boolean useCompression)
          throws IOException {
        throw new IOException();
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);
    List<DocId> ids = Arrays.asList(new DocId("test"), new DocId("test2"));

    Thread.currentThread().interrupt();
    thrown.expect(InterruptedException.class);
    try {
      docIdSender.pushDocIds(ids);
    } finally {
      assertTrue(fileArchiver.feeds.isEmpty());
    }
  }

  @Test
  public void testPushInterruptedLaterBatch() throws Exception {
    final AtomicLong batchCount = new AtomicLong();
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String datasource,
                                     String xmlString, boolean useCompression)
          throws IOException {
        long count = batchCount.incrementAndGet();
        if (count >= 2) {
          throw new IOException();
        }
      }
    };
    config.setValue("feed.maxUrls", "1");
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);
    List<DocId> ids = Arrays.asList(new DocId("test"), new DocId("test2"));

    Thread.currentThread().interrupt();
    assertEquals(new DocId("test2"), docIdSender.pushDocIds(ids));
    assertTrue(Thread.currentThread().isInterrupted());
  }

  @Test
  public void testPushIncrementalDocIdsFailureByError() throws Exception {
    class InternalErrorInTest extends Error {};
    PollingIncrementalLister listener = new PollingIncrementalLister() {
      @Override
      public void getModifiedDocIds(DocIdPusher pusher) throws IOException, InterruptedException {
        throw new InternalErrorInTest();
      }
    };

    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);

    assertEquals(CompletionStatus.SUCCESS, journal.getLastIncrementalPushStatus());
    thrown.expect(InternalErrorInTest.class);
    try {
      docIdSender.pushIncrementalDocIdsFromAdaptor(listener, runtimeExceptionHandler);
    } finally {
      assertEquals(CompletionStatus.FAILURE, journal.getLastIncrementalPushStatus());
    }
  }

  private static Map<GroupPrincipal, Collection<Principal>> sampleGroups() {
    return ImmutableMap.<GroupPrincipal, Collection<Principal>>of(
        new GroupPrincipal("g1"), ImmutableList.<Principal>of(
            new UserPrincipal("u1"), new GroupPrincipal("g2")),
        new GroupPrincipal("g2"), ImmutableList.<Principal>of(
            new UserPrincipal("u2"), new GroupPrincipal("g3")),
        new GroupPrincipal("g3"), ImmutableList.<Principal>of(
            new UserPrincipal("u3"), new GroupPrincipal("g4")));
  }

  private static Map<GroupPrincipal, Collection<Principal>> emptyGroups() {
    return ImmutableMap.<GroupPrincipal, Collection<Principal>>of();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsNormal() throws Exception {
    config.setValue("feed.maxUrls", "2");
    config.setValue("feed.name", "foo");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(), false, null));

    assertEquals(2, fileMaker.i);
    assertEquals(expectedResult(2, sampleGroups()), fileMaker.groupses);
    assertEquals(ImmutableList.of("incremental", "incremental"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo", "foo"), fileSender.groupsources);
    assertEquals(ImmutableList.of("0", "1"), fileSender.xmlStrings);
    assertEquals(ImmutableList.of("0", "1"), fileArchiver.feeds);
    assertTrue(fileArchiver.failedFeeds.isEmpty());
    assertEquals(CompletionStatus.SUCCESS, journal.getLastGroupPushStatus());
  }

  @Test
  public void testPushGroupsAllDocsPublic() throws Exception {
    config.setValue("adaptor.markAllDocsAsPublic", "true");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(), false, null));

    assertEquals(0, fileMaker.i);
    assertTrue(fileMaker.groupses.isEmpty());
    assertTrue(fileArchiver.failedFeeds.isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsReplaceAllGroupsBeforeVersion74() throws Exception {
    config.setValue("feed.maxUrls", "2");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(2, fileMaker.i);
    assertEquals(expectedResult(2, sampleGroups()), fileMaker.groupses);
    assertEquals(ImmutableList.of("incremental", "incremental"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo", "foo"), fileSender.groupsources);
    assertEquals(ImmutableList.of("0", "1"), fileSender.xmlStrings);
    assertEquals(ImmutableList.of("0", "1"), fileArchiver.feeds);
    assertTrue(fileArchiver.failedFeeds.isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsReplaceAllGroupsAtVersion740() throws Exception {
    config.setValue("feed.maxUrls", "2");
    config.setValue("gsa.version", "7.4.0-1");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(3, fileMaker.i);
    assertEquals(expectedResult(2, sampleGroups(), emptyGroups()),
        fileMaker.groupses);
    assertEquals(ImmutableList.of("incremental", "incremental", "full"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo-FULL1", "foo"),
        fileSender.groupsources);
    assertEquals(ImmutableList.of("0", "1", "2"), fileSender.xmlStrings);
    assertEquals(ImmutableList.of("0", "1", "2"), fileArchiver.feeds);
    assertTrue(fileArchiver.failedFeeds.isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsReplaceAllGroupsSingleFeed() throws Exception {
    config.setValue("gsa.version", "7.4.0-1");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(2, fileMaker.i);
    assertEquals(
        expectedResult(Integer.MAX_VALUE, sampleGroups(), emptyGroups()),
        fileMaker.groupses);
    assertEquals(ImmutableList.of("incremental", "full"), fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo"), fileSender.groupsources);
    assertEquals(ImmutableList.of("0", "1"), fileSender.xmlStrings);
    assertEquals(ImmutableList.of("0", "1"), fileArchiver.feeds);
    assertTrue(fileArchiver.failedFeeds.isEmpty());
    assertEquals(CompletionStatus.SUCCESS, journal.getLastGroupPushStatus());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsReplaceAllGroupsAlternateBuffer() throws Exception {
    config.setValue("gsa.version", "7.4.0-1");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(2, fileMaker.i);
    assertEquals(
        expectedResult(Integer.MAX_VALUE, sampleGroups(), emptyGroups()),
        fileMaker.groupses);
    assertEquals(ImmutableList.of("incremental", "full"), fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo"), fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(4, fileMaker.i);
    assertEquals(expectedResult(Integer.MAX_VALUE,
        sampleGroups(), emptyGroups(), sampleGroups(), emptyGroups()),
        fileMaker.groupses);
    assertEquals(ImmutableList.of("incremental", "full", "incremental", "full"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo", "foo-FULL2", "foo-FULL1"),
        fileSender.groupsources);
    assertTrue(fileArchiver.failedFeeds.isEmpty());
    assertEquals(CompletionStatus.SUCCESS, journal.getLastGroupPushStatus());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsIncrementalThenFullThenFull() throws Exception {
    config.setValue("gsa.version", "7.4.0-1");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, INCREMENTAL, "foo", null));

    assertEquals(ImmutableList.of("incremental"), fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo"), fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(ImmutableList.of("incremental", "incremental", "full"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo", "foo-FULL1", "foo"),
        fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(
        ImmutableList.of("incremental", "incremental", "full", "incremental",
                         "full"),
        fileSender.feedtypes);
    assertEquals(
        ImmutableList.of("foo", "foo-FULL1", "foo", "foo-FULL2", "foo-FULL1"),
        fileSender.groupsources);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsFullThenIncrementalThenIncremental()
      throws Exception {
    config.setValue("gsa.version", "7.4.0-1");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(ImmutableList.of("incremental", "full"), fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo"), fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, INCREMENTAL, "foo", null));

    assertEquals(ImmutableList.of("incremental", "full", "incremental"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo", "foo-FULL1"),
        fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, INCREMENTAL, "foo", null));

    assertEquals(
        ImmutableList.of("incremental", "full", "incremental", "incremental"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo", "foo-FULL1", "foo-FULL1"),
        fileSender.groupsources);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsFullThenIncrementalThenFullThenIncremental()
      throws Exception {
    config.setValue("gsa.version", "7.4.0-1");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(ImmutableList.of("incremental", "full"), fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo"), fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, INCREMENTAL, "foo", null));

    assertEquals(ImmutableList.of("incremental", "full", "incremental"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo", "foo-FULL1"),
        fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(
        ImmutableList.of("incremental", "full", "incremental", "incremental",
                         "full"),
        fileSender.feedtypes);
    assertEquals(
        ImmutableList.of("foo-FULL1", "foo", "foo-FULL1", "foo-FULL2",
                         "foo-FULL1"),
        fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, INCREMENTAL, "foo", null));

    assertEquals(
        ImmutableList.of("incremental", "full", "incremental", "incremental",
                         "full", "incremental"),
        fileSender.feedtypes);
    assertEquals(
        ImmutableList.of("foo-FULL1", "foo", "foo-FULL1", "foo-FULL2",
                         "foo-FULL1", "foo-FULL2"),
        fileSender.groupsources);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPushGroupsFromMultipleGroupSources() throws Exception {
    config.setValue("gsa.version", "7.4.0-1");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "foo", null));

    assertEquals(ImmutableList.of("incremental", "full"), fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo"), fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, "bar", null));

    assertEquals(ImmutableList.of("incremental", "full", "incremental", "full"),
        fileSender.feedtypes);
    assertEquals(ImmutableList.of("foo-FULL1", "foo", "bar-FULL1", "bar"),
        fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, INCREMENTAL, "foo", null));

    assertEquals(
        ImmutableList.of("incremental", "full", "incremental", "full",
                         "incremental"),
        fileSender.feedtypes);
    assertEquals(
        ImmutableList.of("foo-FULL1", "foo", "bar-FULL1", "bar", "foo-FULL1"),
        fileSender.groupsources);

    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, INCREMENTAL, "bar", null));

    assertEquals(
        ImmutableList.of("incremental", "full", "incremental", "full",
                         "incremental", "incremental"),
        fileSender.feedtypes);
    assertEquals(
        ImmutableList.of("foo-FULL1", "foo", "bar-FULL1", "bar", "foo-FULL1",
                         "bar-FULL1"),
        fileSender.groupsources);
  }

  @Test
  public void testPushGroupsNoGroupSource() throws Exception {
    config.setValue("feed.name", "default_source");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, FULL, null, null));

    assertEquals(Collections.singletonList("default_source"),
        fileSender.groupsources);
  }

  @Test
  public void testPushGroupsGroupSource() throws Exception {
    config.setValue("feed.name", "default_source");
    assertNull(docIdSender.pushGroupDefinitions(sampleGroups(),
        EVERYTHING_CASE_SENSITIVE, INCREMENTAL, "group_source", null));

    assertEquals(Collections.singletonList("group_source"),
        fileSender.groupsources);
  }

  @Test
  public void testPushGroupsFailure() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendGroups(String feedSource, String feedType,
          String xmlString, boolean useCompression) throws IOException {
        throw new IOException();
      }
    };

    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
        config, adaptor);

    try {
      docIdSender.pushGroupDefinitions(sampleGroups(),
          EVERYTHING_CASE_SENSITIVE, new NeverRetryExceptionHandler());
    } finally {
      assertEquals(CompletionStatus.FAILURE, journal.getLastGroupPushStatus());
    }

    assertEquals(ImmutableList.of("0"), fileArchiver.failedFeeds);
    assertTrue(fileArchiver.feeds.isEmpty());
  }

  @Test
  public void testPushGroups_threadSafe() throws Throwable {
    final int threadCount = 5;
    final int iterations = 10;

    Thread[] threads = new Thread[threadCount];
    final Throwable[] errors = new Throwable[threadCount];

    // Start the threads.
    for (int t = 0; t < threadCount; t++) {
      // Collect errors in the threads to throw them from the test
      // thread or the test won't fail.
      final int tt = t;
      threads[t] = new Thread() {
          @Override public void run() {
            try {
              for (int i = 0; i < iterations; i++) {
                docIdSender.pushGroupDefinitions(sampleGroups(),
                    EVERYTHING_CASE_SENSITIVE, INCREMENTAL,
                    "source" + tt, new NeverRetryExceptionHandler());
              }
            } catch (Throwable e) {
              errors[tt] = e;
            }
          }
        };
      threads[t].start();
    }

    // Wait for each thread and check for errors.
    StringBuilder builder = new StringBuilder();
    for (int t = 0; t < threadCount; t++) {
      threads[t].join();
      if (errors[t] != null) {
        builder.append(errors[t]).append("\n");
      }
    }
    assertEquals(builder.toString(), 0, builder.length());
    assertTrue(fileArchiver.failedFeeds.isEmpty());
    assertEquals(threadCount * iterations, fileArchiver.feeds.size());
    assertEquals(threadCount * iterations, fileSender.groupsources.size());
  }

  /**
   * Returns the group definitions broken into batches of a batchSize entries.
   */
  // TODO(bmj): Uncomment @SafeVarargs when moving to Java 7 and remove the
  // @SuppressWarnings("unchecked") usages below.
  // @SafeVarargs
  private static List<List<Map.Entry<GroupPrincipal, Collection<Principal>>>>
      expectedResult(int batchSize,
          Map<GroupPrincipal, Collection<Principal>>... groupMaps) {
    ImmutableList.Builder<List<Map.Entry<GroupPrincipal, Collection<Principal>>>>
        result = ImmutableList.builder();
    for (Map<GroupPrincipal, Collection<Principal>> groups : groupMaps) {
      ImmutableList.Builder<Map.Entry<GroupPrincipal, Collection<Principal>>>
          batch = ImmutableList.builder();
      int i = 0;
      for (Map.Entry<GroupPrincipal, Collection<Principal>> entry
               : groups.entrySet()) {
        if (++i > batchSize) {
          result.add(batch.build());
          batch = ImmutableList.builder();
          i = 1;
        }
        batch.add(
            new SimpleImmutableEntry<GroupPrincipal, Collection<Principal>>(
                entry));
      }
      result.add(batch.build());
    }
    return result.build();
  }

  private Map.Entry<GroupPrincipal, Collection<Principal>> mapEntry(
      String groupName, String... members) {
    ImmutableList.Builder<Principal> builder = ImmutableList.builder();
    for (String member : members) {
      builder.add(new UserPrincipal(member));
    }
    return new SimpleImmutableEntry<GroupPrincipal, Collection<Principal>>(
        new GroupPrincipal(groupName), builder.build());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExpectedResultEmptyGroups() {
    assertEquals(ImmutableList.<List>of(ImmutableList.<Map.Entry>of()),
                 expectedResult(2, emptyGroups()));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExpectedResultSingleBatch() {
    assertEquals(
        ImmutableList.<List>of(
            ImmutableList.<Map.Entry>of(
                mapEntry("g1", "u1"),
                mapEntry("g2", "u2"),
                mapEntry("g3", "u3"))),
        expectedResult(Integer.MAX_VALUE,
            ImmutableMap.<GroupPrincipal, Collection<Principal>>of(
                new GroupPrincipal("g1"), ImmutableList.<Principal>of(
                    new UserPrincipal("u1")),
                new GroupPrincipal("g2"), ImmutableList.<Principal>of(
                    new UserPrincipal("u2")),
                new GroupPrincipal("g3"), ImmutableList.<Principal>of(
                    new UserPrincipal("u3")))));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExpectedResultExactBatch() {
    assertEquals(
        ImmutableList.<List>of(
            ImmutableList.<Map.Entry>of(
                mapEntry("g1", "u1"),
                mapEntry("g2", "u2"))),
        expectedResult(2,
            ImmutableMap.<GroupPrincipal, Collection<Principal>>of(
                new GroupPrincipal("g1"), ImmutableList.<Principal>of(
                    new UserPrincipal("u1")),
                new GroupPrincipal("g2"), ImmutableList.<Principal>of(
                    new UserPrincipal("u2")))));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExpectedResultMultipleBatchs() {
    assertEquals(
        ImmutableList.<List>of(
            ImmutableList.<Map.Entry>of(
                mapEntry("g1", "u1"),
                mapEntry("g2", "u2")),
            ImmutableList.<Map.Entry>of(
                mapEntry("g3", "u3"))),
        expectedResult(2,
            ImmutableMap.<GroupPrincipal, Collection<Principal>>of(
                new GroupPrincipal("g1"), ImmutableList.<Principal>of(
                    new UserPrincipal("u1")),
                new GroupPrincipal("g2"), ImmutableList.<Principal>of(
                    new UserPrincipal("u2")),
                new GroupPrincipal("g3"), ImmutableList.<Principal>of(
                    new UserPrincipal("u3")))));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExpectedResultMultipleGroupMaps() {
    assertEquals(
        ImmutableList.<List>of(
            ImmutableList.<Map.Entry>of(
                mapEntry("g1", "u1"),
                mapEntry("g2", "u2")),
            ImmutableList.<Map.Entry>of(
                mapEntry("g3", "u3")),
            ImmutableList.<Map.Entry>of(
                mapEntry("g4", "u4"),
                mapEntry("g5", "u5"))),
        expectedResult(2,
            ImmutableMap.<GroupPrincipal, Collection<Principal>>of(
                new GroupPrincipal("g1"), ImmutableList.<Principal>of(
                    new UserPrincipal("u1")),
                new GroupPrincipal("g2"), ImmutableList.<Principal>of(
                    new UserPrincipal("u2")),
                new GroupPrincipal("g3"), ImmutableList.<Principal>of(
                    new UserPrincipal("u3"))),
            ImmutableMap.<GroupPrincipal, Collection<Principal>>of(
                new GroupPrincipal("g4"), ImmutableList.<Principal>of(
                    new UserPrincipal("u4")),
                new GroupPrincipal("g5"), ImmutableList.<Principal>of(
                    new UserPrincipal("u5")))));
  }

  @Test
  public void testNamedResources() throws Exception {
    config.setValue("feed.name", "testing");
    assertNull(docIdSender.pushNamedResources(
        Collections.singletonMap(new DocId("test"), Acl.EMPTY),
        new NeverRetryExceptionHandler()));
    assertEquals(1, fileMaker.i);
    assertEquals(Collections.singletonList(Collections.singletonList(
        new DocIdSender.AclItem(new DocId("test"), Acl.EMPTY)
    )), fileMaker.recordses);
    assertEquals(Arrays.asList(new String[] {
      "0"
    }), fileSender.xmlStrings);
    assertEquals(Arrays.asList(new String[] {
      "0"
    }), fileArchiver.feeds);
    assertTrue(fileMaker.groupses.isEmpty());
    assertTrue(fileArchiver.failedFeeds.isEmpty());
  }

  @Test
  public void testNamedResourcesAllDocsPublic() throws Exception {
    config.setValue("adaptor.markAllDocsAsPublic", "true");
    config.setValue("feed.name", "testing");
    assertNull(docIdSender.pushNamedResources(
        Collections.singletonMap(new DocId("test"), Acl.EMPTY),
        new NeverRetryExceptionHandler()));
    assertEquals(0, fileMaker.i);
    assertTrue(fileMaker.recordses.isEmpty());
    assertTrue(fileMaker.groupses.isEmpty());
    assertTrue(fileSender.xmlStrings.isEmpty());
    assertTrue(fileArchiver.feeds.isEmpty());
    assertTrue(fileArchiver.failedFeeds.isEmpty());
  }

  @Test
  public void testNamedResourcesFailed() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String datasource,
                                     String xmlString, boolean useCompression)
          throws IOException {
        throw new IOException();
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
                                  config, adaptor);

    Map<DocId, Acl> resources = new TreeMap<DocId, Acl>();
    resources.put(new DocId("aaa"), Acl.EMPTY);
    resources.put(new DocId("bbb"), Acl.EMPTY);
    assertEquals(new DocId("aaa"), docIdSender.pushNamedResources(resources,
        new NeverRetryExceptionHandler()));
  }

  @Test
  public void testNullNamedResource() throws Exception {
    thrown.expect(NullPointerException.class);
    docIdSender.pushNamedResources(
        Collections.singletonMap(new DocId("test"), (Acl) null));
  }

  @Test
  public void testNullNamedResourceAcl() throws Exception {
    thrown.expect(NullPointerException.class);
    docIdSender.pushNamedResources(
        Collections.singletonMap((DocId) null, Acl.EMPTY));
  }

  @Test
  public void testAclItemToString() {
    DocId id = new DocId("foxtrot");
    Acl acl = Acl.EMPTY;
    String golden = "AclItem(" + id + ",null," + acl + ")";
    assertEquals(golden, "" + new DocIdSender.AclItem(id, acl));
  }

  private static class MockGsaFeedFileMaker extends GsaFeedFileMaker {
    List<String> names = new ArrayList<String>();
    List<List<? extends DocIdSender.Item>> recordses
        = new ArrayList<List<? extends DocIdSender.Item>>();
    // Don't use generics because of limitations in Java
    List<Object> groupses = new ArrayList<Object>();
    List<String> metadatases = new ArrayList<String>();
    int i;

    public MockGsaFeedFileMaker() {
      super(null, new AclTransform(Arrays.<AclTransform.Rule>asList()));
    }

    @Override
    public String makeMetadataAndUrlXml(String name,
        List<? extends DocIdSender.Item> items) {
      names.add(name);
      recordses.add(items);
      for (DocIdSender.Item item : items) {
        if (item instanceof DocIdPusher.Record) {
          DocIdPusher.Record r = (DocIdPusher.Record) item;
          metadatases.add("" + r.getMetadata());
        }
      }
      return "" + i++;
    }

    @Override
    public <T extends Collection<Principal>> String makeGroupDefinitionsXml(
        Collection<Map.Entry<GroupPrincipal, T>> items,
        boolean caseSensitiveMembers) {
      groupses.add(new ArrayList<Map.Entry<GroupPrincipal, T>>(items));
      return "" + i++;
    }
  }

  private static class MockGsaFeedFileSender extends GsaFeedFileSender {
    List<String> datasources = new ArrayList<String>();
    List<String> groupsources = new ArrayList<String>();
    List<String> feedtypes = new ArrayList<String>();
    List<String> xmlStrings = new ArrayList<String>();

    public MockGsaFeedFileSender() {
      super("localhost", /*secure=*/ false, Charset.forName("UTF-8"));
    }

    @Override
    public void sendMetadataAndUrl(String datasource,
                                   String xmlString, boolean useCompression)
        throws IOException {
      datasources.add(datasource);
      xmlStrings.add(xmlString);
    }

    @Override
    public void sendGroups(String groupsource, String feedtype,
        String xmlString, boolean useCompression) throws IOException {
      groupsources.add(groupsource);
      feedtypes.add(feedtype);
      xmlStrings.add(xmlString);
    }
  }

  private static class MockFeedArchiver implements FeedArchiver {
    List<String> feeds = new ArrayList<String>();
    List<String> failedFeeds = new ArrayList<String>();

    @Override
    public void saveFeed(String feedName, String feedXml) {
      feeds.add(feedXml);
    }

    @Override
    public void saveFailedFeed(String feedName, String feedXml) {
      failedFeeds.add(feedXml);
    }
  }

  private static class RuntimeExceptionExceptionHandler
      implements ExceptionHandler {
    @Override
    public boolean handleException(Exception ex, int ntries) {
      throw new TriggeredException(ex);
    }

    public static class TriggeredException extends RuntimeException {
      public TriggeredException(Throwable cause) {
        super(cause);
      }
    }
  }

  private static class NeverRetryExceptionHandler implements ExceptionHandler {
    private int failed;

    @Override
    public boolean handleException(Exception ex, int ntries) {
      failed++;
      return false;
    }
  }

  private static class DocIdsMockAdaptor extends MockAdaptor {
    public List<List<DocIdPusher.Record>> pushItems;
    public int timesGetDocIdsCalled;

    /**
     * Throws a {@link NullPointerException} unless {@link #pushItems} has been
     * set.
     */
    @Override
    public void getDocIds(DocIdPusher pusher) throws InterruptedException,
        IOException {
      timesGetDocIdsCalled++;
      for (List<DocIdPusher.Record> infos : pushItems) {
        pusher.pushRecords(infos);
      }
    }
  }
}
