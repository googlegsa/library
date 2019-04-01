// Copyright 2014 Google Inc. All Rights Reserved.
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
 * Thrown for unrecoverable startup errors, such as fatal configuration
 * errors or running on the wrong platform.  StartupExceptions will bypass
 * the retry with back-off recovery logic of {@link Application} and immediately
 * terminate the adaptor.
 */
public class StartupException extends RuntimeException {
  /**
   * Constructs a new StartupException with no message and no cause.
   */
  public StartupException() {
    super();
  }

  /**
   * Constructs a StartupException with a supplied message but no cause.
   *
   * @param message the message. Can be retrieved by the {@link #getMessage()}
   *        method.
   */
  public StartupException(String message) {
    super(message);
  }

  /**
   * Constructs a StartupException with message and cause.
   *
   * @param message the message. Can be retrieved by the {@link #getMessage()}
   *        method.
   * @param cause failure cause
   */
  public StartupException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a StartupException with specified cause, copying its message if
   * cause is non-{@code null}.
   *
   * @param cause failure cause
   */
  public StartupException(Throwable cause) {
    super(cause);
  }
}
