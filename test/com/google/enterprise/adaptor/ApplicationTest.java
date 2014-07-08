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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Tests for {@link Application}. */
public class ApplicationTest {
  private Config config;
  private NullAdaptor adaptor = new NullAdaptor();
  private MockFile configFile = new MockFile("non-existent-file");
  private Application app;
  private static final Logger log
      = Logger.getLogger(ApplicationTest.class.getName());

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    config = new ModifiedConfig();
    config.setValue("gsa.hostname", "localhost");
    // Let the OS choose the port
    config.setValue("server.port", "0");
    config.setValue("server.dashboardPort", "0");
    config.setValue("gsa.version", "7.2.0-6");
    app = new Application(adaptor, config);
  }

  @After
  public void teardown() {
    // No need to block.
    new Thread(new Runnable() {
      @Override
      public void run() {
        app.stop(0, TimeUnit.SECONDS);
      }
    }).start();
  }

  @Test
  public void testCommandLine() {
    config = new ModifiedConfig();
    configFile.setExists(false);
    Application.autoConfig(config, new String[] {"-Dgsa.hostname=notreal"},
        configFile);
    assertEquals("notreal", config.getGsaHostname());
  }

  @Test
  public void testConfigFile() {
    config = new ModifiedConfig();
    configFile.setFileContents("gsa.hostname=notreal\n");
    Application.autoConfig(config, new String[0], configFile);
    assertEquals("notreal", config.getGsaHostname());
  }

  @Test
  public void testConfigFileOverrideArgumentParse() {
    config = new ModifiedConfig();
    configFile.setFileContents("gsa.hostname=notreal\n");
    thrown.expect(InvalidConfigurationException.class);
    // provide config file name and above config isn't read; validate fails
    Application.autoConfig(config, new String[] {"-Dadaptor.configfile=NFE"},
        configFile);
  }

  private static void addContent(File f, String content) throws IOException {
    FileOutputStream fw = null;
    try {
      fw = new FileOutputStream(f);
      fw.write(content.getBytes("UTF-8"));
      fw.flush();
    } finally {
      if (null != fw) {
        fw.close();
      }
    }
  }

  @Test
  public void testConfigOverrideFileContentIsUsed() throws IOException {
    config = new Config();
    configFile.setFileContents("gsa.hostname=override-me\n");
    File override = null;
    try {
      override = File.createTempFile("adaptor-test-config", ".cfg");
      addContent(override, "gsa.hostname=flavourful\n");
      String n = override.getAbsolutePath();
      log.info("made real file to override configuration: " + n);      
      Application.autoConfig(config, new String[] {"-Dadaptor.configfile=" + n},
          configFile);
      // getting hostname from the override file passed by -D argument
      assertEquals("flavourful", config.getGsaHostname());
    } finally {
      if (null != override) {
        String n = override.getAbsolutePath();
        boolean deleted = override.delete();
        if (deleted) {
          log.info("deleted real file that overrode configuration: " + n);
        } else {
          log.info("didn't delete real file that overrode configuration: " + n);
        }
      }
    }
  }

  @Test
  public void testSystemPropertiesExtra() throws IOException {
    System.setProperty("bogus-system-property", "fugazi");
    assertEquals("fugazi", System.getProperty("bogus-system-property"));
    System.clearProperty("bogus_2nd");
    assertEquals(null, System.getProperty("bogus_2nd"));
    File extras = null;
    try {
      extras = File.createTempFile("adaptor-test-sysprops", ".props");
      // add sys properties to to file 
      addContent(extras, "bogus-system-property=bigfoot\n"
          + "bogus_2nd=55");
      String n = extras.getAbsolutePath();
      log.info("made file with extra system properties: " + n);      
      Config cfg = new Config();
      Application.autoConfig(cfg, new String[] {"-Dsys.properties.file=" + n,
          "-Dgsa.hostname=not-a-real-host"}, null);
      assertEquals("bigfoot", System.getProperty("bogus-system-property"));
      assertEquals("55", System.getProperty("bogus_2nd"));
      try {
        assertEquals(null, cfg.getValue("bogus-system-property"));
        fail("system property ended up being added to config");
      } catch (InvalidConfigurationException e) {
        // system property should not end up in config
      }
      try {
        assertEquals(null, cfg.getValue("bogus_2nd"));
        fail("system property ended up being added to config");
      } catch (InvalidConfigurationException e) {
        // system property should not end up in config
      }
    } finally {
      if (null != extras) {
        String n = extras.getAbsolutePath();
        boolean deleted = extras.delete();
        if (deleted) {
          log.warning("deleted file that added system properties: " + n);
        } else {
          log.warning("didn't delete file that added system properties: " + n);
        }
      }
      System.clearProperty("bogus-system-property");
      assertEquals(null, System.getProperty("bogus-system-property"));
      System.clearProperty("bogus_2nd");
      assertEquals(null, System.getProperty("bogus_2nd"));
    }
  }

  @Test
  public void testConfigReload() throws Exception {
    app.start();
    assertTrue(adaptor.inited);
    assertFalse(adaptor.hasBeenShutdownAtSomePoint);
    configFile.setFileContents("server.hostname=127.0.0.10\n");
    config.load(configFile);
    assertTrue(adaptor.inited);
    assertTrue(adaptor.hasBeenShutdownAtSomePoint);
  }

  @Test
  public void testConfigReloadNoRestart() throws Exception {
    app.start();
    assertTrue(adaptor.inited);
    assertFalse(adaptor.hasBeenShutdownAtSomePoint);
    configFile.setFileContents("adaptor.fullListingSchedule=1 1 1 1 1\n");
    config.load(configFile);
    assertTrue(adaptor.inited);
    assertFalse(adaptor.hasBeenShutdownAtSomePoint);
  }

  @Test
  public void testFastShutdownWhenStarting() throws Exception {
    class FailAlwaysAdaptor extends NullAdaptor {
      @Override
      public void init(AdaptorContext context) {
        throw new RuntimeException();
      }
    }
    app = new Application(new FailAlwaysAdaptor(), config);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          // Wait a bit for the handler to start.
          Thread.sleep(50);
        } catch (InterruptedException ex) {
          throw new RuntimeException(ex);
        }
        app.stop(0, TimeUnit.SECONDS);
      }
    }).start();
    long startTime = System.nanoTime();
    app.start();
    long duration = System.nanoTime() - startTime;
    final long nanosInAMilli = 1000 * 1000;
    if (duration > 1000 * nanosInAMilli) {
      fail("Starting took a long time to stop after being aborted: "
          + duration);
    }
  }

  @Test
  public void testFastShutdown() throws Exception {
    app.start();
    long startTime = System.nanoTime();
    app.stop(10, TimeUnit.SECONDS);
    long duration = System.nanoTime() - startTime;
    final long nanosInAMilli = 1000 * 1000;
    if (duration > 1000 * nanosInAMilli) {
      fail("Stopping took a long time: " + duration);
    }
  }

  @Test
  public void testBasicListen() throws Exception {
    app.start();
    assertTrue(adaptor.inited);
    URL url = new URL("http", "localhost", config.getServerPort(), "/");
    URLConnection conn = url.openConnection();
    try {
      thrown.expect(java.io.FileNotFoundException.class);
      conn.getContent();
    } finally {
      app.stop(0, TimeUnit.SECONDS);
      assertFalse(adaptor.inited);
    }
  }

  @Test
  public void testBasicHttpsListen() throws Exception {
    config.setValue("server.secure", "true");
    app.start();
    assertTrue(adaptor.inited);
    URL url = new URL("https", "localhost", config.getServerPort(), "/");
    URLConnection conn = url.openConnection();
    thrown.expect(java.io.FileNotFoundException.class);
    conn.getContent();
  }

  @Test
  public void testFailWithStartupException() throws Exception {
    class FailStartupAdaptor extends NullAdaptor {
      @Override
      public void init(AdaptorContext context) throws Exception {
        throw new StartupException("Unrecoverable error.");
      }
    }
    FailStartupAdaptor adaptor = new FailStartupAdaptor();
    app = new Application(adaptor, config);

    // Make sure StartupException bypasses the retry after wait logic.
    long startTime = System.nanoTime();
    try {
      app.start();
      fail("Expected a StartupException, but got none.");
    } catch (StartupException expected) {
      long duration = System.nanoTime() - startTime;
      final long nanosInAMilli = 1000 * 1000;
      if (duration > 1000 * nanosInAMilli) {
        fail("StartupException took a long time: " + duration);
      }
    }
  }

  @Test
  public void testFailOnceInitAdaptor() throws Exception {
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
    app = new Application(adaptor, config);
    app.start();
    assertTrue(adaptor.started);
  }

  @Test
  public void testRestart() throws Exception {
    app.start();
    assertTrue(adaptor.inited);
    app.stop(0, TimeUnit.SECONDS);
    assertFalse(adaptor.inited);
    app.start();
    assertTrue(adaptor.inited);
  }

  private static class NullAdaptor extends AbstractAdaptor {
    private boolean inited;
    private boolean hasBeenShutdownAtSomePoint;

    @Override
    public void init(AdaptorContext context) throws Exception {
      inited = true;
    }

    @Override
    public void destroy() {
      inited = false;
      hasBeenShutdownAtSomePoint = true;
    }

    @Override
    public void getDocIds(DocIdPusher pusher) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void getDocContent(Request req, Response resp) throws IOException {
      throw new UnsupportedOperationException();
    }
  }

  private static class ModifiedConfig extends Config {
    @Override
    Reader createReader(File file) throws IOException {
      return ((MockFile) file).createReader();
    }
  }
}
