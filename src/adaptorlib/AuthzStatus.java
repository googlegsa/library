// Copyright 2011 Google Inc.
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

package adaptorlib;

/**
 * Authorization Status codes.
 * <ul>
 * <li>{@code PERMIT} means that authorization is granted.</li>
 * <li>{@code DENY} means that authorization is explicitly denied.</li>
 * <li>{@code INDETERMINATE} means that permission is neither granted nor
 * denied. If a consumer receives this code, it may decide to try other means
 * to get an explicit decision (i.e. {@code PERMIT} or {@code DENY}).</li>
 * </ul>
 */
public enum AuthzStatus {
  PERMIT("Access PERMITTED"),
  DENY("Access DENIED"),
  INDETERMINATE("No access decision");

  private final String description;

  private AuthzStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
