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

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tests for {@link GsaFeedFileMaker}. */
public class GsaFeedFileMakerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DocIdEncoder encoder = new MockDocIdCodec();
  private AclTransform aclTransform
      = new AclTransform(Arrays.<AclTransform.Rule>asList());
  private GsaFeedFileMaker meker = new GsaFeedFileMaker(encoder, aclTransform);

  @Test
  public void testEmptyMetadataAndUrl() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group/>\n"
        + "</gsafeed>\n";
    String xml = meker.makeMetadataAndUrlXml("t3sT",
        new ArrayList<DocIdPusher.Record>());
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testSimpleMetadataAndUrl() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record mimetype=\"text/plain\""
        + " url=\"http://localhost/E11\"/>\n"
        + "<record mimetype=\"text/plain\""
        + " url=\"http://localhost/elefenta\">\n"
        + "<metadata>\n"
        + "<meta content=\"bar\" name=\"foo\"/>\n"
        + "<meta content=\"baz\" name=\"foo\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "<record mimetype=\"text/plain\""
        + " url=\"http://localhost/empty-not-null-metadata\">\n"
        + "<metadata/>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    ArrayList<DocIdPusher.Record> ids = new ArrayList<DocIdPusher.Record>();
    ids.add(new DocIdPusher.Record.Builder(new DocId("E11")).build());
    ids.add(new DocIdPusher.Record.Builder(new DocId("elefenta"))
        .addMetadata("foo", "baz").addMetadata("foo", "bar").build());
    ids.add(new DocIdPusher.Record.Builder(new DocId("empty-not-null-metadata"))
        .setMetadata(new Metadata()).build());
    String xml = meker.makeMetadataAndUrlXml("t3sT", ids);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testPushAttributesMetadataAndUrl()
      throws java.net.URISyntaxException {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record displayurl=\"http://f000nkey.net\" mimetype=\"text/plain\""
        + " url=\"http://localhost/E11\"/>\n"
        + "<record crawl-immediately=\"true\""
        + " displayurl=\"http://yankee.doodle.com\""
        + " last-modified=\"Thu, 01 Jan 1970 00:00:00 +0000\""
        + " mimetype=\"text/plain\" url=\"http://localhost/elefenta\"/>\n"
        + "<record displayurl=\"http://google.com/news\""
        + " last-modified=\"Fri, 02 Jan 1970 00:00:00 +0000\""
        + " mimetype=\"text/plain\" url=\"http://localhost/gone\"/>\n"
        + "<record crawl-immediately=\"true\" crawl-once=\"true\""
        + " lock=\"true\" mimetype=\"text/plain\""
        + " url=\"http://localhost/flagson\"/>\n"
        + "<record action=\"delete\" mimetype=\"text/plain\""
        + " url=\"http://localhost/deleted\"/>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    ArrayList<DocIdPusher.Record> ids = new ArrayList<DocIdPusher.Record>();
    DocIdPusher.Record.Builder attrBuilder
        = new DocIdPusher.Record.Builder(new DocId("E11"));

    attrBuilder.setResultLink(new URI("http://f000nkey.net"));
    ids.add(attrBuilder.build());

    attrBuilder.setResultLink(new URI("http://yankee.doodle.com"));
    attrBuilder.setLastModified(new Date(0));
    attrBuilder.setCrawlImmediately(true);
    attrBuilder.setDocId(new DocId("elefenta"));
    ids.add(attrBuilder.build());

    attrBuilder.setResultLink(new URI("http://google.com/news"));
    attrBuilder.setLastModified(new Date(1000 * 60 * 60 * 24));
    attrBuilder.setCrawlImmediately(false);
    attrBuilder.setDocId(new DocId("gone"));
    ids.add(attrBuilder.build());

    ids.add(new DocIdPusher.Record.Builder(new DocId("flagson"))
        .setLock(true).setCrawlImmediately(true).setCrawlOnce(true).build());

    ids.add(new DocIdPusher.Record.Builder(new DocId("deleted"))
        .setDeleteFromIndex(true).build());

    String xml = meker.makeMetadataAndUrlXml("t3sT", ids);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testNamedResourcesMetadataAndUrl() {
    String golden
        = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>test</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<acl url=\"http://localhost/docid1\"/>\n"
        + "<acl inheritance-type=\"and-both-permit\""
        + " url=\"http://localhost/docid2\">\n"
        + "<principal access=\"permit\" scope=\"user\">pu1</principal>\n"
        + "<principal access=\"permit\" scope=\"user\">pu2</principal>\n"
        + "<principal access=\"permit\" scope=\"group\">pg1</principal>\n"
        + "<principal access=\"permit\" scope=\"group\">pg2&lt;</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">du1</principal>\n"
        + "<principal access=\"deny\" scope=\"user\">du2</principal>\n"
        + "<principal access=\"deny\" scope=\"group\">dg1</principal>\n"
        + "<principal access=\"deny\" scope=\"group\">dg2&amp;</principal>\n"
        + "</acl>\n"
        + "<acl inherit-from=\"http://localhost/doc%20id4\""
        + " url=\"http://localhost/doc%20id3%22&amp;%3C\"/>\n"
        + "<acl url=\"http://localhost/docid5\">\n"
        + "<principal access=\"permit\""
        + " case-sensitivity-type=\"everything-case-insensitive\""
        + " namespace=\"my_namespace\" scope=\"user\">pu1</principal>\n"
        + "</acl>\n"
        + "</group>\n"
        + "</gsafeed>\n";

    List<DocIdSender.AclItem> acls = new ArrayList<DocIdSender.AclItem>();
    acls.add(new DocIdSender.AclItem(new DocId("docid1"), new Acl.Builder()
        .build()));
    acls.add(new DocIdSender.AclItem(new DocId("docid2"), new Acl.Builder()
        .setInheritanceType(Acl.InheritanceType.AND_BOTH_PERMIT)
        .setPermitUsers(
            Arrays.asList(new UserPrincipal("pu1"), new UserPrincipal("pu2")))
        .setDenyUsers(
            Arrays.asList(new UserPrincipal("du1"), new UserPrincipal("du2")))
        .setPermitGroups(
            Arrays.asList(new GroupPrincipal("pg1"),
                new GroupPrincipal("pg2<")))
        .setDenyGroups(
            Arrays.asList(new GroupPrincipal("dg1"),
                new GroupPrincipal("dg2&")))
        .build()));
    acls.add(new DocIdSender.AclItem(new DocId("doc id3\"&<"), new Acl.Builder()
        .setInheritFrom(new DocId("doc id4"))
        .build()));
    acls.add(new DocIdSender.AclItem(new DocId("docid5"), new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setPermitUsers(Arrays.asList(new UserPrincipal("pu1", "my_namespace")))
        .build()));

    String xml = meker.makeMetadataAndUrlXml("test", acls);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testNamedResourcesAclTransform() {
    String golden
        = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>test</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<acl url=\"http://localhost/docid2\">\n"
        + "<principal access=\"permit\" scope=\"user\">pu2</principal>\n"
        + "</acl>\n"
        + "</group>\n"
        + "</gsafeed>\n";

    List<DocIdSender.AclItem> acls = new ArrayList<DocIdSender.AclItem>();
    acls.add(new DocIdSender.AclItem(new DocId("docid2"), new Acl.Builder()
        .setPermitUsers(
            Arrays.asList(new UserPrincipal("pu1")))
        .build()));

    aclTransform = new AclTransform(Arrays.asList(new AclTransform.Rule(
        new AclTransform.MatchData(null, null, null, null),
        new AclTransform.MatchData(null, "pu2", null, null))));
    meker = new GsaFeedFileMaker(encoder, aclTransform);
    String xml = meker.makeMetadataAndUrlXml("test", acls);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testUnsupportedDocIdSenderItemMetadataAndUrl() {
    class UnsupportedItem implements DocIdSender.Item {};
    List<UnsupportedItem> items = new ArrayList<UnsupportedItem>();
    items.add(new UnsupportedItem());
    thrown.expect(IllegalArgumentException.class);
    meker.makeMetadataAndUrlXml("test", items);
  }

  @Test
  public void test614RecordWorkaround() {
    String golden
        = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>test</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record mimetype=\"text/plain\""
        + " url=\"http://localhost/docid1\"> </record>\n"
        + "</group>\n"
        + "</gsafeed>\n";

    List<DocIdPusher.Record> records = new ArrayList<DocIdPusher.Record>();
    records.add(new DocIdPusher.Record.Builder(new DocId("docid1")).build());

    meker = new GsaFeedFileMaker(encoder, aclTransform,
        true /* 6.14 workaround */, false);
    String xml = meker.makeMetadataAndUrlXml("test", records);
    xml = xml.replace("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void test70RecordWorkaround() {
    String golden
        = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>test</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record authmethod=\"httpsso\""
        + " mimetype=\"text/plain\" url=\"http://localhost/docid1\"/>\n"
        + "</group>\n"
        + "</gsafeed>\n";

    List<DocIdPusher.Record> records = new ArrayList<DocIdPusher.Record>();
    records.add(new DocIdPusher.Record.Builder(new DocId("docid1")).build());

    meker = new GsaFeedFileMaker(encoder, aclTransform, false,
        true /* 7.0 workaround */);
    String xml = meker.makeMetadataAndUrlXml("test", records);
    xml = xml.replace("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testAclFragmentMetadataAndUrl() {
    String golden
        = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>test</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<acl inherit-from=\"http://localhost/docid2?generated\""
        + " url=\"http://localhost/docid1?generated\"/>\n"
        + "</group>\n"
        + "</gsafeed>\n";

    DocIdSender.AclItem acl
        = new DocIdSender.AclItem(new DocId("docid1"), "generated",
          new Acl.Builder()
            .setInheritFrom(new DocId("docid2"), "generated").build());
    String xml = meker.makeMetadataAndUrlXml("test", Arrays.asList(acl));
    xml = xml.replace("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testEmptyGroupDefinitions() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE xmlgroups PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<xmlgroups>\n"
        + "<!--GSA EasyConnector-->\n"
        + "</xmlgroups>\n";
    String xml = meker.makeGroupDefinitionsXml(
        new TreeMap<GroupPrincipal, List<Principal>>().entrySet(), true);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testSimpleGroupDefinitions() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE xmlgroups PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<xmlgroups>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<membership>\n"
        + "<principal namespace=\"Default\" scope=\"GROUP\">immortals</principal>\n"
        + "<members>\n"
        + "<principal"
        + " case-sensitivity-type=\"EVERYTHING_CASE_INSENSITIVE\""
        + " namespace=\"Default\""
        + " scope=\"USER\""
        + ">MacLeod\\Duncan</principal>\n"
        + "</members>\n"
        + "</membership>\n"
        + "</xmlgroups>\n";
    Map<GroupPrincipal, List<Principal>> groupDefs
        = new TreeMap<GroupPrincipal, List<Principal>>();
    List<Principal> members = new ArrayList<Principal>();
    members.add(new UserPrincipal("MacLeod\\Duncan"));
    groupDefs.put(new GroupPrincipal("immortals"), members);
    String xml = meker.makeGroupDefinitionsXml(groupDefs.entrySet(), false);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testMultipleGroupDefinitions() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE xmlgroups PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<xmlgroups>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<membership>\n"
        + "<principal namespace=\"Default\" scope=\"GROUP\">immortals</principal>\n"
        + "<members>\n"
        + "<principal"
        + " case-sensitivity-type=\"EVERYTHING_CASE_INSENSITIVE\""
        + " namespace=\"Default\""
        + " scope=\"USER\""
        + ">Methos</principal>\n"
        + "<principal"
        + " case-sensitivity-type=\"EVERYTHING_CASE_INSENSITIVE\""
        + " namespace=\"Default\""
        + " scope=\"USER\""
        + ">MacLeod\\Duncan</principal>\n"
        + "</members>\n"
        + "</membership>\n"
        + "<membership>\n"
        + "<principal namespace=\"Default\" scope=\"GROUP\">sounds</principal>\n"
        + "<members>\n"
        + "<principal"
        + " case-sensitivity-type=\"EVERYTHING_CASE_INSENSITIVE\""
        + " namespace=\"Default\""
        + " scope=\"USER\""
        + ">plump</principal>\n"
        + "<principal"
        + " case-sensitivity-type=\"EVERYTHING_CASE_INSENSITIVE\""
        + " namespace=\"Default\""
        + " scope=\"USER\""
        + ">splat</principal>\n"
        + "</members>\n"
        + "</membership>\n"
        + "</xmlgroups>\n";
    Map<GroupPrincipal, List<Principal>> groupDefs
        = new TreeMap<GroupPrincipal, List<Principal>>();
    List<Principal> members = new ArrayList<Principal>();
    members.add(new UserPrincipal("MacLeod\\Duncan"));
    members.add(new UserPrincipal("Methos"));
    groupDefs.put(new GroupPrincipal("immortals"), members);
    List<Principal> members2 = new ArrayList<Principal>();
    members2.add(new UserPrincipal("splat"));
    members2.add(new UserPrincipal("plump"));
    groupDefs.put(new GroupPrincipal("sounds"), members2);
    String xml = meker.makeGroupDefinitionsXml(groupDefs.entrySet(), false);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testNestedGroupDefinitions() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE xmlgroups PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<xmlgroups>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<membership>\n"
        + "<principal namespace=\"Default\" scope=\"GROUP\">immortals</principal>\n"
        + "<members>\n"
        + "<principal"
        + " case-sensitivity-type=\"EVERYTHING_CASE_SENSITIVE\""
        + " namespace=\"3vil\""
        + " scope=\"GROUP\""
        + ">badguys</principal>\n"
        + "<principal"
        + " case-sensitivity-type=\"EVERYTHING_CASE_SENSITIVE\""
        + " namespace=\"goodguys\""
        + " scope=\"USER\""
        + ">MacLeod\\Duncan</principal>\n"
        + "</members>\n"
        + "</membership>\n"
        + "</xmlgroups>\n";
    Map<GroupPrincipal, List<Principal>> groupDefs
        = new TreeMap<GroupPrincipal, List<Principal>>();
    List<Principal> members = new ArrayList<Principal>();
    members.add(new UserPrincipal("MacLeod\\Duncan", "goodguys"));
    members.add(new GroupPrincipal("badguys", "3vil"));
    groupDefs.put(new GroupPrincipal("immortals"), members);
    String xml = meker.makeGroupDefinitionsXml(groupDefs.entrySet(), true);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testGroupDefinitionsAclTransform() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE xmlgroups PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<xmlgroups>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<membership>\n"
        + "<principal namespace=\"Default\" scope=\"GROUP\">Clan MacLeod"
        +  "</principal>\n"
        + "<members>\n"
        + "<principal case-sensitivity-type=\"EVERYTHING_CASE_INSENSITIVE\""
        + " namespace=\"Default\" scope=\"USER\">MacLeod\\Connor</principal>\n"
        + "</members>\n"
        + "</membership>\n"
        + "</xmlgroups>\n";
    Map<GroupPrincipal, List<Principal>> groupDefs
        = new TreeMap<GroupPrincipal, List<Principal>>();
    List<Principal> members = new ArrayList<Principal>();
    members.add(new UserPrincipal("MacLeod\\Duncan"));
    groupDefs.put(new GroupPrincipal("immortals"), members);
    aclTransform = new AclTransform(Arrays.asList(
        new AclTransform.Rule(
          new AclTransform.MatchData(null, "Duncan", null, null),
          new AclTransform.MatchData(null, "Connor", null, null)),
        new AclTransform.Rule(
          new AclTransform.MatchData(null, "immortals", null, null),
          new AclTransform.MatchData(null, "Clan MacLeod", null, null))));
    meker = new GsaFeedFileMaker(encoder, aclTransform);
    String xml = meker.makeGroupDefinitionsXml(groupDefs.entrySet(), false);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testCrawlImmediatelyOverride() throws java.net.URISyntaxException {
    GsaFeedFileMaker lclMeker = new GsaFeedFileMaker(encoder, aclTransform,
        false, false,
        /*override crawl-immediately?*/ true,
        /*crawl-immediately value*/ false,
        /*override crawl-once?*/ false,
        /*crawl-once value*/ false,
        Collections.<String>emptyList());
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"

        // (1)
        + "<record crawl-immediately=\"false\""
        + " displayurl=\"http://f000nkey.net\" mimetype=\"text/plain\""
        + " url=\"http://localhost/E11\"/>\n"

        // (2)
        + "<record crawl-immediately=\"false\""
        + " displayurl=\"http://yankee.doodle.com\""
        + " last-modified=\"Thu, 01 Jan 1970 00:00:00 +0000\""
        + " mimetype=\"text/plain\" url=\"http://localhost/elefenta\"/>\n"

        // (3)
        + "<record crawl-immediately=\"false\""
        + " displayurl=\"http://google.com/news\""
        + " last-modified=\"Fri, 02 Jan 1970 00:00:00 +0000\""
        + " mimetype=\"text/plain\" url=\"http://localhost/gone\"/>\n"

        // (4)
        + "<record crawl-immediately=\"false\" crawl-once=\"true\""
        + " lock=\"true\" mimetype=\"text/plain\""
        + " url=\"http://localhost/flagson\"/>\n"

        // (5)
        + "<record action=\"delete\" crawl-immediately=\"false\""
        + " mimetype=\"text/plain\""
        + " url=\"http://localhost/deleted\"/>\n"

        + "</group>\n"
        + "</gsafeed>\n";

    ArrayList<DocIdPusher.Record> ids = new ArrayList<DocIdPusher.Record>();
    DocIdPusher.Record.Builder attrBuilder
        = new DocIdPusher.Record.Builder(new DocId("E11"));

    // (1)
    attrBuilder.setResultLink(new URI("http://f000nkey.net"));
    ids.add(attrBuilder.build());

    // (2)
    attrBuilder.setResultLink(new URI("http://yankee.doodle.com"));
    attrBuilder.setLastModified(new Date(0));
    attrBuilder.setCrawlImmediately(true);
    attrBuilder.setDocId(new DocId("elefenta"));
    ids.add(attrBuilder.build());

    // (3)
    attrBuilder.setResultLink(new URI("http://google.com/news"));
    attrBuilder.setLastModified(new Date(1000 * 60 * 60 * 24));
    attrBuilder.setCrawlImmediately(false);
    attrBuilder.setCrawlOnce(false);
    attrBuilder.setDocId(new DocId("gone"));
    ids.add(attrBuilder.build());

    // (4)
    ids.add(new DocIdPusher.Record.Builder(new DocId("flagson"))
        .setLock(true).setCrawlImmediately(true).setCrawlOnce(true).build());

    // (5)
    ids.add(new DocIdPusher.Record.Builder(new DocId("deleted"))
        .setDeleteFromIndex(true).build());

    String xml = lclMeker.makeMetadataAndUrlXml("t3sT", ids);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testCrawlOnceOverride() throws java.net.URISyntaxException {
    GsaFeedFileMaker lclMeker = new GsaFeedFileMaker(encoder, aclTransform,
        false, false,
        /*override crawl-immediately?*/ false,
        /*crawl-immediately value*/ false,
        /*override crawl-once?*/ true,
        /*crawl-once value*/ false,
        Collections.<String>emptyList());
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"

        // (1)
        + "<record crawl-once=\"false\""
        + " displayurl=\"http://f000nkey.net\" mimetype=\"text/plain\""
        + " url=\"http://localhost/E11\"/>\n"

        // (2)
        + "<record crawl-immediately=\"true\" crawl-once=\"false\""
        + " displayurl=\"http://yankee.doodle.com\""
        + " last-modified=\"Thu, 01 Jan 1970 00:00:00 +0000\""
        + " mimetype=\"text/plain\" url=\"http://localhost/elefenta\"/>\n"

        // (3)
        + "<record crawl-once=\"false\""
        + " displayurl=\"http://google.com/news\""
        + " last-modified=\"Fri, 02 Jan 1970 00:00:00 +0000\""
        + " mimetype=\"text/plain\" url=\"http://localhost/gone\"/>\n"

        // (4)
        + "<record crawl-immediately=\"true\" crawl-once=\"false\""
        + " lock=\"true\" mimetype=\"text/plain\""
        + " url=\"http://localhost/flagson\"/>\n"

        // (5)
        + "<record action=\"delete\" crawl-once=\"false\""
        + " mimetype=\"text/plain\""
        + " url=\"http://localhost/deleted\"/>\n"

        + "</group>\n"
        + "</gsafeed>\n";

    ArrayList<DocIdPusher.Record> ids = new ArrayList<DocIdPusher.Record>();
    DocIdPusher.Record.Builder attrBuilder
        = new DocIdPusher.Record.Builder(new DocId("E11"));

    // (1)
    attrBuilder.setResultLink(new URI("http://f000nkey.net"));
    ids.add(attrBuilder.build());

    // (2)
    attrBuilder.setResultLink(new URI("http://yankee.doodle.com"));
    attrBuilder.setLastModified(new Date(0));
    attrBuilder.setCrawlImmediately(true);
    attrBuilder.setDocId(new DocId("elefenta"));
    ids.add(attrBuilder.build());

    // (3)
    attrBuilder.setResultLink(new URI("http://google.com/news"));
    attrBuilder.setLastModified(new Date(1000 * 60 * 60 * 24));
    attrBuilder.setCrawlImmediately(false);
    attrBuilder.setCrawlOnce(false);
    attrBuilder.setDocId(new DocId("gone"));
    ids.add(attrBuilder.build());

    // (4)
    ids.add(new DocIdPusher.Record.Builder(new DocId("flagson"))
        .setLock(true).setCrawlImmediately(true).setCrawlOnce(true).build());

    // (5)
    ids.add(new DocIdPusher.Record.Builder(new DocId("deleted"))
        .setDeleteFromIndex(true).build());

    String xml = lclMeker.makeMetadataAndUrlXml("t3sT", ids);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testCustomComments() {
    List<String> customComments = new ArrayList<String>() {{
      add("We must prepare for tomorrow night.");
      add("Why? What are we going to do tomorrow night?");
      add("The same thing we do every night, Pinky "
          + " - try to take over the world!");
      add("They're Pinky, They're Pinky and the Brain Brain Brain Brain!");
    }};
    GsaFeedFileMaker lclMeker = new GsaFeedFileMaker(encoder, aclTransform,
        false, false,
        /*override crawl-immediately?*/ false,
        /*crawl-immediately value*/ false,
        /*override crawl-once?*/ true,
        /*crawl-once value*/ false,
        customComments
        );
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--We must prepare for tomorrow night.-->\n"
        + "<!--Why? What are we going to do tomorrow night?-->\n"
        + "<!--The same thing we do every night, Pinky "
            + " - try to take over the world!-->\n"
        + "<!--They're Pinky, They're Pinky and the Brain Brain Brain Brain!-->"
        + "\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group/>\n"
        + "</gsafeed>\n";

    ArrayList<DocIdPusher.Record> ids = new ArrayList<DocIdPusher.Record>();
    String xml = lclMeker.makeMetadataAndUrlXml("t3sT", ids);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }
}
