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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains registers and stats regarding runtime.
 */
class Journal {
  private Map<DocId, Integer> timesPushed;
  private long totalPushes;

  private Map<DocId, Integer> timesGsaRequested;
  private long totalGsaRequests;

  private Map<DocId, Integer> timesNonGsaRequested;
  private long totalNonGsaRequests;

  private final TimeProvider timeProvider;
  private final long startedAt;
  /**
   * Resolution of {@link System#currentTypeMillis()} to the millisecond.
   */
  private final long timeResolution;

  /**
   * Time-based bookkeeping for charts. Each element in the array is for a
   * different time period.
   */
  private Stats[] timeStats;
  private Stats dayStatsByHalfHour;

  /** Request processing start time storage until processing completion. */
  private ThreadLocal<Long> requestProcessingStart = new ThreadLocal<Long>();

  /**
   * Date in milliseconds of current full push start. If zero, then there is not
   * a running full push.
   */
  private long currentFullPushStart;
  /** Date in milliseconds. */
  private long lastSuccessfulFullPushStart;
  /** Date in milliseconds. */
  private long lastSuccessfulFullPushEnd;
  private CompletionStatus lastFullPushStatus = CompletionStatus.SUCCESS;

  private long currentIncrementalPushStart;
  private long lastSuccessfulIncrementalPushStart;
  private long lastSuccessfulIncrementalPushEnd;
  private CompletionStatus lastIncrementalPushStatus = CompletionStatus.SUCCESS;

  enum CompletionStatus {
    SUCCESS,
    INTERRUPTION,
    FAILURE,
  }

  /**
   * @param reducedMem whether to use a fixed amount of memory, at the expense
   *     of some statistics being disabled
   */
  public Journal(boolean reducedMem) {
    this(reducedMem, new SystemTimeProvider());
  }

  /**
   * Same as {@code Journal(false, timeProvider)}.
   */
  protected Journal(TimeProvider timeProvider) {
    this(false, timeProvider);
  }

  protected Journal(boolean reducedMem, TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    this.startedAt = timeProvider.currentTimeMillis();
    this.timeResolution = determineTimeResolution();
    // We want data within the Stats to agree with each other, so we provide the
    // same time to each of them.
    long time = startedAt;
    this.timeStats = new Stats[] {
      new Stats(60, 1000,           time), /* one minute, second granularity */
      new Stats(60, 1000 * 60,      time), /* one hour, minute granularity */
      new Stats(48, 1000 * 60 * 30, time), /* one day, half-hour granularity */
    };
    this.dayStatsByHalfHour = this.timeStats[this.timeStats.length - 1];
    if (reducedMem) {
      timesPushed = new NegSizeFakeMap<DocId, Integer>();
      timesGsaRequested = new NegSizeFakeMap<DocId, Integer>();
      timesNonGsaRequested = new NegSizeFakeMap<DocId, Integer>();
    } else {
      timesPushed = new HashMap<DocId, Integer>();
      timesGsaRequested = new HashMap<DocId, Integer>();
      timesNonGsaRequested = new HashMap<DocId, Integer>();
    }
  }

  synchronized void recordDocIdPush(List<? extends DocIdSender.Item> pushed) {
    for (Object item : pushed) {
      if (item instanceof DocIdPusher.Record) {
        DocIdPusher.Record record = (DocIdPusher.Record) item;
        increment(timesPushed, record.getDocId());
      } else if (item instanceof DocIdSender.AclItem) {
        // Don't record any information.
      } else {
        throw new IllegalArgumentException("Unsupported class: "
                                           + item.getClass().getName());
      }
    }
    totalPushes += pushed.size();
  }

  void recordGsaContentRequest(DocId docId) {
    long time = timeProvider.currentTimeMillis();
    synchronized (this) {
      increment(timesGsaRequested, docId);
      totalGsaRequests++;
      for (Stats stats : timeStats) {
        Stat stat = stats.getCurrentStat(time);
        stat.gsaRetrievedDocument = true;
      }
    }
  }

  synchronized void recordNonGsaContentRequest(DocId requested) {
    increment(timesNonGsaRequested, requested); 
    totalNonGsaRequests++;
  }

  /**
   * Record that the processing of a request has been started on this thread.
   * This relates to internal computation required to satisfy the request.
   */
  void recordRequestProcessingStart() {
    requestProcessingStart.set(timeProvider.currentTimeMillis());
  }

