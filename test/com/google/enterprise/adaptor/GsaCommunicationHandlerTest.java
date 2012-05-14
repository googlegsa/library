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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tests for {@link GsaCommunicationHandler}.
 */
public class GsaCommunicationHandlerTest {
  /**
   * Generated with {@code keytool -alias notadaptor -keystore
   * test/com/google/enterprise/adaptor/GsaCommunicationHandlerTest.valid.jks
   * -storepass notchangeit -genkeypair -keyalg RSA -keypass notchangeit
   * -validity 7300 -dname "CN=Unknown, OU=Unknown, O=Unknown, L=Unknown,
   *  ST=Unknown, C=Unknown"}.
   */
  private static final String KEYSTORE_VALID_FILENAME
      = "test/com/google/enterprise/adaptor/GsaCommunicationHandlerTest.valid.jks";

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
    config.setValue("server.dashboardPort", "0");
    gsa = new GsaCommunicationHandler(adaptor, config);
  }

  @After
  public void teardown() {
    // No need to block.
    new Thread(new Runnable() {
      @Override
      public void run() {
        gsa.stop(0);
      }
    }).start();
  }

  @Test
  public void testAdaptorContext() throws Exception {
    class PollingIncrNullAdaptor extends NullAdaptor {
      @Override
      public void init(AdaptorContext context) {
        assertSame(config, context.getConfig());
        assertNotNull(context.getDocIdPusher());
        assertNotNull(context.getDocIdEncoder());
        assertNotNull(context.getSensitiveValueDecoder());
        GetDocIdsErrorHandler originalHandler
            = context.getGetDocIdsErrorHandler();
        GetDocIdsErrorHandler replacementHandler
            = new DefaultGetDocIdsErrorHandler();
        assertNotNull(originalHandler);
        context.setGetDocIdsErrorHandler(replacementHandler);
        assertSame(replacementHandler, context.getGetDocIdsErrorHandler());

        StatusSource source = new MockStatusSource("test",
            new MockStatus(Status.Code.NORMAL));
        context.addStatusSource(source);
        context.removeStatusSource(source);
      }
    }
    PollingIncrNullAdaptor adaptor = new PollingIncrNullAdaptor();
    gsa = new GsaCommunicationHandler(adaptor, config);
    gsa.start();
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
  public void testFailingInitAdaptor() throws Exception {
    class FailFirstAdaptor extends NullAdaptor {
      private int count = 0;
      public boolean started = false;

      @Override
      public void init(AdaptorContext context) {
        if (count == 0) {
          count++;
          throw new RuntimeException();
        }
        started = true;
      }
    }
    FailFirstAdaptor adaptor = new FailFirstAdaptor();
    gsa = new GsaCommunicationHandler(adaptor, config);
    gsa.start();
    assertTrue(adaptor.started);
  }

  @Test
  public void testBasicListen() throws Exception {
    gsa.start();
    assertTrue(adaptor.inited);
    URL url = new URL("http", "localhost", config.getServerPort(), "/");
    URLConnection conn = url.openConnection();
    try {
      thrown.expect(java.io.FileNotFoundException.class);
      conn.getContent();
    } finally {
      gsa.stop(0);
      assertFalse(adaptor.inited);
    }
  }

  @Test
  public void testBasicHttpsListen() throws Exception {
    config.setValue("server.secure", "true");
    gsa.start();
    assertTrue(adaptor.inited);
    URL url = new URL("https", "localhost", config.getServerPort(), "/");
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
      map.put("factoryMethod", getClass().getName() + ".factoryMethod");
      config.add(map);
    }
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
    assertEquals(1, pipeline.getDocumentTransforms().size());
    assertEquals(IdentityTransform.class, 
        pipeline.getDocumentTransforms().get(0).getClass());
    assertEquals("testing", pipeline.getDocumentTransforms().get(0).getName());
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
      map.put("factoryMethod", "adaptorlib.NotARealClass.fakeMethod");
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
      map.put("factoryMethod", getClass().getName() + ".wrongFactoryMethod");
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
      map.put("factoryMethod",
          getClass().getName() + ".cantInstantiateFactoryMethod");
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
      map.put("factoryMethod",
          getClass().getName() + ".wrongTypeFactoryMethod");
      config.add(map);
    }
    thrown.expect(ClassCastException.class);
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
  }

  @Test
  public void testCreateTransformPipelineNoMethod() throws Exception {
    List<Map<String, String>> config = new ArrayList<Map<String, String>>();
    {
      Map<String, String> map = new HashMap<String, String>();
      map.put("name", "testing");
      map.put("factoryMethod", "noFactoryMethodToBeFound");
      config.add(map);
    }
    thrown.expect(RuntimeException.class);
    TransformPipeline pipeline
        = GsaCommunicationHandler.createTransformPipeline(config);
  }

  @Test
  public void testKeyStore() throws Exception {
    assertNotNull(GsaCommunicationHandler.getKeyPair("notadaptor",
        KEYSTORE_VALID_FILENAME, "JKS", "notchangeit"));
  }

  @Test
  public void testKeyStoreInvalidType() throws Exception {
    thrown.expect(RuntimeException.class);
    GsaCommunicationHandler.getKeyPair("notadaptor", KEYSTORE_VALID_FILENAME,
        "WRONG", "notchangeit");
  }

  @Test
  public void testKeyStoreMissing() throws Exception {
    thrown.expect(java.io.FileNotFoundException.class);
    GsaCommunicationHandler.getKeyPair("notadaptor", "notarealfile.jks", "JKS",
        "notchangeit");
  }

  @Test
  public void testKeyStoreNoAlias() throws Exception {
    thrown.expect(RuntimeException.class);
    GsaCommunicationHandler.getKeyPair("notherealalias",
        KEYSTORE_VALID_FILENAME, "JKS", "notchangeit");
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

  static class IdentityTransform extends AbstractDocumentTransform {
    @Override
    public void transform(ByteArrayOutputStream contentIn,
                          OutputStream contentOut,
                          Metadata metadata,
                          Map<String, String> params) throws IOException {
      contentIn.writeTo(contentOut);
    }
  }

  public static IdentityTransform factoryMethod(Map<String, String> config) {
    IdentityTransform transform = new IdentityTransform();
    transform.configure(config);
    return transform;
  }

  public static IdentityTransform wrongFactoryMethod() {
    return factoryMethod(Collections.<String, String>emptyMap());
  }

  public static IdentityTransform cantInstantiateFactoryMethod(
      Map<String, String> config) {
    throw new RuntimeException("This always seems to happen");
  }

  public static Object wrongTypeFactoryMethod(Map<String, String> config) {
    return new Object();
  }
}
