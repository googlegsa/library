// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.google.common.io.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

/**
 * Test cases for {@link GsaFeedFileArchiver}.
 */
public class GsaFeedFileArchiverTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  File archiveDir;
  GsaFeedFileArchiver archiver;

  @Before
  public void setUp() {
    archiveDir = temp.getRoot().getAbsoluteFile();
    archiver = new GsaFeedFileArchiver(archiveDir.toString());
  }

  @Test
  public void testNoArchiveDirectorySpecified() throws Exception {
    GsaFeedFileArchiver archiver = new GsaFeedFileArchiver("");
    archiver.saveFeed("test", "foo");
    archiver.saveFailedFeed("test", "bar");
    assertEquals(0, getArchiveFeedFiles().length);
  }

  @Test
  public void testArchiveFeed() throws Exception {
    archiver.saveFeed("test", "foo");
    checkOneFeed("test", "foo");
  }

  @Test
  public void testArchiveFailedFeed() throws Exception {
    archiver.saveFailedFeed("test", "foo");
    checkOneFeed("FAILED-test", "foo");
  }

  @Test
  public void testOneOfEachFeed() throws Exception {
    archiver.saveFeed("test", "foo");
    archiver.saveFailedFeed("test", "bar");
    File[] feeds = getArchiveFeedFiles();
    assertEquals(2, feeds.length);
    for (File feed : feeds) {
      if (feed.getName().contains("FAILED")) {
        checkFeedFile(feed, "FAILED-test", "bar");
      } else {
        checkFeedFile(feed, "test", "foo");
      }
    }
  }

  @Test
  public void testMultipleFeeds() throws Exception {
    archiver.saveFeed("test", "foo");
    archiver.saveFeed("test", "bar");
    archiver.saveFeed("test", "baz");
    File[] feeds = getArchiveFeedFiles();
    assertEquals(3, feeds.length);
    Set<String> contents = new HashSet<String>();
    for (File feed : feeds) {
      assertTrue(feed.getName().matches("test-.+\\.xml"));
      contents.add(Files.toString(feed, Charset.forName("UTF-8")));
    }      
    assertTrue(contents.contains("foo"));
    assertTrue(contents.contains("bar"));
    assertTrue(contents.contains("baz"));
  }

  private void checkOneFeed(String feedName, String contents)
      throws Exception {
    File[] files = getArchiveFeedFiles();
    assertEquals(1, files.length);
    checkFeedFile(files[0], feedName, contents);
  }

  private void checkFeedFile(File file, String feedName, String contents)
      throws Exception {
    assertTrue(file.getName().matches(feedName + "-.+\\.xml"));
    assertEquals(contents, Files.toString(file, Charset.forName("UTF-8")));
  }

  /** Returns array of archive feed filenames. */
  private File[] getArchiveFeedFiles() throws IOException {
    return archiveDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".xml");
        }
      });
  }
}
