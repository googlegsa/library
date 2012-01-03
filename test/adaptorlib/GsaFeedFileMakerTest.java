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

package adaptorlib;

import static org.junit.Assert.*;

import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;

/** Tests for {@link GsaFeedFileMaker}. */
public class GsaFeedFileMakerTest {
  private static final DocIdEncoder ENCODER = new DocIdEncoder() {
    public URI encodeDocId(DocId docId) {
      try {
        return new URI(docId.getUniqueId());
      } catch (java.net.URISyntaxException e) {
        throw new IllegalStateException("while testing", e);
      }
    }
  };

  private GsaFeedFileMaker meker = new GsaFeedFileMaker(ENCODER);

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
        + "<record action=\"add\" crawl-immediately=\"false\""
        + " crawl-once=\"false\""
        + " lock=\"false\" mimetype=\"text/plain\" url=\"E11\"/>\n"
        + "<record action=\"add\" crawl-immediately=\"false\""
        + " crawl-once=\"false\""
        + " lock=\"false\" mimetype=\"text/plain\" url=\"elefenta\"/>\n"
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
        + "<record action=\"add\" crawl-immediately=\"false\""
        + " crawl-once=\"false\" displayurl=\"http://f000nkey.net\""
        + " lock=\"false\" mimetype=\"text/plain\" url=\"E11\"/>\n"
        + "<record action=\"add\" crawl-immediately=\"false\""
        + " crawl-once=\"false\" displayurl=\"http://yankee.doodle.com\""
        + " last-modified=\"Thu, 01 Jan 1970 00:00:00 +0000\""
        + " lock=\"false\" mimetype=\"text/plain\" url=\"elefenta\"/>\n"
        + "<record action=\"add\" crawl-immediately=\"false\""
        + " crawl-once=\"false\" displayurl=\"http://google.com/news\"" 
        + " last-modified=\"Fri, 02 Jan 1970 00:00:00 +0000\""
        + " lock=\"false\" mimetype=\"text/plain\" url=\"gone\"/>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    ArrayList<DocIdPusher.Record> ids = new ArrayList<DocIdPusher.Record>();
    DocIdPusher.Record.Builder attrBuilder = new DocIdPusher.Record.Builder(new DocId("E11"));

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

    String xml = meker.makeMetadataAndUrlXml("t3sT", ids);
    xml = xml.replaceAll("\r\n", "\n");
    assertEquals(golden, xml);
  }
}
