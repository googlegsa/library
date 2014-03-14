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

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous sender of feed items. {@code worker()} must be started by client
 * and running for items to be sent.
 */
class AsyncDocIdSender implements AsyncDocIdPusher,
    DocumentHandler.AsyncPusher {
  private static final Logger log
      = Logger.getLogger(AsyncDocIdSender.class.getName());

  private final ItemPusher itemPusher;
  private final int maxBatchSize;
  private final long maxLatency;
  private final TimeUnit maxLatencyUnit;
  private final BlockingQueue<DocIdPusher.Item> queue;
  private final Runnable worker = new WorkerRunnable();

  /**
   * {@code queueCapacity} should be large enough to handle queuing the number
   * of new items that would be expected during the amount of time it takes to
   * send a feed. For example, if requests are coming in at 300 docs/sec and it
   * takes a second to send a feed of {@code maxBatchSize}, then {@code
   * queueCapacity} should be at least {@code 300}.
   */
  public AsyncDocIdSender(ItemPusher itemPusher, int maxBatchSize,
      long maxLatency, TimeUnit maxLatencyUnit, int queueCapacity) {
    if (itemPusher == null || maxLatencyUnit == null) {
      throw new NullPointerException();
    }
    if (maxBatchSize < 1) {
      throw new IllegalArgumentException("maxBatchSize must be positive");
    }
    this.itemPusher = itemPusher;
    this.maxBatchSize = maxBatchSize;
    this.maxLatency = maxLatency;
    this.maxLatencyUnit = maxLatencyUnit;
    this.queue = new ArrayBlockingQueue<DocIdPusher.Item>(queueCapacity);
  }

  /**
   * Enqueue {@code item} to be sent by worker. If the queue is full, then the
   * item will be dropped and a warning will be logged.
   */
  @Override
  public void asyncPushItem(final DocIdPusher.Item item) {
    if (!queue.offer(item)) {
      log.log(Level.WARNING, "Failed to queue item: {0}", item);
    }
  }

  @Override
  public void pushDocId(DocId docId) {
    asyncPushItem(new DocIdPusher.Record.Builder(docId).build());
  }

  @Override
  public void pushRecord(DocIdPusher.Record record) {
    asyncPushItem(record);
  }

  @Override
  public void pushNamedResource(DocId docId, Acl acl) {
    asyncPushItem(new DocIdSender.AclItem(docId, null, acl));
  }

  public Runnable worker() {
    return worker;
  }

  private class WorkerRunnable implements Runnable {
    @Override
    public void run() {
      Set<DocIdPusher.Item> items = new LinkedHashSet<DocIdPusher.Item>();
      try {
        while (true) {
          BlockingQueueBatcher.take(
              queue, items, maxBatchSize, maxLatency, maxLatencyUnit);
          itemPusher.pushItems(items.iterator(), null);
          items.clear();
        }
      } catch (InterruptedException ex) {
        log.log(Level.FINE, "AsyncDocIdSender worker shutting down", ex);
        try {
          // We are shutting down, but there are likely items that haven't been
          // sent because of maxLatency, so we try to send those now.
          // If we were interrupted between calls to take(), then take() may
          // have interrupted itself before draining the queue; might as well
          // send everything that was put on the queue.
          queue.drainTo(items);
          itemPusher.pushItems(items.iterator(),
              ExceptionHandlers.noRetryHandler());
        } catch (InterruptedException ex2) {
          // Ignore, because we are going to interrupt anyway. This should
          // actually not happen because of the ExceptionHandler we are using,
          // but the precise behavior of pushItems() may change in the future.
        } finally {
          log.log(Level.FINE, "AsyncDocIdSender worker shutdown", ex);
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  public interface ItemPusher {
    /**
     * Send {@code items} using {@code handler} to manage errors.
     *
     * @returns {@code null} for success, or the first item that failed if not
     *     all items were sent
     */
    public <T extends DocIdPusher.Item> T pushItems(Iterator<T> items,
        ExceptionHandler handler) throws InterruptedException;
  }
}
