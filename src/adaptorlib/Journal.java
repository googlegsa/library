package adaptorlib;

import java.util.*;

/**
 * Contains registers and stats regarding runtime.
 */
class Journal {
  private HashMap<DocId, Integer> timesPushed
      = new HashMap<DocId, Integer>();
  private long totalPushes;

  private HashMap<DocId, Integer> timesGsaRequested
      = new HashMap<DocId, Integer>();
  private long totalGsaRequests;

  private HashMap<DocId, Integer> timesNonGsaRequested
      = new HashMap<DocId, Integer>();
  private long totalNonGsaRequests;

  private final long startedAt = System.currentTimeMillis();
  /**
   * Resolution of {@link System#currentTypeMillis()} to the millisecond.
   */
  private final long timeResolution = determineTimeResolution();

  /**
   * Time-based bookkeeping for charts. Each element in the array is for a
   * different time period.
   */
  private Stats[] timeStats;

  /** Response start time storage until response completion. */
  private ThreadLocal<Long> requestResponseStart = new ThreadLocal<Long>();
  /** Request processing start time storage until processing completion. */
  private ThreadLocal<Long> requestProcessingStart = new ThreadLocal<Long>();

  public Journal() {
    // We want data within the Stats to agree with each other, so we provide the
    // same time to each of them.
    long time = System.currentTimeMillis();
    this.timeStats = new Stats[] {
      new Stats(60, 1000,           time), /* one minute, second granularity */
      new Stats(60, 1000 * 60,      time), /* one hour, minute granularity */
      new Stats(48, 1000 * 60 * 30, time), /* one day, half-hour granularity */
    };
  }

  synchronized void recordDocIdPush(List<DocId> pushed) {
    for (DocId id : pushed) {
      increment(timesPushed, id);
    }
    totalPushes += pushed.size();
  }

  synchronized void recordGsaContentRequest(DocId docId) {
    increment(timesGsaRequested, docId); 
    totalGsaRequests++;
  }

  synchronized void recordNonGsaContentRequest(DocId requested) {
    increment(timesNonGsaRequested, requested); 
    totalNonGsaRequests++;
  }

  /**
   * Record that the response of a request has been started on this thread. This
   * relates to the actual I/O of sending the response.
   */
  void recordRequestResponseStart() {
    requestResponseStart.set(System.currentTimeMillis());
  }

  /**
   * Record that the response this thread was sending has completed.
   */
  void recordRequestResponseEnd(long responseSize) {
    long time = System.currentTimeMillis();
    long duration = endDuration(requestResponseStart, time);
    synchronized (this) {
      for (Stats stats : timeStats) {
        Stat stat = stats.getCurrentStat(time);
        stat.requestResponsesCount++;
        stat.requestResponsesDurationSum += duration;
        stat.requestResponsesMaxDuration = Math.max(
            stat.requestResponsesMaxDuration, duration);
        stat.requestResponsesThroughput += responseSize;
      }
    }
  }

  /**
   * Record that the processing of a request has been started on this thread.
   * This relates to internal computation required to satisfy the request.
   */
  void recordRequestProcessingStart() {
    requestProcessingStart.set(System.currentTimeMillis());
  }

  /**
   * Record that the processing this thread was performing to satify the request
   * has completed.
   */
  void recordRequestProcessingEnd(long responseSize) {
    long time = System.currentTimeMillis();
    long duration = endDuration(requestProcessingStart, time);
    synchronized (this) {
      for (Stats stats : timeStats) {
        Stat stat = stats.getCurrentStat(time);
        stat.requestProcessingsCount++;
        stat.requestProcessingsDurationSum += duration;
        stat.requestProcessingsMaxDuration = Math.max(
            stat.requestProcessingsMaxDuration, duration);
        stat.requestProcessingsThroughput += responseSize;
      }
    }
  }

  private long endDuration(ThreadLocal<Long> localStartTime, long endTime) {
    Long startTime = localStartTime.get();
    localStartTime.remove();
    if (startTime == null) {
      throw new IllegalStateException("Record start must be called before "
                                      + "record end");
    }
    return endTime - startTime;
  }

  /**
   * Determine the resolution that {@link System#currentTimeMillis} supports, in
   * milliseconds.
   */
  private long determineTimeResolution() {
    long resolution = Long.MAX_VALUE;
    for (int i = 0; i < 5; i++) {
      resolution = Math.min(resolution, determineTimeResolutionOnce());
    }
    return resolution;
  }

  /**
   * One trial of determining the resolution that {@link
   * System#currentTimeMillis} supports, in milliseconds.
   */
  private long determineTimeResolutionOnce() {
    long time = System.currentTimeMillis();
    long startTime = time;
    while (startTime == time) {
      time = System.currentTimeMillis();
    }
    return time - startTime;
  }

  private static void increment(HashMap<DocId, Integer> counts, DocId id) {
    if (!counts.containsKey(id)) {
      counts.put(id, 0);
    }
    counts.put(id, 1 + counts.get(id));
  }

  /**
   * Access to the timeStats for use in {@link DashboardHandler} only.
   */
  synchronized JournalSnapshot getSnapshot() {
    long currentTime = System.currentTimeMillis();
    Stats[] timeStatsClone = new Stats[timeStats.length];
    for (int i = 0; i < timeStats.length; i++) {
      // Cause stats to update its internal structures
      timeStats[i].getCurrentStat(currentTime);
      try {
        timeStatsClone[i] = (Stats) timeStats[i].clone();
      } catch (CloneNotSupportedException ex) {
        throw new IllegalStateException(ex);
      }
    }

    return new JournalSnapshot(this, currentTime, timeStatsClone);
  }

