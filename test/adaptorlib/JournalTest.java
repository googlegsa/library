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

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;

/**
 * Tests for {@link Journal}.
 */
public class JournalTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testPushCounts() {
    Journal journal = new Journal(new MockTimeProvider());
    DocId id  = new DocId("id1");
    DocId id2 = new DocId("id2");
    DocId id3 = new DocId("id3");
    DocId id4 = new DocId("id4");
    ArrayList<DocInfo> docs = new ArrayList<DocInfo>();
    docs.add(new DocInfo(id, Metadata.EMPTY));
    docs.add(new DocInfo(id2, Metadata.EMPTY));
    docs.add(new DocInfo(id3, Metadata.EMPTY));
    journal.recordDocIdPush(docs);
    assertEquals(3, journal.getSnapshot().numUniqueDocIdsPushed);
    journal.recordDocIdPush(docs);
    assertEquals(3, journal.getSnapshot().numUniqueDocIdsPushed);
    docs.add(new DocInfo(id4, Metadata.EMPTY));
    journal.recordDocIdPush(docs);
    assertEquals(4, journal.getSnapshot().numUniqueDocIdsPushed);
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
    journal.recordRequestResponseStart();
    Thread thread = new Thread() {
      public void run() {
        timeProvider.time += 2;
        journal.recordRequestResponseStart();
        timeProvider.time += 2;
        journal.recordRequestResponseEnd(10);
        journal.recordRequestProcessingStart();
        timeProvider.time += 2;
        journal.recordRequestProcessingEnd(10);
      }
    };
    thread.start();
    thread.join();
    journal.recordRequestResponseEnd(8);
    Journal.JournalSnapshot snapshot = journal.getSnapshot();
    Journal.Stat stat
        = snapshot.timeStats[0].stats[snapshot.timeStats[0].currentStat];
    assertEquals(2, stat.requestResponsesCount);
    assertEquals(8, stat.requestResponsesDurationSum);
    assertEquals(6, stat.requestResponsesMaxDuration);
    assertEquals(18, stat.requestResponsesThroughput);
    assertEquals(1, stat.requestProcessingsCount);
    assertEquals(2, stat.requestProcessingsDurationSum);
    assertEquals(2, stat.requestProcessingsMaxDuration);
    assertEquals(10, stat.requestProcessingsThroughput);

    // Did it swap out to a new stat correctly?
    journal.recordRequestProcessingStart();
    long previousTime = timeProvider.time;
    timeProvider.time
        = journal.getSnapshot().timeStats[0].pendingStatPeriodEnd;
    journal.recordRequestProcessingEnd(101);
    snapshot = journal.getSnapshot();
    stat = snapshot.timeStats[0].stats[snapshot.timeStats[0].currentStat];
    assertEquals(0, stat.requestResponsesCount);
    assertEquals(0, stat.requestResponsesDurationSum);
    assertEquals(0, stat.requestResponsesMaxDuration);
    assertEquals(0, stat.requestResponsesThroughput);
    assertEquals(1, stat.requestProcessingsCount);
    assertEquals(timeProvider.time - previousTime,
                 stat.requestProcessingsDurationSum);
    assertEquals(timeProvider.time - previousTime,
                 stat.requestProcessingsMaxDuration);
    assertEquals(101, stat.requestProcessingsThroughput);

    // Does it still have correct values for previous stat?
    stat = snapshot.timeStats[0].stats[(snapshot.timeStats[0].currentStat - 1)
        % snapshot.timeStats[0].stats.length];
    assertEquals(2, stat.requestResponsesCount);
    assertEquals(8, stat.requestResponsesDurationSum);
    assertEquals(6, stat.requestResponsesMaxDuration);
    assertEquals(18, stat.requestResponsesThroughput);
    assertEquals(1, stat.requestProcessingsCount);
    assertEquals(2, stat.requestProcessingsDurationSum);
    assertEquals(2, stat.requestProcessingsMaxDuration);
    assertEquals(10, stat.requestProcessingsThroughput);

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
      assertEquals(0, stat.requestResponsesCount);
      assertEquals(0, stat.requestResponsesDurationSum);
      assertEquals(0, stat.requestResponsesMaxDuration);
      assertEquals(0, stat.requestResponsesThroughput);
      assertEquals(0, stat.requestProcessingsCount);
      assertEquals(0, stat.requestProcessingsDurationSum);
      assertEquals(0, stat.requestProcessingsMaxDuration);
      assertEquals(0, stat.requestProcessingsThroughput);
    }
  }

  @Test
  public void testStatsNoStart() {
    Journal journal = new Journal(new MockTimeProvider());
    thrown.expect(IllegalStateException.class);
    journal.recordRequestProcessingEnd(1);
  }

  @Test
  public void testDefaultTimeProvider() {
    new Journal();
  }
}
