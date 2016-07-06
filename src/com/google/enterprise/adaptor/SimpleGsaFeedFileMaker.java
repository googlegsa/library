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

package com.google.enterprise.adaptor;

import com.google.common.annotations.VisibleForTesting;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Creates an XML feed file destined for the GSA.  Subclasses of this class
 * create the content specific for that type of feed.
 *
 * This code is based on information provided by Google at
 * https://www.google.com/support/enterprise/static/gsa/docs/admin/74/gsa_doc_set/feedsguide/feedsguide.html
 */
public abstract class SimpleGsaFeedFileMaker {
  private static final Logger log
      = Logger.getLogger(SimpleGsaFeedFileMaker.class.getName());

  // DateFormats are relatively expensive to create, and cannot be used from
  // multiple threads (note that this class is not thread-safe).
  final DateFormat rfc822format = new SimpleDateFormat(
      "EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

  private static final List<String> COMMENTS_FOR_FEED =
      Collections.singletonList("Feed file created by send2gsa.");

  // visible to subclasses -- first, the ACL-related properties
  private Collection<String> allowedGroups;
  private Collection<String> allowedUsers;
  private boolean acl;  /* true -> acls set; false -> aclPublic */
  private boolean caseInsensitivity;
  private Collection<String> deniedGroups;
  private Collection<String> deniedUsers;
  private String namespace;

  // and then the non-ACL-related properties
  private String dataSource;
  private Date lastModified;
  private boolean lock;
  private String mimetype;
  // TODO(myk): add support for noArchive and noFollow, once a future FeederGate
  // allows them to be specified.

  // DOM objects exposed to subclasses
  private Document doc; // XML Document for entire feed
  // Guard against creating the same DOM elements a second time when calling
  // toXmlString() or constructFeedFile() multiple times.
  private boolean feedConstructed = false;
  private static String hostname = null;

  private SimpleGsaFeedFileMaker() {
    try {
      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
      doc = docBuilder.newDocument();
      doc.setXmlStandalone(true);
    } catch (ParserConfigurationException pce) {
      throw new IllegalStateException(pce);
    }

    try {
      hostname = InetAddress.getLocalHost().getCanonicalHostName();
      hostname = hostname.toLowerCase(Locale.ENGLISH);  // work around GSA 7.0
    } catch (UnknownHostException ex) {
      // Ignore
    }
    if (null == hostname) {
      hostname = "localhost";
    }
  }

  public void setPublicAcl() { // no ACL information in the feed
    this.caseInsensitivity = false;
    this.namespace = Principal.DEFAULT_NAMESPACE;
    this.allowedUsers = Collections.emptyList();
    this.allowedGroups = Collections.emptyList();
    this.deniedUsers = Collections.emptyList();
    this.deniedGroups = Collections.emptyList();
    this.acl = false;
  }

  public void setDataSource(String dataSource) {
    if (null == dataSource) {
      throw new IllegalArgumentException("dataSource must be non-null");
    }
    // TODO(myk): consider validating against GsaFeedFileMaker.DATASOURCE_FORMAT
    this.dataSource = dataSource;
  }

  public void setLastModified(Date lastModified) {
    this.lastModified = lastModified;
  }

  public void setLock(boolean lock) {
    this.lock = lock;
  }

  public void setMimetype(String mimetype) {
    if (null == mimetype) {
      // GSA Feeds Document requires that mimetype be specified; leaving it null
      // would trigger a NullPointerException when converting from DOM to String
      mimetype = "text/plain";
    }
    this.mimetype = mimetype;
  }

  public void addFile(File file) throws IOException {
    final Date lastModifiedOnEntry = this.lastModified;
    try {
      if (null == this.lastModified) {
        this.lastModified = new Date(file.lastModified());
      }
      FileInputStream fins = new FileInputStream(file); 
      addInputStream(fins, file.getCanonicalPath());
      fins.close();
    } finally {
      this.lastModified = lastModifiedOnEntry;
    }
  }

  // Subclasses must implement this method in a way that applies 
  // current settings (ACLs, binary options) to this input.
  public abstract void addInputStream(InputStream ins, String name)
      throws IOException;

  public void setAclProperties(boolean caseInsensitivity, String namespace,
      Collection<String> allowedUsers, Collection<String> allowedGroups,
      Collection<String> deniedUsers, Collection<String> deniedGroups) {
    this.caseInsensitivity = caseInsensitivity;
    this.namespace = namespace;
    this.allowedUsers = allowedUsers;
    this.allowedGroups = allowedGroups;
    this.deniedUsers = deniedUsers;
    this.deniedGroups = deniedGroups;
    this.acl = true;
    if (allowedUsers.isEmpty() && allowedGroups.isEmpty()
        && deniedUsers.isEmpty() && deniedGroups.isEmpty()) {
      // use equivalent of Acl.FAKE_EMPTY
      this.deniedUsers = Arrays.asList("google:fakeUserToPreventMissingAcl");
      log.fine("Using fakeUserToPreventMissingAcl in setAclProperties().");
    }
  }

  // methods below this are responsible for adding/appending the DOM elements

  @VisibleForTesting
  void constructAclElement(Document doc, Element parent) {
    Element aclElement = doc.createElement("acl");
    if (null != allowedUsers) {
      for (String user : allowedUsers) {
        user = user.trim();
        if (!"".equals(user)) {
          constructAclPrincipal(doc, aclElement, "permit", "user", user,
              namespace, caseInsensitivity);
        }
      }
    }
    if (null != allowedGroups) {
      for (String group : allowedGroups) {
        group = group.trim();
        if (!"".equals(group)) {
          constructAclPrincipal(doc, aclElement, "permit", "group", group,
              namespace, caseInsensitivity);
        }
      }
    }
    if (null != deniedUsers) {
      for (String user : deniedUsers) {
        user = user.trim();
        if (!"".equals(user)) {
          constructAclPrincipal(doc, aclElement, "deny", "user", user,
              namespace, caseInsensitivity);
        }
      }
    }
    if (null != deniedGroups) {
      for (String group : deniedGroups) {
        group = group.trim();
        if (!"".equals(group)) {
          constructAclPrincipal(doc, aclElement, "deny", "group", group,
              namespace, caseInsensitivity);
        }
      }
    }
    parent.appendChild(aclElement);
  }

  private void constructAclPrincipal(Document doc, Element acl,
      String access, String scope, String principal, String namespace,
      boolean caseInsensitivity) {
    Element principalElement = doc.createElement("principal");
    principalElement.setAttribute("scope", scope);
    principalElement.setAttribute("access", access);
    if (!Principal.DEFAULT_NAMESPACE.equals(namespace)) {
      principalElement.setAttribute("namespace", namespace);
    }
    if (caseInsensitivity) {
      principalElement.setAttribute(
          "case-sensitivity-type", "everything-case-insensitive");
    }
    principalElement.appendChild(doc.createTextNode(principal));
    acl.appendChild(principalElement);
  }

  /** Makes a Java String from the XML feed-file-document passed in. */
  private String documentToString(Document doc)
      throws TransformerConfigurationException, TransformerException {
    TransformerFactory transfac = TransformerFactory.newInstance();
    Transformer trans = transfac.newTransformer();
    String doctype = "-//Google//DTD GSA Feeds//EN";
    trans.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctype);
    trans.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "gsafeed.dtd");
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
    StringWriter sw = new StringWriter();
    StreamResult result = new StreamResult(sw);
    DOMSource source = new DOMSource(doc);
    trans.transform(source, result);
    String xmlString = "" + sw;
    return xmlString;
  }

  public String toXmlString() {
    try {
      constructFeedFile(doc);
      String xmlString = documentToString(doc);
      return xmlString;
    } catch (TransformerConfigurationException tce) {
      throw new IllegalStateException(tce);
    } catch (TransformerException te) {
      throw new IllegalStateException(te);
    }
  }

  /**
   * Generates a URL-like identifier for a filename
   */
  private static String urlForFilename(String filename) {
    while (filename.startsWith("./")) {
      filename = filename.substring(2);
    }
    if (filename.startsWith("/")) {
      return "googleconnector://" + hostname + filename;
    } else {
      return "googleconnector://" + hostname + "/" + filename;
    }
  }

  // subclasses must implement the following two methods.
  abstract void constructFeedFileHead(Document doc, Element root);
  abstract void constructFeedFileBody(Document doc, Element root);

  private void constructFeedFile(Document doc) {
    Element root;
    if (feedConstructed) {
      root = (Element) doc.getElementsByTagName("gsafeed").item(0);
    } else {
      root = doc.createElement("gsafeed");
      doc.appendChild(root);
    }
    constructFeedFileHead(doc, root);
    constructFeedFileBody(doc, root);
    feedConstructed = true;
  }

  /**
   * A version of SimpleGsaFeedFileMaker that creates Content feeds (both full
   * and incremental).  Each file we put generates one record in the feed,
   * including the file's content.
   */
  public static class Content extends SimpleGsaFeedFileMaker {
    private Collection<Element> savedRecords = new ArrayList<Element>();
    // <record> elements are created and stored as items are added

    private String feedType;

    public Content(String feedType) {
      setFeedType(feedType);
    }

    private void setFeedType(String feedType) {
      if ("incremental".equals(feedType) || "full".equals(feedType)) {
        this.feedType = feedType;
      } else {
        throw new IllegalArgumentException(feedType);
      }
    }

    /** Adds header to document's root. */
    void constructFeedFileHead(Document doc, Element root) {
      if (super.feedConstructed) {
        return;  // use same "header" from first feed construction
      }
      for (String commentString : COMMENTS_FOR_FEED) {
        Comment comment = doc.createComment(commentString);
        root.appendChild(comment);
      }
      Element header = doc.createElement("header");
      root.appendChild(header);
      Element datasource = doc.createElement("datasource");
      header.appendChild(datasource);
      Text srcText = doc.createTextNode(super.dataSource);
      datasource.appendChild(srcText);
      Element feedtype = doc.createElement("feedtype");
      header.appendChild(feedtype);
      Text feedText = doc.createTextNode(feedType);
      feedtype.appendChild(feedText);
    }

    @Override
    public void addInputStream(InputStream ins, String name)
        throws IOException {
      savedRecords.add(
          constructContentSingleFeedFileRecord(super.doc, ins, name));
    }

    /**
     * Adds all the files into feed-file-document, one record (file) at a time.
     */
    void constructFeedFileBody(Document doc, Element root) {
      Element group;
      if (super.feedConstructed) {
        group = (Element) root.getElementsByTagName("group").item(0);
      } else {
        group = doc.createElement("group");
        root.appendChild(group);
      }
      Iterator<Element> recordsIterator = savedRecords.iterator();
      while (recordsIterator.hasNext()) {
        Node newrec = recordsIterator.next().cloneNode(true);
        group.appendChild(newrec);
        recordsIterator.remove();
      }
    }

    /**
     * Creates a record from given input
     */
    private Element constructContentSingleFeedFileRecord(Document doc,
        InputStream ins, String name) throws IOException {
      byte[] fileContent = IOHelper.readInputStreamToByteArray(ins);
      Element record = doc.createElement("record");
      // records get added to the "group" element each time that toXmlString()
      // is called -- not when this method is called.
      record.setAttribute("url", urlForFilename(name));
      record.setAttribute("mimetype", super.mimetype);
      if (null != super.lastModified) {
        String dateStr = rfc822format.format(super.lastModified);
        record.setAttribute("last-modified", dateStr);
      }
      if (super.lock) {
        record.setAttribute("lock", "true");
      }
      if (super.acl) {
        constructAclElement(super.doc, record);
      }

      Element content = super.doc.createElement("content");
      record.appendChild(content);
      // since files containing binary data break the CDATA approach, we just
      // resort to base64-encoding all file contents
      if (null == fileContent || fileContent.length == 0) {
        content.appendChild(super.doc.createTextNode("empty"));
      } else {
        content.setAttribute("encoding", "base64binary");
        content.appendChild(doc.createTextNode(
            DatatypeConverter.printBase64Binary(fileContent)));
      }
      return record;
    }
  }

  /**
   * A version of SimpleGsaFeedFileMaker that creates Web feeds.  More than
   * one URL (and record in the feed) may come from each file we are pushing.
   */
  public static class MetadataAndUrl extends SimpleGsaFeedFileMaker {
    private Collection<Element> savedRecords = new ArrayList<Element>();
    // <record> elements are created and stored as content is added

    private String feedType = "incremental";
    private boolean crawlImmediately;
    private boolean crawlOnce;

    public MetadataAndUrl() {
    }

    public void setCrawlImmediately(boolean crawlImmediately) {
      this.crawlImmediately = crawlImmediately;
    }

    public void setCrawlOnce(boolean crawlOnce) {
      this.crawlOnce = crawlOnce;
    }

    void constructFeedFileHead(Document doc, Element root) {
      if (super.feedConstructed) {
        return;  // use same "header" from first feed construction
      }
      for (String commentString : COMMENTS_FOR_FEED) {
        Comment comment = doc.createComment(commentString);
        root.appendChild(comment);
      }
      Element header = doc.createElement("header");
      root.appendChild(header);
      Element datasource = doc.createElement("datasource");
      header.appendChild(datasource);
      Text srcText = doc.createTextNode("web");
      datasource.appendChild(srcText);
      Element feedtype = doc.createElement("feedtype");
      header.appendChild(feedtype);
      Text feedText = doc.createTextNode("metadata-and-url");
      feedtype.appendChild(feedText);
    }

    @Override
    public void addInputStream(InputStream ins, String name)
        throws IOException {
      savedRecords.addAll(
          constructWebFeedFileBodyForFile(super.doc, ins, name));
    }

    /** Adds all the DocIds into feed-file-document one record at a time. */
    void constructFeedFileBody(Document doc, Element root) {
      Element group;
      if (super.feedConstructed) {
        group = (Element) root.getElementsByTagName("group").item(0);
      } else {
        group = doc.createElement("group");
        root.appendChild(group);
      }
      Iterator<Element> recordsIterator = savedRecords.iterator();
      while (recordsIterator.hasNext()) {
        Node newrec = recordsIterator.next().cloneNode(true);
        group.appendChild(newrec);
        recordsIterator.remove();
      }
    }

    /** Creates all the Records from the URLs found in the given input. */
    private Collection<Element> constructWebFeedFileBodyForFile(Document doc,
        InputStream ins, String name) throws IOException {
      Collection<Element> records = new ArrayList<Element>();
      long urlsFound = 0;
      BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
      String line;
      long lineCounter = 0;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        lineCounter++;
        try {
          URL valid = new URL(line);
          urlsFound++;
          records.add(constructWebSingleFeedFileRecord(doc, line));
        } catch (java.net.MalformedURLException e) {
          // warn about lines that aren't valid URLs.
          if (!"".equals(line) && !line.startsWith("#")) {
            log.warning("Ignoring line " + lineCounter + " of URL file "
                + name + " - " + line + " is not a URL");
          }
        }
      }
      log.fine("Found " + urlsFound + " URL(s) in file " + name);
      return records;
    }

    /**
     * Creates a record for a URL
     */
    private Element constructWebSingleFeedFileRecord(Document doc,
        String url) {
      Element record = doc.createElement("record");
      record.setAttribute("url", url);
      record.setAttribute("mimetype", super.mimetype);
      if (crawlImmediately) {
        record.setAttribute("crawl-immediately", "true");
      }
      if (crawlOnce) {
        record.setAttribute("crawl-once", "true");
      }
      if (super.lock) {
        record.setAttribute("lock", "true");
      }
      if (super.acl) {
        constructAclElement(doc, record);
      }
      return record;
    }
  }
}
