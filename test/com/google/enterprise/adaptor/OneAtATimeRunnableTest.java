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

/**
 * Test cases for {@link OneAtATimeRunnable}.
 */
public class OneAtATimeRunnableTest {
  private MustInterruptRunnable mainRunnable = new MustInterruptRunnable();
  private CountingRunnable alreadyRunning = new CountingRunnable();
  private OneAtATimeRunnable runnable
      = new OneAtATimeRunnable(mainRunnable, alreadyRunning);

  @Test(expected = NullPointerException.class)
  public void testNullRunnable() {
    new OneAtATimeRunnable(null, alreadyRunning);
  }

  @Test(expected = NullPointerException.class)
  public void testNullAlreadyRunningRunnable() {
    new OneAtATimeRunnable(mainRunnable, null);
  }

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
    assertTrue(runnable.isRunning());
    thread1.interrupt();
    thread2.interrupt();
    thread3.interrupt();
    thread1.join();
    thread2.join();
    thread3.join();
    assertFalse(runnable.isRunning());
    assertEquals(1, mainRunnable.getCounter());
    assertEquals(2, alreadyRunning.getCounter());
    assertSame(mainRunnable, runnable.getRunnable());
    assertSame(alreadyRunning, runnable.getAlreadyRunningRunnable());

    // Test to make sure later executions work as well.
    thread1 = new Thread(runnable);
    thread1.start();
    thread1.interrupt();
    thread1.join();
    assertEquals(2, mainRunnable.getCounter());
    assertEquals(2, alreadyRunning.getCounter());
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
