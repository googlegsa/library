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

import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A generic scheduler similar in use to {@link Timer}, but more general. You
 * should use it as if it were {@code Timer}, but with a slightly different API.
 * Gratitude to
 * http://www.ibm.com/developerworks/java/library/j-schedule/index.html
 */
public class Scheduler {
  private final Timer timer;

  public Scheduler() {
    timer = new Timer();
  }

  public Scheduler(boolean isDaemon) {
    timer = new Timer(isDaemon);
  }

  public void cancel() {
      timer.cancel();
  }

  public void schedule(SchedulerTask schedulerTask, Iterator<Date> iterator) {
    Date time = iterator.hasNext() ? iterator.next() : null;
    if (time == null) {
      schedulerTask.cancel();
    } else {
      synchronized (schedulerTask.lock) {
        if (schedulerTask.state != SchedulerTask.VIRGIN) {
          throw new IllegalStateException("already scheduled or cancelled");
        }
        schedulerTask.state = SchedulerTask.SCHEDULED;
        schedulerTask.timerTask
            = new SchedulerTimerTask(schedulerTask, iterator);
        timer.schedule(schedulerTask.timerTask, time);
      }
    }
  }

  private void reschedule(SchedulerTask schedulerTask,
                          Iterator<Date> iterator) {
    Date time = iterator.hasNext() ? iterator.next() : null;
    if (time == null) {
      schedulerTask.cancel();
    } else {
      synchronized (schedulerTask.lock) {
        if (schedulerTask.state != SchedulerTask.CANCELLED) {
          schedulerTask.timerTask
              = new SchedulerTimerTask(schedulerTask, iterator);
          timer.schedule(schedulerTask.timerTask, time);
        }
      }
    }
  }

  private class SchedulerTimerTask extends TimerTask {
    private SchedulerTask schedulerTask;
    private Iterator<Date> iterator;

    public SchedulerTimerTask(SchedulerTask schedulerTask,
                              Iterator<Date> iterator) {
      this.schedulerTask = schedulerTask;
      this.iterator = iterator;
    }

    public void run() {
      schedulerTask.run();
      reschedule(schedulerTask, iterator);
    }
  }
}
