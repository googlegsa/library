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

import java.util.Locale;

/**
 * Multi-state indicator providing the user with a notification of broken parts
 * of the system. A {@code Status} instance should not change its results over
 * time; instead a {@link StatusSource} should return different instances as the
 * status changes.
 */
public interface Status {
  /**
   * Available statuses for displaying state indicators on the dashboard.
   */
  public enum Code {
    /**
     * The status is disabled because it does not apply. Assumably the feature
     * it monitors is disabled.
     *
     * <p>Represented with an empty LED.
     */
    INACTIVE,
    /**
     * The status is enabled but was unable to be resolved. This is not an
     * error, but simply a "we don't know yet" state. Consider a status that
     * pings a machine to make sure it is alive and the network is up. While the
     * very first ping is in progress "unavailable" would be the most
     * appropriate status.
     *
     * <p>Represented with an empty LED.
     */
    UNAVAILABLE,
    /**
     * Everything is go; all is right with the world.
     *
     * <p>Represented with a green LED.
     */
    NORMAL,
    /**
     * There may be a problem, but maybe not; user intervention may aid the
     * situation, but things may fix themselves. Alternatively, a user can't do
     * anything to improve the situation, but they need to be aware that it is
     * occuring.
     *
     * <p>Represented with a yellow LED.
     */
    WARNING,
    /**
     * There is a known problem; user intervention is likely needed to resolve
     * the problem.
     *
     * <p>Represented with a red LED.
     */
    ERROR,
  }

  /**
   * The code to represent the state of the status. Will not return {@code
   * null}.
   *
   * @return the state of the status, but never {@code null}.
   */
  public Code getCode();

  /**
   * A message appropriate for displaying to an end-user concerning the state of
   * the status. A message is not required and is only encouraged if it provides
   * helpful information. For example, if {@link #getCode} returns {@link
   * Code#NORMAL}, then a message is typically discouraged, unless it provides
   * statistics or additional information not obvious when provided {@link
   * StatusSource#getName} and {@link #getCode}.
   *
   * @param locale non-{@code null} locale for localization.
   * @return a localized message for the end-user, or {@code null} if one is not
   *     necessary.
   */
  public String getMessage(Locale locale);
}
