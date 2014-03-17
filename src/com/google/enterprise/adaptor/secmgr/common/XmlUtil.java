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

package com.google.enterprise.adaptor.secmgr.common;

import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

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
}
