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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Provides performance data when responding to 
 * ajax calls from dashboard.
 */
class StatRpcMethod implements RpcHandler.RpcMethod {
  private Journal journal;

  public StatRpcMethod(Journal journal) {
    this.journal = journal;
  }

  public Object run(List request) {
    Journal.JournalSnapshot journalSnap = journal.getSnapshot();

    Map<String, Object> map = new TreeMap<String, Object>();

    {
      Map<String, Object> simple = new TreeMap<String, Object>();
      simple.put("numTotalDocIdsPushed", journalSnap.numTotalDocIdsPushed);
      simple.put("numUniqueDocIdsPushed", journalSnap.numUniqueDocIdsPushed);
      simple.put("numTotalGsaRequests", journalSnap.numTotalGsaRequests);
      simple.put("numUniqueGsaRequests", journalSnap.numUniqueGsaRequests);
      simple.put("numTotalNonGsaRequests", journalSnap.numTotalNonGsaRequests);
      simple.put("numUniqueNonGsaRequests",
                 journalSnap.numUniqueNonGsaRequests);
      simple.put("timeResolution", journalSnap.timeResolution);
      simple.put("lastSuccessfulPushStart",
                 journalSnap.lastSuccessfulPushStart);
      simple.put("lastSuccessfulPushEnd", journalSnap.lastSuccessfulPushEnd);
      simple.put("currentPushStart", journalSnap.currentPushStart);
      simple.put("whenStarted", journalSnap.whenStarted);
      map.put("simpleStats", simple);
    }

    {
      List<Object> statsList = new ArrayList<Object>();
      long currentTime = journalSnap.currentTime;
      for (Journal.Stats stats : journalSnap.timeStats) {
        Map<String, Object> stat = new TreeMap<String, Object>();
        stat.put("snapshotDuration", stats.snapshotDurationMs);
        stat.put("currentTime", currentTime);
        List<Map<String, Object>> statData
            = new ArrayList<Map<String, Object>>(stats.stats.length);
        long time = stats.pendingStatPeriodEnd;
        // Rewind to beginning
        time -= stats.stats.length * stats.snapshotDurationMs;
        for (int i = stats.currentStat + 1; i < stats.stats.length; i++) {
          statData.add(getStat(stats.stats[i], time));
          time += stats.snapshotDurationMs;
        }
        for (int i = 0; i <= stats.currentStat; i++) {
          statData.add(getStat(stats.stats[i], time));
          time += stats.snapshotDurationMs;
        }
        stat.put("statData", statData);
        statsList.add(stat);
      }
      map.put("stats", statsList);
    }

    return map;
  }

  private Map<String, Object> getStat(Journal.Stat stat, long time) {
    Map<String, Object> statMap = new TreeMap<String, Object>();
    statMap.put("time", time);
    statMap.put("requestResponsesCount", stat.requestResponsesCount);
    statMap.put("requestResponsesDurationSum",
                stat.requestResponsesDurationSum);
    statMap.put("requestResponsesMaxDuration",
                stat.requestResponsesMaxDuration);
    statMap.put("requestResponsesThroughput",
                stat.requestResponsesThroughput);
    statMap.put("requestProcessingsCount", stat.requestProcessingsCount);
    statMap.put("requestProcessingsDurationSum",
                stat.requestProcessingsDurationSum);
    statMap.put("requestProcessingsMaxDuration",
                stat.requestProcessingsMaxDuration);
    statMap.put("requestProcessingsThroughput",
                stat.requestProcessingsThroughput);
    return statMap;
  }
}
