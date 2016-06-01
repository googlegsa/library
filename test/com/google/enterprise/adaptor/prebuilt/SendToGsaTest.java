// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor.prebuilt;

import com.google.enterprise.adaptor.InvalidConfigurationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Tests for {@link SendToGsa}.
 */
public class SendToGsaTest {
  public static final String PREFIX =
      "test/com/google/enterprise/adaptor/prebuilt/resources/";
  public static final String FILE_1 = PREFIX + "TomSawyer.txt";
  public static final String FILE_2 = PREFIX + "MobyDick.txt";
  public static final String SCRIPT_FILE = PREFIX + "script.txt";

  @Test
  public void testZeroValueFlagsThatDontForceWebFeed() throws Exception {
    String[] args = new String[]{"--aclCaseInsensitive", "--aclPublic",
        "--dontSend", "-lock", "--noarchive", "--nofollow", FILE_1};
    String goldenConfig = "Config(aclcaseinsensitive=true,aclpublic=true,"
        + "dontsend=true,feedtype=incremental,lock=true,noarchive=true,"
        + "nofollow=true,files to feed: [test/com/google/enterprise/adaptor/"
        + "prebuilt/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // only test with crawlImmediately, but also true for crawlOnce and
  // filesOfUrls
  @Test
  public void testCrawlImmediatelyForcesWebFeed() throws Exception {
    String[] args = new String[]{"--crawlImmediately", "--aclPublic",
        "--dontsend", FILE_1};
    String goldenConfig = "Config(aclpublic=true,crawlimmediately=true,"
        + "dontsend=true,feedtype=web,files to feed: [test/com/google/"
        + "enterprise/adaptor/prebuilt/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // test how aclAllowUsers, aclDenyUsers, and suffixToIgnore can be called
  // more than once.
  @Test
  public void testMultiValueFlags() throws Exception {
    String[] args = new String[]{"--crawlImmediately", "--aclAllowUsers", "one",
        "--aclAllowUsers", "two,three", "--aclDenyUsers", "four,five",
        "--suffixToIgnore", ".gif", "--acldenyusers", "six", "--suffixToIgnore",
        ".jpg", "--dontsend", FILE_1};
    String goldenConfig = "Config(aclallowusers=one,two,three,acldenyusers="
        + "four,five,six,crawlimmediately=true,dontsend=true,feedtype=web,"
        + "suffixtoignore=.gif,.jpg,files to feed: [test/com/google/enterprise/"
        + "adaptor/prebuilt/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // same as above, from a script file
  @Test
  public void testMultiValueFlagsFromScriptFile() throws Exception {
    String[] args = new String[]{"--script", SCRIPT_FILE, FILE_1};
    String goldenConfig = "Config(aclallowusers=one,two,three,acldenyusers="
        + "four,five,six,crawlimmediately=true,dontsend=true,feedtype=web,"
        + "suffixtoignore=.gif,.jpg,files to feed: [test/com/google/enterprise/"
        + "adaptor/prebuilt/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // test calling each of the single value flags (twice), with identical values
  // each time.
  @Test
  public void testDuplicateSingleValueFlags() throws Exception {
    String[] args = new String[]{"--aclPublic", "-aclpublic", "--aclNameSpace",
        "namespace", "--aclNameSpace", "namespace", "--feedType", "full",
        "--datasource", "datasource", "--feedDirectory", "/tmp/feeds",
        "--feeddirectory", "/tmp/feeds", "--gsa", "mygsa.example.com",
        "--dataSource", "datasource", "--lastModified", "12345678", "--gsa",
        "mygsa.example.com", "--feedtype", "full", "--mimeType", "text/plain",
        "--lastmodified", "12345678", "--mimetype", "text/plain",
        "--dontsend", FILE_1};
    String goldenConfig = "Config(aclnamespace=namespace,aclpublic=true,"
        + "datasource=datasource,dontsend=true,feeddirectory=/tmp/feeds,"
        + "feedtype=full,gsa=mygsa.example.com,lastmodified=12345678,"
        + "mimetype=text/plain,files to feed: [test/com/google/enterprise/"
        + "adaptor/prebuilt/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // test calling each of the single value flags (twice), with conflicting
  // values each time.
  @Test
  public void testConflictingSingleValueFlags() {
    String[] args = new String[]{"--aclPublic", "-aclpublic", "--aclNameSpace",
        "namespace", "--aclNameSpace", "namespace2", "--feedType", "full",
        "--datasource", "datasource", "--feedDirectory", "/tmp/feeds",
        "--feeddirectory", "/tmp/feeds2", "--gsa", "mygsa.example.com",
        "--dataSource", "datasource2", "--lastModified", "12345678", "--gsa",
        "mygsa2.example.com", "--feedtype", "incremental", "--mimeType",
        "text/plain", "--lastmodified", "12345679", "--mimetype", "text/rtf",
        "--dontsend", FILE_1};
    String goldenConfig = "Config(aclnamespace=namespace,aclpublic=true,"
        + "datasource=datasource,dontsend=true,feeddirectory=/tmp/feeds,"
        + "feedtype=full,gsa=mygsa.example.com,lastmodified=12345678,"
        + "mimetype=text/plain,files to feed: [test/com/google/enterprise/"
        + "adaptor/prebuilt/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    try {
      test.parseArgs(args);
    } catch (InvalidConfigurationException e) {
      String message = e.getMessage();
      assertTrue(message.startsWith("Encountered 7 error(s)"));
      assertEquals(7, message.split("was already set to", -1).length - 1);
    }
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // test calling with no flags
  @Test
  public void testWithoutFlags() {
    String[] args = new String[]{};
    String goldenConfig = "Config(feedtype=incremental,files to feed: [])";
    SendToGsa test = new SendToGsa();
    try {
      test.parseArgs(args);
    } catch (InvalidConfigurationException e) {
      String message = e.getMessage();
      assertTrue(message.startsWith("Encountered 3 error(s)"));
      assertTrue(message.contains("either aclPublic flag or at least"));
      assertTrue(message.contains("No content specified.  send2gsa must be "
          + "invoked with a non-empty list of files"));
      assertTrue(message.contains("You must either specify the 'gsa' or the "
          + "'dontSend' flag"));
    }
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // test calling with no flags
  @Test
  public void testWithNullFlag() {
    String[] args = new String[]{null};
    String goldenConfig = "Config(feedtype=incremental,files to feed: [])";
    SendToGsa test = new SendToGsa();
    try {
      test.parseArgs(args);
    } catch (InvalidConfigurationException e) {
      String message = e.getMessage();
      assertTrue(message.startsWith("Encountered 4 error(s)"));
      assertTrue(message.contains("Encountered null flag"));
      assertTrue(message.contains("either aclPublic flag or at least"));
      assertTrue(message.contains("No content specified.  send2gsa must be "
          + "invoked with a non-empty list of files"));
      assertTrue(message.contains("You must either specify the 'gsa' or the "
          + "'dontSend' flag"));
    }
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // test calling with both 'aclPublic' and 'aclDenyUsers'
  @Test
  public void testAclPublicAndAclDenyUsers() {
    String[] args = new String[]{"--aclPublic", "--aclDenyUsers", "one",
        "--dontsend", FILE_1};
    String goldenConfig = "Config(acldenyusers=one,aclpublic=true,dontsend=true"
        + ",feedtype=incremental,files to feed: [test/com/google/enterprise/"
        + "adaptor/prebuilt/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    try {
      test.parseArgs(args);
    } catch (InvalidConfigurationException e) {
      String message = e.getMessage();
      assertTrue(message.startsWith("Encountered 1 error(s)"));
      assertTrue(message.contains("aclPublic flag may not be set together"));
    }
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // test calling with both 'crawlOnce' and 'feedType full'
  @Test
  public void testCrawlOnceAndFeedTypeFull() {
    String[] args = new String[]{"--crawlonce", "--aclpublic", "--feedtype",
        "full", "--dontsend", FILE_1};
    String goldenConfig = "Config(aclpublic=true,crawlonce=true,dontsend=true,"
        + "feedtype=full,files to feed: [test/com/google/enterprise/adaptor/"
        + "prebuilt/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    try {
      test.parseArgs(args);
    } catch (InvalidConfigurationException e) {
      String message = e.getMessage();
      assertTrue(message.startsWith("Encountered 1 error(s)"));
      assertTrue(message.contains("at least one of"));
      assertTrue(message.contains("but feedtype set to full"));
    }
    assertEquals(goldenConfig, test.getConfig().toString());
  }

  // test calling with invalid feedType
  @Test
  public void testInvalidFeedType() {
    String[] args = new String[]{"--aclpublic", "--feedtype", "foo",
       "--dontsend", FILE_1};
    String goldenConfig = "Config(aclpublic=true,dontsend=true,feedtype=foo,"
        + "files to feed: [test/com/google/enterprise/adaptor/prebuilt"
        + "/resources/TomSawyer.txt])";
    SendToGsa test = new SendToGsa();
    try {
      test.parseArgs(args);
    } catch (InvalidConfigurationException e) {
      String message = e.getMessage();
      assertTrue(message.startsWith("Encountered 1 error(s)"));
      assertTrue(message.contains("feedType must be set to 'full'"));
      assertTrue(message.contains(", not 'foo'"));
    }
    assertEquals(goldenConfig, test.getConfig().toString());
  }
}
