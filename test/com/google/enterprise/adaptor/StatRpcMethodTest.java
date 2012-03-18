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

import com.google.enterprise.adaptor.RpcHandler;
import com.google.enterprise.adaptor.StatRpcMethod;
import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/**
 * Tests for {@link StatRpcMethod}.
 */
public class StatRpcMethodTest {
  private RpcHandler.RpcMethod method;

  public StatRpcMethodTest() {
    method = new StatRpcMethod(new SnapshotMockJournal());
  }

  @Test
  public void testStat() throws Exception {
    Map<String, Object> golden = new HashMap<String, Object>();
    {
      Map<String, Object> simpleStats = new HashMap<String, Object>();
      simpleStats.put("numTotalDocIdsPushed", 0L);
      simpleStats.put("numTotalGsaRequests", 0L);
      simpleStats.put("numTotalNonGsaRequests", 0L);
      simpleStats.put("numUniqueDocIdsPushed", 0L);
      simpleStats.put("numUniqueGsaRequests", 0L);
      simpleStats.put("numUniqueNonGsaRequests", 0L);
      simpleStats.put("timeResolution", 1L);
      simpleStats.put("lastSuccessfulPushStart", 0L);
      simpleStats.put("lastSuccessfulPushEnd", 0L);
      simpleStats.put("currentPushStart", 0L);
      simpleStats.put("whenStarted", 0L);
      golden.put("simpleStats", simpleStats);

      List<Map<String, Object>> stats = new ArrayList<Map<String, Object>>();
      Map<String, Object> stat = new HashMap<String, Object>();
      stat.put("currentTime", 0L);
      stat.put("snapshotDuration", 100L);
      List<Map<String, Object>> datas = new ArrayList<Map<String, Object>>();
      Map<String, Object> data = new HashMap<String, Object>();
      data.put("requestProcessingsCount", 0L);
      data.put("requestProcessingsDurationSum", 0L);
      data.put("requestProcessingsMaxDuration", 0L);
      data.put("requestProcessingsThroughput", 0L);
      data.put("time", -100L);
      datas.add(data);
      data = new HashMap<String, Object>();
      data.put("requestProcessingsCount", 0L);
      data.put("requestProcessingsDurationSum", 0L);
      data.put("requestProcessingsMaxDuration", 0L);
      data.put("requestProcessingsThroughput", 0L);
      data.put("time", 0L);
      datas.add(data);
      stat.put("statData", datas);
      stats.add(stat);
      golden.put("stats", stats);

      golden = Collections.unmodifiableMap(golden);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) method.run(null);
    assertEquals(golden, map);
  }

  private class SnapshotMockJournal extends MockJournal {
    SnapshotMockJournal() {
      super(new MockTimeProvider());
    }

    @Override
    JournalSnapshot getSnapshot() {
      return new JournalSnapshot(this, 0, new Stats[] {
        new Stats(2, 100, 0),
      });
    }
  }
}
