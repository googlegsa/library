package com.google.enterprise.adaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Test cases for {@link CronScheduler}. */
public class CronSchedulerTest {
  private ScheduledExecutorService executor
      = Executors.newSingleThreadScheduledExecutor();
  private Runnable noopRunnable = new Runnable() {
    @Override
    public void run() {}
  };

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @After
  public void tearDown() {
    executor.shutdownNow();
  }

  @Test
  public void testNullExecutor() {
    thrown.expect(NullPointerException.class);
    new CronScheduler(null);
  }

  @Test
  public void testWrongFutureReschedule() {
    CronScheduler scheduler = new CronScheduler(executor);
    Future<?> future = new FutureTask<Object>(noopRunnable, null);
    thrown.expect(IllegalArgumentException.class);
    scheduler.reschedule(future, "* * * * *");
  }

  @Test
  public void testParsing() {
    CronScheduler scheduler = new CronScheduler(executor);
    assertEquals("CronFuture([{0}, {0}, {0}, {0}, {0}])",
        scheduler.schedule("0 0 1 1 0", noopRunnable).toString());
    assertEquals("CronFuture([{59}, {23}, {30}, {11}, {6}])",
        scheduler.schedule("59 23 31 12 6", noopRunnable).toString());
    assertEquals("CronFuture([{0}, {0}, {0}, {0}, {0, 1, 3, 5}])",
        scheduler.schedule("0 0 1 1 1-7/2", noopRunnable).toString());
    assertEquals("CronFuture([{0}, {0}, {1, 3, 5, 7, 9, 11}, {0}, {0}])",
        scheduler.schedule("0 0 2-13/2 1 0", noopRunnable).toString());
    assertEquals("CronFuture([{0}, {0}, {0}, {0}, null])",
        scheduler.schedule("0 0 1 1 *", noopRunnable).toString());
    assertEquals("CronFuture([{0}, {0}, {0}, {0}, {0, 1, 2, 3, 4, 5, 6}])",
        scheduler.schedule("0 0 1 1 */1", noopRunnable).toString());
  }

  @Test
  public void testWrongNumberOfFields() {
    CronScheduler scheduler = new CronScheduler(executor);
    thrown.expect(IllegalArgumentException.class);
    scheduler.schedule("0 0 1 1 0 0", noopRunnable);
  }

  @Test
  public void testValueNotInRange() {
    CronScheduler scheduler = new CronScheduler(executor);
    thrown.expect(IllegalArgumentException.class);
    scheduler.schedule("0 0 1 0 0", noopRunnable);
  }

  @Test
  public void testValueNotInRange2() {
    CronScheduler scheduler = new CronScheduler(executor);
    thrown.expect(IllegalArgumentException.class);
    scheduler.schedule("0 0 1 1000 0", noopRunnable);
  }

  @Test
  public void testNotANumber() {
    CronScheduler scheduler = new CronScheduler(executor);
    thrown.expect(IllegalArgumentException.class);
    scheduler.schedule("0 0 1 wat 0", noopRunnable);
  }