  /**
   * Record that the processing this thread was performing to satisfy the
   * request has completed.
   */
  void recordRequestProcessingEnd(long responseSize) {
    recordRequestProcessingEnd(responseSize, timeProvider.currentTimeMillis());
  }

  private void recordRequestProcessingEnd(long responseSize, long time) {
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

  /**
   * Record that the processing this thread was performing to satisfy the
   * request has completed, but with an error.
   */
  void recordRequestProcessingFailure() {
    long time = timeProvider.currentTimeMillis();
    synchronized (this) {
      recordRequestProcessingEnd(0, time);
      for (Stats stats : timeStats) {
        Stat stat = stats.getCurrentStat(time);
        stat.requestProcessingsFailureCount++;
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
    long time = timeProvider.currentTimeMillis();
    long startTime = time;
    while (startTime == time) {
      time = timeProvider.currentTimeMillis();
    }
    return time - startTime;
  }

  private static void increment(Map<DocId, Integer> counts, DocId id) {
    if (!counts.containsKey(id)) {
      counts.put(id, 1);
    } else {
      counts.put(id, 1 + counts.get(id));
    }
  }

  /**
   * Record that a full push has started. Only one is tracked at a time.
   */
  synchronized void recordFullPushStarted() {
    if (currentFullPushStart != 0) {
      throw new IllegalStateException("Full push already started");
    }
    currentFullPushStart = timeProvider.currentTimeMillis();
  }

  /**
   * Record that the full push completed successfully.
   */
  void recordFullPushSuccessful() {
    long endTime = timeProvider.currentTimeMillis();
    synchronized (this) {
      long startTime = currentFullPushStart;
      currentFullPushStart = 0;
      this.lastSuccessfulFullPushStart = startTime;
      this.lastSuccessfulFullPushEnd = endTime;
      lastFullPushStatus = CompletionStatus.SUCCESS;
    }
  }

  /**
   * Record that the full push was interrupted prematurely.
   */
  synchronized void recordFullPushInterrupted() {
    if (currentFullPushStart == 0) {
      throw new IllegalStateException("Full push not already started");
    }
    currentFullPushStart = 0;
    lastFullPushStatus = CompletionStatus.INTERRUPTION;
  }

  /**
   * Record that the full push was interrupted prematurely.
   */
  synchronized void recordFullPushFailed() {
    if (currentFullPushStart == 0) {
      throw new IllegalStateException("Full push not already started");
    }
    currentFullPushStart = 0;
    lastFullPushStatus = CompletionStatus.FAILURE;
  }

  synchronized CompletionStatus getLastFullPushStatus() {
    return lastFullPushStatus;
  }

  /**
   * Record that an incremental push has started.
   */
  synchronized void recordIncrementalPushStarted() {
    if (currentIncrementalPushStart != 0) {
      throw new IllegalStateException("Incremental push already started");
    }
    currentIncrementalPushStart = timeProvider.currentTimeMillis();
  }

  /**
   * Record that the incremental push completed successfully.
   */
  void recordIncrementalPushSuccessful() {
    long endTime = timeProvider.currentTimeMillis();
    synchronized (this) {
      long startTime = currentIncrementalPushStart;
      currentIncrementalPushStart = 0;
      this.lastSuccessfulIncrementalPushStart = startTime;
      this.lastSuccessfulIncrementalPushEnd = endTime;
      lastIncrementalPushStatus = CompletionStatus.SUCCESS;
    }
  }

  /**
   * Record that the incremental push was interrupted prematurely.
   */
  synchronized void recordIncrementalPushInterrupted() {
    if (currentIncrementalPushStart == 0) {
      throw new IllegalStateException("Incremental push not already started");
    }
    currentIncrementalPushStart = 0;
    lastIncrementalPushStatus = CompletionStatus.INTERRUPTION;
  }

  /**
   * Record that the incremental push was interrupted prematurely.
   */
  synchronized void recordIncrementalPushFailed() {
    if (currentIncrementalPushStart == 0) {
      throw new IllegalStateException("Incremental push not already started");
    }
    currentIncrementalPushStart = 0;
    lastIncrementalPushStatus = CompletionStatus.FAILURE;
  }

  synchronized CompletionStatus getLastIncrementalPushStatus() {
    return lastIncrementalPushStatus;
  }

  double getRetrieverErrorRate(long maxCount) {
    long currentTime = timeProvider.currentTimeMillis();
    long count = 0;
    long failures = 0;

    synchronized (Journal.this) {
      Stats stats = dayStatsByHalfHour;
      // Update bookkeeping.
      stats.getCurrentStat(currentTime);

      for (int i = 0; i < stats.stats.length && count < maxCount; i++) {
        // Walk through indexes in reverse order, starting with most current.
        int index = (stats.currentStat - i + stats.stats.length)
            % stats.stats.length;
        Stat stat = stats.stats[index];
        count += stat.requestProcessingsCount;
        failures += stat.requestProcessingsFailureCount;
      }
    }

    double rate = 0;
    if (count > 0) {
      rate = failures / (double) count;
    }
    return rate;
  }

  boolean hasGsaCrawledWithinLastDay() {
    long currentTime = timeProvider.currentTimeMillis();
    synchronized (Journal.this) {
      Stats stats = dayStatsByHalfHour;
      // Update bookkeeping.
      stats.getCurrentStat(currentTime);

      for (Stat stat : stats.stats) {
        if (stat.gsaRetrievedDocument) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Access to the timeStats for use in {@link DashboardHandler} only.
   */
  synchronized JournalSnapshot getSnapshot() {
    long currentTime = timeProvider.currentTimeMillis();
    Stats[] timeStatsClone = new Stats[timeStats.length];
    for (int i = 0; i < timeStats.length; i++) {
      // Cause stats to update its internal structures
      timeStats[i].getCurrentStat(currentTime);
      timeStatsClone[i] = timeStats[i].clone();
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
    final long lastSuccessfulFullPushStart;
    final long lastSuccessfulFullPushEnd;
    final long currentFullPushStart;
    final long lastSuccessfulIncrementalPushStart;
    final long lastSuccessfulIncrementalPushEnd;
    final long currentIncrementalPushStart;
    final Stats[] timeStats;

    JournalSnapshot(Journal journal, long currentTime, Stats[] timeStatsClone) {
      this.numUniqueDocIdsPushed = journal.timesPushed.size();
      this.numTotalDocIdsPushed = journal.totalPushes;
      this.numUniqueGsaRequests = journal.timesGsaRequested.size();
      this.numTotalGsaRequests = journal.totalGsaRequests;
      this.numUniqueNonGsaRequests = journal.timesNonGsaRequested.size();
      this.numTotalNonGsaRequests = journal.totalNonGsaRequests;
      this.timeResolution = journal.timeResolution;
      this.lastSuccessfulFullPushStart = journal.lastSuccessfulFullPushStart;
      this.lastSuccessfulFullPushEnd = journal.lastSuccessfulFullPushEnd;
      this.currentFullPushStart = journal.currentFullPushStart;
      this.lastSuccessfulIncrementalPushStart
          = journal.lastSuccessfulIncrementalPushStart;
      this.lastSuccessfulIncrementalPushEnd
          = journal.lastSuccessfulIncrementalPushEnd;
      this.currentIncrementalPushStart = journal.currentIncrementalPushStart;
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

    public Stats clone() {
      Stats statsClone;
      try {
        statsClone = (Stats) super.clone();
      } catch (CloneNotSupportedException ex) {
        throw new AssertionError();
      }
      statsClone.stats = new Stat[stats.length];
      for (int i = 0; i < stats.length; i++) {
        statsClone.stats[i] = stats[i].clone();
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
     * The number of requests processed.
     */
    long requestProcessingsCount;
    /**
     * The number of request processings that errored.
     */
    long requestProcessingsFailureCount;
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
    /**
     * True if the GSA requested a document.
     */
    boolean gsaRetrievedDocument;

    public Stat() {
      reset();
    }

    /**
     * Reset object for reuse.
     */
    private void reset() {
      requestProcessingsCount = 0;
      requestProcessingsFailureCount = 0;
      requestProcessingsDurationSum = 0;
      requestProcessingsMaxDuration = 0;
      requestProcessingsThroughput = 0;
      gsaRetrievedDocument = false;
    }

    public Stat clone() {
      try {
        return (Stat) super.clone();
      } catch (CloneNotSupportedException ex) {
        throw new AssertionError();
      }
    }
  }

  private static class NegSizeFakeMap<K, V> extends FakeMap<K, V> {
    @Override
    public int size() {
      return -1;
    }
  }
}