  static class JournalSnapshot {
    final long numUniqueDocIdsPushed;
    final long numTotalDocIdsPushed;
    final long numUniqueGsaRequests;
    final long numTotalGsaRequests;
    final long numUniqueNonGsaRequests;
    final long numTotalNonGsaRequests;
    final long whenStarted;
    final long currentTime;
    final long timeResolution;
    final Stats[] timeStats;

    private JournalSnapshot(Journal journal, long currentTime,
                            Stats[] timeStatsClone) {
      this.numUniqueDocIdsPushed = journal.timesPushed.size();
      this.numTotalDocIdsPushed = journal.totalPushes;
      this.numUniqueGsaRequests = journal.timesGsaRequested.size();
      this.numTotalGsaRequests = journal.totalGsaRequests;
      this.numUniqueNonGsaRequests = journal.timesNonGsaRequested.size();
      this.numTotalNonGsaRequests = journal.totalNonGsaRequests;
      this.timeResolution = journal.timeResolution;
      this.whenStarted = journal.startedAt;
      this.currentTime = currentTime;
      this.timeStats = timeStatsClone;
    }
  }

  static class Stats implements Cloneable {
    /**
     * Circular buffer containing all the statistics this object contains. When
     * the current {@link Stat} "expires", {@link #currentStat} is incremented
     * (floor {@code stats.length}) and the new current {@code Stat} object is
     * reset.
     */
    Stat[] stats;
    /**
     * The amount of time each {@link Stat} object's statistics were measured.
     */
    long snapshotDurationMs;
    /**
     * Current {@link Stat} object within {@link #stats}. All incoming
     * statistics should update the current {@code Stat} object.
     */
    int currentStat;
    /**
     * Time that the current {@link Stat} object will expire and {@link
     * #currentStat} will be incremented.
     */
    long pendingStatPeriodEnd;

    public Stats(int statCount, long snapshotDuration, long currentTime) {
      this.snapshotDurationMs = snapshotDuration;

      this.stats = new Stat[statCount];
      // Pre-allocate all Stat objects
      for (int i = 0; i < this.stats.length; i++) {
        this.stats[i] = new Stat();
      }

      // Initialize expiration times for pendingStat objects
      long duration = this.snapshotDurationMs;
      this.pendingStatPeriodEnd = ((currentTime / duration) * duration)
          + duration;
    }

    /**
     * Retrive the current {@code Stat} object that applies to {@code
     * currentTime}. {@code currentTime} is expected to be an actual point in
     * time while the caller was holding the lock on {@link Journal}. It may
     * never be less than the previous call.
     */
    public Stat getCurrentStat(long currentTime) {
      // Check if the current Stat object is still valid to write to
      if (pendingStatPeriodEnd > currentTime) {
        return stats[currentStat];
      }
      // Check if all the Stat objects are invalid. This occurs when the last
      // request was a long time ago.
      if (currentTime - pendingStatPeriodEnd
          > snapshotDurationMs * stats.length) {
        for (int i = 0; i < stats.length; i++) {
          stats[i].reset();
        }
        long duration = snapshotDurationMs;
        pendingStatPeriodEnd = ((currentTime / duration) * duration) + duration;
      }
      // Walk through time to get the current Stat object
      while (pendingStatPeriodEnd <= currentTime) {
        currentStat++;
        currentStat %= stats.length;
        stats[currentStat].reset();
        pendingStatPeriodEnd += snapshotDurationMs;
      }
      return stats[currentStat];
    }

    public Object clone() throws CloneNotSupportedException {
      Stats statsClone = (Stats) super.clone();
      statsClone.stats = new Stat[stats.length];
      for (int i = 0; i < stats.length; i++) {
        statsClone.stats[i] = (Stat) stats[i].clone();
      }
      return statsClone;
    }
  }

  /**
   * Structure for holding statistics data. Data applies to a time period only,
   * as controlled by {@link Stats}.
   */
  static class Stat implements Cloneable {
    /**
     * The number of responses sent.
     */
    long requestResponsesCount;
    /**
     * The total duration of responses sent.
     */
    long requestResponsesDurationSum;
    /**
     * The maximal duration of any one response.
     */
    long requestResponsesMaxDuration;
    /**
     * Number of bytes sent to client.
     */
    long requestResponsesThroughput;

    /**
     * The number of requests processed.
     */
    long requestProcessingsCount;
    /**
     * The total duration of response processings.
     */
    long requestProcessingsDurationSum;
    /**
     * The maximal duration of any one response processing.
     */
    long requestProcessingsMaxDuration;
    /**
     * Number of bytes generated by the adaptor.
     */
    long requestProcessingsThroughput;

    public Stat() {
      reset();
    }

    /**
     * Reset object for reuse.
     */
    private void reset() {
      requestResponsesCount = 0;
      requestResponsesDurationSum = 0;
      requestResponsesMaxDuration = 0;
      requestResponsesThroughput = 0;

      requestProcessingsCount = 0;
      requestProcessingsDurationSum = 0;
      requestProcessingsMaxDuration = 0;
      requestProcessingsThroughput = 0;
    }

    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }
}
