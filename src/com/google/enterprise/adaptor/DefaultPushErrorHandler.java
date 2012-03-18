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
 * Default handler of errors during a push of {@code DocId}s to the GSA.
 */
public class DefaultPushErrorHandler implements PushErrorHandler {
  private int maximumTries;
  private long sleepTimeMillis;

  /**
   * Same as {@code DefaultPushErrorHandler(12, 5000)}.
   *
   * @see #DefaultPushErrorHandler(int, long)
   */
  public DefaultPushErrorHandler() {
    this(12, 5000);
  }

  /**
   * Create a default error handler that gives up after {@code maximumTries} and
   * sleeps {@code sleepTimeMillis * numberOfTries} before retrying.
   */
  public DefaultPushErrorHandler(int maximumTries, long sleepTimeMillis) {
    this.maximumTries = maximumTries;
    this.sleepTimeMillis = sleepTimeMillis;
  }

  /**
   * {@inheritDoc}
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  @Override
  public boolean handleFailedToConnect(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * {@inheritDoc}
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  @Override
  public boolean handleFailedWriting(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * {@inheritDoc}
   *
   * <p>By default, calls {@link #handleGeneric}.
   */
  @Override
  public boolean handleFailedReadingReply(Exception ex, int ntries)
      throws InterruptedException {
    return handleGeneric(ex, ntries);
  }

  /**
   * Common handle method for generic error handling. The handler gives up after
   * {@code maximumTries} and sleeps {@code sleepTimeMillis * ntries} before
   * retrying.
   */
  protected boolean handleGeneric(Exception ex, int ntries)
      throws InterruptedException {
    if (ntries > maximumTries) {
      return false;
    }
    Thread.sleep(sleepTimeMillis * ntries);
    return true;
  }
}
