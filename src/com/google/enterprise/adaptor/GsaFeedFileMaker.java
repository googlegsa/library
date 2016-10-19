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

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.google.enterprise.adaptor.ext.feedtype.Group;
import com.google.enterprise.adaptor.ext.feedtype.Gsafeed;
import com.google.enterprise.adaptor.ext.feedtype.Header;
import com.google.enterprise.adaptor.ext.feedtype.Meta;
import com.google.enterprise.adaptor.ext.feedtype.Record;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeSet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
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


/** Makes XML metadata-and-url feed file from DocIds.
  This code is based on information provided by Google at
  http://code.google.com/apis/searchappliance/documentation/64/feedsguide.html
 */
class GsaFeedFileMaker {
  // DateFormats are relatively expensive to create, and cannot be used from
  // multiple threads
  private static ThreadLocal<DateFormat> rfc822Format
      = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
          DateFormat df = new SimpleDateFormat(
              "EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
          df.setTimeZone(TimeZone.getTimeZone("GMT"));
          return df;
        }
      };

  private final DocIdEncoder idEncoder;
  private final AclTransform aclTransform;
  private final boolean separateClosingRecordTagWorkaround;
  private final boolean useAuthMethodWorkaround;
  private final boolean crawlImmediatelyIsOverriden;
  private final boolean crawlImmediatelyOverrideValue;
  private final boolean crawlOnceIsOverriden;
  private final boolean crawlOnceOverrideValue;
  private final List<String> commentsForFeed;

  public GsaFeedFileMaker(DocIdEncoder encoder, AclTransform aclTransform) {
    this(encoder, aclTransform, false, false);
  }

  public GsaFeedFileMaker(DocIdEncoder encoder, AclTransform aclTransform,
      boolean separateClosingRecordTagWorkaround,
      boolean useAuthMethodWorkaround) {
    this(encoder, aclTransform, separateClosingRecordTagWorkaround,
        useAuthMethodWorkaround, false, false, false, false,
        Collections.<String>emptyList());
  }

  public GsaFeedFileMaker(DocIdEncoder encoder, AclTransform aclTransform,
      boolean separateClosingRecordTagWorkaround,
      boolean useAuthMethodWorkaround,
      boolean overrideCrawlImmediately,
      boolean crawlImmediately,
      boolean overrideCrawlOnce,
      boolean crawlOnce,
      List<String> commentsToIncludeInFeedFile) {
    this.idEncoder = encoder;
    this.aclTransform = aclTransform;
    this.separateClosingRecordTagWorkaround
        = separateClosingRecordTagWorkaround;
    this.useAuthMethodWorkaround = useAuthMethodWorkaround;
    this.crawlImmediatelyIsOverriden = overrideCrawlImmediately;
    this.crawlImmediatelyOverrideValue = crawlImmediately;
    this.crawlOnceIsOverriden = overrideCrawlOnce;
    this.crawlOnceOverrideValue = crawlOnce;
    if (commentsToIncludeInFeedFile.isEmpty()) {
      // Including one comment to avoid self-closing XML.
      this.commentsForFeed = Collections.singletonList(
          "GSA EasyConnector");
    } else {
      this.commentsForFeed = Collections.unmodifiableList(
          new ArrayList<String>(commentsToIncludeInFeedFile));
    }
  }

  /** Adds header to document's root.
      @param srcName Used as datasource name. */
  private void constructMetadataAndUrlFeedFileHead(Gsafeed feed, String srcName,
      StringBuilder comments) {
    for (String commentString : commentsForFeed) {
      comments.append("<!--").append(commentString).append("-->\n");
    }
    Header header = new Header(); 
    feed.setHeader(header);
    header.setDatasource(srcName);
    header.setFeedtype("metadata-and-url");
  }

  /** Adds a single record to feed-file-document's group,
      communicating the information represented by DocId. */
  private void constructSingleMetadataAndUrlFeedFileRecord(Group group,
      DocIdPusher.Record docRecord) {
    DocId docForGsa = docRecord.getDocId();
    Record record = new Record();
    group.getAclOrRecord().add(record);
    record.setUrl("" + idEncoder.encodeDocId(docForGsa));
    // We are no longer automatically clearing the displayurl if unset. We are
    // moving the setting of displayurl to crawl-time and we don't want a lister
    // and retriever to fight.
    if (null != docRecord.getResultLink()) {
      record.setDisplayurl("" + docRecord.getResultLink());
    }
    if (docRecord.isToBeDeleted()) {
      record.setAction("delete");
    }
    record.setMimetype("text/plain"); // Required but ignored :)
    if (null != docRecord.getLastModified()) {
      String dateStr = rfc822Format.get().format(docRecord.getLastModified());
      record.setLastModified(dateStr);
    }
    if (docRecord.isToBeLocked()) {
      record.setLock("true");
    }
    if (crawlImmediatelyIsOverriden) {
      record.setCrawlImmediately("" + crawlImmediatelyOverrideValue);
    } else if (docRecord.isToBeCrawledImmediately()) {
      record.setCrawlImmediately("true");
    }
    if (crawlOnceIsOverriden) {
      record.setCrawlOnce("" + crawlOnceOverrideValue);
    } else if (docRecord.isToBeCrawledOnce()) {
      record.setCrawlOnce("true");
    }
    if (useAuthMethodWorkaround) {
      record.setAuthmethod("httpsso");
    }
    // TODO(pjo): record.setAttribute(no-follow,);

    Metadata metadata = docRecord.getMetadata();
    if (null != metadata) {
      com.google.enterprise.adaptor.ext.feedtype.Metadata metadataElement = 
          new com.google.enterprise.adaptor.ext.feedtype.Metadata();
      record.getMetadata().add(metadataElement);
      for (Iterator<Map.Entry<String, String>> i = metadata.iterator();
          i.hasNext();) {
        Map.Entry<String, String> e = i.next();
        Meta metadatum = new Meta();
        metadatum.setName(e.getKey());
        metadatum.setContent(e.getValue());
        metadataElement.getMeta().add(metadatum);
      }
    }
  }

  /**
   * Adds a single ACL tag to the provided group, communicating the named
   * resource's information provided in {@code docAcl}.
   */
  private void constructSingleMetadataAndUrlFeedFileAcl(Group group, DocIdSender.AclItem docAcl) {
    com.google.enterprise.adaptor.ext.feedtype.Acl aclElement = 
        new com.google.enterprise.adaptor.ext.feedtype.Acl();
    group.getAclOrRecord().add(aclElement);
    URI uri = idEncoder.encodeDocId(docAcl.getDocId());
    try {
      // Although it is named "fragment", we put the docIdFragment in the query
      // portion of the URI because the GSA removes fragments when it
      // "normalizes" the identifier.
      uri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
          docAcl.getDocIdFragment(), null);
    } catch (URISyntaxException ex) {
      throw new AssertionError(ex);
    }
    aclElement.setUrl(uri.toString());
    Acl acl = docAcl.getAcl();
    acl = aclTransform.transform(acl);
    if (acl.getInheritFrom() != null) {
      URI inheritFrom = idEncoder.encodeDocId(acl.getInheritFrom());
      try {
        // Although it is named "fragment", we use a query parameter because the
        // GSA "normalizes" away fragments.
        inheritFrom = new URI(inheritFrom.getScheme(),
            inheritFrom.getAuthority(), inheritFrom.getPath(),
            acl.getInheritFromFragment(), null);
      } catch (URISyntaxException ex) {
        throw new AssertionError(ex);
      }
      aclElement.setInheritFrom(inheritFrom.toString());
    }
    if (acl.getInheritanceType() != Acl.InheritanceType.LEAF_NODE) {
      aclElement.setInheritanceType(acl.getInheritanceType().getCommonForm());
    }
    boolean noCase = acl.isEverythingCaseInsensitive();
    for (UserPrincipal permitUser : acl.getPermitUsers()) {
      constructMetadataAndUrlPrincipal(aclElement, "permit",
          permitUser, noCase);
    }
    for (GroupPrincipal permitGroup : acl.getPermitGroups()) {
      constructMetadataAndUrlPrincipal(aclElement, "permit",
          permitGroup, noCase);
    }
    for (UserPrincipal denyUser : acl.getDenyUsers()) {
      constructMetadataAndUrlPrincipal(aclElement, "deny",
          denyUser, noCase);
    }
    for (GroupPrincipal denyGroup : acl.getDenyGroups()) {
      constructMetadataAndUrlPrincipal(aclElement, "deny",
          denyGroup, noCase);
    }
  }

  private void constructMetadataAndUrlPrincipal(com.google.enterprise.adaptor.ext.feedtype.Acl acl,
      String access, Principal principal, boolean everythingCaseInsensitive) {
    String scope = principal.isUser() ? "user" : "group";
    com.google.enterprise.adaptor.ext.feedtype.Principal principalElement = 
        new com.google.enterprise.adaptor.ext.feedtype.Principal();
    principalElement.setScope(scope);
    principalElement.setAccess(access);
    if (!Principal.DEFAULT_NAMESPACE.equals(principal.getNamespace())) {
      principalElement.setNamespace(principal.getNamespace());
    }
    if (everythingCaseInsensitive) {
      principalElement.setCaseSensitivityType("everything-case-insensitive");
    }
    principalElement.setvalue(principal.getName());
    acl.getPrincipal().add(principalElement);
  }

  /** Adds all the DocIds into feed-file-document one record
    at a time. */
  private void constructMetadataAndUrlFeedFileBody(Gsafeed feed,
      List<? extends DocIdSender.Item> items) {
    Group group = new Group();
    feed.getGroup().add(group);
    for (DocIdSender.Item item : items) {
      if (item instanceof DocIdPusher.Record) {
        constructSingleMetadataAndUrlFeedFileRecord(group, (DocIdPusher.Record) item);
      } else if (item instanceof DocIdSender.AclItem) {
        constructSingleMetadataAndUrlFeedFileAcl(group, (DocIdSender.AclItem) item);
      } else {
        throw new IllegalArgumentException("Unable to process class: "
                                           + item.getClass().getName());
      }
    }
  }

  /** Puts all DocId into metadata-and-url GSA feed file. */
  private void constructMetadataAndUrlFeedFile(Gsafeed feed, String srcName,
      List<? extends DocIdSender.Item> items, StringBuilder comments) {
    constructMetadataAndUrlFeedFileHead(feed, srcName, comments);
    constructMetadataAndUrlFeedFileBody(feed, items);
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
  public String makeMetadataAndUrlXml(String srcName,
      List<? extends DocIdSender.Item> items) {
    try {
      Gsafeed feed = new Gsafeed();
      StringBuilder comments = new StringBuilder();
      constructMetadataAndUrlFeedFile(feed, srcName, items, comments);
      JAXBContext jaxbContext = JAXBContext.newInstance(Gsafeed.class);
      Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
      StringBuilder xmlHeaders = new StringBuilder();
      xmlHeaders.append("<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"http://gsa/gsafeed.dtd\">\n");
      xmlHeaders.append(comments.toString());
      try {
        jaxbMarshaller.setProperty("com.sun.xml.bind.xmlHeaders", xmlHeaders.toString());
      } catch (PropertyException ex) {
        // JDK 1.6
        jaxbMarshaller.setProperty("com.sun.xml.internal.bind.xmlHeaders", xmlHeaders.toString());
      }
      StringWriter sw = new StringWriter();
      jaxbMarshaller.marshal(feed, sw);
      return sw.toString();
    } catch (JAXBException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Creates single group definition of group principal key and members. */
  private void constructSingleMembership(Document doc, Element root,
      GroupPrincipal groupPrincipal, Collection<Principal> members,
      boolean caseSensitiveMembers) {
    groupPrincipal = aclTransform.transform(groupPrincipal);
    members = new TreeSet<Principal>(aclTransform.transform(members));
    Element groupWithDef = doc.createElement("membership");
    root.appendChild(groupWithDef);
    Element groupKey = doc.createElement("principal");
    groupWithDef.appendChild(groupKey);
    groupKey.setAttribute("namespace", groupPrincipal.getNamespace());
    groupKey.setAttribute("scope", "GROUP");
    groupKey.appendChild(doc.createTextNode(groupPrincipal.getName()));
    Element groupDef = doc.createElement("members");
    groupWithDef.appendChild(groupDef);
    for (Principal member : members) {
      Element groupDefElement = doc.createElement("principal");
      groupDefElement.setAttribute("namespace", member.getNamespace());
      String scope = member.isUser() ? "USER" : "GROUP";
      groupDefElement.setAttribute("scope", scope);
      if (caseSensitiveMembers) {
        groupDefElement.setAttribute(
            "case-sensitivity-type", "EVERYTHING_CASE_SENSITIVE");
      } else {
        groupDefElement.setAttribute(
            "case-sensitivity-type", "EVERYTHING_CASE_INSENSITIVE");
      }
      groupDefElement.appendChild(doc.createTextNode(member.getName()));
      groupDef.appendChild(groupDefElement);
    }
  }

  /** Adds all the groups' definitions into body. */
  private <T extends Collection<Principal>> void
      constructGroupDefinitionsFileBody(Document doc, Element root,
      Collection<Map.Entry<GroupPrincipal, T>> items,
      boolean caseSensitiveMembers) {
    for (Map.Entry<GroupPrincipal, T> group : items) {
      constructSingleMembership(doc, root, group.getKey(), group.getValue(),
          caseSensitiveMembers);
    }
  }

  /** Puts all groups' definitions into document. */
  private <T extends Collection<Principal>> void
      constructGroupDefinitionsFeedFile(Document doc,
      Collection<Map.Entry<GroupPrincipal, T>> items,
      boolean caseSensitiveMembers) {
    Element root = doc.createElement("xmlgroups");
    doc.appendChild(root);
    for (String commentString : commentsForFeed) {
      Comment comment = doc.createComment(commentString);
      root.appendChild(comment);
    }
    constructGroupDefinitionsFileBody(doc, root, items, caseSensitiveMembers);
  }

  // This and all the methods it calls with things from 'items' requires the
  // parameter T even though ? would normally suffice. See comment in
  // DocIdSender to learn about the Java limitation causing the need for T.
  /** Makes feed file with groups and their definitions. */
  public <T extends Collection<Principal>> String makeGroupDefinitionsXml(
      Collection<Map.Entry<GroupPrincipal, T>> items,
      boolean caseSensitiveMembers) {
    try {
      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
      Document doc = docBuilder.newDocument();
      constructGroupDefinitionsFeedFile(doc, items, caseSensitiveMembers);
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
