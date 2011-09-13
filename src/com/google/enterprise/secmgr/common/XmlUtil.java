// Copyright 2009 Google Inc.
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

package com.google.enterprise.secmgr.common;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSParser;
import org.w3c.dom.ls.LSSerializer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

/**
 * Utilities for manipulating XML files.
 */
public class XmlUtil {

  // DOM and XML constants.
  private static final String DOM_FEATURES_XML = "XML 3.0";
  private static final String DOM_FEATURES_LOAD_SAVE = "LS";

  private static final String DOM_CONFIG_COMMENTS = "comments";
  private static final String DOM_CONFIG_ELEMENT_CONTENT_WHITESPACE = "element-content-whitespace";
  private static final String DOM_CONFIG_CANONICAL_FORM = "canonical-form";

  private static final String XML_VERSION = "1.0";

  private final DOMImplementation domImpl;
  private final DOMImplementationLS domImplLs;

  /**
   * Construct an XmlUtil with a default DOM implementation.
   */
  public static XmlUtil make() {
    DOMImplementationRegistry registry = makeRegistry();
    return new XmlUtil(
        registry.getDOMImplementation(DOM_FEATURES_XML),
        DOMImplementationLS.class.cast(registry.getDOMImplementation(DOM_FEATURES_LOAD_SAVE)));
  }

  private static final XmlUtil INSTANCE = make();

  public static synchronized XmlUtil getInstance() {
    return INSTANCE;
  }

