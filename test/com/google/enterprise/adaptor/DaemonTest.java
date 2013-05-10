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

import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;

/** Tests for {@link Daemon}. */
public class DaemonTest {
  private String[] arguments = new String[] {
      SingleDocAdaptor.class.getName(), "-Dgsa.hostname=localhost",
      "-Dserver.port=0", "-Dserver.dashboardPort=0"};

  @Rule
  public ExpectedException thrown = ExpectedException.none();

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
  public void testBasicListen() throws Exception {
    DaemonContext context = new Context(arguments, null);
    Daemon daemon = new Daemon();
    SingleDocAdaptor adaptor = null;
    daemon.init(context);
    try {
      daemon.start();
      try {
        Adaptor tmpAdaptor = daemon.getApplication()
            .getGsaCommunicationHandler().getAdaptor();
        adaptor = (SingleDocAdaptor) tmpAdaptor;
        assertTrue(adaptor.inited);
        int port = daemon.getApplication().getConfig().getServerPort();
        URL url = new URL("http", "localhost", port, "/doc/");
        URLConnection conn = url.openConnection();
        InputStream is = conn.getInputStream();
        try {
          String out
              = IOHelper.readInputStreamToString(is, Charset.forName("UTF-8"));
          assertEquals("success", out);
        } finally {
          is.close();
        }
      } finally {
        daemon.stop();
      }
    } finally {
      daemon.destroy();
    }
    assertFalse(adaptor.inited);
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
  public static class SingleDocAdaptor extends AbstractAdaptor {
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
    public void getDocIds(DocIdPusher pusher) {
      throw new UnsupportedOperationException();
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
}
