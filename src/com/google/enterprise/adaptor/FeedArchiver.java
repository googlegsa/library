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

/**
 * Takes an XML feed destined for the GSA and makes a copy in the
 * configured feed archive directory.  The feed archive directory is
 * specified using the {@code feed.archiveDirectory} configuration property.
 */
interface FeedArchiver {
  /**
   * Save the supplied XML string as a file in the feed archive
   * directory.  The file's name will start with the feed name
   * and have a {@code .xml} extension.
   *
   * @param feedName the name of the feed or datasource
   * @param feedXml the XML string that will be saved
   */
  public void saveFeed(String feedName, String feedXml);

  /**
   * Save the supplied XML string as a file in the feed archive
   * directory.  The file's name will start with {@code FAILED-},
   * followed by the feed name, and have a {@code .xml} extension.
   *
   * @param feedName the name of the feed or datasource
   * @param feedXml the XML string that will be saved
   */
  public void saveFailedFeed(String feedName, String feedXml);
}
