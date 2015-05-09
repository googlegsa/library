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
 * Authorization status codes.
 */
public enum AuthzStatus {
  /** The authorization is granted. */
  PERMIT("Access PERMITTED"),
  /** The authorization is explicitly forbidden. */
  DENY("Access DENIED"),
  /**
   * Permission is neither granted nor forbidden. If a consumer recieves this
   * code it may decide to try other means to get an explicit decision (i.e.,
   * {@code PERMIT} or {@code DENY}.
   */
  INDETERMINATE("No access decision");

  private final String description;

  private AuthzStatus(String description) {
    this.description = description;
  }

  /** @return String description of decision */
  public String getDescription() {
    return description;
  }
}
