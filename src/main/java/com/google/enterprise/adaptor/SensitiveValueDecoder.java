// Copyright 2012 Google Inc. All Rights Reserved.
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
 * Provides parsing of sensitive values that can be plain text, obfuscated, or
 * encrypted.
 */
public interface SensitiveValueDecoder {
  /**
   * Decode an encoded sensitive string into its original string.
   * @param nonReadable is input needing decoding
   * @return String in readable form
   * @throws IllegalArgumentException if {@code nonReadable} is unable to be
   *     decoded
   */
  public String decodeValue(String nonReadable);
}
