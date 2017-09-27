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

import org.junit.Test;

import java.util.concurrent.TimeUnit;

/** Tests for {@link ExceptionHandlers}. */
public class ExceptionHandlersTest {
  @Test
  public void testBackoffToString() {
    ExceptionHandler handler
        = ExceptionHandlers.exponentialBackoffHandler(1, 2, TimeUnit.MINUTES);
    assertEquals("ExponentialBackoffExceptionHandler(1,2 MINUTES)",
        handler.toString());
  }

  @Test
  public void testNoRetry() throws Exception {
    ExceptionHandler handler = ExceptionHandlers.noRetryHandler();
    // Make sure it doesn't throw InterruptedException
    Thread.currentThread().interrupt();
    assertFalse(handler.handleException(new RuntimeException(), 1));
    // Clear flag
    Thread.interrupted();
  }
}
