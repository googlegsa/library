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

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.daemon.DaemonContext;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Allows running an adaptor as a daemon when used in conjunction with procrun
 * or jsvc.
 *
 * <p>Example execution with jsvc:
 * <pre>jsvc -pidfile adaptor.pid -cp someadaptor-withlib.jar \
 *    com.google.enterprise.adaptor.Daemon package.SomeAdaptor</pre>
 */
public class Daemon implements org.apache.commons.daemon.Daemon {
  private Application app;
  private DaemonContext context;

  @Override
  public synchronized void init(DaemonContext context) throws Exception {
    if (this.context != null) {
      throw new IllegalStateException("Already initialized");
    }
    this.context = context;
    String[] args = context.getArguments();
    if (args.length < 1) {
      throw new IllegalArgumentException(
          "Missing argument: adaptor class name");
    }
    Adaptor adaptor
        = Class.forName(args[0]).asSubclass(Adaptor.class).newInstance();
    args = Arrays.copyOfRange(args, 1, args.length);

    app = Application.daemonMain(adaptor, args);
    app.daemonInit();
  }

  @Override
  public synchronized void destroy() {
    if (app != null) {
      app.daemonDestroy(5, TimeUnit.SECONDS);
    }
    context = null;
    app = null;
  }

  @Override
  public void start() throws Exception {
    final Application savedApp;
    final DaemonContext savedContext;
    final CountDownLatch latch = new CountDownLatch(1);
    // Save values so that there aren't any races with stop/destroy.
    synchronized (this) {
      savedApp = this.app;
      savedContext = this.context;
    }
    // Run in a new thread so that stop() can be called before we complete
    // starting (since starting can take a long time if the Adaptor keeps
    // throwing an exception). However, we still try to wait for start to
    // complete normally to ease testing and improve the user experience in the
    // common case of starting being quick.
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          savedApp.daemonStart();
        } catch (InterruptedException ex) {
          // We must be shutting down.
          Thread.currentThread().interrupt();
        } catch (Exception ex) {
          savedContext.getController().fail(ex);
        } finally {
          latch.countDown();
        }
      }
    }).start();
    latch.await(5, TimeUnit.SECONDS);
  }

  @Override
  public synchronized void stop() throws Exception {
    app.daemonStop(5, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  Application getApplication() {
    return app;
  }
}
