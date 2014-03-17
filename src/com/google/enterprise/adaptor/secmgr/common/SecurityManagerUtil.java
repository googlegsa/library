// Copyright 2009 Google Inc.
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

package com.google.enterprise.adaptor.secmgr.common;

import com.google.common.annotations.VisibleForTesting;

import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Utilities useful throughout the security manager.
 */
@ThreadSafe
public class SecurityManagerUtil {
  private static final Logger LOGGER = Logger.getLogger(SecurityManagerUtil.class.getName());

  // don't instantiate
  private SecurityManagerUtil() {
    throw new UnsupportedOperationException();
  }

  /**
   * Annotate a log message with a given session ID.  This should be implemented
   * in the session manager, but can't be due to cyclic build dependencies.
   *
   * @param sessionId The session ID to annotate the message with.
   * @param message The log message to annotate.
   * @return The annotated log message.
   */
  public static String sessionLogMessage(String sessionId, String message) {
    return "sid " + ((sessionId != null) ? sessionId : "?") + ": " + message;
  }

  /**
   * Is a given remote "before" time valid?  In other words, is it possible that
   * the remote "before" time is less than or equal to the remote "now" time?
   *
   * @param before A before time from a remote host.
   * @param now The current time on this host.
   * @return True if the before time might not have passed on the remote host.
   */
  public static boolean isRemoteBeforeTimeValid(long before, long now) {
    return before - CLOCK_SKEW_TIME <= now;
  }

  /**
   * Is a given remote "on or after" time valid?  In other words, is it possible
   * that the remote "on or after" time is greater than the remote "now" time?
   *
   * @param onOrAfter An on-or-after time from a remote host.
   * @param now The current time on this host.
   * @return True if the remote time might have passed on the remote host.
   */
  public static boolean isRemoteOnOrAfterTimeValid(long onOrAfter, long now) {
    return onOrAfter + CLOCK_SKEW_TIME > now;
  }

  @VisibleForTesting
  public static long getClockSkewTime() {
    return CLOCK_SKEW_TIME;
  }

  private static final long CLOCK_SKEW_TIME = 5000;
}
