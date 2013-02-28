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

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

/** Tests for {@link Application}. */
public class ApplicationTest {
  private Config config;
  private NullAdaptor adaptor = new NullAdaptor();
  private Application app;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    config = new Config();
    config.setValue("gsa.hostname", "localhost");
    // Let the OS choose the port
    config.setValue("server.port", "0");
    config.setValue("server.dashboardPort", "0");
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
    if (duration > 200 * nanosInAMilli) {
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
    public void getDocContent(Request req, Response resp) throws IOException {
      throw new UnsupportedOperationException();
    }
  }
}
