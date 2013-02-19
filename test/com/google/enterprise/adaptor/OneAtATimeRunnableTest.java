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

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test cases for {@link OneAtATimeRunnable}.
 */
public class OneAtATimeRunnableTest {
  private MustInterruptRunnable mainRunnable = new MustInterruptRunnable();
  private CountingRunnable alreadyRunning = new CountingRunnable();
  private OneAtATimeRunnable runnable
      = new OneAtATimeRunnable(mainRunnable, alreadyRunning);

  @Test
  public void testMultipleSimultaneosInvocations() throws Exception {
    Thread thread1 = new Thread(runnable);
    Thread thread2 = new Thread(runnable);
    Thread thread3 = new Thread(runnable);
    thread1.start();
    thread2.start();
    thread3.start();
    // We have to give all the threads enough time to get up and running. We
    // can't use Thread.join() because we don't know which thread is going to
    // run forever. This means either do this weird Thread.yield() trickery or
    // hard-code a Thread.sleep() and cross our fingers.
    while (mainRunnable.getCounter() != 1) {
      Thread.yield();
    }
    while (alreadyRunning.getCounter() != 2) {
      Thread.yield();
    }
    thread1.interrupt();
    thread2.interrupt();
    thread3.interrupt();
    thread1.join();
    thread2.join();
    thread3.join();
    assertEquals(1, mainRunnable.getCounter());
    assertEquals(2, alreadyRunning.getCounter());
    assertSame(mainRunnable, runnable.getRunnable());
    assertSame(alreadyRunning, runnable.getAlreadyRunningRunnable());

    thread1 = runnable.runInNewThread();
    thread2 = runnable.runInNewThread();
    assertNull(thread2);
    thread3 = runnable.runInNewThread();
    assertNull(thread3);
    assertTrue(thread1.isAlive());
    thread1.interrupt();
    thread1.join();
    assertEquals(2, mainRunnable.getCounter());
    assertEquals(2, alreadyRunning.getCounter());
  }

  @Test
  public void testStop() throws Exception {
    Thread thread = runnable.runInNewThread();
    // We must wait for the thread to get running, otherwise the stop() later
    // could happen first and cause alreadyRunning to be called instead of
    // mainRunnable. We want to test that mainRunnable's thread is interrupted
    // and not that it is prevented from running.
    while (mainRunnable.getCounter() != 1) {
      Thread.yield();
    }
    runnable.stop();
    thread.join();
    assertEquals(1, mainRunnable.getCounter());
    assertEquals(0, alreadyRunning.getCounter());

    // Stop a second time, just to test that it doesn't blow up.
    runnable.stop();

    thread = runnable.runInNewThread();
    assertNull(thread);
    assertEquals(1, mainRunnable.getCounter());
    assertEquals(0, alreadyRunning.getCounter());

    thread = new Thread(runnable);
    thread.start();
    thread.join();
    assertEquals(1, mainRunnable.getCounter());
    assertEquals(1, alreadyRunning.getCounter());
  }

  @Test
  public void testStopThreadIsNotNull() throws Exception {
    Thread thread = runnable.runInNewThread();
    while (mainRunnable.getCounter() != 1) {
      Thread.sleep(1);
    }
    runnable.stop();
    thread.join();

    thread = runnable.runInNewThread();
    assertNull(thread);
  }

  /** Thread-safe. */
  private static class CountingRunnable implements Runnable {
    private AtomicInteger counter = new AtomicInteger();

    @Override
    public void run() {
      counter.incrementAndGet();
    }

    public int getCounter() {
      return counter.get();
    }
  }

  /** Thread-safe. */
  private static class MustInterruptRunnable extends CountingRunnable {
    @Override
    public void run() {
      super.run();
      while (true) {
        try {
          Thread.sleep(100000);
        } catch (InterruptedException ex) {
          break;
        }
      }
    }
  }
}
