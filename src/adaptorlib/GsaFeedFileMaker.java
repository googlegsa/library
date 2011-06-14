package adaptorlib;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

/** Makes XML metadata-and-url feed file from DocIds.
  This code is based on information provided by Google at
  http://code.google.com/apis/searchappliance/documentation/64/feedsguide.html
 */
class GsaFeedFileMaker {

  /** Adds header to document's root.
      @param srcName Used as datasource name. */
  private static void constructMetadataAndUrlFeedFileHead(Document doc,
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
  private static void constructSingleMetadataAndUrlFeedFileRecord(
      Document doc, Element group, DocId docForGsa) {
    Element record = doc.createElement("record");
    group.appendChild(record);
    record.setAttribute("url", "" + docForGsa.getFeedFileUrl());
    record.setAttribute("action", docForGsa.getFeedFileAction());
    record.setAttribute("mimetype", "text/plain"); // Required but ignored :)

    boolean metadataPermitted = true; // !(docForGsa instanceof DeletedDocId);
    if (metadataPermitted) {  // No metadata with deletes.
      // Make "metadata" element.
      Element metadata = doc.createElement("metadata");
      record.appendChild(metadata);

      // Make displayurl "meta" element.
      Element displayurl = doc.createElement("meta");
      metadata.appendChild(displayurl);
      displayurl.setAttribute("name", "displayurl");
      displayurl.setAttribute("content", "" + docForGsa.getFeedFileUrl());

      // Add present permissions as "meta" elements.
      DocReadPermissions permits = docForGsa.getDocReadPermissions();
      Element isPublic = doc.createElement("meta");
      metadata.appendChild(isPublic);
      isPublic.setAttribute("name", "google:ispublic");
      isPublic.setAttribute("content", "" + permits.isPublic());
      // Add users if present.
      if (null != permits.getUsers()) {
        Element aclUsers = doc.createElement("meta");
        metadata.appendChild(aclUsers);
        aclUsers.setAttribute("name", "google:aclusers");
        String users = permits.getUsers();
        aclUsers.setAttribute("content", users);
      }
      // Add groups if present.
      if (null != permits.getGroups()) {
        Element aclGroups = doc.createElement("meta");
        metadata.appendChild(aclGroups);
        aclGroups.setAttribute("name", "google:aclgroups");
        String groups = permits.getGroups();
        aclGroups.setAttribute("content", groups);
      }
    }
    // TODO: Add "no-recrawl" signal.
    // TODO: Add "crawl-immediately" signal.
    // TODO: Add "no-follow" signal.
  }

  /** Adds all the DocIds into feed-file-document one record
    at a time. */
  private static void constructMetadataAndUrlFeedFileBody(Document doc,
      Element root, List<DocId> handles) {
    Element group = doc.createElement("group");
    root.appendChild(group);
    for (int i = 0; i < handles.size(); i++) {
      DocId h = handles.get(i);
      constructSingleMetadataAndUrlFeedFileRecord(doc, group, h);
    }
  }

  /** Puts all DocId into metadata-and-url GSA feed file. */
  private static void constructMetadataAndUrlFeedFile(Document doc,
      String srcName, List<DocId> handles) {
    Element root = doc.createElement("gsafeed");
    doc.appendChild(root);
    constructMetadataAndUrlFeedFileHead(doc, root, srcName);
    constructMetadataAndUrlFeedFileBody(doc, root, handles);
  }

  /** Makes a Java String from the XML feed-file-document passed in. */
  private static String documentToString(Document doc)
      throws TransformerConfigurationException, TransformerException {
    TransformerFactory transfac = TransformerFactory.newInstance();
    Transformer trans = transfac.newTransformer();
    String doctype = "-//Google//DTD GSA Feeds//EN";
    trans.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctype);
    trans.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "");
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
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
  static String makeMetadataAndUrlXml(String srcName, List<DocId> handles) {
    try {
      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
      Document doc = docBuilder.newDocument();
      constructMetadataAndUrlFeedFile(doc, srcName, handles);
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
