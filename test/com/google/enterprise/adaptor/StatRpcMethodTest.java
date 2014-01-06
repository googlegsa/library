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

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/**
 * Tests for {@link StatRpcMethod}.
 */
public class StatRpcMethodTest {
  private RpcHandler.RpcMethod method = new StatRpcMethod(
      new SnapshotMockJournal(), new AdaptorMock(), false, null);

  @Test
  public void testStat() throws Exception {
    Map<String, Object> golden = new HashMap<String, Object>();
    {
      Map<String, Object> simpleStats = new HashMap<String, Object>();
      simpleStats.put("isIncrementalSupported", false);
      simpleStats.put("numTotalDocIdsPushed", 0L);
      simpleStats.put("numTotalGsaRequests", 0L);
      simpleStats.put("numTotalNonGsaRequests", 0L);
      simpleStats.put("numUniqueDocIdsPushed", 0L);
      simpleStats.put("numUniqueGsaRequests", 0L);
      simpleStats.put("numUniqueNonGsaRequests", 0L);
      simpleStats.put("timeResolution", 1L);
      simpleStats.put("lastSuccessfulFullPushStart", 0L);
      simpleStats.put("lastSuccessfulFullPushEnd", 0L);
      simpleStats.put("currentFullPushStart", 0L);
      simpleStats.put("lastSuccessfulIncrementalPushStart", 0L);
      simpleStats.put("lastSuccessfulIncrementalPushEnd", 0L);
      simpleStats.put("currentIncrementalPushStart", 0L);
      simpleStats.put("whenStarted", 0L);
      golden.put("simpleStats", simpleStats);

      Locale locale = Locale.ENGLISH;
      Map<String, Object> versionMap = new HashMap<String, Object>();
      versionMap.put("versionJvm", System.getProperty("java.version"));
      versionMap.put("versionAdaptorLibrary",
                     Translation.STATS_VERSION_UNKNOWN.toString(locale));
      versionMap.put("typeAdaptor", AdaptorMock.class.getSimpleName());
      versionMap.put("versionAdaptor",
                     Translation.STATS_VERSION_UNKNOWN.toString(locale));
      versionMap.put("cwd", System.getProperty("user.dir"));
      versionMap.put("configFileName",
                     Translation.STATUS_NONE.toString(locale));

      golden.put("versionStats", versionMap);

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

  private class AdaptorMock extends WrapperAdaptor {
    AdaptorMock() {
      super(null);
    }
  }
}
