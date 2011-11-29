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
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

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
    String xml = meker.makeMetadataAndUrlXml("t3sT", new ArrayList());
    assertEquals(golden, xml);
  }

/*
  TODO: Replace tests after PushAttributes is in place.

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
        + "<record action=\"add\" mimetype=\"text/plain\" url=\"E11\">\n"
        + "<metadata>\n"
        + "<meta content=\"true\" name=\"google:ispublic\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "<record action=\"add\" mimetype=\"text/plain\" url=\"elefenta\">\n"
        + "<metadata>\n"
        + "<meta content=\"true\" name=\"google:ispublic\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    ArrayList<DocIdPusher.DocInfo> ids = new ArrayList<DocIdPusher.DocInfo>();
    ids.add(new DocIdPusher.DocInfo(new DocId("E11"),
        PushAttributes.DEFAULT));
    ids.add(new DocIdPusher.DocInfo(new DocId("elefenta"), 
        PushAttributes.DEFAULT));
    String xml = meker.makeMetadataAndUrlXml("t3sT", ids);
    assertEquals(golden, xml);
  }

  @Test
  public void testMetadata() {
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
        + "<record action=\"add\" mimetype=\"text/plain\" url=\"E11\">\n"
        + "<metadata>\n"
        + "<meta content=\"true\" name=\"google:ispublic\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "<record action=\"add\" mimetype=\"text/plain\" url=\"elefenta\">\n"
        + "<metadata>\n"
        + "<meta content=\"f000nkey\" name=\"displayurl\"/>\n"
        + "<meta content=\"in rods\" name=\"distance\"/>\n"
        + "<meta content=\"Po\" name=\"google:aclgroups\"/>\n"
        + "<meta content=\"Ty\" name=\"google:aclusers\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "<record action=\"delete\" mimetype=\"text/plain\" url=\"gone\"/>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    ArrayList<DocIdPusher.DocInfo> ids = new ArrayList<DocIdPusher.DocInfo>();
    Set<MetaItem> item1 = Collections.singleton(MetaItem.isPublic());
    Metadata metadata1 = new Metadata(item1);
    ids.add(new DocIdPusher.DocInfo(new DocId("E11"), PushAttributes.DEFAULT));
    Set<MetaItem> items2 = new TreeSet<MetaItem>();
    items2.add(MetaItem.displayUrl("f000nkey"));
    items2.add(MetaItem.raw("distance", "in rods"));
    items2.add(MetaItem.permittedGroups(Collections.singletonList("Po")));
    items2.add(MetaItem.permittedUsers(Collections.singletonList("Ty")));
    Metadata metadata2 = new Metadata(items2);
    ids.add(new DocIdPusher.DocInfo(new DocId("elefenta"), metadata2));
    ids.add(new DocIdPusher.DocInfo(new DocId("gone"), PushAttributes.DEFAULT));
    String xml = meker.makeMetadataAndUrlXml("t3sT", ids);
    assertEquals(golden, xml);
  }
*/
}
