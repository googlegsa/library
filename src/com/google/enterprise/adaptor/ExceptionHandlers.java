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

import java.util.concurrent.TimeUnit;

/**
 * Utility class for {@link ExceptionHandler}s.
 */
public class ExceptionHandlers {
  private static final ExceptionHandler defaultHandler
      = exponentialBackoffHandler(12, 5, TimeUnit.SECONDS);
  private static final ExceptionHandler noRetryHandler
      = exponentialBackoffHandler(-1, 0, TimeUnit.SECONDS);

  // Prevent instantiation.
  private ExceptionHandlers() {}

  /**
   * The default exception handler. Currently it is equivalent to {@code
   * exponentialBackoffHandler(12, 5, TimeUnit.SECONDS)}, but it is free to
   * change in the future.
   * @return ExceptionHandler that comes with library
   */
  public static ExceptionHandler defaultHandler() {
    return defaultHandler;
  }

  /**
   * Create a handler that uses exponential backoff to sleep before retrying.
   * @param maximumTries how many times to try before permanent failure
   * @param initialSleepDuration is countdown on first failure
   * @param initialSleepUnit are the units of countdown
   * @return ExceptionHandler specified by parameters
   */
  public static ExceptionHandler exponentialBackoffHandler(int maximumTries,
      long initialSleepDuration, TimeUnit initialSleepUnit) {
    if (initialSleepUnit == null) {
      throw new NullPointerException();
    }
    return new ExponentialBackoffExceptionHandler(
        maximumTries, initialSleepDuration, initialSleepUnit);
  }

  /**
   * Create a handler that always returns {@code false}, causing no retries.
   * @return ExceptionHandler that does not retry
   */
  public static ExceptionHandler noRetryHandler() {
    return noRetryHandler;
  }

  private static class ExponentialBackoffExceptionHandler
      implements ExceptionHandler {
    private final int maximumTries;
    private final long sleepDuration;
    private final TimeUnit sleepUnit;

    public ExponentialBackoffExceptionHandler(int maximumTries,
        long sleepDuration, TimeUnit sleepUnit) {
      this.maximumTries = maximumTries;
      this.sleepDuration = sleepDuration;
      this.sleepUnit = sleepUnit;
    }

    @Override
    public boolean handleException(Exception ex, int ntries)
        throws InterruptedException {
      if (ntries > maximumTries) {
        return false;
      }
      sleepUnit.sleep(sleepDuration * ntries);
      return true;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "(" + maximumTries + ","
          + sleepDuration + " " + sleepUnit + ")";
    }
  }
}
