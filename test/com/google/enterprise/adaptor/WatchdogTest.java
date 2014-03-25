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

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link Watchdog}.
 */
public class WatchdogTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private ScheduledExecutorService executor
      = Executors.newSingleThreadScheduledExecutor();
  private Watchdog watchdog;

  @After
  public void stopExecutor() {
    executor.shutdownNow();
  }

  @Test
  public void testNullExecutor() {
    thrown.expect(NullPointerException.class);
    new Watchdog(null);
  }

  @Test
  public void testInterruption() throws InterruptedException {
    watchdog = new Watchdog(executor);
    watchdog.processingStarting(1);
    try {
      thrown.expect(InterruptedException.class);
      Thread.sleep(100);
    } finally {
      watchdog.processingCompleted();
    }
  }

  @Test
  public void testDifferentThread() throws InterruptedException {
    watchdog = new Watchdog(executor);
    final Thread thread = Thread.currentThread();
    watchdog.processingStarting(1000);
    final AtomicBoolean success = new AtomicBoolean();
    Thread thread2 = new Thread() {
      @Override
      public void run() {
        watchdog.processingCompleted(thread);
        watchdog.processingStarting(thread, 1000);
        success.set(true);
      }
    };
    thread2.start();
    thread2.join();
    assertTrue(success.get());

    watchdog.processingCompleted();
  }

  @Test
  public void testNoInterruption() throws InterruptedException {
    watchdog = new Watchdog(executor);
    watchdog.processingStarting(5);
    try {
      Thread.sleep(1);
    } finally {
      watchdog.processingCompleted();
    }
    Thread.sleep(10);
  }

  @Test
  public void testDoubleAdd() {
    watchdog = new Watchdog(executor);
    watchdog.processingStarting(1000);
    try {
      thrown.expect(IllegalStateException.class);
      watchdog.processingStarting(1000);
    } finally {
      watchdog.processingCompleted();
    }
  }

  @Test
  public void testRemoveButNoAdd() {
    watchdog = new Watchdog(executor);
    thrown.expect(IllegalStateException.class);
    watchdog.processingCompleted();
  }

  @Test
  public void testDoubleRemove() {
    watchdog = new Watchdog(executor);
    watchdog.processingStarting(1000);
    watchdog.processingCompleted();
    thrown.expect(IllegalStateException.class);
    watchdog.processingCompleted();
  }
}
