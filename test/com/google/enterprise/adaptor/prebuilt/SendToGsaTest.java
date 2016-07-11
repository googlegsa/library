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
import com.google.enterprise.adaptor.MockHttpHandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Tests for {@link SendToGsa}.
 */
public class SendToGsaTest {
  public static final String PREFIX =
      "test/com/google/enterprise/adaptor/prebuilt/resources/";
  public static final String FILE_1 = PREFIX + "TomSawyer.txt";
  public static final String FILE_2 = PREFIX + "MobyDick.txt";
  public static final String FILE_URLS = PREFIX + "urls.txt";
  public static final String SCRIPT_FILE = PREFIX + "script.txt";

  // configuration tests
  @Test
  public void testZeroValueFlagsThatDontForceWebFeed() throws Exception {
    String[] args = new String[]{"--aclCaseInsensitive", "--aclPublic",
        "--dontSend", "-lock", FILE_1};
    String goldenConfig = "Config(aclcaseinsensitive=true,aclpublic=true,"
        + "dontsend=true,feedtype=incremental,lock=true,files to feed: [test/"
        + "com/google/enterprise/adaptor/prebuilt/resources/TomSawyer.txt])";
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
    String[] args = new String[]{"--script", SCRIPT_FILE, "--dontsend", FILE_1};
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
        "--dataSource", "datasource", "--lastModified", "06/13/2016", "--gsa",
        "mygsa.example.com", "--feedtype", "full", "--mimeType", "text/plain",
        "--lastmodified", "06/13/2016", "--mimetype", "text/plain",
        "--dontsend", FILE_1};
    String goldenConfig = "Config(aclnamespace=namespace,aclpublic=true,"
        + "datasource=datasource,dontsend=true,feeddirectory=/tmp/feeds,"
        + "feedtype=full,gsa=mygsa.example.com,lastmodified=06/13/2016,"
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
        "--dataSource", "datasource2", "--lastModified", "6/13/2016", "--gsa",
        "mygsa2.example.com", "--feedtype", "incremental", "--mimeType",
        "text/plain", "--lastmodified", "12345679", "--mimetype", "text/rtf",
        "--dontsend", FILE_1};
    String goldenConfig = "Config(aclnamespace=namespace,aclpublic=true,"
        + "datasource=datasource,dontsend=true,feeddirectory=/tmp/feeds,"
        + "feedtype=full,gsa=mygsa.example.com,lastmodified=6/13/2016,"
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

  // createFeedFile tests
  // TODO(myk): tests for SimpleGsaFeedFileMaker also needed!
  @Test
  public void testCreateIncrementalFeedOfFile1() throws Exception {
    String[] args = new String[]{"--aclPublic", "--dontSend", FILE_1};
    String hostname = getHostname();
    String goldenConfig = "Config(aclpublic=true,dontsend=true,"
        + "feedtype=incremental,files to feed: [test/com/google/enterprise/"
        + "adaptor/prebuilt/resources/TomSawyer.txt])";
    String goldenXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>send2gsa</datasource>\n"
        + "<feedtype>incremental</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record last-modified=\"Wed, 01 Jun 2016 09:55:36 -0700\" "
        + "mimetype=\"text/plain\""
        + " url=\"googleconnector://" + hostname + "/test/com/google/enterprise"
        + "/adaptor/prebuilt/resources/TomSawyer.txt\">\n"
        + "<content encoding=\"base64binary\">c25pcHBldCBmcm9tIFRvbSBTYXd5ZXIsI"
        + "GJ5IE1hcmsgVHdhaW4uCgoiVG9tIHNhaWQgdG8gaGltc2VsZiB0aGF0IGl0IHdhcyBub"
        + "3Qgc3VjaCBhIGhvbGxvdyB3b3JsZCwgYWZ0ZXIgYWxsLiBIZSBoYWQKZGlzY292ZXJlZ"
        + "CBhIGdyZWF0IGxhdyBvZiBodW1hbiBhY3Rpb24sIHdpdGhvdXQga25vd2luZyBpdCAtL"
        + "SBuYW1lbHksIHRoYXQgaW4Kb3JkZXIgdG8gbWFrZSBhIG1hbiBvciBhIGJveSBjb3Zld"
        + "CBhIHRoaW5nLCBpdCBpcyBvbmx5IG5lY2Vzc2FyeSB0byBtYWtlIHRoZQp0aGluZyBka"
        + "WZmaWN1bHQgdG8gYXR0YWluLiIK</content>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
    String xml = test.createFeedFile();
    xml = removePathPrefixOfTestDirectory(hostname, xml);
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(goldenXml, xml);
  }

  @Test
  public void testCreateFullFeedOfFile1() throws Exception {
    String[] args = new String[]{"--aclPublic", "--dontSend", "--feedType",
        "full", "--lastModified", "06/13/2016", FILE_1};
    String hostname = getHostname();
    String goldenConfig = "Config(aclpublic=true,dontsend=true,"
        + "feedtype=full,lastmodified=06/13/2016,files to feed: [test/com/"
        + "google/enterprise/adaptor/prebuilt/resources/TomSawyer.txt])";
    String goldenXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>send2gsa</datasource>\n"
        + "<feedtype>full</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record last-modified=\"Mon, 13 Jun 2016 00:00:00 -0700\" "
        + "mimetype=\"text/plain\""
        + " url=\"googleconnector://" + hostname + "/test/com/google/enterprise"
        + "/adaptor/prebuilt/resources/TomSawyer.txt\">\n"
        + "<content encoding=\"base64binary\">c25pcHBldCBmcm9tIFRvbSBTYXd5ZXIsI"
        + "GJ5IE1hcmsgVHdhaW4uCgoiVG9tIHNhaWQgdG8gaGltc2VsZiB0aGF0IGl0IHdhcyBub"
        + "3Qgc3VjaCBhIGhvbGxvdyB3b3JsZCwgYWZ0ZXIgYWxsLiBIZSBoYWQKZGlzY292ZXJlZ"
        + "CBhIGdyZWF0IGxhdyBvZiBodW1hbiBhY3Rpb24sIHdpdGhvdXQga25vd2luZyBpdCAtL"
        + "SBuYW1lbHksIHRoYXQgaW4Kb3JkZXIgdG8gbWFrZSBhIG1hbiBvciBhIGJveSBjb3Zld"
        + "CBhIHRoaW5nLCBpdCBpcyBvbmx5IG5lY2Vzc2FyeSB0byBtYWtlIHRoZQp0aGluZyBka"
        + "WZmaWN1bHQgdG8gYXR0YWluLiIK</content>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
    String xml = test.createFeedFile();
    xml = removePathPrefixOfTestDirectory(hostname, xml);
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(goldenXml, xml);
  }

  @Test
  public void testCreateAclPublicWebFeed() throws Exception {
    String[] args = new String[]{"--aclPublic", "--dontSend", "--feedType",
        "web", FILE_URLS};
    String hostname = getHostname();
    String goldenConfig = "Config(aclpublic=true,dontsend=true,"
        + "feedtype=web,files to feed: [test/com/google/enterprise/adaptor/"
        + "prebuilt/resources/urls.txt])";
    String goldenXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>web</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record mimetype=\"text/plain\" url=\"http://www.google.com/\"/>\n"
        + "<record mimetype=\"text/plain\" url=\"https://www.google.com/\"/>\n"
        + "<record mimetype=\"text/plain\" url=\"http://www.yahoo.com/\"/>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
    String xml = test.createFeedFile();
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(goldenXml, xml);
  }

  @Test
  public void testCreateIncrementalFeedOfFile1WithAcls() throws Exception {
    String[] args = new String[]{"--aclAllowUsers", "user1", "--aclDenyUsers",
        "user2", "--aclAllowGroups", "group1,group2", "--aclDenyGroups",
        "group3", "--aclDenyGroups", "group4", "--dontSend", FILE_1};
    String hostname = getHostname();
    String goldenConfig = "Config(aclallowgroups=group1,group2,"
        + "aclallowusers=user1,acldenygroups=group3,group4,acldenyusers=user2,"
        + "dontsend=true,feedtype=incremental,files to feed: [test/com/google/"
        + "enterprise/adaptor/prebuilt/resources/TomSawyer.txt])";
    String goldenXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>send2gsa</datasource>\n"
        + "<feedtype>incremental</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record last-modified=\"Wed, 01 Jun 2016 09:55:36 -0700\" "
        + "mimetype=\"text/plain\""
        + " url=\"googleconnector://" + hostname + "/test/com/google/enterprise"
        + "/adaptor/prebuilt/resources/TomSawyer.txt\">\n"
        + "<acl>\n"
        + "<principal access=\"permit\" scope=\"user\">user1</principal>\n"
        + "<principal access=\"permit\" scope=\"group\">group1</principal>\n"
        + "<principal access=\"permit\" scope=\"group\">group2</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">user2</principal>\n"
        + "<principal access=\"deny\" scope=\"group\">group3</principal>\n"
        + "<principal access=\"deny\" scope=\"group\">group4</principal>\n"
        + "</acl>\n"
        + "<content encoding=\"base64binary\">c25pcHBldCBmcm9tIFRvbSBTYXd5ZXIsI"
        + "GJ5IE1hcmsgVHdhaW4uCgoiVG9tIHNhaWQgdG8gaGltc2VsZiB0aGF0IGl0IHdhcyBub"
        + "3Qgc3VjaCBhIGhvbGxvdyB3b3JsZCwgYWZ0ZXIgYWxsLiBIZSBoYWQKZGlzY292ZXJlZ"
        + "CBhIGdyZWF0IGxhdyBvZiBodW1hbiBhY3Rpb24sIHdpdGhvdXQga25vd2luZyBpdCAtL"
        + "SBuYW1lbHksIHRoYXQgaW4Kb3JkZXIgdG8gbWFrZSBhIG1hbiBvciBhIGJveSBjb3Zld"
        + "CBhIHRoaW5nLCBpdCBpcyBvbmx5IG5lY2Vzc2FyeSB0byBtYWtlIHRoZQp0aGluZyBka"
        + "WZmaWN1bHQgdG8gYXR0YWluLiIK</content>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
    String xml = test.createFeedFile();
    xml = removePathPrefixOfTestDirectory(hostname, xml);
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(goldenXml, xml);
  }

  @Test
  public void testCreateWebFeedWithAclsFromScript() throws Exception {
    String[] args = new String[]{"--dontSend", "--feedType", "web", "--script",
        SCRIPT_FILE, FILE_URLS};
    String hostname = getHostname();
    String goldenConfig = "Config(aclallowusers=one,two,three,"
        + "acldenyusers=four,five,six,crawlimmediately=true,dontsend=true,"
        + "feedtype=web,suffixtoignore=.gif,.jpg,files to feed: [test/com/"
        + "google/enterprise/adaptor/prebuilt/resources/urls.txt])";
    String expectedAcl = "<acl>\n"
        + "<principal access=\"permit\" scope=\"user\">one</principal>\n"
        + "<principal access=\"permit\" scope=\"user\">two</principal>\n"
        + "<principal access=\"permit\" scope=\"user\">three</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">four</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">five</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">six</principal>\n"
        + "</acl>\n";
    String goldenXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>web</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record crawl-immediately=\"true\" mimetype=\"text/plain\" "
        + "url=\"http://www.google.com/\">\n"
        + expectedAcl
        + "</record>\n"
        + "<record crawl-immediately=\"true\" mimetype=\"text/plain\" "
        + "url=\"https://www.google.com/\">\n"
        + expectedAcl
        + "</record>\n"
        + "<record crawl-immediately=\"true\" mimetype=\"text/plain\" "
        + "url=\"http://www.yahoo.com/\">\n"
        + expectedAcl
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
    String xml = test.createFeedFile();
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(goldenXml, xml);
  }

  // pushFeedFile tests
  @Test
  public void testPushIncrementalFeedOfFile1() throws Exception {
    String[] args = new String[]{"--aclPublic", "--gsa", "localhost", FILE_1};
    String hostname = getHostname();
    String goldenConfig = "Config(aclpublic=true,feedtype=incremental,"
        + "gsa=localhost,files to feed: [test/com/google/enterprise/adaptor/"
        + "prebuilt/resources/TomSawyer.txt])";
    String goldenXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>send2gsa</datasource>\n"
        + "<feedtype>incremental</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record last-modified=\"Wed, 01 Jun 2016 09:55:36 -0700\" "
        + "mimetype=\"text/plain\""
        + " url=\"googleconnector://" + hostname + "/test/com/google/enterprise"
        + "/adaptor/prebuilt/resources/TomSawyer.txt\">\n"
        + "<content encoding=\"base64binary\">c25pcHBldCBmcm9tIFRvbSBTYXd5ZXIsI"
        + "GJ5IE1hcmsgVHdhaW4uCgoiVG9tIHNhaWQgdG8gaGltc2VsZiB0aGF0IGl0IHdhcyBub"
        + "3Qgc3VjaCBhIGhvbGxvdyB3b3JsZCwgYWZ0ZXIgYWxsLiBIZSBoYWQKZGlzY292ZXJlZ"
        + "CBhIGdyZWF0IGxhdyBvZiBodW1hbiBhY3Rpb24sIHdpdGhvdXQga25vd2luZyBpdCAtL"
        + "SBuYW1lbHksIHRoYXQgaW4Kb3JkZXIgdG8gbWFrZSBhIG1hbiBvciBhIGJveSBjb3Zld"
        + "CBhIHRoaW5nLCBpdCBpcyBvbmx5IG5lY2Vzc2FyeSB0byBtYWtlIHRoZQp0aGluZyBka"
        + "WZmaWN1bHQgdG8gYXR0YWluLiIK</content>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    final String goldenResponse
        = "--<<\r\n"
        + "Content-Disposition: form-data; name=\"datasource\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "send2gsa\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"feedtype\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "incremental\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"data\"\r\n"
        + "Content-Type: text/xml\r\n"
        + "\r\n"
        + goldenXml + "\r\n"
        + "--<<--\r\n";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
    String xml = test.createFeedFile();
    xml = removePathPrefixOfTestDirectory(hostname, xml);
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(goldenXml, xml);

    Charset charset = Charset.forName("UTF-8");
    HttpServer server = HttpServer.create(new InetSocketAddress(19900), 0);
    server.start();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    test.pushFeedFile(xml);
    assertEquals("POST", handler.getRequestMethod());
    assertEquals(URI.create("/xmlfeed"), handler.getRequestUri());
    assertEquals("multipart/form-data; boundary=<<",
        handler.getRequestHeaders().getFirst("Content-Type"));
    assertEquals(goldenResponse,
        new String(handler.getRequestBytes(), charset));
    server.stop(5 /* seconds */); // allow enough time to tear down the server
    server = null;
  }

  @Test
  public void testPushFullFeedOfFile1WithAcls() throws Exception {
    String[] args = new String[]{"--aclAllowUsers", "user1", "--aclDenyUsers",
        "user2", "--aclAllowGroups", "group1,group2", "--aclDenyGroups",
        "group3", "--aclDenyGroups", "group4", "--gsa", "localhost",
        "--feedtype", "full", FILE_1};
    String hostname = getHostname();
    String goldenConfig = "Config(aclallowgroups=group1,group2,"
        + "aclallowusers=user1,acldenygroups=group3,group4,acldenyusers=user2,"
        + "feedtype=full,gsa=localhost,files to feed: [test/com/google/"
        + "enterprise/adaptor/prebuilt/resources/TomSawyer.txt])";
    String goldenXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>send2gsa</datasource>\n"
        + "<feedtype>full</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record last-modified=\"Wed, 01 Jun 2016 09:55:36 -0700\" "
        + "mimetype=\"text/plain\""
        + " url=\"googleconnector://" + hostname + "/test/com/google/enterprise"
        + "/adaptor/prebuilt/resources/TomSawyer.txt\">\n"
        + "<acl>\n"
        + "<principal access=\"permit\" scope=\"user\">user1</principal>\n"
        + "<principal access=\"permit\" scope=\"group\">group1</principal>\n"
        + "<principal access=\"permit\" scope=\"group\">group2</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">user2</principal>\n"
        + "<principal access=\"deny\" scope=\"group\">group3</principal>\n"
        + "<principal access=\"deny\" scope=\"group\">group4</principal>\n"
        + "</acl>\n"
        + "<content encoding=\"base64binary\">c25pcHBldCBmcm9tIFRvbSBTYXd5ZXIsI"
        + "GJ5IE1hcmsgVHdhaW4uCgoiVG9tIHNhaWQgdG8gaGltc2VsZiB0aGF0IGl0IHdhcyBub"
        + "3Qgc3VjaCBhIGhvbGxvdyB3b3JsZCwgYWZ0ZXIgYWxsLiBIZSBoYWQKZGlzY292ZXJlZ"
        + "CBhIGdyZWF0IGxhdyBvZiBodW1hbiBhY3Rpb24sIHdpdGhvdXQga25vd2luZyBpdCAtL"
        + "SBuYW1lbHksIHRoYXQgaW4Kb3JkZXIgdG8gbWFrZSBhIG1hbiBvciBhIGJveSBjb3Zld"
        + "CBhIHRoaW5nLCBpdCBpcyBvbmx5IG5lY2Vzc2FyeSB0byBtYWtlIHRoZQp0aGluZyBka"
        + "WZmaWN1bHQgdG8gYXR0YWluLiIK</content>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    final String goldenResponse
        = "--<<\r\n"
        + "Content-Disposition: form-data; name=\"datasource\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "send2gsa\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"feedtype\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "full\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"data\"\r\n"
        + "Content-Type: text/xml\r\n"
        + "\r\n"
        + goldenXml + "\r\n"
        + "--<<--\r\n";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
    String xml = test.createFeedFile();
    xml = removePathPrefixOfTestDirectory(hostname, xml);
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(goldenXml, xml);

    Charset charset = Charset.forName("UTF-8");
    HttpServer server = HttpServer.create(new InetSocketAddress(19900), 0);
    server.start();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    test.pushFeedFile(xml);
    assertEquals("POST", handler.getRequestMethod());
    assertEquals(URI.create("/xmlfeed"), handler.getRequestUri());
    assertEquals("multipart/form-data; boundary=<<",
        handler.getRequestHeaders().getFirst("Content-Type"));
    assertEquals(goldenResponse,
        new String(handler.getRequestBytes(), charset));
    server.stop(5 /* seconds */); // allow enough time to tear down the server
    server = null;
  }

  @Test
  public void testPushWebFeedWithAcls() throws Exception {
    String[] args = new String[]{"--gsa", "localhost", "--feedType", "web",
        "--script", SCRIPT_FILE, FILE_URLS};
    String goldenConfig = "Config(aclallowusers=one,two,three,"
        + "acldenyusers=four,five,six,crawlimmediately=true,feedtype=web,"
        + "gsa=localhost,suffixtoignore=.gif,.jpg,files to feed: [test/com/"
        + "google/enterprise/adaptor/prebuilt/resources/urls.txt])";
    String expectedAcl = "<acl>\n"
        + "<principal access=\"permit\" scope=\"user\">one</principal>\n"
        + "<principal access=\"permit\" scope=\"user\">two</principal>\n"
        + "<principal access=\"permit\" scope=\"user\">three</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">four</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">five</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">six</principal>\n"
        + "</acl>\n";
    String goldenXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>web</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record crawl-immediately=\"true\" mimetype=\"text/plain\" "
        + "url=\"http://www.google.com/\">\n"
        + expectedAcl
        + "</record>\n"
        + "<record crawl-immediately=\"true\" mimetype=\"text/plain\" "
        + "url=\"https://www.google.com/\">\n"
        + expectedAcl
        + "</record>\n"
        + "<record crawl-immediately=\"true\" mimetype=\"text/plain\" "
        + "url=\"http://www.yahoo.com/\">\n"
        + expectedAcl
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    final String goldenResponse
        = "--<<\r\n"
        + "Content-Disposition: form-data; name=\"datasource\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "web\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"feedtype\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "metadata-and-url\r\n"
        + "--<<\r\n"
        + "Content-Disposition: form-data; name=\"data\"\r\n"
        + "Content-Type: text/xml\r\n"
        + "\r\n"
        + goldenXml + "\r\n"
        + "--<<--\r\n";
    SendToGsa test = new SendToGsa();
    test.parseArgs(args);
    assertEquals(goldenConfig, test.getConfig().toString());
    String xml = test.createFeedFile();
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(goldenXml, xml);

    Charset charset = Charset.forName("UTF-8");
    HttpServer server = HttpServer.create(new InetSocketAddress(19900), 0);
    server.start();
    MockHttpHandler handler
        = new MockHttpHandler(200, "Success".getBytes(charset));
    server.createContext("/xmlfeed", handler);

    test.pushFeedFile(xml);
    assertEquals("POST", handler.getRequestMethod());
    assertEquals(URI.create("/xmlfeed"), handler.getRequestUri());
    assertEquals("multipart/form-data; boundary=<<",
        handler.getRequestHeaders().getFirst("Content-Type"));
    assertEquals(goldenResponse,
        new String(handler.getRequestBytes(), charset));
    server.stop(5 /* seconds */); // allow enough time to tear down the server
    server = null;
  }

  // helper routines

  private static String getHostname() {
    String hostname = null;
    try {
      hostname = InetAddress.getLocalHost().getCanonicalHostName();
      hostname = hostname.toLowerCase(Locale.ENGLISH);
      return hostname;
    } catch (UnknownHostException ex) {
      fail("could not determine hostname");
    }
    return hostname;
  }

  /**
   * Allows the xml to match the golden xml by removing the prefix of the
   * test directory (everything up to the leading "test/com/google/enterprise").
   */
  private static String removePathPrefixOfTestDirectory(String hostname,
      String xml) {
    int hostnameLocation = xml.indexOf(hostname + "/");
    if (hostnameLocation == -1) {
      fail("could not find hostname in xml '" + xml + "'");
    }
    hostnameLocation += hostname.length() + 1;
    int testLocation = xml.indexOf("test/com/google/enterprise",
        hostnameLocation);
    if (testLocation == -1) {
      fail("could not find test directory in xml '" + xml + "'");
    }
    return xml.substring(0, hostnameLocation) + xml.substring(testLocation);
  }

  /**
   * Allows the xml to match the golden xml by removing the specific Java
   * version from the gsafeed header.
   */
  private static String removeJavaVersion(String javaVersion, String xml) {
    String versionPrefix = "<!--Version ";
    int javaLocation = xml.indexOf(versionPrefix + javaVersion);
    if (javaLocation == -1) {
      fail("could not find Java version string in xml '" + xml + "'");
    }
    return xml.replace(versionPrefix + javaVersion, versionPrefix + "X.Y.Z");
  }
}
