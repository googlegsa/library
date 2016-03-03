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

package com.google.enterprise.adaptor.experimental;

import static java.util.Map.Entry;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Demonstrates what happens when "intersecting" Metadata elements are specified
 * in the Lister, Retriever, and internal Metadata of an object.  Code based
 * mostly on the AdaptorTemplate from the "examples" directory.
 */
public class MetadataExplorer extends AbstractAdaptor {
  // think of the document id as an n-digit number (in the appropriate base(s))
  // converted to decimal, and then added to the offset.
  private static final int DOC_ID_OFFSET = 1000;
  private static final Logger log
      = Logger.getLogger(MetadataExplorer.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  private static final Metadata firstListerMetadata = new Metadata();
  private static final ArrayList<Metadata> listerMetadataList =
      new ArrayList<Metadata>();
  private static int sizeOfListerMetadataList;

  private static final Metadata firstRetrieverMetadata = new Metadata();
  private static final ArrayList<Metadata> retrieverMetadataList =
      new ArrayList<Metadata>();
  private static int sizeOfRetrieverMetadataList;

  private static final Metadata firstInternalMetadata = new Metadata();
  private static final ArrayList<Metadata> internalMetadataList =
      new ArrayList<Metadata>();
  private static int sizeOfInternalMetadataList;

  private static Config config;

  @Override
  public void init(AdaptorContext context) throws Exception {
    config = context.getConfig();

    // initialize each of the "first" Metadata entries
    firstListerMetadata.add("unique-to-Lister", "first Lister-only value");
    firstListerMetadata.add("unique-to-Lister", "second Lister-only value");
    firstListerMetadata.add("shared-Lister-Retriever", "value set by Lister");
    firstListerMetadata.add("shared-Lister-Internal", "value set by Lister");
    firstListerMetadata.add("shared-global", "value set by Lister");
    firstListerMetadata.add("shared-global",
        "value set by all 3 sources of Metadata");

    firstRetrieverMetadata.add("unique-to-Retriever",
        "first Retriever-only value");
    firstRetrieverMetadata.add("unique-to-Retriever",
        "second Retriever-only value");
    firstRetrieverMetadata.add("shared-Lister-Retriever",
        "value set by Retriever");
    firstRetrieverMetadata.add("shared-Retriever-Internal",
        "value set by Retriever");
    firstRetrieverMetadata.add("shared-global", "value set by Retriever");
    firstRetrieverMetadata.add("shared-global",
        "value set by all 3 sources of Metadata");

    firstInternalMetadata.add("unique-to-Internal",
        "first Internal-only value");
    firstInternalMetadata.add("unique-to-Internal",
        "second Internal-only value");
    firstInternalMetadata.add("shared-Lister-Internal",
        "value set by Internal");
    firstInternalMetadata.add("shared-Retriever-Internal",
        "value set by Internal");
    firstInternalMetadata.add("shared-global", "value set by Internal");
    firstInternalMetadata.add("shared-global",
        "value set by all 3 sources of Metadata");

    // initialize each of the Metadata lists, and calculate their size.
    listerMetadataList.add(firstListerMetadata);
    listerMetadataList.add(new Metadata()); // empty metadata
    listerMetadataList.add(null);
    sizeOfListerMetadataList = listerMetadataList.size();

    retrieverMetadataList.add(firstRetrieverMetadata);
    retrieverMetadataList.add(new Metadata()); // empty metadata
    retrieverMetadataList.add(null);
    sizeOfRetrieverMetadataList = retrieverMetadataList.size();

    internalMetadataList.add(firstInternalMetadata);
    internalMetadataList.add(new Metadata()); // empty metadata
    internalMetadataList.add(null);
    sizeOfInternalMetadataList = internalMetadataList.size();
  }

  /**
   * Gives list of document ids that you'd like on the GSA.
   * DocIdPusher.Records are used, so that Metadata is specified.
   */
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    ArrayList<DocIdPusher.Record> records = new ArrayList<DocIdPusher.Record>();
    int count = 0;
    for (int i = 0; i < sizeOfInternalMetadataList; i++) { // internal
      for (int r = 0; r < sizeOfRetrieverMetadataList; r++) { // retriever
        for (int l = 0; l < sizeOfListerMetadataList; l++) { // lister
          int id = (i * sizeOfRetrieverMetadataList + r)
              * sizeOfListerMetadataList + l + DOC_ID_OFFSET;
          DocId docId = new DocId("" + id);
          DocIdPusher.Record record = new DocIdPusher.Record.Builder(docId)
              .setMetadata(listerMetadataList.get(l)).build();
          records.add(record);
          count++;
        }
      }
    }
    pusher.pushRecords(records);
    log.info("Pushed " + count + " records.");
  }

