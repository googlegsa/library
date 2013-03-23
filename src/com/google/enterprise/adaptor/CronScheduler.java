package com.google.enterprise.adaptor;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes runnables based on whether the current time matches the provided
 * cron time specification.
 */
class CronScheduler {
  private static final Logger log
      = Logger.getLogger(CronScheduler.class.getName());

  private final ScheduledExecutorService executor;
  private final TimeProvider timeProvider;
  private final TimeZone timeZone;

  public CronScheduler(ScheduledExecutorService executor) {
    this(executor, new SystemTimeProvider(), TimeZone.getDefault());
  }

  CronScheduler(ScheduledExecutorService executor, TimeProvider timeProvider,
      TimeZone timeZone) {
    if (executor == null) {
      throw new NullPointerException();
    }
    this.executor = executor;
    this.timeProvider = timeProvider;
    this.timeZone = timeZone;
  }

  /**
   * Long-running runnables are not supported; the runnable must be completed by
   * the time the next minute arrives.
   *
   * @return a Future representing pending completion of the task, and whose
   *     get() method will throw an exception upon cancellation.
   */
  public Future<?> schedule(String pattern, Runnable runnable)
      throws IllegalArgumentException {
    CronPattern compiledPattern = CronPattern.create(pattern);
    CronFilterRunnable toRun
        = new CronFilterRunnable(compiledPattern, runnable);
    final long minuteMillis = 60 * 1000;
    long delayToNextMinuteMillis
        = minuteMillis - (timeProvider.currentTimeMillis() % minuteMillis);
    // Add a second to the delay, so we are less likely to fire before the
    // minute starts.
    long delayMillis = delayToNextMinuteMillis + 1000;
    // If toRun takes a while to run, invocations to it will be queued and will
    // be run as soon as it completes.
    Future<?> future = executor.scheduleAtFixedRate(
        toRun, delayMillis, minuteMillis, TimeUnit.MILLISECONDS);
    return CronFuture.create(future, toRun);
  }

  public void reschedule(Future<?> future, String pattern) {
    if (!(future instanceof CronFuture)) {
      throw new IllegalArgumentException("Provided future is not a CronFuture");
    }
    CronFuture cronFuture = (CronFuture) future;
    CronPattern compiledPattern = CronPattern.create(pattern);
    cronFuture.getCronFilterRunnable().setPattern(compiledPattern);
  }

  /**
   * Manager of per-schedule information.
   */
  private class CronFilterRunnable implements Runnable {
    private volatile CronPattern pattern;
    private final Runnable delegate;

    public CronFilterRunnable(CronPattern pattern, Runnable delegate) {
      this.pattern = pattern;
      this.delegate = delegate;
    }

    @Override
    public void run() {
      try {
        Date now = new Date(timeProvider.currentTimeMillis());
        if (pattern.matches(now, timeZone)) {
          delegate.run();
        }
      } catch (Exception ex) {
        // We need to prevent any exceptions from being thrown, because that
        // would cause this task not to be run again.
        log.log(Level.WARNING, "Exception during cron task", ex);
      }
    }

    public CronPattern getPattern() {
      return pattern;
    }

    public void setPattern(CronPattern pattern) {
      this.pattern = pattern;
    }
  }

  /**
   * Forwards all calls to the delegate returned by {@link #delegate}.
   */
  private abstract static class ForwardingFuture<V> implements Future<V> {
    protected abstract Future<V> delegate();

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return delegate().cancel(mayInterruptIfRunning);
    }

    @Override
    public V get() throws ExecutionException, InterruptedException {
      return delegate().get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws ExecutionException,
        InterruptedException, TimeoutException {
      return delegate().get(timeout, unit);
    }

    @Override
    public boolean isCancelled() {
      return delegate().isCancelled();
    }

    @Override
    public boolean isDone() {
      return delegate().isDone();
    }
  }

  private static class CronFuture<V> extends ForwardingFuture<V> {
    private final Future<V> delegate;
    private final CronFilterRunnable runnable;

    public CronFuture(Future<V> delegate, CronFilterRunnable runnable) {
      this.delegate = delegate;
      this.runnable = runnable;
    }

    public static <V> CronFuture<V> create(
        Future<V> delegate, CronFilterRunnable runnable) {
      return new CronFuture<V>(delegate, runnable);
    }

    @Override
    protected Future<V> delegate() {
      return delegate;
    }

