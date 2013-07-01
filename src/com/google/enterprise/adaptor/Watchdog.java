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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Calls Thread.interrupt() when a thread takes too long to complete a task.
 * You must ensure that a processingCompleted() is executed on the same thread
 * after each processingStarted(); using try-finally is highly encouraged:
 *
 * <code>
 *   watchdog.processingStarted();
 *   try {
 *     doWork();
 *   } finally {
 *     watchdog.processingCompleted();
 *   }
 * </code>
 */
class Watchdog {
  private final ScheduledExecutorService executor;
  private final ThreadLocal<FutureInfo> inProcess
      = new ThreadLocal<FutureInfo>();

  /**
   * @param executor executor to schedule tasks
   */
  public Watchdog(ScheduledExecutorService executor) {
    if (executor == null) {
      throw new NullPointerException();
    }
    this.executor = executor;
  }

  /**
   * @param timeout maximum allowed duration in milliseconds
   */
  public void processingStarting(long timeout) {
    if (inProcess.get() != null) {
      throw new IllegalStateException("Processing is already occuring on the "
          + "thread");
    }
    AtomicBoolean interruptNeeded = new AtomicBoolean(true);
    Runnable task = new Interrupter(Thread.currentThread(), interruptNeeded);
    Future<?> future = executor.schedule(task, timeout, TimeUnit.MILLISECONDS);
    inProcess.set(new FutureInfo(future, interruptNeeded));
  }

  public void processingCompleted() {
    FutureInfo info = inProcess.get();
    if (info == null) {
      throw new IllegalStateException("No processing was started on the "
          + "thread");
    }
    inProcess.remove();
    // Prevent Interrupter from running if it hasn't started already. It may
    // still be running after this call and Future doesn't tell us if it is
    // currently running.
    info.future.cancel(false);
    synchronized (info.interruptNeeded) {
      if (info.interruptNeeded.get()) {
        // Interrupter hasn't interrupted this thread.
        // Prevent the Interrupter from interrupting this thread in the future.
        info.interruptNeeded.set(false);
      } else {
        // Interrupter has interrupted this thread.
        // Clear the interrupt, if not already cleared, since we don't want to
        // interrupt this thread any further.
        Thread.currentThread().interrupted();
      }
    }
  }

  private static class Interrupter implements Runnable {
    private final Thread thread;
    /**
     * Denotes the interrupter has responsibility to interrupt the thread. It
     * must be cleared after the thread has been interrupted.
     */
    private AtomicBoolean interruptNeeded;

    public Interrupter(Thread thread, AtomicBoolean interruptNeeded) {
      this.thread = thread;
      this.interruptNeeded = interruptNeeded;
    }

    public void run() {
      // Must synchronize to prevent processingCompleted() from attempting to
      // clear the interrupt before interrupt() is called here.
      synchronized (interruptNeeded) {
        if (interruptNeeded.get()) {
          thread.interrupt();
          interruptNeeded.set(false);
        }
      }
    }
  }

  private static class FutureInfo {
    public final Future<?> future;
    /**
     * Denotes that the interrupter has responibility to interrupt the thread.
     */
    public final AtomicBoolean interruptNeeded;

    public FutureInfo(Future<?> future, AtomicBoolean interruptNeeded) {
      this.future = future;
      this.interruptNeeded = interruptNeeded;
    }
  }
}
