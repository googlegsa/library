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

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Batches elements from a BlockingQueue. */
class BlockingQueueBatcher {
  @VisibleForTesting
  static RelativeTimeProvider timeProvider = new SystemRelativeTimeProvider();

  // Prevent instantiation.
  private BlockingQueueBatcher() {}

  /**
   * Read a batch-worth of elements from {@code queue}, placing them in {@code
   * batch}, blocking until a batch is ready. No element will be delayed waiting
   * for the batch to complete longer than {@code maxLatency}. Latency should
   * not be confused with a timeout for the overall call, since the latency
   * applies only once an element arrives and begins the moment the first
   * element arrives.
   *
   * <p>At least one element will be added to {@code batch}, except if an
   * exception is thrown.
   *
   * <p>Uses of this method that reuse {@code batch} should not forget to remove
   * items from the collection after they are consumed. Otherwise, they will
   * accumulate.
   *
   * @return number of elements added to {@code batch}
   * @throws InterruptedException if interrupted while waiting
   */
  public static <T> int take(BlockingQueue<T> queue,
      Collection<? super T> batch, int maxBatchSize, long maxLatency,
      TimeUnit maxLatencyUnit) throws InterruptedException {
    long maxLatencyNanos = maxLatencyUnit.toNanos(maxLatency);

    int curBatchSize = 0;
    long stopBatchTimeNanos = -1;

    // The loop flow is 1) block, 2) drain queue, 3) possibly consume batch.
    while (true) {
      boolean timeout = false;
      if (stopBatchTimeNanos == -1) {
        // Start of new batch. Block for the first item of this batch.
        batch.add(queue.take());
        curBatchSize++;
        stopBatchTimeNanos = timeProvider.relativeTime(TimeUnit.NANOSECONDS)
            + maxLatencyNanos;
      } else {
        // Continue existing batch. Block until an item is in the queue or the
        // batch timeout expires.
        T element = queue.poll(
            stopBatchTimeNanos
                - timeProvider.relativeTime(TimeUnit.NANOSECONDS),
            TimeUnit.NANOSECONDS);
        if (element == null) {
          // Timeout occurred.
          break;
        }
        batch.add(element);
        curBatchSize++;
      }
      curBatchSize += queue.drainTo(batch, maxBatchSize - curBatchSize);

      if (curBatchSize >= maxBatchSize) {
        // End current batch.
        break;
      }
    }

    return curBatchSize;
  }
}
