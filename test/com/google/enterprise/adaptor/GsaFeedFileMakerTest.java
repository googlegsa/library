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

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.*;

/** Tests for {@link GsaFeedFileMaker}. */
public class GsaFeedFileMakerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DocIdEncoder encoder = new MockDocIdCodec();
  private GsaFeedFileMaker meker = new GsaFeedFileMaker(encoder);

  @Test
  public void testEmpty() {
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
  public void testSimple() {
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
        + "<record displayurl=\"\" mimetype=\"text/plain\""
        + " url=\"http://localhost/E11\"/>\n"
        + "<record displayurl=\"\" mimetype=\"text/plain\""
        + " url=\"http://localhost/elefenta\"/>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    ArrayList<DocIdPusher.Record> ids = new ArrayList<DocIdPusher.Record>();
    ids.add(new DocIdPusher.Record.Builder(new DocId("E11")).build());
    ids.add(new DocIdPusher.Record.Builder(new DocId("elefenta")).build());
    String xml = meker.makeMetadataAndUrlXml("t3sT", ids);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testPushAttributes() throws java.net.URISyntaxException {
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
        + " displayurl=\"\" lock=\"true\" mimetype=\"text/plain\""
        + " url=\"http://localhost/flagson\"/>\n"
        + "<record action=\"delete\" displayurl=\"\" mimetype=\"text/plain\""
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
  public void testNamedResources() {
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
        + "</group>\n"
        + "</gsafeed>\n";

    List<DocIdSender.AclItem> acls = new ArrayList<DocIdSender.AclItem>();
    acls.add(new DocIdSender.AclItem(new DocId("docid1"), new Acl.Builder()
        .build()));
    acls.add(new DocIdSender.AclItem(new DocId("docid2"), new Acl.Builder()
        .setInheritanceType(Acl.InheritanceType.AND_BOTH_PERMIT)
        .setPermitUsers(Arrays.asList("pu1", "pu2"))
        .setDenyUsers(Arrays.asList("du1", "du2"))
        .setPermitGroups(Arrays.asList("pg1", "pg2<"))
        .setDenyGroups(Arrays.asList("dg1", "dg2&"))
        .build()));
    acls.add(new DocIdSender.AclItem(new DocId("doc id3\"&<"), new Acl.Builder()
        .setInheritFrom(new DocId("doc id4"))
        .build()));

    String xml = meker.makeMetadataAndUrlXml("test", acls);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }

  @Test
  public void testUnsupportedDocIdSenderItem() {
    class UnsupportedItem implements DocIdSender.Item {};
    List<UnsupportedItem> items = new ArrayList<UnsupportedItem>();
    items.add(new UnsupportedItem());
    thrown.expect(IllegalArgumentException.class);
    meker.makeMetadataAndUrlXml("test", items);
  }
}
