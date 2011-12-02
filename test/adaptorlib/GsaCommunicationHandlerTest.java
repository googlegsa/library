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
  public void testBasicListen() throws Exception {
    gsa.start();
    assertTrue(adaptor.inited);
    URL url = new URL("http", "localhost", config.getServerPort(), "/");
    URLConnection conn = url.openConnection();
    thrown.expect(java.io.FileNotFoundException.class);
    conn.getContent();
  }

  @Test
  public void testCreateTransformPipeline() {
    List<Map<String, String>> config = new ArrayList<Map<String, String>>();
    {
      Map<String, String> map = new HashMap<String, String>();
      map.put("name", "testing");
      map.put("class", InstantiatableTransform.class.getName());
      config.add(map);
    }
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
    assertEquals(1, pipeline.size());
    assertEquals(InstantiatableTransform.class, pipeline.get(0).getClass());
    assertEquals("testing", pipeline.get(0).name());
  }

  @Test
  public void testCreateTransformPipelineEmpty() {
    assertNull(GsaCommunicationHandler.createTransformPipeline(
        Collections.<Map<String, String>>emptyList()));
  }

  @Test
  public void testCreateTransformPipelineNoClassSpecified() {
    List<Map<String, String>> config = new ArrayList<Map<String, String>>();
    {
      Map<String, String> map = new HashMap<String, String>();
      map.put("name", "testing");
      config.add(map);
    }
    thrown.expect(RuntimeException.class);
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
  }

  @Test
  public void testCreateTransformPipelineMissingClass() {
    List<Map<String, String>> config = new ArrayList<Map<String, String>>();
    {
      Map<String, String> map = new HashMap<String, String>();
      map.put("name", "testing");
      map.put("class", "adaptorlib.NotARealClass");
      config.add(map);
    }
    thrown.expect(RuntimeException.class);
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
  }

  @Test
  public void testCreateTransformPipelineNoGoodConstructor() throws Exception {
    List<Map<String, String>> config = new ArrayList<Map<String, String>>();
    {
      Map<String, String> map = new HashMap<String, String>();
      map.put("name", "testing");
      map.put("class", WrongConstructorTransform.class.getName());
      config.add(map);
    }
    thrown.expect(RuntimeException.class);
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
  }

  @Test
  public void testCreateTransformPipelineConstructorFails() throws Exception {
    List<Map<String, String>> config = new ArrayList<Map<String, String>>();
    {
      Map<String, String> map = new HashMap<String, String>();
      map.put("name", "testing");
      map.put("class", CantInstantiateTransform.class.getName());
      config.add(map);
    }
    thrown.expect(RuntimeException.class);
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
  }

  @Test
  public void testCreateTransformPipelineWrongType() throws Exception {
    List<Map<String, String>> config = new ArrayList<Map<String, String>>();
    {
      Map<String, String> map = new HashMap<String, String>();
      map.put("name", "testing");
      map.put("class", WrongTypeTransform.class.getName());
      config.add(map);
    }
    thrown.expect(RuntimeException.class);
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
  }

  private static class NullAdaptor extends AbstractAdaptor {
    private boolean inited;

    @Override
    public void init(AdaptorContext context) {
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

  static class InstantiatableTransform extends DocumentTransform {
    public InstantiatableTransform(Map<String, String> config) {
      super("Test");
    }
  }

  static class WrongConstructorTransform extends DocumentTransform {
    public WrongConstructorTransform() {
      super("Test");
    }
  }

  static class CantInstantiateTransform extends DocumentTransform {
    public CantInstantiateTransform(Map<String, String> config) {
      super("Test");
      throw new RuntimeException("This always seems to happen");
    }
  }

  static class WrongTypeTransform {
    public WrongTypeTransform(Map<String, String> config) {}
  }
}
