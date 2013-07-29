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
 * Interface for handling errors and handling retrying policy.
 */
public interface ExceptionHandler {
  /**
   * Determine how to proceed after an exception was thrown. The thrown
   * exception is provided as well as the number of times the call has been
   * attempted. It is fine to call {@code Thread.sleep()} before returning.
   *
   * @return {@code true} for immediate retry, {@code false} to abort
   */
  public boolean handleException(Exception ex, int ntries)
      throws InterruptedException;
}