  private static DOMImplementationRegistry makeRegistry() {
    try {
      return DOMImplementationRegistry.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalStateException(e);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    } catch (InstantiationException e) {
      throw new IllegalStateException(e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Construct an XmlUtil with a given DOM implementation.
   *
   * @param domImpl The XML DOM implementation.
   * @param domImplLs The Load and Save DOM implementation.
   */
  public XmlUtil(DOMImplementation domImpl, DOMImplementationLS domImplLs) {
    this.domImpl = domImpl;
    this.domImplLs = domImplLs;
  }

  /**
   * Make a new XML document.
   *
   * @param nsUri The namespace URI of the document element to create or null.
   * @param localPart The qualified name of the document element to be created or null.
   * @param docType The type of document to be created or null. When doctype is
   *     not null, its Node.ownerDocument attribute is set to the document being
   *     created.
   * @return The new document.
   */
  public Document makeDocument(String nsUri, String localPart, DocumentType docType) {
    Document document = domImpl.createDocument(nsUri, localPart, docType);
    document.setXmlVersion(XML_VERSION);
    return document;
  }

  /**
   * Make a new XML document.
   *
   * @param qname The qualified name of the document element to be created.
   * @param docType The type of document to be created or null. When doctype is
   *     not null, its Node.ownerDocument attribute is set to the document being
   *     created.
   * @return The new document.
   */
  public Document makeDocument(QName qname, DocumentType docType) {
    return makeDocument(qname.getNamespaceURI(), qname.getLocalPart(), docType);
  }

  /**
   * Make a new XML document.
   *
   * @param qname The qualified name of the document element to be created.
   * @return The new document.
   */
  public Document makeDocument(QName qname) {
    return makeDocument(qname, null);
  }

  /**
   * Set standard DOM configuration.
   *
   * @param document The document to set the configuration for.
   */
  public static void setConfigParams(Document document) {
    DOMConfiguration config = document.getDomConfig();
    config.setParameter(DOM_CONFIG_COMMENTS, true);
    config.setParameter(DOM_CONFIG_ELEMENT_CONTENT_WHITESPACE, true);
    if (config.canSetParameter(DOM_CONFIG_CANONICAL_FORM, true)) {
      config.setParameter(DOM_CONFIG_CANONICAL_FORM, true);
    }
  }

  /**
   * Read an XML document from a file.
   *
   * @param input The stream to read the document from.
   * @return The XML document.
   * @throws IOException if the document can't be parsed.
   */
  public Document readXmlDocument(Reader input)
      throws IOException {
    LSInput lsInput = domImplLs.createLSInput();
    lsInput.setCharacterStream(input);
    LSParser parser = domImplLs.createLSParser(DOMImplementationLS.MODE_SYNCHRONOUS, null);
    try {
      return parser.parse(lsInput);
    } catch (LSException e) {
      throw new IOException(e);
    }
  }

  /**
   * Write an XML document to a writer.
   *
   * @param document The XML document to write.
   * @param output A writer to which the document will be written.
   * @throws IOException if the document can't be serialized.
   */
  public void writeXmlDocument(Document document, Writer output)
      throws IOException {
    writeXmlDocument(document, getLsOutput(output));
  }

  /**
   * Write an XML document to an {@link LSOutput} object.
   *
   * @param document The XML document to write.
   * @param output The output object to write the document to.
   * @throws IOException if the document can't be serialized.
   */
  public void writeXmlDocument(Document document, LSOutput output)
      throws IOException {
    writeXmlDocument(document, makeSerializer(), output);
  }

  /**
   * Write an XML document to an {@link LSOutput} object.
   *
   * @param document The XML document to write.
   * @param serializer The serializer to use.
   * @param output The output object to write the document to.
   * @throws IOException if the document can't be serialized.
   */
  public static void writeXmlDocument(Document document, LSSerializer serializer, LSOutput output)
      throws IOException {
    try {
      serializer.write(document, output);
    } catch (LSException e) {
      throw new IOException(e);
    }
  }

  public LSSerializer makeSerializer() {
    return domImplLs.createLSSerializer();
  }

  /**
   * Get an {@link LSOutput} object suitable for writing a document.
   *
   * @param output A Writer to which the output will be written.
   * @return An LSOutput object correspoding to the given writer.
   */
  public LSOutput getLsOutput(Writer output) {
    LSOutput lsOutput = domImplLs.createLSOutput();
    lsOutput.setCharacterStream(output);
    lsOutput.setEncoding("UTF-8");
    return lsOutput;
  }

  /**
   * Convert an XML document to a string.
   *
   * @param document The XML document to write.
   * @return The XML serialization as a string.
   * @throws IOException if the document can't be serialized.
   */
  public String buildXmlString(Document document)
      throws IOException {
    StringWriter output = new StringWriter();
    writeXmlDocument(document, output);
    return output.toString();
  }

  /**
   * Add a namespace declaration to the given element.
   *
   * @param element The element to add the declaration to.
   * @param prefix The namespace prefix to declare.
   * @param uri The namespace URI to associate with the given prefix.
   * @return The namespace declaration as an attribute object.
   */
  public static Attr addNamespaceDeclaration(Element element, String prefix, String uri) {
    return makeAttrChild(
        element,
        new QName(
            XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
            prefix,
            XMLConstants.XMLNS_ATTRIBUTE),
        uri);
  }

  /**
   * Get the child elements.
   *
   * @param parent The parent element to look in.
   * @return A list of the child elements.
   */
  public static List<Element> getChildElements(Element parent) {
    List<Element> elements = Lists.newArrayList();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node instanceof Element) {
        elements.add((Element) node);
      }
    }
    return elements;
  }

  /**
   * Find a child element with a given name.
   *
   * @param parent The parent element to look in.
   * @param qname The qname of the child element to look for.
   * @param required True if the method should throw an exception when no such child.
   * @return The specified child element, or null if non such (and required is false).
   * @throws IllegalArgumentException if required is true and there's no such child.
   */
  public static Element findChildElement(Element parent, QName qname, boolean required) {
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (isElementWithQname(node, qname)) {
        return (Element) node;
      }
    }
    if (required) {
      throw new IllegalArgumentException(
          "Entity doesn't contain child named " + qname.toString());
    }
    return null;
  }

