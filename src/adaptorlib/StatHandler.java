// Copyright 2011 Google Inc.
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

import com.sun.net.httpserver.HttpExchange;

import org.json.simple.JSONValue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.LogManager;

/**
 * Provides performance data when responding to 
 * ajax calls from dashboard.
 */
class StatHandler extends AbstractHandler {
  private Config config;
  private Journal journal;
  private CircularBufferHandler circularLog = new CircularBufferHandler();

  public StatHandler(Config configuration, Journal journal) {
    super(configuration.getServerHostname(),
        configuration.getGsaCharacterEncoding());
    this.config = configuration;
    this.journal = journal;
    LogManager.getLogManager().getLogger("").addHandler(circularLog);
  }

  protected void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if (!"GET".equals(requestMethod)) {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
          "Unsupported request method");
      return;
    }
    if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
      cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND, "text/plain",
                    "Not found");
      return;
    }
    String contents = generateJson();
    enableCompressionIfSupported(ex);
    cannedRespond(ex, HttpURLConnection.HTTP_OK, "application/json",
                  contents);
  }

  private String generateJson() throws IOException {
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

    map.put("log", circularLog.writeOut());

    {
      Map<String, String> configMap = new TreeMap<String, String>();
      for (String key : config.getAllKeys()) {
        configMap.put(key, config.getValue(key));
      }
      map.put("config", configMap);
    }

    return JSONValue.toJSONString(map);
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
