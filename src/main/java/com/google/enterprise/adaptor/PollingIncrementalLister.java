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

import java.io.IOException;

/**
 * Interface for library-assisted polling incremental adaptors. This means that
 * the adaptor can provide incremental changes, must poll to notice changes,
 * and wants the adaptor library to help out.
 *
 * <p>If the adaptor has no <em>easy</em> way of determining repository changes,
 * then it should not implement this interface and let the GSA naturally find
 * changes. If the adaptor can use an event-based method of discovering changes,
 * it should use that method instead. If an adaptor doesn't want to relinguish
 * control to the frequency of the polling and other such details, then there is
 * no need to use this interface; simply manually using {@link java.util.Timer}
 * is appropriate for a adaptor to do if it wishes.
 *
 * <p>Implementing this interface does improve the ease of configuring the
 * adaptor for the user. Thus, adaptors are encouraged to implement this
 * interface if it is applicable.
 */
public interface PollingIncrementalLister {
  /**
   * Check for documents modified since the last call to the method. This method
   * is intended to provide little-effort updates to the GSA about recent
   * modifications. Providing updates here allows greatly decreasing the amount
   * of latency before the GSA notices a document was added/modified/deleted,
   * but nothing more. It does not need to be perfect since the GSA's recrawling
   * will notice modifications and deletions and {@link Adaptor#getDocIds} will
   * provide additions.
   *
   * <p>For the first invocation, implementations can simply provide a small
   * amount of very recent history (e.g., last five revisions or last hour of
   * modifications) or provide no history and just initialize data structures
   * for future invocations. Adaptors are always encouraged to not persist
   * state; providing recent history during the first invocation allows the
   * adaptor to handle upgrades, computer restarts, and power outages without
   * trouble and still provide low-latency for those time periods. If an hour is
   * too short a period of time, then feel free to send a day's worth of history
   * instead. However, remember that missing modifications here only increases
   * the amount of latency before the GSA notices the modification.
   *
   * @param pusher convenience reference to pusher
   * @throws IOException on failure getting doc ids
   * @throws InterruptedException may percolate from IO calls
   */
  public void getModifiedDocIds(DocIdPusher pusher) throws IOException,
      InterruptedException;
}
