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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Tests for {@link BlockingQueueBatcher}. */
public class BlockingQueueBatcherTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private final RelativeTimeProvider savedTimeProvider;

  public BlockingQueueBatcherTest() {
    savedTimeProvider = BlockingQueueBatcher.timeProvider;
  }

  @After
  public void restoreTimeProvider() {
    BlockingQueueBatcher.timeProvider = savedTimeProvider;
  }

  @Test(timeout = 500)
  public void testAlreadyAvailable() throws Exception {
    final List<Object> golden = Arrays.asList(
        new Object(), new Object(), new Object());
    BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
    queue.addAll(golden);
    List<Object> list = new ArrayList<Object>();
    // No blocking should occur.
    assertEquals(golden.size(), BlockingQueueBatcher.take(
        queue, list, golden.size(), 1, TimeUnit.SECONDS));
    assertEquals(golden, list);
  }

  @Test(timeout = 500)
  public void testBatchFilledWhileWaiting() throws Exception {
    final List<Object> golden = Arrays.asList(
        new Object(), new Object(), new Object());
    final AtomicBoolean addedExtraElements = new AtomicBoolean();
    BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>() {
      @Override
      public Object poll(long timeout, TimeUnit unit) {
        // This method should only be called once.
        assertFalse(addedExtraElements.get());
        addedExtraElements.set(true);
        assertEquals(0, size());
        // Add the third for later retrieval; the second will be returned now.
        add(golden.get(2));
        return golden.get(1);
      }
    };
    queue.add(golden.get(0));
    List<Object> list = new ArrayList<Object>();
    // No blocking should occur.
    assertEquals(golden.size(), BlockingQueueBatcher.take(
        queue, list, golden.size(), 1, TimeUnit.SECONDS));
    assertEquals(golden, list);
  }

  @Test
  public void testTimeout() throws Exception {
    final List<Object> golden = Arrays.asList(new Object(), new Object());
    final AtomicLong currentTime = new AtomicLong(5);
    BlockingQueueBatcher.timeProvider = new RelativeTimeProvider() {
      public long relativeTime(TimeUnit unit) {
        return unit.convert(currentTime.get(), TimeUnit.MILLISECONDS);
      }
    };
    final AtomicBoolean timedOut = new AtomicBoolean();
    BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>() {
      private long timesPolledCalled;

      @Override
      public Object take() throws InterruptedException {
        Object o = super.take();
        // Simulate 100ms passing.
        currentTime.set(currentTime.get() + 100);
        return o;
      }

      @Override
      public Object poll(long timeout, TimeUnit unit) {
        assertEquals(0, size());
        if (timesPolledCalled == 0) {
          timesPolledCalled++;
          assertEquals(1000, unit.toMillis(timeout));
          // Simulate 100ms passing.
          currentTime.set(currentTime.get() + 100);
          return golden.get(1);
        } else if (timesPolledCalled == 1) {
          timesPolledCalled++;
          // the last poll() took 100ms, so 900ms is left of the second.
          assertEquals(900, unit.toMillis(timeout));
          // Claim that we timed out.
          currentTime.set(currentTime.get() + 900);
          timedOut.set(true);
          return null;
        } else {
          fail("poll called more times than expected");
          throw new AssertionError();
        }
      }
    };
    queue.add(golden.get(0));
    List<Object> list = new ArrayList<Object>();
    // No blocking should occur, because we overrode the queue's implementation.
    // In normal circumstances it would block until timeout. If blocking did
    // occur, it is likely the test is out-of-date.
    assertEquals(golden.size(), BlockingQueueBatcher.take(
        queue, list, 3, 1, TimeUnit.SECONDS));
    assertEquals(golden, list);
    assertTrue(timedOut.get());
  }

  @Test
  public void testInterrupt() throws Exception {
    BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
    List<Object> list = new ArrayList<Object>();
    Thread.currentThread().interrupt();
    thrown.expect(InterruptedException.class);
    BlockingQueueBatcher.take(queue, list, 1, 1, TimeUnit.SECONDS);
  }
}
