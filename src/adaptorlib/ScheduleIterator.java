package adaptorlib;
import java.util.Date;
/**
 * Provdes sequence of Date instances.  Intended to provide
 * chronologically increasing points in time to Scheduler.
 *
 * Gratitude to
 * http://www.ibm.com/developerworks/java/library/j-schedule/index.html
 */
public interface ScheduleIterator {
  public Date next();
}
