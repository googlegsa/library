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

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test cases for {@link DocIdSender}.
 */
public class DocIdSenderTest {
  private MockGsaFeedFileMaker fileMaker = new MockGsaFeedFileMaker();
  private MockGsaFeedFileSender fileSender = new MockGsaFeedFileSender();
  private Journal journal = new Journal(new MockTimeProvider());
  private Config config = new Config();
  private DocIdsMockAdaptor adaptor = new DocIdsMockAdaptor();
  private DocIdSender docIdSender
      = new DocIdSender(fileMaker, fileSender, journal, config, adaptor);
  private GetDocIdsErrorHandler runtimeExceptionHandler
      = new RuntimeExceptionGetDocIdsErrorHandler();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    config.setValue("gsa.hostname", "localhost");
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
      "localhost", "localhost", "localhost", "localhost",
    }), fileSender.hosts);
    assertEquals(Arrays.asList(new String[] {
      "testing", "testing", "testing", "testing",
    }), fileSender.datasources);
    assertEquals(Arrays.asList(new String[] {
      "0", "1", "2", "3",
    }), fileSender.xmlStrings);
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
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);
    thrown.expect(InterruptedException.class);
    docIdSender.pushFullDocIdsFromAdaptor(runtimeExceptionHandler);
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

    class TryTwiceGetDocIdsErrorHandler implements GetDocIdsErrorHandler {
      @Override
      public boolean handleFailedToGetDocIds(Exception ex, int ntries) {
        return ntries < 2;
      }
    }

    FailureAdaptor adaptor = new FailureAdaptor();
    GetDocIdsErrorHandler errorHandler = new TryTwiceGetDocIdsErrorHandler();
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);
    docIdSender.pushFullDocIdsFromAdaptor(errorHandler);
    assertEquals(2, adaptor.times);
  }

  @Test
  public void testPushSizedBatchFailedToConnect() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String host, String datasource,
                                     String xmlString, boolean useCompression)
          throws FailedToConnect {
        throw new FailedToConnect(new IOException());
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);
    List<DocId> ids = Arrays.asList(new DocId[] {new DocId("test")});
    NeverRetryPushErrorHandler errorHandler = new NeverRetryPushErrorHandler();

    docIdSender.pushDocIds(ids, errorHandler);
    assertEquals(1, errorHandler.failedToConnect);
  }

  @Test
  public void testPushSizedBatchFailedWriting() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String host, String datasource,
                                     String xmlString, boolean useCompression)
          throws FailedWriting {
        throw new FailedWriting(new IOException());
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);
    List<DocId> ids = Arrays.asList(new DocId[] {new DocId("test")});
    NeverRetryPushErrorHandler errorHandler = new NeverRetryPushErrorHandler();

    docIdSender.pushDocIds(ids, errorHandler);
    assertEquals(1, errorHandler.failedWriting);
  }

  @Test
  public void testPushSizedBatchRetrying() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String host, String datasource,
                                     String xmlString, boolean useCompression)
          throws FailedWriting {
        throw new FailedWriting(new IOException());
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);
    List<DocId> ids = Arrays.asList(new DocId[] {new DocId("test")});
    NeverRetryPushErrorHandler errorHandler = new NeverRetryPushErrorHandler() {
      @Override
      public boolean handleFailedWriting(Exception ex, int ntries) {
        super.handleFailedWriting(ex, ntries);
        return ntries < 2;
      }
    };

    docIdSender.pushDocIds(ids, errorHandler);
    assertEquals(2, errorHandler.failedWriting);
  }

  @Test
  public void testPushSizedBatchFailedReadingReply() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String host, String datasource,
                                     String xmlString, boolean useCompression)
          throws FailedReadingReply {
        throw new FailedReadingReply(new IOException());
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);
    List<DocId> ids = Arrays.asList(new DocId[] {new DocId("test")});
    NeverRetryPushErrorHandler errorHandler = new NeverRetryPushErrorHandler();

    docIdSender.pushDocIds(ids, errorHandler);
    assertEquals(1, errorHandler.failedReadingReply);
  }

  @Test
  public void testPushInterruptedFirstBatch() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String host, String datasource,
                                     String xmlString, boolean useCompression)
          throws FailedReadingReply {
        throw new FailedReadingReply(new IOException());
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);
    List<DocId> ids = Arrays.asList(new DocId("test"), new DocId("test2"));

    Thread.currentThread().interrupt();
    thrown.expect(InterruptedException.class);
    docIdSender.pushDocIds(ids);
  }

  @Test
  public void testPushInterruptedLaterBatch() throws Exception {
    final AtomicLong batchCount = new AtomicLong();
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String host, String datasource,
                                     String xmlString, boolean useCompression)
          throws FailedReadingReply {
        long count = batchCount.incrementAndGet();
        if (count >= 2) {
          throw new FailedReadingReply(new IOException());
        }
      }
    };
    config.setValue("feed.maxUrls", "1");
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);
    List<DocId> ids = Arrays.asList(new DocId("test"), new DocId("test2"));

    Thread.currentThread().interrupt();
    assertEquals(new DocId("test2"), docIdSender.pushDocIds(ids));
    assertTrue(Thread.currentThread().isInterrupted());
  }

  @Test
  public void testNamedResources() throws Exception {
    config.setValue("feed.name", "testing");
    assertNull(docIdSender.pushNamedResources(
        Collections.singletonMap(new DocId("test"), Acl.EMPTY),
        new NeverRetryPushErrorHandler()));
    assertEquals(1, fileSender.hosts.size());
  }

  @Test
  public void testNamedResourcesFailed() throws Exception {
    fileSender = new MockGsaFeedFileSender() {
      @Override
      public void sendMetadataAndUrl(String host, String datasource,
                                     String xmlString, boolean useCompression)
          throws FailedReadingReply {
        throw new FailedReadingReply(new IOException());
      }
    };
    docIdSender = new DocIdSender(fileMaker, fileSender, journal, config,
                                  adaptor);

    Map<DocId, Acl> resources = new TreeMap<DocId, Acl>();
    resources.put(new DocId("aaa"), Acl.EMPTY);
    resources.put(new DocId("bbb"), Acl.EMPTY);
    assertEquals(new DocId("aaa"), docIdSender.pushNamedResources(resources,
        new NeverRetryPushErrorHandler()));
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

  private static class MockGsaFeedFileMaker extends GsaFeedFileMaker {
    List<String> names = new ArrayList<String>();
    List<List<? extends DocIdSender.Item>> recordses
        = new ArrayList<List<? extends DocIdSender.Item>>();
    int i;

    public MockGsaFeedFileMaker() {
      super(null);
    }

    @Override
    public String makeMetadataAndUrlXml(String name,
        List<? extends DocIdSender.Item> items) {
      names.add(name);
      recordses.add(items);
      return "" + i++;
    }
  }

  private static class MockGsaFeedFileSender extends GsaFeedFileSender {
    List<String> hosts = new ArrayList<String>();
    List<String> datasources = new ArrayList<String>();
    List<String> xmlStrings = new ArrayList<String>();

    public MockGsaFeedFileSender() {
      super(new Config());
    }

    @Override
    public void sendMetadataAndUrl(String host, String datasource,
                                   String xmlString, boolean useCompression)
        throws FailedToConnect, FailedWriting, FailedReadingReply {
      hosts.add(host);
      datasources.add(datasource);
      xmlStrings.add(xmlString);
    }
  }

  private static class RuntimeExceptionGetDocIdsErrorHandler
      implements GetDocIdsErrorHandler {
    @Override
    public boolean handleFailedToGetDocIds(Exception ex, int ntries) {
      throw new TriggeredException(ex);
    }

    public static class TriggeredException extends RuntimeException {
      public TriggeredException(Throwable cause) {
        super(cause);
      }
    }
  }

  private static class NeverRetryPushErrorHandler implements PushErrorHandler {
    private int failedToConnect;
    private int failedWriting;
    private int failedReadingReply;

    @Override
    public boolean handleFailedToConnect(Exception ex, int ntries) {
      failedToConnect++;
      return false;
    }

    @Override
    public boolean handleFailedWriting(Exception ex, int ntries) {
      failedWriting++;
      return false;
    }

    @Override
    public boolean handleFailedReadingReply(Exception ex, int ntries) {
      failedReadingReply++;
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
