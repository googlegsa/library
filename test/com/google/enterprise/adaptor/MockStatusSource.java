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

import com.google.enterprise.adaptor.Status;
import com.google.enterprise.adaptor.StatusSource;
import java.util.Locale;

/**
 * Simplistic implementation of {@link StatusSource} for use with foreign logic
 * calling {@link #setStatus} to update state.
 *
 * <p>This class is thread-safe and can safely have its status viewed and
 * changed in multiple threads without external synchronization.
 */
class MockStatusSource implements StatusSource {
  private final String name;
  private volatile Status status;

  public MockStatusSource(String name, Status status) {
    if (name == null || status == null) {
      throw new NullPointerException();
    }
    this.name = name;
    this.status = status;
  }

  @Override
  public Status retrieveStatus() {
    return status;
  }

  /**
   * Set the status.
   *
   * @throws NullPointerException when {@code status} is {@code null}
   */
  public void setStatus(Status status) {
    if (status == null) {
      throw new NullPointerException();
    }
    this.status = status;
  }

  @Override
  public String getName(Locale locale) {
    return name;
  }
}