    public CronFilterRunnable getCronFilterRunnable() {
      return runnable;
    }

    @Override
    public String toString() {
      return "CronFuture(" + runnable.getPattern().toString() + ")";
    }
  }

  private static class CronPattern {
    private static final List<Field> FIELDS = Arrays.asList(Field.values());

    private static enum Field {
      MINUTE(0, 60, Calendar.MINUTE, 0),
      HOUR(0, 24, Calendar.HOUR_OF_DAY, 0),
      DAY_OF_MONTH(1, 31, Calendar.DAY_OF_MONTH, 1),
      MONTH(1, 12, Calendar.MONTH, 0),
      DAY_OF_WEEK(0, 7, Calendar.DAY_OF_WEEK, 1) {
        @Override
        public int numberToIndex(int i) {
          // Sunday is both 0 and 7.
          if (i == 7) {
            i = 0;
          }
          return super.numberToIndex(i);
        }
      };

      private final int offset;
      private final int numValues;
      private final int calendarField;
      private final int calendarOffset;

      private Field(int offset, int numValues, int calendarField,
          int calendarOffset) {
        this.offset = offset;
        this.numValues = numValues;
        this.calendarField = calendarField;
        this.calendarOffset = calendarOffset;
      }

      public int numberToIndex(int i) {
        if (i < offset || i >= offset + numValues) {
          throw new IllegalArgumentException("Bounds for " + name()
              + " is " + offset + "-" + (offset + numValues - 1) + ": " + i);
        }
        return i - offset;
      }

      public int calendarToIndex(Calendar calendar) {
        return calendar.get(calendarField) - calendarOffset;
      }

      public int numValues() {
        return numValues;
      }
    }

    // minute: 0-59
    // hour: 0-23
    // day of month: 0-30, 0 is 1st day of month.
    // month: 0-11, 0 in January.
    // day of week: 0-6, 0 is Sunday.
    private final List<BitSet> fields;

    private CronPattern(List<BitSet> fields) {
      this.fields = fields;
    }

    public boolean matches(Date date, TimeZone timeZone) {
      GregorianCalendar calendar = new GregorianCalendar(timeZone);
      calendar.setTime(date);
      for (int i = 0; i < FIELDS.size(); i++) {
        Field field = FIELDS.get(i);
        if (!fields.get(i).get(field.calendarToIndex(calendar))) {
          return false;
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return "" + fields;
    }

    public static CronPattern create(String timeSpecification)
        throws IllegalArgumentException {
      timeSpecification = timeSpecification.trim();
      String[] stringFields = timeSpecification.split("\\s+", -1);
      if (stringFields.length != FIELDS.size()) {
        throw new IllegalArgumentException(
            "Should have precisely 5 fields: "
            + "minute, hour, day of month, month, day of week");
      }
      List<BitSet> fields = new ArrayList<BitSet>(FIELDS.size());
      for (int i = 0; i < FIELDS.size(); i++) {
        String stringField = stringFields[i];
        Field fieldType = FIELDS.get(i);
        BitSet set = new BitSet(fieldType.numValues());
        for (String element : stringField.split(",", -1)) {
          int step = 1;
          {
            String[] parts = element.split("/", 2);
            if (parts.length == 2) {
              element = parts[0];
              step = Integer.parseInt(parts[1]);
            }
          }

          if ("*".equals(element)) {
            for (int j = 0; j < fieldType.numValues(); j += step) {
              set.set(j);
            }
          } else { // Is numerical, which permits ranges.
            int[] range = stringsToInts(element.split("-", 2));
            if (range.length == 1) {
              set.set(fieldType.numberToIndex(range[0]));
            } else {
              // Set each individually since after numberToIndex, range[0] may
              // be larger than range[1] in the case of weekdays. This also
              // causes trouble with steps with ranges (1-7/2 should become
              // 0,1,3,5), so handle step at the same time.
              for (int j = range[0]; j <= range[1]; j += step) {
                set.set(fieldType.numberToIndex(j));
              }
            }
          }
        }
        fields.add(set);
      }
      return new CronPattern(fields);
    }

    private static int[] stringsToInts(String[] ss) {
      int[] is = new int[ss.length];
      for (int i = 0; i < ss.length; i++) {
        is[i] = Integer.parseInt(ss[i]);
      }
      return is;
    }
  }
}
