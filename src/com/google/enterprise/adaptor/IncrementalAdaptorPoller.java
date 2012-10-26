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

import java.util.*;
import java.util.logging.*;

/**
 * Adaptor polling agent for incremental updates.
 */
class IncrementalAdaptorPoller {
  private static final Logger log
      = Logger.getLogger(IncrementalAdaptorPoller.class.getName());

  private Timer timer = new Timer("incrementalPolling", true);
  private PollingIncrementalAdaptor adaptor;
  private DocIdPusher pusher;
  private PollTimerTask task;

  public IncrementalAdaptorPoller(PollingIncrementalAdaptor adaptor,
                                  DocIdPusher pusher) {
    if (adaptor == null || pusher == null) {
      throw new NullPointerException();
    }
    this.adaptor = adaptor;
    this.pusher = pusher;
  }

  public void start(long periodMillis) {
    if (task != null) {
      throw new IllegalStateException("Already started");
    }
    task = new PollTimerTask();
    timer.schedule(task, 0, periodMillis);
  }

  /**
   * Cancels future polling and blocks until any currently running poll
   * completes.
   */
  public void cancel() {
    // Prevent further invocations.
    timer.cancel();
    // Interrupt and wait for current invocation to complete.
    Thread thread = task.thread;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private class PollTimerTask extends TimerTask {
    private volatile Thread thread;

    @Override
    public void run() {
      thread = Thread.currentThread();
      try {
        adaptor.getModifiedDocIds(pusher);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        // We need to catch all exceptions, because if the exception is thrown
        // to Timer, then Timer's thread gets killed.
        log.log(Level.WARNING, "Exception during incremental polling", ex);
      } finally {
        thread = null;
      }
    }
  }
}