  @Test
  public void testCatchesException() {
    executor.shutdownNow();
    final AtomicReference<Runnable> atomicCommand
        = new AtomicReference<Runnable>();
    executor = new ScheduledThreadPoolExecutor(1) {
      @Override
      public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
          long initialDelay, long period, TimeUnit unit) {
        atomicCommand.set(command);
        // Set high delay, because we don't want it to actually run.
        return super.scheduleAtFixedRate(command, 100000, period, unit);
      }
    };
    MockTimeProvider timeProvider = new MockTimeProvider();
    timeProvider.autoIncrement = false;
    TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");
    CronScheduler scheduler
        = new CronScheduler(executor, timeProvider, timeZone);
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        throw new RuntimeException("This should be caught");
      }
    };
    // Capture internally-used runnable.
    Future<?> future = scheduler.schedule("* * * * *", runnable);
    // Should not throw an exception.
    atomicCommand.get().run();
  }

  @Test
  public void testRunning() throws Exception {
    executor.shutdownNow();
    final AtomicReference<Runnable> atomicCommand
        = new AtomicReference<Runnable>();
    executor = new ScheduledThreadPoolExecutor(1) {
      @Override
      public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
          long initialDelay, long period, TimeUnit unit) {
        atomicCommand.set(command);
        assertEquals((60 * 1000) - (10 * 1000) + 1000, initialDelay);
        assertEquals(60 * 1000, period);
        assertEquals(TimeUnit.MILLISECONDS, unit);
        // Set high delay, because we don't want it to actually run.
        return super.scheduleAtFixedRate(command, 100000, period, unit);
      }
    };
    MockTimeProvider timeProvider = new MockTimeProvider();
    timeProvider.autoIncrement = false;
    TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");
    CronScheduler scheduler
        = new CronScheduler(executor, timeProvider, timeZone);
    final AtomicLong counter = new AtomicLong();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        counter.incrementAndGet();
      }
    };
    // 1970-01-01T00:00:10Z
    timeProvider.time = 10 * 1000;
    // Capture internally-used runnable.
    Future<?> future = scheduler.schedule("2 2 2 2 1", runnable);
    counter.set(0);

    // 1970-02-02T10:02:01Z, which is a Monday. That is 02:02:01 in PST.
    timeProvider.time = 2800921L * 1000;
    atomicCommand.get().run();
    assertEquals(1, counter.get());

    // Wrong minute.
    // 1970-02-02T10:01:01Z, which is a Monday. That is 02:01:01 in PST.
    timeProvider.time = 2800861L * 1000;
    atomicCommand.get().run();
    assertEquals(1, counter.get());

    // Wrong hour.
    // 1970-02-02T09:02:01Z, which is a Monday. That is 01:02:01 in PST.
    timeProvider.time = 2797321L * 1000;
    atomicCommand.get().run();
    assertEquals(1, counter.get());

    // Wrong day of month.
    // 1970-02-02T10:02:01Z, which is a Monday. That is 02:02:01 in PST.
    timeProvider.time = 2800921L * 1000;
    scheduler.reschedule(future, "2 2 1 2 *");
    atomicCommand.get().run();
    assertEquals(1, counter.get());

    // Wrong month.
    // 1970-02-02T10:02:01Z, which is a Monday. That is 02:02:01 in PST.
    timeProvider.time = 2800921L * 1000;
    scheduler.reschedule(future, "2 2 2 1 1");
    atomicCommand.get().run();
    assertEquals(1, counter.get());

    // Wrong day of week.
    // 1970-02-02T10:02:01Z, which is a Monday. That is 02:02:01 in PST.
    timeProvider.time = 2800921L * 1000;
    scheduler.reschedule(future, "2 2 * 2 2");
    atomicCommand.get().run();
    assertEquals(1, counter.get());

    // Wrong day of week and day of month.
    // 1970-02-02T10:02:01Z, which is a Monday. That is 02:02:01 in PST.
    timeProvider.time = 2800921L * 1000;
    scheduler.reschedule(future, "2 2 1 2 2");
    atomicCommand.get().run();
    assertEquals(1, counter.get());

    // Wrong day of week, but right day of month.
    // 1970-02-02T10:02:01Z, which is a Monday. That is 02:02:01 in PST.
    timeProvider.time = 2800921L * 1000;
    scheduler.reschedule(future, "2 2 2 2 2");
    atomicCommand.get().run();
    assertEquals(2, counter.get());

    // Wrong day of month, but right day of week.
    // 1970-02-02T10:02:01Z, which is a Monday. That is 02:02:01 in PST.
    timeProvider.time = 2800921L * 1000;
    scheduler.reschedule(future, "2 2 1 2 1");
    atomicCommand.get().run();
    assertEquals(3, counter.get());
  }

  @Test
  public void testRunsTwice() throws Exception {
    executor.shutdownNow();
    executor = new ScheduledThreadPoolExecutor(1) {
      @Override
      public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
          long initialDelay, long period, TimeUnit unit) {
        assertEquals((60 * 1000) - (10 * 1000) + 1000, initialDelay);
        assertEquals(60 * 1000, period);
        assertEquals(TimeUnit.MILLISECONDS, unit);
        // Set low delay, because we want it to run soon.
        return super.scheduleAtFixedRate(command, 1, 10, unit);
      }
    };
    final MockTimeProvider timeProvider = new MockTimeProvider();
    timeProvider.autoIncrement = false;
    TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");
    CronScheduler scheduler
        = new CronScheduler(executor, timeProvider, timeZone);
    final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        assertTrue(queue.offer(new Object()));
        timeProvider.time += 60 * 1000;
      }
    };
    // 1970-01-01T00:00:10Z
    timeProvider.time = 10 * 1000;
    // Capture internally-used runnable.
    Future<?> future = scheduler.schedule("* * * * *", runnable);
    assertNotNull(queue.poll(100, TimeUnit.MILLISECONDS));
    assertNotNull(queue.poll(100, TimeUnit.MILLISECONDS));
    future.cancel(false);
  }
}
