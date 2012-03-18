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
      runningThread.set(null);
    }
  }

  /**
   * Gives the opportunity to run {@code runnable} in a new thread if it is not
   * already running, but otherwise does nothing. This method will not call
   * {@code alreadyRunningRunnable}.
   *
   * @return new thread if it was successfully started, {@code null} otherwise
   */
  public Thread runInNewThread() {
    Thread newThread = new Thread(new SetBackToNullRunnable());
    boolean success = runningThread.compareAndSet(null, newThread);
    if (!success) {
      return null;
    }
    newThread.start();
    return newThread;
  }

  /**
   * Prevent later executions of {@code runnable} and interrupt the currently
   * running thread, if any. It does not wait for the thread to actually
   * complete.
   */
  public void stop() {
    // Permanently set the runningThread to non-null.
    Thread thread = runningThread.getAndSet(new Thread());
    if (thread != null) {
      thread.interrupt();
    }
  }

  public Runnable getRunnable() {
    return runnable;
  }

  public Runnable getAlreadyRunningRunnable() {
    return alreadyRunningRunnable;
  }

  /**
   * @see #runInNewThread
   */
  private class SetBackToNullRunnable implements Runnable {
    @Override
    public void run() {
      try {
        runnable.run();
      } finally {
        runningThread.set(null);
      }
    }
  }
}
