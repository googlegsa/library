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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tests for {@link ShutdownWaiter}. */
public class ShutdownWaiterTest {
  private ShutdownWaiter waiter = new ShutdownWaiter();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullThreadStarting() throws Exception {
    thrown.expect(NullPointerException.class);
    waiter.processingStarting(null);
  }

  @Test
  public void testNullThreadCompleted() {
    thrown.expect(NullPointerException.class);
    waiter.processingCompleted(null);
  }

  @Test
  public void testBasicUsage() throws Exception {
    waiter.processingStarting(Thread.currentThread());
    waiter.processingCompleted(Thread.currentThread());
    long start = System.nanoTime();
    // This should be really fast because it has no lock contention and doesn't
    // need to even interrupt any threads.
    assertTrue(waiter.shutdown(1, TimeUnit.SECONDS));
    long timeTakenUs = TimeUnit.MICROSECONDS.convert(
        System.nanoTime() - start, TimeUnit.NANOSECONDS);
    assertFalse(Thread.currentThread().isInterrupted());
    assertTrue("shutdown took " + timeTakenUs + " microseconds",
        timeTakenUs < 1300);
  }

  @Test
  public void testStopped() throws Exception {
    assertTrue(waiter.shutdown(0, TimeUnit.SECONDS));
    thrown.expect(ShutdownWaiter.ShutdownException.class);
    waiter.processingStarting(Thread.currentThread());
  }

  @Test
  public void testInterruptFullWait() throws Exception {
    final AtomicBoolean interrupted = new AtomicBoolean();
    Thread testThread = new Thread() {
      @Override
      public void run() {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          interrupted.set(true);
        }
      }
    };
    testThread.start();
    waiter.processingStarting(testThread);
    long start = System.nanoTime();
    // This will need to wait the entire allocated time.
    assertFalse(waiter.shutdown(50, TimeUnit.MILLISECONDS));
    long timeTakenUs = TimeUnit.MICROSECONDS.convert(
        System.nanoTime() - start, TimeUnit.NANOSECONDS);
    testThread.join();
    assertTrue(interrupted.get());
    waiter.processingCompleted(testThread);
    // Because the test blocks, it causes noticeable variation occasionally.
    // Thus the high amount of slop.  Consider bumping the maximum to
    // 75000 or even 100000 if this test continues to fail frequently.
    assertTrue("shutdown took " + timeTakenUs + " microseconds",
        timeTakenUs > 48000 && timeTakenUs < 65000);
  }

  @Test
  public void testInterruptNonfullWait() throws Exception {
    final AtomicBoolean completed = new AtomicBoolean();
    final CountDownLatch latch = new CountDownLatch(1);
    Thread testThread = new Thread() {
      @Override
      public void run() {
        try {
          waiter.processingStarting(Thread.currentThread());
        } catch (ShutdownWaiter.ShutdownException e) {
          throw new RuntimeException(e);
        }
        try {
          latch.countDown();
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          completed.set(true);
        } finally {
          waiter.processingCompleted(Thread.currentThread());
        }
      }
    };
    testThread.start();
    // Wait until testThread gets started.
    latch.await();
    long start = System.nanoTime();
    // This will need to interrupt and wait for the thread to stop, but should
    // not need tons of time.
    assertTrue(waiter.shutdown(1, TimeUnit.SECONDS));
    long timeTakenUs = TimeUnit.MICROSECONDS.convert(
        System.nanoTime() - start, TimeUnit.NANOSECONDS);
    assertTrue(completed.get());
    assertTrue("shutdown took " + timeTakenUs + " microseconds",
        timeTakenUs < 1000);
  }
}