  /**
   * Gives the bytes of a document referenced with id.
   * Includes "Retriever Metadata" and "Interal Metadata" elements.
   */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    int nonOffset;
    // used to hold the complete list of expected metadata from up to 3 sources
    try {
      nonOffset = Integer.parseInt(id.getUniqueId()) - DOC_ID_OFFSET;
    } catch (NumberFormatException nfe) {
      throw new IOException(nfe);
    }
    if ((nonOffset < 0) || (nonOffset >= sizeOfInternalMetadataList
        * sizeOfRetrieverMetadataList * sizeOfListerMetadataList)) {
      throw new IOException("Illegal DocId: " + id);
    }
    int l = nonOffset % sizeOfListerMetadataList;
    nonOffset -= l;
    nonOffset /= sizeOfListerMetadataList;
    int r = nonOffset % sizeOfRetrieverMetadataList;
    nonOffset -= r;
    nonOffset /= sizeOfRetrieverMetadataList;
    Metadata listerM = listerMetadataList.get(l);
    Metadata retrieverM = retrieverMetadataList.get(r);
    Metadata internalM = internalMetadataList.get(nonOffset);
    String head = "<head>\n";
    int internalMetadataCount = 0;
    if (internalM != null) {
      head += "<meta charset=\"UTF-8\">\n";
      Iterator<Entry<String, String>> it = internalM.iterator();
      while (it.hasNext()) {
        Entry<String, String> e = it.next();
        head += "<meta name=\"" + e.getKey() + "\" content=\"" + e.getValue()
            + "\">\n";
        internalMetadataCount++;
      }
    }
    head += "</head>\n";

    int listerMetadataCount = 0;
    if (listerM != null) {
      Iterator<Entry<String, String>> it = listerM.iterator();
      while (it.hasNext()) {
        Entry<String, String> e = it.next();
        listerMetadataCount++;
      }
    }

    int retrieverMetadataCount = 0;
    // create Metadata entries on the response, if appropriate
    if (retrieverM != null) {
      Iterator<Entry<String, String>> it = retrieverM.iterator();
      while (it.hasNext()) {
        Entry<String, String> e = it.next();
        resp.addMetadata(e.getKey(), e.getValue());
        retrieverMetadataCount++;
      }
    }

    String body = "<body>\n";
    String endl = "\n<br>"; // newline + html code to switch to the next line
    body += "<p>This is the body of document " + id.getUniqueId() + "." + endl;
    if (listerM == null) {
      body += "It had no (null) metadata at Lister time." + endl;
    } else {
      body += "It had " + internalMetadataCount + " metadata entries at Lister "
          + "time." + endl;
    }

    if (retrieverM == null) {
      body += "It has no (null) metadata at Retriever time." + endl;
    } else {
      body += "It had " + retrieverMetadataCount + " metadata entries at "
          + "Retriever time." + endl;
    }

    if (internalM == null) {
      body += "There is no (null) internal metadata.</p>" + endl;
    } else {
      body += "It had " + internalMetadataCount + " internal metadata entries.";
      body += endl;
    }

    String link = config.getValue("gsa.hostname") + ":8000/";
    link += "EnterpriseController#actionType=contentStatus&";
    link += "collection=default_collection&uriAt=http%3A%2F%2F";
    link += config.getValue("server.hostname") + "%3A";
    link += config.getValue("server.port") + "%2Fdoc%2F";
    link += id.getUniqueId() + "&a=contentDiagnostics";
    body += "<p><a href=\"" + link + "\">Here</a>";
    body += " is a link to the GSA Metadata page for this doc.</p>";
    body += "<p>Expected Metadata ("
        + (internalMetadataCount + listerMetadataCount + retrieverMetadataCount)
        + " entries):</p>\n";
    if (internalMetadataCount + listerMetadataCount + retrieverMetadataCount
        == 0) {
      body += "None.\n";
    } else {
      body += "<table>\n";
      body += "<tr><td align=left>Metadata source</td>";
      body += "<td align=left>Metadata Name</td>";
      body += "<td align=right>Metadata Content</td></tr>\n";
      // GSA outputs sorted Retriever / Lister / internal Metadata in that order
      if (retrieverM != null) {
        Iterator<Entry<String, String>> it = retrieverM.iterator();
        while (it.hasNext()) {
          Entry<String, String> e = it.next();
          body += "<tr><td align=left>Retriever</td>";
          body += "<td align=left>" + e.getKey() + "</td>";
          body += "<td align=right>" + e.getValue() + "</td></tr>\n";
        }
      }
      if (listerM != null) {
        Iterator<Entry<String, String>> it = listerM.iterator();
        while (it.hasNext()) {
          Entry<String, String> e = it.next();
          body += "<tr><td align=left>Lister</td>";
          body += "<td align=left>" + e.getKey() + "</td>";
          body += "<td align=right>" + e.getValue() + "</td></tr>\n";
        }
      }
      if (internalM != null) {
        Iterator<Entry<String, String>> it = internalM.iterator();
        while (it.hasNext()) {
          Entry<String, String> e = it.next();
          body += "<tr><td align=left>Internal</td>";
          body += "<td align=left>" + e.getKey() + "</td>";
          body += "<td align=right>" + e.getValue() + "</td></tr>\n";
        }
      }
      body += "</table>\n";
    }

    body += "</body>\n";
    String str = head + body;

    resp.setContentType("text/html; charset=utf-8");
    OutputStream os = resp.getOutputStream();
    os.write(str.getBytes(encoding));
  }

  /** Call default main for adaptors.
   *  @param args argv
   */
  public static void main(String[] args) {
    AbstractAdaptor.main(new MetadataExplorer(), args);
  }
}
