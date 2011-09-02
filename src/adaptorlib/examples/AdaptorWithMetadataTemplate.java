// Copyright 2011 Google Inc.
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

package adaptorlib.examples;

import adaptorlib.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Demonstrates what code is necessary for putting restricted
 * content onto a GSA.  The key operations are:
 * <ol><li> providing document ids with ACLs
 *   <li> providing document bytes given a document id</ol>
 */
public class AdaptorWithMetadataTemplate extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(AdaptorWithMetadataTemplate.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  /** Gives list of document ids that you'd like on the GSA. */
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    // Going to put our doc ids with metadata into a list.
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();

    try {
      // Make set to accumulate meta items.
      Set<MetaItem> metaItemsFor1001 = new TreeSet<MetaItem>();
      // Add user ACL.
      List<String> users1001 = Arrays.asList("peter,bart,simon");
      metaItemsFor1001.add(MetaItem.permittedUsers(users1001));
      // Add group ACL.
      List<String> groups1001 = Arrays.asList("support,sales");
      metaItemsFor1001.add(MetaItem.permittedGroups(groups1001));
      // Add custom meta items.
      metaItemsFor1001.add(MetaItem.raw("my-special-key", "my-custom-value"));
      metaItemsFor1001.add(MetaItem.raw("date", "not soon enough"));
      // Make metadata object, which checks items for consistency.
      Metadata metadataFor1001 = new Metadata(metaItemsFor1001);
      // Add our doc id with metadata to list.
      mockDocIds.add(new DocIdWithMetadata("1001", metadataFor1001));
    } catch (IllegalArgumentException e) {
      // Thrown by meta items and by metadata constructor.
      log.log(Level.SEVERE, "failed to make metadata" , e);
      // TODO: Get failed doc ids into dashboard.
    }
  
    try {
      // Another example.
      Set<MetaItem> metaItemsFor1002 = new TreeSet<MetaItem>();
      // A document that's not public and has no ACLs causes head requests.
      metaItemsFor1002.add(MetaItem.isNotPublic());
      // Set display URL.
      metaItemsFor1002.add(MetaItem.displayUrl("http://www.google.com"));
      // Add custom meta items.
      metaItemsFor1002.add(MetaItem.raw("date", "better never than late"));
      // Make metadata object, which checks items for consistency.
      Metadata metadataFor1002 = new Metadata(metaItemsFor1002);
      // Add our doc id with metadata to list.
      mockDocIds.add(new DocIdWithMetadata("1002", metadataFor1002));
    } catch (IllegalArgumentException e) {
      // Thrown by meta items and by metadata constructor.
      log.log(Level.SEVERE, "failed to make metadata" , e);
      // TODO: Get failed doc ids into dashboard.
    }

    pusher.pushDocIds(mockDocIds);
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    String str;
    if ("1001".equals(id.getUniqueId())) {
      str = "Document 1001 says hello and apple orange";
    } else if ("1002".equals(id.getUniqueId())) {
      str = "Document 1002 says hello and banana strawberry";
    } else {
      throw new FileNotFoundException(id.getUniqueId());
    }
    // Must get the OutputStream after any possibility of throwing a
    // FileNotFoundException
    OutputStream os = resp.getOutputStream();
    os.write(str.getBytes(encoding));
  }

  /** An example main for an adaptor that:<br>
   * <ol><li> enables serving doc contents,
   *   <li> sends docs ids at program start
   *   <li> and sends doc ids on schedule.</ol>
   */
  public static void main(String a[]) throws InterruptedException {
    Config config = new Config();
    config.autoConfig(a);
    Adaptor adaptor = new AdaptorWithMetadataTemplate();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content.
    try {
      gsa.beginListeningForContentRequests();
      log.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    // Push once at program start.
    gsa.pushDocIds();

    // Schedule pushing of doc ids once per day.
    gsa.beginPushingDocIds(
        new ScheduleOncePerDay(/*hour*/3, /*minute*/0, /*second*/0));
    log.info("doc id pushing has been put on schedule");
  }
}
