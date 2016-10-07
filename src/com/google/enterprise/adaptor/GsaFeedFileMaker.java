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
import org.w3c.dom.Text;

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
  private void constructMetadataAndUrlFeedFileHead(Document doc,
      Element root, String srcName) {
    for (String commentString : commentsForFeed) {
      Comment comment = doc.createComment(commentString);
      root.appendChild(comment);
    }
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
      Document doc, Element group, DocIdPusher.Record docRecord) {
    DocId docForGsa = docRecord.getDocId();
    Element record = doc.createElement("record");
    group.appendChild(record);
    record.setAttribute("url", "" + idEncoder.encodeDocId(docForGsa));
    // We are no longer automatically clearing the displayurl if unset. We are
    // moving the setting of displayurl to crawl-time and we don't want a lister
    // and retriever to fight.
    if (null != docRecord.getResultLink()) {
      record.setAttribute("displayurl", "" + docRecord.getResultLink());
    }
    if (docRecord.isToBeDeleted()) {
      record.setAttribute("action", "delete");
    }
    record.setAttribute("mimetype", "text/plain"); // Required but ignored :)
    if (null != docRecord.getLastModified()) {
      String dateStr = rfc822Format.get().format(docRecord.getLastModified());
      record.setAttribute("last-modified", dateStr);
    }
    if (docRecord.isToBeLocked()) {
      record.setAttribute("lock", "true");
    }
    if (crawlImmediatelyIsOverriden) {
      record.setAttribute("crawl-immediately",
          "" + crawlImmediatelyOverrideValue);
    } else if (docRecord.isToBeCrawledImmediately()) {
      record.setAttribute("crawl-immediately", "true");
    }
    if (crawlOnceIsOverriden) {
      record.setAttribute("crawl-once", "" + crawlOnceOverrideValue);
    } else if (docRecord.isToBeCrawledOnce()) {
      record.setAttribute("crawl-once", "true");
    }
    if (useAuthMethodWorkaround) {
      record.setAttribute("authmethod", "httpsso");
    }
    // TODO(pjo): record.setAttribute(no-follow,);

    Metadata metadata = docRecord.getMetadata();
    if (null != metadata) {
      Element metadataElement = doc.createElement("metadata");
      record.appendChild(metadataElement);
      for (Iterator<Map.Entry<String, String>> i = metadata.iterator();
          i.hasNext();) {
        Map.Entry<String, String> e = i.next();
        Element metadatum = doc.createElement("meta");
        metadatum.setAttribute("name", e.getKey());
        metadatum.setAttribute("content", e.getValue());
        metadataElement.appendChild(metadatum);
      }
    }

    if (separateClosingRecordTagWorkaround) {
      // GSA 6.14 has a feed parsing bug (fixed in patch 2) that fails to parse
      // self-closing record tags. Thus, here we force record to have a separate
      // close tag.
      record.appendChild(doc.createTextNode(" "));
    }
  }

  /**
   * Adds a single ACL tag to the provided group, communicating the named
   * resource's information provided in {@code docAcl}.
   */
  private void constructSingleMetadataAndUrlFeedFileAcl(
      Document doc, Element group, DocIdSender.AclItem docAcl) {
    Element aclElement = doc.createElement("acl");
    group.appendChild(aclElement);
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
    aclElement.setAttribute("url", uri.toString());
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
      aclElement.setAttribute("inherit-from", inheritFrom.toString());
    }
    if (acl.getInheritanceType() != Acl.InheritanceType.LEAF_NODE) {
      aclElement.setAttribute("inheritance-type",
          acl.getInheritanceType().getCommonForm());
    }
    boolean noCase = acl.isEverythingCaseInsensitive();
    for (UserPrincipal permitUser : acl.getPermitUsers()) {
      constructMetadataAndUrlPrincipal(doc, aclElement, "permit",
          permitUser, noCase);
    }
    for (GroupPrincipal permitGroup : acl.getPermitGroups()) {
      constructMetadataAndUrlPrincipal(doc, aclElement, "permit",
          permitGroup, noCase);
    }
    for (UserPrincipal denyUser : acl.getDenyUsers()) {
      constructMetadataAndUrlPrincipal(doc, aclElement, "deny",
          denyUser, noCase);
    }
    for (GroupPrincipal denyGroup : acl.getDenyGroups()) {
      constructMetadataAndUrlPrincipal(doc, aclElement, "deny",
          denyGroup, noCase);
    }
  }

  private void constructMetadataAndUrlPrincipal(Document doc, Element acl,
      String access, Principal principal, boolean everythingCaseInsensitive) {
    String scope = principal.isUser() ? "user" : "group";
    Element principalElement = doc.createElement("principal");
    principalElement.setAttribute("scope", scope);
    principalElement.setAttribute("access", access);
    if (!Principal.DEFAULT_NAMESPACE.equals(principal.getNamespace())) {
      principalElement.setAttribute("namespace", principal.getNamespace());
    }
    if (everythingCaseInsensitive) {
      principalElement.setAttribute(
          "case-sensitivity-type", "everything-case-insensitive");
    }
    principalElement.appendChild(doc.createTextNode(principal.getName()));
    acl.appendChild(principalElement);
  }

  /** Adds all the DocIds into feed-file-document one record
    at a time. */
  private void constructMetadataAndUrlFeedFileBody(Document doc,
      Element root, List<? extends DocIdSender.Item> items) {
    Element group = doc.createElement("group");
    root.appendChild(group);
    for (DocIdSender.Item item : items) {
      if (item instanceof DocIdPusher.Record) {
        constructSingleMetadataAndUrlFeedFileRecord(doc, group,
                                                    (DocIdPusher.Record) item);
      } else if (item instanceof DocIdSender.AclItem) {
        constructSingleMetadataAndUrlFeedFileAcl(doc, group,
                                                 (DocIdSender.AclItem) item);
      } else {
        throw new IllegalArgumentException("Unable to process class: "
                                           + item.getClass().getName());
      }
    }
  }

  /** Puts all DocId into metadata-and-url GSA feed file. */
  private void constructMetadataAndUrlFeedFile(Document doc,
      String srcName, List<? extends DocIdSender.Item> items) {
    Element root = doc.createElement("gsafeed");
    doc.appendChild(root);
    constructMetadataAndUrlFeedFileHead(doc, root, srcName);
    constructMetadataAndUrlFeedFileBody(doc, root, items);
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
      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
      Document doc = docBuilder.newDocument();
      constructMetadataAndUrlFeedFile(doc, srcName, items);
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
