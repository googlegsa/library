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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides performance data when responding to 
 * ajax calls from dashboard.
 */
class StatRpcMethod implements RpcHandler.RpcMethod {
  private String adaptorVersion = null;
  private String adaptorType = null;
  private Journal journal;
  private boolean isFullPushSupported;
  private boolean isIncrementalPushSupported;
  private File configFile;
  private static final Logger log
      = Logger.getLogger(StatRpcMethod.class.getName());

  public StatRpcMethod(Journal journal, Adaptor adaptor,
      boolean isFullPushSupported, boolean isIncrementalPushSupported,
      File configFile) {
    this.journal = journal;
    this.isFullPushSupported = isFullPushSupported;
    this.isIncrementalPushSupported = isIncrementalPushSupported;
    this.configFile = configFile;

    Class adaptorClass = adaptor.getClass();
    if (adaptorClass.getPackage() != null) {
      adaptorVersion = adaptorClass.getPackage().getImplementationVersion();
      adaptorType = adaptorClass.getPackage().getImplementationTitle();
    }
    if (adaptorType == null) {
      adaptorType = adaptorClass.getSimpleName();
    }
  }

  @Override
  public Object run(List request) {
    // TODO(ejona): choose locale based on Accept-Languages.
    Locale locale = Locale.ENGLISH;

    Journal.JournalSnapshot journalSnap = journal.getSnapshot();

    Map<String, Object> map = new TreeMap<String, Object>();

    {
      Map<String, Object> simple = new TreeMap<String, Object>();
      simple.put("isFullPushSupported", isFullPushSupported);
      simple.put("isIncrementalPushSupported", isIncrementalPushSupported);
      simple.put("numTotalDocIdsPushed", journalSnap.numTotalDocIdsPushed);
      simple.put("numUniqueDocIdsPushed", journalSnap.numUniqueDocIdsPushed);
      simple.put("numTotalGroupsPushed", journalSnap.numTotalGroupsPushed);
      simple.put("numTotalGroupMembersPushed",
                 journalSnap.numTotalGroupMembersPushed);
      simple.put("numUniqueGroupsPushed", journalSnap.numUniqueGroupsPushed);
      simple.put("numTotalGsaRequests", journalSnap.numTotalGsaRequests);
      simple.put("numUniqueGsaRequests", journalSnap.numUniqueGsaRequests);
      simple.put("numTotalNonGsaRequests", journalSnap.numTotalNonGsaRequests);
      simple.put("numUniqueNonGsaRequests",
                 journalSnap.numUniqueNonGsaRequests);
      simple.put("timeResolution", journalSnap.timeResolution);
      simple.put("lastSuccessfulFullPushStart",
                 journalSnap.lastSuccessfulFullPushStart);
      simple.put("lastSuccessfulFullPushEnd",
                 journalSnap.lastSuccessfulFullPushEnd);
      simple.put("currentFullPushStart", journalSnap.currentFullPushStart);
      simple.put("lastSuccessfulIncrementalPushStart",
                 journalSnap.lastSuccessfulIncrementalPushStart);
      simple.put("lastSuccessfulIncrementalPushEnd",
                 journalSnap.lastSuccessfulIncrementalPushEnd);
      simple.put("currentIncrementalPushStart",
                 journalSnap.currentIncrementalPushStart);
      simple.put("lastSuccessfulGroupPushStart",
                 journalSnap.lastSuccessfulGroupPushStart);
      simple.put("lastSuccessfulGroupPushEnd",
                 journalSnap.lastSuccessfulGroupPushEnd);
      simple.put("currentGroupPushStart", journalSnap.currentGroupPushStart);
      simple.put("whenStarted", journalSnap.whenStarted);
      map.put("simpleStats", simple);
    }

    {
      Map<String, Object> versionMap = new TreeMap<String, Object>();

      versionMap.put("versionJvm", System.getProperty("java.version"));
      versionMap.put("versionAdaptorLibrary", getAdaptorLibraryVersion(locale));
      versionMap.put("typeAdaptor", adaptorType);
      versionMap.put("versionAdaptor", getAdaptorVersion(locale));
      versionMap.put("cwd", System.getProperty("user.dir"));
      versionMap.put("configFileName", getConfigFilename(locale));
      map.put("versionStats", versionMap);
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
    statMap.put("requestProcessingsCount", stat.requestProcessingsCount);
    statMap.put("requestProcessingsDurationSum",
                stat.requestProcessingsDurationSum);
    statMap.put("requestProcessingsMaxDuration",
                stat.requestProcessingsMaxDuration);
    statMap.put("requestProcessingsThroughput",
                stat.requestProcessingsThroughput);
    return statMap;
  }

  private String getAdaptorLibraryVersion(Locale locale) {
    String version = this.getClass().getPackage().getImplementationVersion();
    return version == null
        ? Translation.STATS_VERSION_UNKNOWN.toString(locale) : version;
  }

  private String getAdaptorVersion(Locale locale) {
    return adaptorVersion == null
        ? Translation.STATS_VERSION_UNKNOWN.toString(locale) : adaptorVersion;
  }

  private String getConfigFilename(Locale locale) {
    File canonicalConfigFile = null;
    try {
      if (configFile != null) {
        canonicalConfigFile = configFile.getCanonicalFile();
      }
    } catch (IOException e) {
      log.log(Level.WARNING, "Could not determine file location for \""
          + configFile + "\"", e);
      // treat file as if it were not specified -- leave it null below
    }

    return canonicalConfigFile == null
        ? Translation.STATS_CONFIG_NONE.toString(locale)
        : canonicalConfigFile.toString();
  }
}
