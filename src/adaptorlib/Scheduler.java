package adaptorlib;

import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Gratitude to
 * http://www.ibm.com/developerworks/java/library/j-schedule/index.html
 */
class Scheduler {
  private final Timer timer = new Timer();

  public Scheduler() {
  }

  public void cancel() {
      timer.cancel();
  }

  public void schedule(Task schedulerTask, Iterator<Date> iterator) {
    Date time = iterator.next();
    if (time == null) {
      schedulerTask.cancel();
    } else {
      synchronized (schedulerTask.lock) {
        if (schedulerTask.state != Task.VIRGIN) {
          throw new IllegalStateException("already scheduled or cancelled");
        }
        schedulerTask.state = Task.SCHEDULED;
        schedulerTask.timerTask
            = new SchedulerTimerTask(schedulerTask, iterator);
        timer.schedule(schedulerTask.timerTask, time);
      }
    }
  }

  private void reschedule(Task schedulerTask, Iterator<Date> iterator) {
    Date time = iterator.hasNext() ? iterator.next() : null;
    if (time == null) {
      schedulerTask.cancel();
    } else {
      synchronized (schedulerTask.lock) {
        if (schedulerTask.state != Task.CANCELLED) {
          schedulerTask.timerTask
              = new SchedulerTimerTask(schedulerTask, iterator);
          timer.schedule(schedulerTask.timerTask, time);
        }
      }
    }
  }

  private class SchedulerTimerTask extends TimerTask {
    private Task schedulerTask;
    private Iterator<Date> iterator;

    public SchedulerTimerTask(Task schedulerTask, Iterator<Date> iterator) {
      this.schedulerTask = schedulerTask;
      this.iterator = iterator;
    }

    public void run() {
      schedulerTask.run();
      reschedule(schedulerTask, iterator);
    }
  }

  /**
   * Gratitude to
   * http://www.ibm.com/developerworks/java/library/j-schedule/index.html
   */
  public abstract static class Task implements Runnable {
    private int state = VIRGIN;
    private final Object lock = new Object();

    private static final int VIRGIN = 0;
    private static final int SCHEDULED = 1;
    private static final int CANCELLED = 2;

    private TimerTask timerTask;

    protected Task() {
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
}
