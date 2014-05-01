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
 * Thrown if the adaptor cannot be run on this OS environment.
 */
public class UnsupportedPlatformException extends StartupException {
  /**
   * Constructs a new UnsupportedPlatformException with a default message.
   */
  public UnsupportedPlatformException() {
    this(System.getProperty("os.name") +
         " is not a supported platform for this adaptor.");
  }

  /**
   * Constructs a UnsupportedPlatformException with a supplied message.
   *
   * @param message the message. Can be retrieved by the {@link #getMessage()}
   *        method.
   */
  public UnsupportedPlatformException(String message) {
    super(message);
  }
}
