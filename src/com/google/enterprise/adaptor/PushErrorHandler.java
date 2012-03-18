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

/**
 * Interface for handling errors encountered during pushing of {@code DocId}s
 * via {@link DocIdPusher}.
 */
public interface PushErrorHandler {
  /**
   * Handle a failure that {@link DocIdPusher} had connecting with the GSA to
   * send a batch. This includes determining if the operation should be retried
   * or if it should be aborted while also allowing other logic, like running
   * {@code Thread.sleep()} to permit time for the situation to improve. The
   * thrown exception is provided as well as the number of times that this batch
   * was attempted to be sent.
   *
   * @return {@code true} to indicate the batch should be resent,
   *     {@code false} to abort the batch
   */
  public boolean handleFailedToConnect(Exception ex, int ntries)
      throws InterruptedException;

  /**
   * Handle a failure that {@link DocIdPusher} had writing to the GSA while
   * sending a batch. This includes determining if the operation should be
   * retried or if it should be aborted while also allowing other logic, like
   * running {@code Thread.sleep()} to permit time for the situation to improve.
   * The thrown exception is provided as well as the number of times that this
   * batch was attempted to be sent.
   *
   * @return {@code true} to indicate the batch should be resent,
   *     {@code false} to abort the batch
   */
  public boolean handleFailedWriting(Exception ex, int ntries)
      throws InterruptedException;

  /**
   * Handle a failure that {@link DocIdPusher} had reading the GSA's response.
   * This includes determining if the operation should be retried or if it
   * should be aborted while also allowing other logic, like running {@code
   * Thread.sleep()} to permit time for the situation to improve. The thrown
   * exception is provided as well as the number of times that this batch was
   * attempted to be sent.
   *
   * @return {@code true} to indicate the batch should be resent,
   *     {@code false} to abort the batch
   */
  public boolean handleFailedReadingReply(Exception ex, int ntries)
      throws InterruptedException;
}
