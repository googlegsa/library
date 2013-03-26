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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Ensures that at most one thread is executing a particular runnable. The first
 * thread that issues {@code run()} will cause the execution of {@code
 * runnable}, but further simultaneous calls to {@code run()} will execute
 * {@code alreadyRunningRunnable} and return immediately.
 *
 * <p>This class is thread-safe.
 */
class OneAtATimeRunnable implements Runnable {
  /**
   * We use an atomic reference to a thread to manage the life-cycle of this
   * object. Whenever the value is "null" it means we are not currently running
   * and a new thread can be started. Whenever we are currently executing we
   * store reference to the thread that is executing our {@link #runnable}.
   *
   * Life-cycle:
   *  - {@link #run} is called by an external thread; we check if
   *  {@link #runningThread} is "null": if true, it means that {@link #runnable}
   *  is not running, we start a new thread of execution and store reference to
   *  the new thread of execution, otherwise we run code in
   *  {@link #alreadyRunningRunnable} to signify that one instance is already
   *  running.
   */
  private AtomicReference<Thread> runningThread = new AtomicReference<Thread>();
  private Runnable runnable;
  private Runnable alreadyRunningRunnable;

  /**
   * @param runnable {@code Runnable} to call at most once at a time
   * @param alreadyRunningRunnable {@code Runnable} to call if {@link #run} was
   *   called and {@code runnable} is already running
   */
  public OneAtATimeRunnable(Runnable runnable,
                            Runnable alreadyRunningRunnable) {
    if (runnable == null || alreadyRunningRunnable == null) {
      throw new NullPointerException();
    }
    this.runnable = runnable;
    this.alreadyRunningRunnable = alreadyRunningRunnable;
  }

  @Override
  public void run() {
    Thread thisThread = Thread.currentThread();
    boolean success = runningThread.compareAndSet(null, thisThread);
    if (!success) {
      alreadyRunningRunnable.run();
      return;
    }
    try {
      runnable.run();
    } finally {
      runningThread.compareAndSet(thisThread, null);
    }
  }

  /** {@code true} if the runnable is currently running. */
  public boolean isRunning() {
    return runningThread.get() != null;
  }

  public Runnable getRunnable() {
    return runnable;
  }

  public Runnable getAlreadyRunningRunnable() {
    return alreadyRunningRunnable;
  }
}
