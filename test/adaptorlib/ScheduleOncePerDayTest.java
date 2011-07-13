package adaptorlib;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;
import org.junit.Test;

public class ScheduleOncePerDayTest {
  @Test
  public void testIteration() {
    // Simple case of no DST changeover
    final int millisecondsInADay = 1000 * 60 * 60 * 24;
    Date date = new Date(0);
    // localtime's 7:00 AM on January 1st in utc (assuming northern hemisphere -
    // thus no DST)
    final long startTime = 1000 * 60 * 60 * 7
        - Calendar.getInstance().get(Calendar.ZONE_OFFSET);
    ScheduleOncePerDay it = new ScheduleOncePerDay(7, 0, 0, date);
    Date cur = it.next();
    assertEquals(startTime + millisecondsInADay * 0, cur.getTime());
    cur = it.next();
    assertEquals(startTime + millisecondsInADay * 1, cur.getTime());
    cur = it.next();
    assertEquals(startTime + millisecondsInADay * 2, cur.getTime());
    cur = it.next();
    assertEquals(startTime + millisecondsInADay * 3, cur.getTime());
    assertTrue(it.hasNext());
  }
}
