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

import static java.util.AbstractMap.SimpleEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link Journal}.
 */
public class JournalTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static SimpleEntry<GroupPrincipal, Collection<Principal>> 
      makePair(GroupPrincipal g, Collection<Principal> members) {
    return new SimpleEntry<GroupPrincipal, Collection<Principal>>(g, members);
  }

  @Test
  public void testPushCounts() {
    Journal journal = new Journal(new MockTimeProvider());
    DocId id  = new DocId("id1");
    DocId id2 = new DocId("id2");
    DocId id3 = new DocId("id3");
    DocId id4 = new DocId("id4");
    GroupPrincipal g1 = new GroupPrincipal("group1");
    GroupPrincipal g2 = new GroupPrincipal("group2");
    GroupPrincipal g3 = new GroupPrincipal("group3");
    Principal u1 = new UserPrincipal("user1");
    Principal u2 = new UserPrincipal("user2");
    ArrayList<DocIdPusher.Record> docs = new ArrayList<DocIdPusher.Record>();
    docs.add(new DocIdPusher.Record.Builder(id).build());
    docs.add(new DocIdPusher.Record.Builder(id2).build());
    docs.add(new DocIdPusher.Record.Builder(id3).build());
    ArrayList<Map.Entry<GroupPrincipal, Collection<Principal>>> groups =
        new ArrayList<Map.Entry<GroupPrincipal, Collection<Principal>>>();
    List<Principal> g1members = new ArrayList<Principal>();
    List<Principal> g2members = new ArrayList<Principal>();
    List<Principal> g3members = new ArrayList<Principal>();
    g1members.add(new UserPrincipal("Marc"));
    g1members.add(new UserPrincipal("John"));
    g2members.add(new UserPrincipal("PJ"));
    g3members.add(new UserPrincipal("Bill"));
    g3members.add(new UserPrincipal("Tony"));
    groups.add(makePair(g1, g1members));
    groups.add(makePair(g2, g2members));
    journal.recordDocIdPush(docs);
    assertEquals(3, journal.getSnapshot().numUniqueDocIdsPushed);
    journal.recordDocIdPush(docs);
    assertEquals(3, journal.getSnapshot().numUniqueDocIdsPushed);
    docs.add(new DocIdPusher.Record.Builder(id4).build());
    journal.recordDocIdPush(docs);
    assertEquals(4, journal.getSnapshot().numUniqueDocIdsPushed);
    journal.recordGroupPush(groups);
    assertEquals(2, journal.getSnapshot().numTotalGroupsPushed);
    assertEquals(2, journal.getSnapshot().numUniqueGroupsPushed);
    assertEquals(3, journal.getSnapshot().numTotalGroupMembersPushed);
    journal.recordGroupPush(groups);
    assertEquals(4, journal.getSnapshot().numTotalGroupsPushed);
    assertEquals(2, journal.getSnapshot().numUniqueGroupsPushed);
    assertEquals(6, journal.getSnapshot().numTotalGroupMembersPushed);
    groups.add(makePair(g3, g3members));
    journal.recordGroupPush(groups);
    assertEquals(7, journal.getSnapshot().numTotalGroupsPushed);
    assertEquals(3, journal.getSnapshot().numUniqueGroupsPushed);
    assertEquals(11, journal.getSnapshot().numTotalGroupMembersPushed);
  }

  @Test
  public void testDisabledPushCounts() {
    Journal journal = new Journal(true, new MockTimeProvider());
    journal.recordDocIdPush(Collections.singletonList(
        new DocIdPusher.Record.Builder(new DocId("id")).build()));
    assertEquals(-1, journal.getSnapshot().numUniqueDocIdsPushed);
    assertEquals(-1, journal.getSnapshot().numUniqueGroupsPushed);
    assertEquals(0, journal.getSnapshot().numTotalGroupsPushed);
    assertEquals(0, journal.getSnapshot().numTotalGroupMembersPushed);
  }

  @Test
  public void testUnsupportedDocIdPush() {
    class UnsupportedItem implements DocIdSender.Item {};
    Journal journal = new Journal(true, new MockTimeProvider());
    thrown.expect(IllegalArgumentException.class);
    journal.recordDocIdPush(Collections.singletonList(new UnsupportedItem()));
  }

  @Test
  public void testRequestCounts() {
    Journal journal = new Journal(new MockTimeProvider());
    Journal.JournalSnapshot snapshot = journal.getSnapshot();
    assertEquals(0, snapshot.numUniqueGsaRequests);
    assertEquals(0, snapshot.numTotalGsaRequests);
    assertEquals(0, snapshot.numUniqueNonGsaRequests);
    assertEquals(0, snapshot.numTotalNonGsaRequests);

    DocId doc1 = new DocId("1");
    DocId doc2 = new DocId("2");

    journal.recordGsaContentRequest(doc1);
    snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.numUniqueGsaRequests);
    assertEquals(1, snapshot.numTotalGsaRequests);

    journal.recordGsaContentRequest(doc1);
    snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.numUniqueGsaRequests);
    assertEquals(2, snapshot.numTotalGsaRequests);

    journal.recordGsaContentRequest(doc2);
    snapshot = journal.getSnapshot();
    assertEquals(2, snapshot.numUniqueGsaRequests);
    assertEquals(3, snapshot.numTotalGsaRequests);

    assertEquals(0, snapshot.numUniqueNonGsaRequests);
    assertEquals(0, snapshot.numTotalNonGsaRequests);

    journal.recordNonGsaContentRequest(doc1);
    snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.numUniqueNonGsaRequests);
    assertEquals(1, snapshot.numTotalNonGsaRequests);

    journal.recordNonGsaContentRequest(doc1);
    snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.numUniqueNonGsaRequests);
    assertEquals(2, snapshot.numTotalNonGsaRequests);

    journal.recordNonGsaContentRequest(doc2);
    snapshot = journal.getSnapshot();
    assertEquals(2, snapshot.numUniqueNonGsaRequests);
    assertEquals(3, snapshot.numTotalNonGsaRequests);

    assertEquals(2, snapshot.numUniqueGsaRequests);
    assertEquals(3, snapshot.numTotalGsaRequests);
  }

  @Test
  public void testStats() throws InterruptedException {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    timeProvider.autoIncrement = false;
    timeProvider.time
        = journal.getSnapshot().timeStats[0].pendingStatPeriodEnd;
    journal.recordRequestProcessingStart();
    Thread thread = new Thread() {
      public void run() {
        timeProvider.time += 2;
        journal.recordRequestProcessingStart();
        timeProvider.time += 2;
        journal.recordRequestProcessingEnd(10);
      }
    };
    thread.start();
    thread.join();
    journal.recordRequestProcessingEnd(8);
    Journal.JournalSnapshot snapshot = journal.getSnapshot();
    Journal.Stat stat
        = snapshot.timeStats[0].stats[snapshot.timeStats[0].currentStat];
    assertEquals(2, stat.requestProcessingsCount);
    assertEquals(6, stat.requestProcessingsDurationSum);
    assertEquals(4, stat.requestProcessingsMaxDuration);
    assertEquals(18, stat.requestProcessingsThroughput);

    // Did it swap out to a new stat correctly?
    journal.recordRequestProcessingStart();
    long previousTime = timeProvider.time;
    timeProvider.time
        = journal.getSnapshot().timeStats[0].pendingStatPeriodEnd;
    journal.recordRequestProcessingEnd(101);
    snapshot = journal.getSnapshot();
    stat = snapshot.timeStats[0].stats[snapshot.timeStats[0].currentStat];
    assertEquals(1, stat.requestProcessingsCount);
    assertEquals(timeProvider.time - previousTime,
                 stat.requestProcessingsDurationSum);
    assertEquals(timeProvider.time - previousTime,
                 stat.requestProcessingsMaxDuration);
    assertEquals(101, stat.requestProcessingsThroughput);

    // Does it still have correct values for previous stat?
    stat = snapshot.timeStats[0].stats[(snapshot.timeStats[0].currentStat - 1)
        % snapshot.timeStats[0].stats.length];
    assertEquals(2, stat.requestProcessingsCount);
    assertEquals(6, stat.requestProcessingsDurationSum);
    assertEquals(4, stat.requestProcessingsMaxDuration);
    assertEquals(18, stat.requestProcessingsThroughput);

    // Did it clear out everything after a long time?
    // Subtract off a reasonable amount of time to prevevent overflow.
    timeProvider.time = Long.MAX_VALUE - 1000 * 60 * 60 * 24 * 7;
    journal.recordRequestProcessingStart();
    journal.recordRequestProcessingEnd(1);
    snapshot = journal.getSnapshot();
    for (int i = 0; i < snapshot.timeStats[0].stats.length; i++) {
      if (i == snapshot.timeStats[0].currentStat) {
        continue;
      }
      stat = snapshot.timeStats[0].stats[i];
      assertEquals(0, stat.requestProcessingsCount);
      assertEquals(0, stat.requestProcessingsDurationSum);
      assertEquals(0, stat.requestProcessingsMaxDuration);
      assertEquals(0, stat.requestProcessingsThroughput);
    }
  }

  @Test
  public void testFullPushStats() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    timeProvider.autoIncrement = false;

    timeProvider.time = 1;
    journal.recordFullPushStarted();
    timeProvider.time = 2;
    journal.recordFullPushSuccessful();
    Journal.JournalSnapshot snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.lastSuccessfulFullPushStart);
    assertEquals(2, snapshot.lastSuccessfulFullPushEnd);

    timeProvider.time = 5;
    journal.recordFullPushStarted();
    timeProvider.time = 7;
    journal.recordFullPushInterrupted();
    snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.lastSuccessfulFullPushStart);
    assertEquals(2, snapshot.lastSuccessfulFullPushEnd);

    timeProvider.time = 8;
    journal.recordFullPushStarted();
    timeProvider.time = 9;
    journal.recordFullPushFailed();
    snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.lastSuccessfulFullPushStart);
    assertEquals(2, snapshot.lastSuccessfulFullPushEnd);

    timeProvider.time = 11;
    journal.recordFullPushStarted();
    timeProvider.time = 15;
    journal.recordFullPushSuccessful();
    snapshot = journal.getSnapshot();
    assertEquals(11, snapshot.lastSuccessfulFullPushStart);
    assertEquals(15, snapshot.lastSuccessfulFullPushEnd);
  }

  @Test
  public void testFullPushStartDouble() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    // Make sure we are past epoch, because that is a special value in the
    // journaling code.
    timeProvider.time = 1;
    journal.recordFullPushStarted();
    thrown.expect(IllegalStateException.class);
    journal.recordFullPushStarted();
  }

  @Test
  public void testFullPushInterruptedPremature() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    // Make sure we are past epoch, because that is a special value in the
    // journaling code.
    timeProvider.time = 1;
    thrown.expect(IllegalStateException.class);
    journal.recordFullPushInterrupted();
  }

  @Test
  public void testFullPushFailedPremature() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    // Make sure we are past epoch, because that is a special value in the
    // journaling code.
    timeProvider.time = 1;
    thrown.expect(IllegalStateException.class);
    journal.recordFullPushFailed();
  }

  @Test
  public void testIncrementalPushStats() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    timeProvider.autoIncrement = false;

    timeProvider.time = 1;
    journal.recordIncrementalPushStarted();
    timeProvider.time = 2;
    journal.recordIncrementalPushSuccessful();
    Journal.JournalSnapshot snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.lastSuccessfulIncrementalPushStart);
    assertEquals(2, snapshot.lastSuccessfulIncrementalPushEnd);

    timeProvider.time = 5;
    journal.recordIncrementalPushStarted();
    timeProvider.time = 7;
    journal.recordIncrementalPushInterrupted();
    snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.lastSuccessfulIncrementalPushStart);
    assertEquals(2, snapshot.lastSuccessfulIncrementalPushEnd);

    timeProvider.time = 8;
    journal.recordIncrementalPushStarted();
    timeProvider.time = 9;
    journal.recordIncrementalPushFailed();
    snapshot = journal.getSnapshot();
    assertEquals(1, snapshot.lastSuccessfulIncrementalPushStart);
    assertEquals(2, snapshot.lastSuccessfulIncrementalPushEnd);

    timeProvider.time = 11;
    journal.recordIncrementalPushStarted();
    timeProvider.time = 15;
    journal.recordIncrementalPushSuccessful();
    snapshot = journal.getSnapshot();
    assertEquals(11, snapshot.lastSuccessfulIncrementalPushStart);
    assertEquals(15, snapshot.lastSuccessfulIncrementalPushEnd);
  }

  @Test
  public void testIncrementalPushStartDouble() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    // Make sure we are past epoch, because that is a special value in the
    // journaling code.
    timeProvider.time = 1;
    journal.recordIncrementalPushStarted();
    thrown.expect(IllegalStateException.class);
    journal.recordIncrementalPushStarted();
  }

  @Test
  public void testIncrementalPushInterruptedPremature() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    // Make sure we are past epoch, because that is a special value in the
    // journaling code.
    timeProvider.time = 1;
    thrown.expect(IllegalStateException.class);
    journal.recordIncrementalPushInterrupted();
  }

  @Test
  public void testIncrementalPushFailedPremature() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    // Make sure we are past epoch, because that is a special value in the
    // journaling code.
    timeProvider.time = 1;
    thrown.expect(IllegalStateException.class);
    journal.recordIncrementalPushFailed();
  }
  @Test
  public void testStatsNoStart() {
    Journal journal = new Journal(new MockTimeProvider());
    thrown.expect(IllegalStateException.class);
    journal.recordRequestProcessingEnd(1);
  }

  @Test
  public void testDefaultTimeProvider() {
    new Journal(false);
  }

  @Test
  public void testLastFullPushStatus() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);

    assertEquals(Journal.CompletionStatus.SUCCESS,
        journal.getLastFullPushStatus());

    journal.recordFullPushStarted();
    journal.recordFullPushInterrupted();
    assertEquals(Journal.CompletionStatus.INTERRUPTION,
        journal.getLastFullPushStatus());

    journal.recordFullPushStarted();
    journal.recordFullPushFailed();
    assertEquals(Journal.CompletionStatus.FAILURE,
        journal.getLastFullPushStatus());

    journal.recordFullPushStarted();
    journal.recordFullPushSuccessful();
    assertEquals(Journal.CompletionStatus.SUCCESS,
        journal.getLastFullPushStatus());
  }

  @Test
  public void testLastIncrementalPushStatus() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);

    assertEquals(Journal.CompletionStatus.SUCCESS,
        journal.getLastIncrementalPushStatus());

    journal.recordIncrementalPushStarted();
    journal.recordIncrementalPushInterrupted();
    assertEquals(Journal.CompletionStatus.INTERRUPTION,
        journal.getLastIncrementalPushStatus());

    journal.recordIncrementalPushStarted();
    journal.recordIncrementalPushFailed();
    assertEquals(Journal.CompletionStatus.FAILURE,
        journal.getLastIncrementalPushStatus());

    journal.recordIncrementalPushStarted();
    journal.recordIncrementalPushSuccessful();
    assertEquals(Journal.CompletionStatus.SUCCESS,
        journal.getLastIncrementalPushStatus());
  }

  @Test
  public void testLastGroupPushStatus() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);

    assertEquals(Journal.CompletionStatus.SUCCESS,
        journal.getLastGroupPushStatus());

    journal.recordGroupPushStarted();
    journal.recordGroupPushInterrupted();
    assertEquals(Journal.CompletionStatus.INTERRUPTION,
        journal.getLastGroupPushStatus());

    journal.recordGroupPushStarted();
    journal.recordGroupPushFailed();
    assertEquals(Journal.CompletionStatus.FAILURE,
        journal.getLastGroupPushStatus());

    journal.recordGroupPushStarted();
    journal.recordGroupPushSuccessful();
    assertEquals(Journal.CompletionStatus.SUCCESS,
        journal.getLastGroupPushStatus());

    // attempt to start two simultaneous group pushes
    try {
      journal.recordGroupPushStarted();
      journal.recordGroupPushStarted();
      fail ("Second group push should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      journal.recordGroupPushSuccessful(); // resets currentGroupPushStart
    };

    // attempt to interrupt a group push (when it was never started)
    try {
      journal.recordGroupPushInterrupted();
      fail ("Interrupting non-existing group push shouldn't have worked");
    } catch (IllegalStateException e) {
      // ignore expected exception
    };

    // attempt to mark a group push as a failure (when it was never started)
    try {
      journal.recordGroupPushFailed();
      fail ("Failing non-existing group push shouldn't have worked");
    } catch (IllegalStateException e) {
      // ignore expected exception
    };
  }

  @Test
  public void testRetrieverStatusSource() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    timeProvider.autoIncrement = false;
    DocId docId = new DocId("");
    final long hourInMillis = 1000 * 60 * 60;

    double rate = journal.getRetrieverErrorRate(10);
    assertEquals(0., rate, 0.);

    journal.recordRequestProcessingStart();
    journal.recordRequestProcessingFailure();
    rate = journal.getRetrieverErrorRate(10);
    assertEquals(1., rate, 0.);

    timeProvider.time += hourInMillis;
    for (int i = 0; i < 7; i++) {
      journal.recordRequestProcessingStart();
      journal.recordRequestProcessingEnd(0);
    }
    rate = journal.getRetrieverErrorRate(10);
    assertEquals(1. / 8., rate, 0.);

    timeProvider.time += hourInMillis;
    for (int i = 0; i < 7; i++) {
      journal.recordRequestProcessingStart();
      journal.recordRequestProcessingFailure();
    }
    rate = journal.getRetrieverErrorRate(10);
    assertEquals(1. / 2., rate, 0.);
    rate = journal.getRetrieverErrorRate(7);
    assertEquals(1., rate, 0.);

    timeProvider.time += hourInMillis;
    for (int i = 0; i < 10; i++) {
      journal.recordRequestProcessingStart();
      journal.recordRequestProcessingEnd(0);
    }
    rate = journal.getRetrieverErrorRate(10);
    assertEquals(0., rate, 0.);
  }

  @Test
  public void testGsaCrawlingStatusSource() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final Journal journal = new Journal(timeProvider);
    timeProvider.autoIncrement = false;
    DocId docId = new DocId("");

    assertFalse(journal.hasGsaCrawledWithinLastDay());

    journal.recordGsaContentRequest(docId);
    assertTrue(journal.hasGsaCrawledWithinLastDay());

    final long dayInMillis = 1000 * 60 * 60 * 24;
    timeProvider.time += 2 * dayInMillis;
    assertFalse(journal.hasGsaCrawledWithinLastDay());
  }
}
