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

package adaptorlib;

/**
 * A source of {@link Status} messages.
 */
public interface StatusSource {
  /**
   * Retrieve the current status. This method should return quickly, within the
   * order of ten milliseconds. That necessitates that most implementations
   * perform their status checks outside of this method. This method should
   * never return {@code null}.
   */
  public Status retrieveStatus();

  /**
   * Get the name of this source, for displaying to the user. This method should
   * never return {@code null}.
   */
  public String getName();
}
