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

import java.util.TimerTask;

/**
 * A task that can be scheduled for execution by a {@link Scheduler}.
 * Gratitude to
 * http://www.ibm.com/developerworks/java/library/j-schedule/index.html
 */
public abstract class SchedulerTask implements Runnable {
  static final int VIRGIN = 0;
  static final int SCHEDULED = 1;
  static final int CANCELLED = 2;

  int state = VIRGIN;
  final Object lock = new Object();
  TimerTask timerTask;

  protected SchedulerTask() {
  }

  public abstract void run();

  public boolean cancel() {
    synchronized (lock) {
      if (timerTask != null) {
        timerTask.cancel();
      }
      boolean result = (state == SCHEDULED);
      state = CANCELLED;
      return result;
    }
  }

  public long scheduledExecutionTime() {
    synchronized (lock) {
      return timerTask == null ? 0 : timerTask.scheduledExecutionTime();
    }
  }
}
