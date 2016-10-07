// Copyright 2013 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tests for {@link DownloadDumpHandler}.
 */
public class DownloadDumpHandlerTest {
  private final Config config = new Config();
  private DownloadDumpHandler handler =
      new ModifiedDownloadDumpHandler(config, "adaptor");
  private final String pathPrefix = "/";
  private final MockHttpContext httpContext =
      new MockHttpContext(handler, pathPrefix);
  private MockHttpExchange ex = createExchange("");
  private TimeZone previousTimeZone;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() {
    // problems arise if gsa.version is left unset
    config.setValue("gsa.version", "7.0.14-114");
    previousTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("PST"));
  }

  @After
  public void tearDown() {
    TimeZone.setDefault(previousTimeZone);
  }

  @Test
  public void testNullConfig() throws Exception {
    thrown.expect(NullPointerException.class);
    handler = new DownloadDumpHandler(null, "feedname", null);
  }

  @Test
  public void testNullFeedName() throws Exception {
    thrown.expect(NullPointerException.class);
    handler = new ModifiedDownloadDumpHandler(config, null);
  }

  @Test
  public void testIllegalFeedName() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    handler = new ModifiedDownloadDumpHandler(config, "bad\"name");
  }

  @Test
  public void testPost() throws Exception {
    ex = new MockHttpExchange("POST", pathPrefix, httpContext);
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testNotFound() throws Exception {
    ex = createExchange("notfound");
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testLogFilesWithCannedLogsDir() throws Exception {
    // set up File using MockFile
    MockFile mockLogsDir = new MockFile("parentDir").setChildren(new File[] {
      new MockFile("log1.log").setFileContents("Log file 1"),
      new MockFile("log1.log.lck").setFileContents("skipped lock file"),
      new MockFile("log2.log").setFileContents("Log file 2"),
      new MockFile("subdir").setChildren(new File[] {
        new MockFile("nested.log").setFileContents("This file skipped.")
      })});
    MockTimeProvider timeProvider = new MockTimeProvider();
    timeProvider.time = 1383763470000L; // November 6, 2013 @ 10:44:30
    StatRpcMethod statRpcMethod = new MockStatRpcMethod(
        new MockJournal(new MockTimeProvider()),
        new MockAdaptor(),
        /*isAdaptorIncremental=*/ false,
        /*configFile=*/ new MockFile("no-such-dir").setExists(false));
    handler = new ModifiedDownloadDumpHandler(config, "adaptor",
        statRpcMethod, mockLogsDir, timeProvider);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("application/zip",
        ex.getResponseHeaders().getFirst("Content-Type"));
    assertEquals("attachment; filename=\"adaptor-20131106.zip\"",
        ex.getResponseHeaders().getFirst("Content-Disposition"));
    // extract the zip contents and just count the number of entries
    byte[] zipContents = ex.getResponseBytes();
    int entries = countZipEntries(zipContents);
    assertEquals(5, entries); /* 2 log files + thread dump + config + stats */

    // verify contents of thread dump
    String threadContents = extractFileFromZip("threaddump.txt", zipContents);
    assertTrue(threadContents.contains("Thread[Signal Dispatcher,"));
    assertTrue(threadContents.contains("Thread[main,"));

    // verify contents of config file
    String configContents = extractFileFromZip("config.txt", zipContents);
    // search for: \ngsa.version[spaces]= 7.0.14-114\n
    int position = configContents.indexOf("\ngsa.version");
    assertTrue(position > 0);
    int equals = configContents.indexOf("=", position);
    assertTrue(equals > 0);
    assertEquals("= 7.0.14-114\n",
        configContents.substring(equals, equals + 13));

    // verify contents of stats file
    String goldenStats = "versionJvm = 1.6.0\n\n"
        + "isIncrementalSupported = false\n"
        + "numTotalDocIdsPushed   = 0\n\n";
    String statsContents = extractFileFromZip("stats.txt", zipContents);
    assertEquals(goldenStats, statsContents);
  }

  @Test
  public void testLogFilesWithNoLogsDir() throws Exception {
    handler = new ModifiedDownloadDumpHandler(config, "myadaptor",
        new MockFile("no-such-dir").setExists(false), new MockTimeProvider());
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("attachment; filename=\"myadaptor-19691231.zip\"",
        ex.getResponseHeaders().getFirst("Content-Disposition"));
    int entries = countZipEntries(ex.getResponseBytes());
    assertEquals(2, entries); /* 0 log files + thread dump + config */
  }

  @Test
  public void testLogFilesWithEmptyStats() throws Exception {
    // set up File using MockFile
    handler = new ModifiedDownloadDumpHandler(config, "myadaptor",
        new MockFile("no-such-dir").setExists(false), new MockTimeProvider());
    StatRpcMethod statRpcMethod = new MockStatRpcMethod(
        new MockJournal(new MockTimeProvider()),
        new MockAdaptor(),
        /*isAdaptorIncremental=*/ false,
        /*configFile=*/ new MockFile("no-such-dir").setExists(false)) {

      /** generate neither versionStats nor simpleStats maps */
      @Override
      public Object run(List request) {
        Map<String, Object> results = new HashMap<String, Object>();
        Map<String, Object> otherStats = new HashMap<String, Object>();
        otherStats.put("someBooleanValue", false);
        otherStats.put("someLongValue", 42L);
        // rest omitted
        results.put("otherStats", otherStats);

        return Collections.unmodifiableMap(results);
      }
    };
    handler = new ModifiedDownloadDumpHandler(config, "adaptor",
        statRpcMethod, new MockFile("no-such-dir").setExists(false),
        new MockTimeProvider());
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("application/zip",
        ex.getResponseHeaders().getFirst("Content-Type"));
    // extract the zip contents and just count the number of entries
    byte[] zipContents = ex.getResponseBytes();
    int entries = countZipEntries(zipContents);
    assertEquals(3, entries); /* 0 log files + thread dump + config + stats */

    // verify contents of stats file
    String goldenStats = "";
    String statsContents = extractFileFromZip("stats.txt", zipContents);
    assertEquals(goldenStats, statsContents);
  }

  @Test
  public void testLogFilesWithUnusualStats() throws Exception {
    // set up File using MockFile
    handler = new ModifiedDownloadDumpHandler(config, "myadaptor",
        new MockFile("no-such-dir").setExists(false), new MockTimeProvider());
    StatRpcMethod statRpcMethod = new MockStatRpcMethod(
        new MockJournal(new MockTimeProvider()),
        new MockAdaptor(),
        /*isAdaptorIncremental=*/ false,
        /*configFile=*/ new MockFile("no-such-dir").setExists(false)) {

      /** generate neither versionStats nor simpleStats maps */
      @Override
      public Object run(List request) {
        Map<String, Object> results = new HashMap<String, Object>();
        Map<String, Object> simpleStats = new TreeMap<String, Object>();
        // keys get sorted into alphabetic order in golden map below
        simpleStats.put("whenStarted", "non-numeric value");
        simpleStats.put("lastSuccessfulFullPushEnd", null);
        simpleStats.put("currentFullPushStart", 0);
        simpleStats.put("lastSuccessfulIncrementalPushStart", -5);
        // rest omitted
        results.put("simpleStats", simpleStats);

        return Collections.unmodifiableMap(results);
      }
    };
    handler = new ModifiedDownloadDumpHandler(config, "adaptor",
        statRpcMethod, new MockFile("no-such-dir").setExists(false),
        new MockTimeProvider());
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("application/zip",
        ex.getResponseHeaders().getFirst("Content-Type"));
    // extract the zip contents and just count the number of entries
    byte[] zipContents = ex.getResponseBytes();
    int entries = countZipEntries(zipContents);
    assertEquals(3, entries); /* 0 log files + thread dump + config + stats */

    // verify contents of stats file
    String goldenStats = "currentFullPushStart               = 0\n"
        + "lastSuccessfulFullPushEnd          = [null]\n"
        + "lastSuccessfulIncrementalPushStart = -5\n"
        + "whenStarted                        = non-numeric value\n\n";
    String statsContents = extractFileFromZip("stats.txt", zipContents);
    assertEquals(goldenStats, statsContents);
  }

  private MockHttpExchange createExchange(String path) {
    return new MockHttpExchange("GET", pathPrefix + path, httpContext);
  }

  private int countZipEntries(byte[] bytes) throws IOException {
    ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes));
    int entries = 0;
    ZipEntry nextEntry = null;
    do {
      nextEntry = zis.getNextEntry();
      if (null != nextEntry) {
        entries++;
      }
    } while (nextEntry != null);
    zis.close();
    return entries;
  }

  /**
   * extracts the contents of a specified filename from a given zip (as a
   * <code>String</code>)
   */
  private String extractFileFromZip(String filename, byte[] zipContents)
      throws IOException {
    ZipInputStream zis = new ZipInputStream(
        new ByteArrayInputStream(zipContents));
    ZipEntry ze = null;
    while ((ze = zis.getNextEntry()) != null) {
      if (ze.getName().equals(filename)) {
        return IOHelper.readInputStreamToString(zis, Charset.forName("UTF-8"))
            .replace("\r\n", "\n");
      }
    }
    return null; // file not found
  }

  private static class ModifiedDownloadDumpHandler extends DownloadDumpHandler {
    public ModifiedDownloadDumpHandler(Config config, String feedName) {
      super(config, feedName, null);
    }

    ModifiedDownloadDumpHandler(Config config, String feedName, File logsDir,
        TimeProvider timeProvider) {
      super(config, feedName, null, logsDir, timeProvider);
    }

    ModifiedDownloadDumpHandler(Config config, String feedName,
        StatRpcMethod statRpcMethod, File logsDir, TimeProvider timeProvider) {
      super(config, feedName, statRpcMethod, logsDir, timeProvider);
    }

    @Override
    InputStream createInputStream(File file) throws IOException {
      if (!(file instanceof MockFile)) {
        throw new IllegalArgumentException("implemented only for MockFile.");
      }
      return ((MockFile) file).createInputStream();
    }
  }

  private static class MockStatRpcMethod extends StatRpcMethod {
    MockStatRpcMethod(Journal journal, Adaptor adaptor,
        boolean isAdaptorIncremental, File configFile) {
      super(journal, adaptor, true, isAdaptorIncremental, configFile);
    }

    @Override
    public Object run(List request) {
      Map<String, Object> golden = new HashMap<String, Object>();
      Map<String, Object> simpleStats = new HashMap<String, Object>();
      simpleStats.put("isIncrementalSupported", false);
      simpleStats.put("numTotalDocIdsPushed", 0L);
      // rest omitted
      golden.put("simpleStats", simpleStats);

      Map<String, Object> versionMap = new HashMap<String, Object>();
      versionMap.put("versionJvm", "1.6.0");
      // rest omitted

      golden.put("versionStats", versionMap);

      return Collections.unmodifiableMap(golden);
    }
  }
}