  /**
   * Get the child elements with a given name.
   *
   * @param parent The parent element to look in.
   * @param qname The qname of the child elements to retrieve.
   * @return A list of the child elements with the given tag name.
   */
  public static List<Element> getChildElements(Element parent, QName qname) {
    List<Element> elements = Lists.newArrayList();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (isElementWithQname(node, qname)) {
        elements.add((Element) node);
      }
    }
    return elements;
  }

  /**
   * Count the child elements with a given name.
   *
   * @param parent The parent element to look in.
   * @param qname The qname of the child elements to look for.
   * @return The number of child elements with the given tag name.
   */
  public static int countChildElements(Element parent, QName qname) {
    int nElements = 0;
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (isElementWithQname(node, qname)) {
        nElements += 1;
      }
    }
    return nElements;
  }

  public static boolean isElementWithQname(Node node, QName qname) {
    return node instanceof Element && elementHasQname((Element) node, qname);
  }

  /**
   * Compare an element's tag name to a given name.
   *
   * @param element The element to get the tag name from.
   * @param qname The qname to compare to.
   * @return True if the element's tag name and namespace URI match those of the qname.
   */
  public static boolean elementHasQname(Element element, QName qname) {
    return qname.getLocalPart().equals(element.getLocalName())
        && qname.getNamespaceURI().equals(getNamespaceUri(element));
  }

  /**
   * Get the namespace URI of a given element.
   *
   * @param element The element to get the namespace URI of.
   * @return The namespace URI, or the null namespace URI if the element has none.
   */
  public static String getNamespaceUri(Element element) {
    String namespace = element.getNamespaceURI();
    return (namespace != null) ? namespace : XMLConstants.NULL_NS_URI;
  }

  /**
   * Get the text from an element that contains only text.
   *
   * @param element The element to get the text from.
   * @return The text contained in that element.
   * @throws IllegalArgumentException if the element isn't text-only.
   */
  public static String getElementText(Element element) {
    NodeList nodes = element.getChildNodes();
    Preconditions.checkArgument(nodes.getLength() == 1
        && nodes.item(0).getNodeType() == Node.TEXT_NODE);
    return nodes.item(0).getNodeValue();
  }

  /**
   * Get the text from a child element that contains only text.
   *
   * @param parent The parent element to look in.
   * @param qname The qname of the child element to look for.
   * @param required True if the method should throw an exception when no such child.
   * @return The text of the specified child element, or null if non such (and required is false).
   * @throws IllegalArgumentException if required is true and there's no such child.
   */
  public static String getChildElementText(Element parent, QName qname, boolean required) {
    Element child = findChildElement(parent, qname, required);
    return (child != null) ? getElementText(child) : null;
  }

  /**
   * Find all the comments in a given element that contain some given text.
   *
   * @param parent The parent element to look in.
   * @param text The comment string to search for.
   * @return A list of the comments containing that string.
   */
  public static List<Comment> findChildComments(Element parent, String text) {
    List<Comment> comments = Lists.newArrayList();
    NodeList nodes = parent.getChildNodes();
    for (int index = 0; index < nodes.getLength(); index++) {
      Node node = nodes.item(index);
      if (node instanceof Comment && node.getNodeValue().contains(text)) {
        comments.add((Comment) node);
      }
    }
    return comments;
  }

  /**
   * Get the descendant elements with a given name.
   *
   * @param parent The parent element to look in.
   * @param qname The qname of the descendant elements to look for.
   * @return A list of the descendant elements with the given tag name.
   */
  public static NodeList getElementsByQname(Element parent, QName qname) {
    return parent.getElementsByTagNameNS(qname.getNamespaceURI(), qname.getLocalPart());
  }

  /**
   * Get a given element's attribute with a given name.
   *
   * @param element The element to get the attribute from.
   * @param qname The qname of the attribute to look for.
   * @param required True if the method should throw an exception when no such attribute.
   * @return The attribute's value, or null if no such attribute (and required is false).
   * @throws IllegalArgumentException if no such attribute and required is true.
   */
  public static String findAttribute(Element element, QName qname, boolean required) {
    String ns = qname.getNamespaceURI();
    String localName = qname.getLocalPart();
    if (ns.isEmpty()) {
      ns = null;
    }
    if (element.hasAttributeNS(ns, localName)) {
      String value = element.getAttributeNS(ns, localName);
      return value;
    }
    if (required) {
      throw new IllegalArgumentException(
          "Entity doesn't contain attribute named " + qname.toString());
    }
    return null;
  }

  /**
   * Make a new DOM element child.
   *
   * @param parent The element to put the new child element in.
   * @param qname The qname of the new child element.
   * @return The new child element.
   */
  public static Element makeElementChild(Element parent, QName qname) {
    Element child = makeElement(parent, qname);
    parent.appendChild(child);
    return child;
  }

  /**
   * Make a new DOM text child.
   *
   * @param parent The element to put the new child text in.
   * @param content The text content of the new child.
   * @return The new child text.
   */
  public static Text makeTextChild(Element parent, String content) {
    Text child = makeText(parent, content);
    parent.appendChild(child);
    return child;
  }

  /**
   * Make a new DOM element child containing some text.
   *
   * @param parent The element to put the new child element in.
   * @param qname The name of the new child element.
   * @param content The text content of the new child element.
   * @return The new child element.
   */
  public static Element makeTextElementChild(Element parent, QName qname, String content) {
    Element child = makeElementChild(parent, qname);
    makeTextChild(child, content);
    return child;
  }

  /**
   * Make a new DOM comment child.
   *
   * @param parent The element to put the new child comment in.
   * @param content The text content of the new child comment.
   * @return The new child comment.
   */
  public static Comment makeCommentChild(Element parent, String content) {
    Comment child = makeComment(parent, content);
    parent.appendChild(child);
    return child;
  }

  /**
   * Make a new DOM attribute child.
   *
   * @param parent The element to put the new child attribute in.
   * @param qname The qname of the new child attribute.
   * @param value The value of the new child attribute.
   * @return The new child attribute.
   */
  public static Attr makeAttrChild(Element parent, QName qname, String value) {
    Attr child = makeAttr(parent, qname, value);
    parent.setAttributeNodeNS(child);
    return (child);
  }

  /**
   * Make a new DOM element.
   *
   * @param element Any element in the target document for the new element.
   * @param qname The qname of the new element.
   * @return The new element.
   */
  public static Element makeElement(Element element, QName qname) {
    return element.getOwnerDocument()
        .createElementNS(qname.getNamespaceURI(), qnameToString(qname));
  }

  /**
   * Make a new DOM text.
   *
   * @param element Any element in the target document for the new text.
   * @param content The content of the new text.
   * @return The new text.
   */
  public static Text makeText(Element element, String content) {
    return element.getOwnerDocument().createTextNode(content);
  }

  /**
   * Make a new DOM comment.
   *
   * @param element Any element in the target document for the new comment.
   * @param content The content of the new comment.
   * @return The new comment.
   */
  public static Comment makeComment(Element element, String content) {
    return element.getOwnerDocument().createComment(content);
  }

  /**
   * Make a new DOM attribute.
   *
   * @param element Any element in the target document for the new attribute.
   * @param qname The qname of the new attribute.
   * @param value The attribute's value.
   * @return The new attribute.
   */
  public static Attr makeAttr(Element element, QName qname, String value) {
    Attr attr = element.getOwnerDocument()
        .createAttributeNS(qname.getNamespaceURI(), qnameToString(qname));
    attr.setValue(value);
    return attr;
  }

  private static String qnameToString(QName qname) {
    String localPart = qname.getLocalPart();
    String prefix = qname.getPrefix();
    return
        (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix))
        ? localPart
        : prefix + ":" + localPart;
  }
}
