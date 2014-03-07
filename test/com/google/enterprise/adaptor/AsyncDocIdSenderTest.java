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

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Tests for {@link AsyncDocIdSender}. */
public class AsyncDocIdSenderTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private AccumulatingPusher pusher = new AccumulatingPusher();

  @Test
  public void testNullPusher() {
    thrown.expect(NullPointerException.class);
    new AsyncDocIdSender(null, 3, 1, TimeUnit.SECONDS, 5);
  }

  @Test
  public void testNullUnit() {
    thrown.expect(NullPointerException.class);
    new AsyncDocIdSender(pusher, 3, 1, null, 5);
  }

  @Test
  public void testZeroMaxBatchSize() {
    thrown.expect(IllegalArgumentException.class);
    new AsyncDocIdSender(pusher, 0, 1, TimeUnit.SECONDS, 5);
  }

  @Test(timeout = 50)
  public void testWorkerInterrupts() throws Exception {
    AsyncDocIdSender sender = new AsyncDocIdSender(pusher, 3,
        1, TimeUnit.SECONDS, 5);
    Thread.currentThread().interrupt();
    sender.worker().run();
  }

  @Test(timeout = 100)
  public void testPushOfFullBatch() throws Exception {
    AsyncDocIdSender sender = new AsyncDocIdSender(pusher, 3 /* maxBatchSize */,
        1, TimeUnit.SECONDS, 3 /* queueCapacity */);
    final List<DocIdPusher.Record> golden = Arrays.asList(
        new DocIdPusher.Record.Builder(new DocId("1")).build(),
        new DocIdPusher.Record.Builder(new DocId("2")).build(),
        new DocIdPusher.Record.Builder(new DocId("3")).build());
    for (DocIdPusher.Record record : golden) {
      sender.asyncPushItem(record);
    }
    // We shouldn't block while adding this item, even though the queue is full.
    // Instead, it should simply be dropped.
    sender.asyncPushItem(
        new DocIdPusher.Record.Builder(new DocId("4")).build());
    Thread workerThread = new Thread(sender.worker());
    workerThread.start();

    Thread.sleep(10);
    workerThread.interrupt();
    workerThread.join();
    assertEquals(golden, pusher.getItems());
  }

  @Test(timeout = 100)
  public void testPushOnInterrupt() throws Exception {
    AsyncDocIdSender sender = new AsyncDocIdSender(pusher, 2 /* maxBatchSize */,
        1, TimeUnit.SECONDS, 1 /* queueCapacity */);
    final List<DocIdPusher.Record> golden = Arrays.asList(
        new DocIdPusher.Record.Builder(new DocId("1")).build());
    sender.asyncPushItem(golden.get(0));
    Thread workerThread = new Thread(sender.worker());
    workerThread.start();
    // We want to trigger the code that waits on maxLatency.
    Thread.sleep(10);
    workerThread.interrupt();
    workerThread.join();
    assertEquals(golden, pusher.getItems());
  }

  @Test(timeout = 100)
  public void testPushOnInterruptDrainQueue() throws Exception {
    AsyncDocIdSender sender = new AsyncDocIdSender(pusher, 2 /* maxBatchSize */,
        1, TimeUnit.SECONDS, 1 /* queueCapacity */);
    final List<DocIdPusher.Record> golden = Arrays.asList(
        new DocIdPusher.Record.Builder(new DocId("1")).build());
    sender.asyncPushItem(golden.get(0));
    final Runnable worker = sender.worker();
    Thread workerThread = new Thread(new Runnable() {
      @Override
      public void run() {
        Thread.currentThread().interrupt();
        worker.run();
      }
    });
    workerThread.start();
    workerThread.join();
    assertEquals(golden, pusher.getItems());
  }

  private static class AccumulatingPusher
      implements AsyncDocIdSender.ItemPusher {
    private final List<DocIdPusher.Item> items
        = new LinkedList<DocIdPusher.Item>();

    @Override
    public <T extends DocIdPusher.Item> T pushItems(Iterator<T> items,
        ExceptionHandler handler) throws InterruptedException {
      while (items.hasNext()) {
        this.items.add(items.next());
      }
      return null;
    }

    public List<DocIdPusher.Item> getItems() {
      return items;
    }
  }
}
