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
import org.junit.rules.ExpectedException;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Tests for {@link GsaCommunicationHandler}.
 */
public class GsaCommunicationHandlerTest {
  private Config config;
  private GsaCommunicationHandler gsa;
  private NullAdaptor adaptor = new NullAdaptor();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    config = new Config();
    config.setValue("gsa.hostname", "localhost");
    // Let the OS choose the port
    config.setValue("server.port", "0");
    gsa = new GsaCommunicationHandler(adaptor, config);
  }

  @After
  public void teardown() {
    gsa.stop(0);
    assertFalse(adaptor.inited);
  }

  @Test
  public void testRelativeDot() {
    String docId = ".././hi/.h/";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertFalse(uriStr.contains("/../"));
    assertFalse(uriStr.contains("/./"));
    assertTrue(uriStr.contains("/hi/.h/"));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDot() {
    String docId = ".";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("..."));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleDot() {
    String docId = "..";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("...."));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testNotToBeConfusedDots() {
    String docId = "...";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("....."));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testNotToBeChanged() {
    String docId = "..safe../.h/h./..h/h..";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains(docId));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  private void decodeAndEncode(String id) {
    URI uri = gsa.encodeDocId(new DocId(id));
    assertEquals(id, gsa.decodeDocId(uri).getUniqueId());
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
  public void testPollingIncrementalAdaptor() throws Exception {
    class PollingIncrNullAdaptor extends NullAdaptor
        implements PollingIncrementalAdaptor {
      public final ArrayBlockingQueue<Object> queue
          = new ArrayBlockingQueue<Object>(1);

      @Override
      public void getModifiedDocIds(DocIdPusher pusher) {
        queue.offer(new Object());
      }
    }
    PollingIncrNullAdaptor adaptor = new PollingIncrNullAdaptor();
    gsa = new GsaCommunicationHandler(adaptor, config);
    gsa.start();
    assertNotNull(adaptor.queue.poll(1, TimeUnit.SECONDS));
  }

  @Test
  public void testLogRpcMethod() {
    String golden = "Testing\n";

    GsaCommunicationHandler.CircularLogRpcMethod method
        = new GsaCommunicationHandler.CircularLogRpcMethod();
    try {
      Logger logger = Logger.getLogger("");
      Level origLevel = logger.getLevel();
      logger.setLevel(Level.FINEST);
      Logger.getLogger("").finest("Testing");
      logger.setLevel(origLevel);
      String str = (String) method.run(null);
      assertTrue(str.endsWith(golden));
    } finally {
      method.close();
    }
  }

  @Test
  public void testConfigRpcMethod() {
    Map<String, String> golden = new HashMap<String, String>();
    golden.put("gsa.characterEncoding", "UTF-8");
    golden.put("server.hostname", "localhost");

    MockConfig config = new MockConfig();
    config.setKey("gsa.characterEncoding", "UTF-8");
    config.setKey("server.hostname", "localhost");
    GsaCommunicationHandler.ConfigRpcMethod method
        = new GsaCommunicationHandler.ConfigRpcMethod(config);
    Map map = (Map) method.run(null);
    assertEquals(golden, map);
  }

  @Test
  public void testStatusRpcMethod() {
    List<Map<String, Object>> golden = new ArrayList<Map<String, Object>>();
    {
      Map<String, Object> goldenObj = new HashMap<String, Object>();
      goldenObj.put("source", "mock");
      goldenObj.put("code", "NORMAL");
      goldenObj.put("message", "fine");
      golden.add(goldenObj);
    }

    StatusMonitor monitor = new StatusMonitor();
    Status status = new Status(StatusCode.NORMAL, "fine");
    BasicStatusSource source = new BasicStatusSource("mock", status);
    monitor.addSource(source);
    GsaCommunicationHandler.StatusRpcMethod method
        = new GsaCommunicationHandler.StatusRpcMethod(monitor);
    List list = (List) method.run(null);
    assertEquals(golden, list);
  }

  @Test
  public void testBasicListen() throws Exception {
    gsa.start();
    assertTrue(adaptor.inited);
    URL url = new URL("http", "localhost", config.getServerPort(), "/");
    URLConnection conn = url.openConnection();
    thrown.expect(java.io.FileNotFoundException.class);
    conn.getContent();
  }

  @Test
  public void testLastPushStatusSource() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final AtomicReference<Journal.CompletionStatus> ref
        = new AtomicReference<Journal.CompletionStatus>();
    final Journal journal = new Journal(timeProvider) {
      @Override
      public CompletionStatus getLastPushStatus() {
        return ref.get();
      }
    };
    StatusSource source
        = new GsaCommunicationHandler.LastPushStatusSource(journal);
    assertNotNull(source.getName());
    Status status;

    ref.set(Journal.CompletionStatus.SUCCESS);
    status = source.retrieveStatus();
    assertEquals(StatusCode.NORMAL, status.getCode());

    ref.set(Journal.CompletionStatus.INTERRUPTION);
    status = source.retrieveStatus();
    assertEquals(StatusCode.WARNING, status.getCode());

    ref.set(Journal.CompletionStatus.FAILURE);
    status = source.retrieveStatus();
    assertEquals(StatusCode.ERROR, status.getCode());
  }

  @Test
  public void testRetrieverStatusSource() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final AtomicReference<Double> errorRate = new AtomicReference<Double>();
    final Journal journal = new Journal(timeProvider) {
      @Override
      public double getRetrieverErrorRate(long maxCount) {
        return errorRate.get();
      }
    };
    StatusSource source
        = new GsaCommunicationHandler.RetrieverStatusSource(journal);
    assertNotNull(source.getName());
    Status status;

    errorRate.set(0.);
    status = source.retrieveStatus();
    assertEquals(StatusCode.NORMAL, status.getCode());
    assertNotNull(status.getMessage());

    errorRate.set(
        GsaCommunicationHandler.RetrieverStatusSource.WARNING_THRESHOLD);
    status = source.retrieveStatus();
    assertEquals(StatusCode.WARNING, status.getCode());
    assertNotNull(status.getMessage());

    errorRate.set(
        GsaCommunicationHandler.RetrieverStatusSource.ERROR_THRESHOLD);
    status = source.retrieveStatus();
    assertEquals(StatusCode.ERROR, status.getCode());
    assertNotNull(status.getMessage());
  }

  @Test
  public void testGsaCrawlingStatusSource() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final AtomicBoolean gsaCrawled = new AtomicBoolean();
    final Journal journal = new Journal(timeProvider) {
      @Override
      public boolean getGsaCrawled() {
        return gsaCrawled.get();
      }
    };
    StatusSource source
        = new GsaCommunicationHandler.GsaCrawlingStatusSource(journal);
    assertNotNull(source.getName());
    Status status;

    gsaCrawled.set(true);
    status = source.retrieveStatus();
    assertEquals(StatusCode.NORMAL, status.getCode());

    gsaCrawled.set(false);
    status = source.retrieveStatus();
    assertEquals(StatusCode.WARNING, status.getCode());
  }

  private static class NullAdaptor extends AbstractAdaptor {
    private boolean inited;

    @Override
    public void init(Config config, DocIdPusher pusher) {
      inited = true;
    }

    @Override
    public void destroy() {
      inited = false;
    }

    @Override
    public void getDocIds(DocIdPusher pusher) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void getDocContent(Request req, Response resp) {
      throw new UnsupportedOperationException();
    }
  }
}
