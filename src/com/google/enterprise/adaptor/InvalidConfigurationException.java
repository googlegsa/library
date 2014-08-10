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
 * Thrown for unrecoverable configuration errors. Recoverable configuration
 * problems (such as server off-line) should throw something other than
 * StartupException, so that the retry with backoff logic will be used.
 */
public class InvalidConfigurationException extends StartupException {
  /**
   * Constructs a new InvalidConfigurationException with no message and no
   * cause.
   */
  public InvalidConfigurationException() {
    super();
  }

  /**
   * Constructs a InvalidConfigurationException with a supplied message but no
   * cause.
   *
   * @param message the message. Can be retrieved by the {@link #getMessage()}
   *        method.
   */
  public InvalidConfigurationException(String message) {
    super(message);
  }

  /**
   * Constructs a InvalidConfigurationException with message and cause.
   *
   * @param message the message. Can be retrieved by the {@link #getMessage()}
   *        method.
   * @param cause failure cause
   */
  public InvalidConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a InvalidConfigurationException with specified cause, copying
   * its message if cause is non-{@code null}.
   *
   * @param message the message. Can be retrieved by the {@link #getMessage()}
   *        method.
   * @param cause failure cause
   */
  public InvalidConfigurationException(Throwable cause) {
    super(cause);
  }
}
