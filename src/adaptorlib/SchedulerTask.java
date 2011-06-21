package adaptorlib;
import java.util.TimerTask;
/**
 * Gratitude to http://www.ibm.com/developerworks/java/library/j-schedule/index.html
 */
abstract class SchedulerTask implements Runnable {
  final Object lock = new Object();

  int state = VIRGIN;
  static final int VIRGIN = 0;
  static final int SCHEDULED = 1;
  static final int CANCELLED = 2;

  TimerTask timerTask;

  protected SchedulerTask() {
  }

  public abstract void run();

  public boolean cancel() {
    synchronized(lock) {
      if (timerTask != null) {
        timerTask.cancel();
      }
      boolean result = (state == SCHEDULED);
      state = CANCELLED;
      return result;
    }
  }

  public long scheduledExecutionTime() {
    synchronized(lock) {
      return timerTask == null ? 0 : timerTask.scheduledExecutionTime();
    }
  }
}
