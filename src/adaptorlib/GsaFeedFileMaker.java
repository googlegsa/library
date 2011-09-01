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

package adaptorlib;

import org.w3c.dom.*;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

/** Makes XML metadata-and-url feed file from DocIds.
  This code is based on information provided by Google at
  http://code.google.com/apis/searchappliance/documentation/64/feedsguide.html
 */
class GsaFeedFileMaker {
  private DocIdEncoder idEncoder;

  public GsaFeedFileMaker(DocIdEncoder encoder) {
    this.idEncoder = encoder;
  }

  /** Adds header to document's root.
      @param srcName Used as datasource name. */
  private void constructMetadataAndUrlFeedFileHead(Document doc,
      Element root, String srcName) {
    Comment comment = doc.createComment("GSA EasyConnector");
    root.appendChild(comment);
    Element header = doc.createElement("header");
    root.appendChild(header);
    Element datasource = doc.createElement("datasource");
    header.appendChild(datasource);
    Element feedtype = doc.createElement("feedtype");
    header.appendChild(feedtype);
    Text srcText = doc.createTextNode(srcName);
    datasource.appendChild(srcText);
    Text feedText = doc.createTextNode("metadata-and-url");
    feedtype.appendChild(feedText);
  }

  /** Adds a single record to feed-file-document's group,
      communicating the information represented by DocId. */
  private void constructSingleMetadataAndUrlFeedFileRecord(
      Document doc, Element group, DocId docForGsa) {
    Element record = doc.createElement("record");
    group.appendChild(record);
    record.setAttribute("url", "" + idEncoder.encodeDocId(docForGsa));
    record.setAttribute("action", docForGsa.getFeedFileAction());
    record.setAttribute("mimetype", "text/plain"); // Required but ignored :)

    if (docForGsa instanceof DeletedDocId) {
      // No metadata allowed on deleted documents.
    } else if (docForGsa instanceof DocIdWithMetadata) {
      Element metadataXml = doc.createElement("metadata");
      record.appendChild(metadataXml);
      Metadata metadataValues = ((DocIdWithMetadata) docForGsa).getMetadata();
      addMetadataHelper(doc, metadataXml, metadataValues);
    } else {
      // Generic DocId handling.
      Element metadataXml = doc.createElement("metadata");
      record.appendChild(metadataXml);
      Element displayurl = doc.createElement("meta");
      metadataXml.appendChild(displayurl);
      displayurl.setAttribute("name", "displayurl");
      String value = "" + idEncoder.encodeDocId(docForGsa);
      displayurl.setAttribute("content", value);
    }
    // TODO(pjo): Add "no-recrawl" signal.
    // TODO(pjo): Add "crawl-immediately" signal.
    // TODO(pjo): Add "no-follow" signal.
  }

  private void addMetadataHelper(Document doc, Element metadataXml,
      Metadata metadataValues) {
    for (MetaItem item : metadataValues) {
      Element metaXml = doc.createElement("meta");
      metadataXml.appendChild(metaXml);
      metaXml.setAttribute("name", item.getName());
      metaXml.setAttribute("content", item.getValue());
    }
  }

  /** Adds all the DocIds into feed-file-document one record
    at a time. */
  private void constructMetadataAndUrlFeedFileBody(Document doc,
      Element root, List<DocId> docIds) {
    Element group = doc.createElement("group");
    root.appendChild(group);
    for (int i = 0; i < docIds.size(); i++) {
      DocId h = docIds.get(i);
      constructSingleMetadataAndUrlFeedFileRecord(doc, group, h);
    }
  }

  /** Puts all DocId into metadata-and-url GSA feed file. */
  private void constructMetadataAndUrlFeedFile(Document doc,
      String srcName, List<DocId> docIds) {
    Element root = doc.createElement("gsafeed");
    doc.appendChild(root);
    constructMetadataAndUrlFeedFileHead(doc, root, srcName);
    constructMetadataAndUrlFeedFileBody(doc, root, docIds);
  }

  /** Makes a Java String from the XML feed-file-document passed in. */
  private String documentToString(Document doc)
      throws TransformerConfigurationException, TransformerException {
    TransformerFactory transfac = TransformerFactory.newInstance();
    Transformer trans = transfac.newTransformer();
    String doctype = "-//Google//DTD GSA Feeds//EN";
    trans.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctype);
    trans.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "");
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
    trans.setOutputProperty(OutputKeys.STANDALONE, "no");
    StringWriter sw = new StringWriter();
    StreamResult result = new StreamResult(sw);
    DOMSource source = new DOMSource(doc);
    trans.transform(source, result);
    String xmlString = "" + sw;
    return xmlString;
  }

  /** Makes a metadata-and-url feed file from upto 
     provided DocIds and source name.  Is used by
     GsaCommunicationHandler.pushDocIds(). */
  public String makeMetadataAndUrlXml(String srcName, List<DocId> docIds) {
    try {
      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
      Document doc = docBuilder.newDocument();
      constructMetadataAndUrlFeedFile(doc, srcName, docIds);
      String xmlString = documentToString(doc); 
      return xmlString;
    } catch (TransformerConfigurationException tce) {
      throw new IllegalStateException(tce);
    } catch (TransformerException te) {
      throw new IllegalStateException(te);
    } catch (ParserConfigurationException pce) {
      throw new IllegalStateException(pce);
    }
  }
}
