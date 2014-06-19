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

import com.google.common.base.Strings;
import com.google.common.io.Files;

import java.io.IOException;
import java.io.File;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Takes an XML feed file destined for the GSA and makes a copy in the
 * configured feed archive directory.  The feed archive directory is
 * specified using the {@code feed.archiveDirectory} configuration property.
 */
class GsaFeedFileArchiver implements FeedArchiver {
  private static final Logger log =
      Logger.getLogger(GsaFeedFileArchiver.class.getName());

  private static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
  private final File archiveDir;
  
  public GsaFeedFileArchiver(String archiveDirectory) {
    this.archiveDir = Strings.isNullOrEmpty(archiveDirectory) 
        ? null : new File(archiveDirectory);
  }

  public void saveFeed(String feedName, String feedXml) {
    if (archiveDir != null) {
      try {
        File file = File.createTempFile(feedName + "-", ".xml", archiveDir);
        Files.write(feedXml, file, CHARSET_UTF8);
      } catch (IOException e) {
        log.log(Level.WARNING, "failed to archive feed file", e);
      }
    }
  }

  public void saveFailedFeed(String feedName, String feedXml) {
    saveFeed("FAILED-" + feedName, feedXml);
  }
}
