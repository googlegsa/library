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
 * Exception produced by {@link DocumentTransform}s and {@link
 * DocumentTransform} in the case of a fatal error.
 */
public class TransformException extends Exception {
  /**
   * Constructs a new exception with a detailed message. The exception's cause
   * is left uninitialized.
   */
  public TransformException(String message) {
    super(message);
  }

  /**
   * Constructs a new exception with a detailed message and a cause initialized
   * to {@code cause}.
   */
  public TransformException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new exception with a {@code null} message and a cause
   * initalized to {@code cause}.
   */
  public TransformException(Throwable cause) {
    super(cause);
  }
}
