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

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tests for {@link DownloadDumpHandler}.
 */
public class DownloadDumpHandlerTest {
  private DownloadDumpHandler handler =
      new ModifiedDownloadDumpHandler("adaptor");
  private String pathPrefix = "/";
  private MockHttpContext httpContext =
      new MockHttpContext(handler, pathPrefix);
  private MockHttpExchange ex = createExchange("");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullFeedName() throws Exception {
    thrown.expect(NullPointerException.class);
    handler = new ModifiedDownloadDumpHandler(null);
  }

  @Test
  public void testIllegalFeedName() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    handler = new ModifiedDownloadDumpHandler("bad\"name");
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
    TimeZone previousTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("PST"));
    MockFile mockLogsDir = new MockFile("parentDir").setChildren(new File[] {
      new MockFile("log1.log").setFileContents("Log file 1"),
      new MockFile("log1.log.lck").setFileContents("skipped lock file"),
      new MockFile("log2.log").setFileContents("Log file 2"),
      new MockFile("subdir").setChildren(new File[] {
        new MockFile("nested.log").setFileContents("This file skipped.")
      })});
    MockTimeProvider timeProvider = new MockTimeProvider();
    timeProvider.time = 1383763470000L; // November 6, 2013 @ 10:44:30
    try {
      handler = new ModifiedDownloadDumpHandler("adaptor", mockLogsDir,
          timeProvider);
      handler.handle(ex);
      assertEquals(200, ex.getResponseCode());
      assertEquals("application/zip",
          ex.getResponseHeaders().getFirst("Content-Type"));
      assertEquals("attachment; filename=\"adaptor-20131106.zip\"",
          ex.getResponseHeaders().getFirst("Content-Disposition"));
      // extract the zip contents and just count the number of entries
      int entries = countZipEntries(ex.getResponseBytes());
      assertEquals(3, entries); /* 2 expected log files + thread dump */
    } finally {
       TimeZone.setDefault(previousTimeZone);
    }
  }

  @Test
  public void testLogFilesWithNoLogsDir() throws Exception {
    TimeZone previousTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("PST"));
    try {
      handler = new ModifiedDownloadDumpHandler("myadaptor",
          new MockFile("no-such-dir").setExists(false), new MockTimeProvider());
      handler.handle(ex);
      assertEquals(200, ex.getResponseCode());
      assertEquals("attachment; filename=\"myadaptor-19691231.zip\"",
          ex.getResponseHeaders().getFirst("Content-Disposition"));
      int entries = countZipEntries(ex.getResponseBytes());
      assertEquals(1, entries); /* 0 expected log files + thread dump */
   } finally {
      TimeZone.setDefault(previousTimeZone);
   }
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

  private static class ModifiedDownloadDumpHandler extends DownloadDumpHandler {

    public ModifiedDownloadDumpHandler(String feedName) {
      super(feedName);
    }

    ModifiedDownloadDumpHandler(String feedName, File logsDir,
        TimeProvider timeProvider) {
      super(feedName, logsDir, timeProvider);
    }

    @Override
    protected InputStream createInputStream(File file) {
      if (!(file instanceof MockFile)) {
        throw new IllegalArgumentException("implemented only for MockFile.");
      }
      return ((MockFile) file).createInputStream();
    }
  }
}
