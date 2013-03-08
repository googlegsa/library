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

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Allows interrupting and waiting until threads are done processing. A single
 * instance can efficiently be used for many threads. This class does not
 * support recursive usage (a thread calling processingStarting a second time
 * without first calling processingCompleted).
 *
 * <p>Threads to be tracked should call {@link #processingStarting} and {@link
 * #processingCompleted}. There are convenience implemenations of {@link Filter}
 * and {@link Runnable} that call these methods appropriately.
 *
 * <p>Once {@link #shutdown shutdown}, instance cannot be re-used.
 */
class ShutdownWaiter {
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final NotificationFilter filter = new NotificationFilter();
  private final Set<Thread> processingThreads
      = Collections.synchronizedSet(new HashSet<Thread>());
  private volatile boolean stopped;

  /**
   * Prevents current and future threads from being processed.
   *
   * @return true if shutdown cleanly, false if threads may still be
   *     processing.
   */
  public boolean shutdown(long time, TimeUnit unit)
      throws InterruptedException {
    stopped = true;
    // Inform processing requests to shut down.
    for (Thread thread : processingThreads.toArray(new Thread[0])) {
      thread.interrupt();
    }
    // Wait for all requests to complete processing.
    if (!lock.writeLock().tryLock(time, unit)) {
      return false;
    }
    // stopped == true guarantees no future request processing and obtaining the
    // lock guarantees no current request processing.
    lock.writeLock().unlock();
    return true;
  }

  /**
   * Marks provided thread as processing. Callers of this method must ensure
   * that {@link #processingCompleted} is guaranteed to be called exactly once
   * for each call to this method, unless it throws an exception.
   *
   * <p>Expected usage pattern:
   * <pre>waiter.processingStarting(Thread.currentThread());
   *try {
   *  // Do work.
   *} finally {
   *  waiter.processingCompleted(Thread.currentThread());
   *}</pre>
   *
   * @throws ShutdownException if processing has been shutdown
   */
  public void processingStarting(Thread thread) throws ShutdownException {
    if (thread == null) {
      throw new NullPointerException();
    }
    // Locks can throw exceptions.
    lock.readLock().lock();
    try {
      processingThreads.add(thread);
    } catch (RuntimeException e) {
      lock.readLock().unlock();
      throw e;
    } catch (Error e) {
      lock.readLock().unlock();
      throw e;
    }
    if (stopped) {
      // Cleanup.
      processingCompleted(thread);
      throw new ShutdownException();
    }
  }

  /**
   * Marks provided thread as completed.
   */
  public void processingCompleted(Thread thread) {
    if (thread == null) {
      throw new NullPointerException();
    }
    try {
      // Locks can throw exceptions.
      lock.readLock().unlock();
    } finally {
      processingThreads.remove(Thread.currentThread());
    }
  }

  /** Convenience filter that notifies this waiter of processing events. */
  public Filter filter() {
    return filter;
  }

  /**
   * Wrap provided runnable with a convenience one that notifies this waiter of
   * processing events.
   */
  public Runnable runnable(Runnable runnable) {
    return new NotificationRunnable(runnable);
  }

  /**
   * Denotes that processing has been shutdown.
   */
  public class ShutdownException extends Exception {
    private ShutdownException() {
      super("Already shutdown");
    }
  }

  private class NotificationFilter extends Filter {
    @Override
    public String description() {
      return "Notifies ShutdownWaiter of requests";
    }

    @Override
    public void doFilter(HttpExchange ex, Filter.Chain chain)
        throws IOException {
      Thread thread = Thread.currentThread();
      try {
        processingStarting(thread);
      } catch (ShutdownException e) {
        throw new IOException(e);
      }
      try {
        chain.doFilter(ex);
      } finally {
        processingCompleted(thread);
      }
    }
  }

  private class NotificationRunnable implements Runnable {
    private final Runnable delegate;

    public NotificationRunnable(Runnable delegate) {
      this.delegate = delegate;
    }

    @Override
    public void run() {
      Thread thread = Thread.currentThread();
      try {
        processingStarting(thread);
      } catch (ShutdownException ex) {
        throw new RuntimeException(ex);
      }
      try {
        delegate.run();
      } finally {
        processingCompleted(thread);
      }
    }
  }
}
