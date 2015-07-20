// Copyright 2013 Google Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;

/** Tests for {@link Daemon}. */
public class DaemonTest {

  private static final String GUARANTEED_CONFIGEMPTY_FILEPATH;
  static {
    try {
      File f = File.createTempFile("adaptor-test-config", ".props");
      f.deleteOnExit();
      GUARANTEED_CONFIGEMPTY_FILEPATH = f.getAbsolutePath();
    } catch (IOException ioe) {
      throw new RuntimeException("failed to make temp file");
    }
  }

  private String[] arguments =
      makeArguments(SingleDocAdaptor.class.getName(), 0);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private String[] makeArguments(String className, int serverPort) {
    return new String[] { className, "-Dgsa.hostname=localhost",
      "-Dserver.port=" + serverPort, "-Dserver.dashboardPort=0",
      "-Dgsa.version=7.2.0-0",
      "-Dadaptor.configfile=" + GUARANTEED_CONFIGEMPTY_FILEPATH };
  }

  @Test
  public void testNoArguments() throws Exception {
    DaemonContext context = new Context(new String[0], null);
    Daemon daemon = new Daemon();
    thrown.expect(IllegalArgumentException.class);
    daemon.init(context);
  }

  @Test
  public void testDoubleInit() throws Exception {
    DaemonContext context = new Context(arguments, null);
    Daemon daemon = new Daemon();
    daemon.init(context);
    try {
      thrown.expect(IllegalStateException.class);
      daemon.init(context);
    } finally {
      daemon.destroy();
    }
  }

  @Test
  public void testAnyTimeDestroy() {
    Daemon daemon = new Daemon();
    // Destroy without init should still work.
    daemon.destroy();
  }

  @Test
  public void testStopAfterInit() throws Exception {
    DaemonContext context = new Context(arguments, null);
    Daemon daemon = new Daemon();
    daemon.init(context);
    try {
      daemon.stop();
    } finally {
      daemon.destroy();
    }
  }

  @Test
  public void testApplicationInitFailure() throws Exception {
    // Invalid server.port should force Application.daemonInit() to fail.
    String[] args =
        makeArguments(MockAdaptor.class.getName(), Integer.MAX_VALUE);
    DaemonContext context = new Context(args, null);
    Daemon daemon = new Daemon();
    thrown.expect(IllegalArgumentException.class);
    daemon.init(context);
  }

  @Test
  public void testApplicationStartFailure() throws Exception {
    String[] args = makeArguments(BrokenInitAdaptor.class.getName(), 0);
    Daemon daemon = new Daemon();        
    Controller controller = new Controller(daemon);
    DaemonContext context = new Context(args, controller);
    daemon.init(context);
    assertNotNull(daemon.getApplication());
    daemon.start();
    assertNotNull(controller.thrownException);
    assertTrue(controller.thrownException instanceof StartupException);
    assertNull(daemon.getApplication());
  }

  @Test
  public void testApplicationRetryStartFailure() throws Exception {
    String[] args = makeArguments(RetryInitAdaptor.class.getName(), 0);
    Daemon daemon = new Daemon();        
    Controller controller = new Controller(daemon);
    DaemonContext context = new Context(args, controller);
    daemon.init(context);
    assertNotNull(daemon.getApplication());
    try {
      daemon.start();
      assertNull(controller.thrownException);
      assertNotNull(daemon.getApplication());
    } finally {
      daemon.stop();
      daemon.destroy();
    }
  }

  private URL getContentUrl(Daemon daemon) throws MalformedURLException {
    int port = daemon.getApplication().getConfig().getServerPort();
    return new URL("http", "localhost", port, "/doc/");
  }

  private String getDocContent(URL contentUrl) throws IOException {
    URLConnection conn = contentUrl.openConnection();
    InputStream is = conn.getInputStream();
    try {
      return IOHelper.readInputStreamToString(is, Charset.forName("UTF-8"));
    } finally {
      is.close();
    }
  }

  @Test
  public void testBasicListen() throws Exception {
    Daemon daemon = new Daemon();
    Controller controller = new Controller(daemon);
    DaemonContext context = new Context(arguments, controller);
    SingleDocAdaptor adaptor = null;
    URL contentUrl;
    daemon.init(context);
    try {
      daemon.start();
      assertNull(controller.thrownException);
      try {
        Adaptor tmpAdaptor = daemon.getApplication()
            .getGsaCommunicationHandler().getAdaptor();
        adaptor = (SingleDocAdaptor) tmpAdaptor;
        assertTrue(adaptor.inited);
        contentUrl = getContentUrl(daemon);
        assertEquals("success", getDocContent(contentUrl));
      } finally {
        daemon.stop();
      }
    } finally {
      daemon.destroy();
    }
    assertFalse(adaptor.inited);
    assertNull(controller.thrownException);
    // Service should be shut down.
    try {
      getDocContent(contentUrl);      
      fail("Expected a ConnectException, but got none.");
    } catch (ConnectException expected) {
      // expected;
    }      
  }

  @Test
  public void testStaticServiceStartTwice() throws Exception {
    Daemon.serviceStart(arguments);
    Daemon daemon = Daemon.getServiceDaemon();
    try {
      thrown.expect(IllegalStateException.class);
      Daemon.serviceStart(arguments);    
    } finally {
      assertSame(daemon, Daemon.getServiceDaemon());
      Daemon.serviceStop(new String[0]);
    }
  }

  @Test
  public void testStaticServiceStartFatalInitFailure() throws Exception {
    // Invalid server.port should force Application.daemonInit() to fail.
    // Unrecoverable startup error should shutdown the service.
    String[] args =
        makeArguments(MockAdaptor.class.getName(), Integer.MAX_VALUE);
    Daemon.serviceStart(args);
    assertNull(Daemon.getServiceDaemon());
  }

  // TODO(bmj): Use logging capture to verify the correct exception is logged.
  @Test
  public void testStaticServiceStartFatalStartFailure() throws Exception {
    // Unrecoverable startup error should shutdown the service.
    String[] args = makeArguments(BrokenInitAdaptor.class.getName(), 0);
    Daemon.serviceStart(args);
    assertNull(Daemon.getServiceDaemon());
  }

  @Test
  public void testStaticServiceStartRetryStartFailure() throws Exception {
    // Possibly recoverable startup error should not shutdown the service,
    // even if content is not served.
    String[] args = makeArguments(RetryInitAdaptor.class.getName(), 0);
    Daemon.serviceStart(args);
    Daemon daemon = Daemon.getServiceDaemon();
    try {
      assertNotNull(daemon);
      assertNotNull(daemon.getApplication());
      thrown.expect(IOException.class);
      getDocContent(getContentUrl(daemon));
    } finally {
      Daemon.serviceStop(new String[0]);
    }
  }

  @Test
  public void testStaticServiceStartAndStop() throws Exception {
    Daemon.serviceStart(arguments);
    Daemon daemon = Daemon.getServiceDaemon();
    assertNotNull(daemon);
    assertNotNull(daemon.getApplication());

    SingleDocAdaptor adaptor = (SingleDocAdaptor) 
        daemon.getApplication().getGsaCommunicationHandler().getAdaptor();
    assertTrue(adaptor.inited);
    assertEquals("success", getDocContent(getContentUrl(daemon)));

    Daemon.serviceStop(new String[0]);
    assertFalse(adaptor.inited);
    assertNull(daemon.getApplication());
    assertNull(Daemon.getServiceDaemon());
  }

  private static class Controller implements DaemonController {
    private final Daemon daemon;
    Exception thrownException;

    public Controller(Daemon daemon) {
      this.daemon = daemon;
    }

    @Override
    public void fail(String message, Exception exception) {
      fail(exception);
    }
      
    @Override public void fail(Exception exception) {
      thrownException = exception;
      shutdown();
    }

    @Override
    public void shutdown() {
      try {
        daemon.stop();
      } catch (Exception e) {
        // ignore it
      } finally {
        daemon.destroy();
      }
    }

    @Override public void fail() {
      throw new UnsupportedOperationException();
    }

    @Override public void fail(String message) {
      throw new UnsupportedOperationException();
    }

    @Override public void reload() {
      throw new UnsupportedOperationException();
    }
  }

  private static class Context implements DaemonContext {
    private final String[] args;
    private final DaemonController controller;

    public Context(String[] args, DaemonController controller) {
      this.args = args;
      this.controller = controller;
    }

    @Override
    public String[] getArguments() {
      return args;
    }

    @Override
    public DaemonController getController() {
      return controller;
    }
  }

  /**
   * Adaptor with a single document. Marked public so that it can be loaded by
   * Daemon.
   */
  public static class SingleDocAdaptor extends MockAdaptor {
    private volatile boolean inited;

    @Override
    public void init(AdaptorContext context) {
      inited = true;
    }

    @Override
    public void destroy() {
      inited = false;
    }

    @Override
    public void getDocContent(Request req, Response resp) throws IOException {
      if ("".equals(req.getDocId().getUniqueId())) {
        resp.getOutputStream().write("success".getBytes("UTF-8"));
      } else {
        resp.respondNotFound();
      }
    }
  }

  /**
   * Adaptor whose startup failure is unrecoverable and the service should
   * be terminated.
   */
  public static class BrokenInitAdaptor extends MockAdaptor {
    @Override
    public void init(AdaptorContext context) throws Exception {
      throw new StartupException("You shall not pass!");
    }
  }
    
  /**
   * Adaptor whose startup failure is possibly recoverable so the Application
   * will retry initialization. The service should not be terminated.
   */
  public static class RetryInitAdaptor extends MockAdaptor {
    @Override
    public void init(AdaptorContext context) throws Exception {
      throw new ConnectException("Server offline");
    }
  }
}
