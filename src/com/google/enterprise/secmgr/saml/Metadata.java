// Copyright 2008 Google Inc.
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

package com.google.enterprise.secmgr.saml;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.enterprise.secmgr.common.FileUtil;
import com.google.enterprise.secmgr.common.SecurityManagerUtil;
import com.google.enterprise.secmgr.common.XmlUtil;
import com.google.enterprise.secmgr.config.ConfigSingleton;

import org.opensaml.saml2.metadata.EntitiesDescriptor;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.provider.AbstractObservableMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.saml2.metadata.provider.ObservableMetadataProvider;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.UnmarshallingException;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An abstract interface to the SAML metadata configuration.  Tracks a given
 * metadata file and keeps it up to date.  Also rewrites the metadata so it uses
 * the correct hostname.
 */
@ThreadSafe
public class Metadata {

  private static final Logger LOGGER = Logger.getLogger(Metadata.class.getName());

  @GuardedBy("itself")
  private static final Map<String, Metadata> perHostMap = Maps.newHashMap();
  private static File metadataFile = null;

  private final MetadataProvider provider;

  private Metadata(String urlPrefix)
      throws IOException {
    try {
      this.provider = new MyProvider(
          OpenSamlUtil.getMetadataFromFile(
              (metadataFile != null)
              ? metadataFile
              : FileUtil.getContextFile(ConfigSingleton.getSamlMetadataFilename())),
          urlPrefix, SecurityManagerUtil.getGsaEntConfigName());
    } catch (MetadataProviderException e) {
      throw new IOException(e);
    }
  }

  @VisibleForTesting
  static void setMetadataFile(File metadataFile) {
    Metadata.metadataFile = metadataFile;
  }

  @VisibleForTesting
  public static Metadata getInstance(String host)
      throws IOException {
    return getInstance("http", host);
  }

  public static Metadata getInstance(URL url)
      throws IOException {
    return getInstance(url.getProtocol(), url.getHost());
  }

  public static Metadata getInstance(String protocol, String host)
      throws IOException {
    String urlPrefix = protocol + "://" + host;
    Metadata result;
    synchronized (perHostMap) {
      result = perHostMap.get(urlPrefix);
      if (result == null) {
        result = new Metadata(urlPrefix);
        perHostMap.put(urlPrefix, result);
      }
    }
    return result;
  }

  public MetadataProvider getProvider() {
    return provider;
  }

  public EntitiesDescriptor getMetadata() throws IOException {
    XMLObject root;
    try {
      root = provider.getMetadata();
    } catch (MetadataProviderException e) {
      throw new IOException(e);
    }
    if (root instanceof EntitiesDescriptor) {
      return (EntitiesDescriptor) root;
    }
    throw new IOException("Malformed SAML metadata");
  }

  public EntityDescriptor getEntity(String id) throws IOException {
    EntityDescriptor entity;
    try {
      entity = provider.getEntityDescriptor(id);
    } catch (MetadataProviderException e) {
      throw new IOException(e);
    }
    if (entity == null) {
      throw new IllegalArgumentException("Unknown entity ID: " + id);
    }
    return entity;
  }

  public EntityDescriptor getSmEntity() throws IOException {
    for (EntityDescriptor e : getMetadata().getEntityDescriptors()) {
      if (MetadataEditor.SECMGR_ID_FOR_ENTITY.equals(e.getID())) {
        return e;
      }
    }
    throw new IllegalStateException("Can't find security manager's entity descriptor");
  }

  public String getSmEntityId() throws IOException {
    return getSmEntity().getEntityID();
  }

  /**
   * This class implements a wrapper around an OpenSAML
   * ObservableMetadataProvider that customizes the metadata for a particular
   * host.  When the metadata is updated, as when the configuration file is
   * changed, this provider notices that, gets the updated metadata, and
   * customizes it.  To speed things up a bit, the customized metadata is
   * cached, so it need not be customized every time.
   */
  private static class MyProvider
      extends AbstractObservableMetadataProvider
      implements ObservableMetadataProvider.Observer {

    private final ObservableMetadataProvider wrappedProvider;
    private final String urlPrefix;
    private final String gsaEntConfigName;
    private XMLObject savedMetadata;

    public MyProvider(ObservableMetadataProvider wrappedProvider,
        String urlPrefix, String gsaEntConfigName) {
      super();
      this.wrappedProvider = wrappedProvider;
      this.urlPrefix = urlPrefix;
      this.gsaEntConfigName = gsaEntConfigName;
      savedMetadata = null;
      wrappedProvider.getObservers().add(this);
    }

    public synchronized void onEvent(MetadataProvider provider) {
      LOGGER.info("Clearing cached metadata");
      savedMetadata = null;
      emitChangeEvent();
    }

    public synchronized XMLObject getMetadata() throws MetadataProviderException {
      // This will call onEvent if the file has changed:
      XMLObject rawMetadata = wrappedProvider.getMetadata();
      if (savedMetadata == null) {
        try {
          savedMetadata = OpenSamlUtil.unmarshallXmlObject(
              substituteTopLevel(
                  OpenSamlUtil.marshallXmlObject(rawMetadata)));
        } catch (MarshallingException e) {
          throw new MetadataProviderException(e);
        } catch (UnmarshallingException e) {
          throw new MetadataProviderException(e);
        }
      }
      return savedMetadata;
    }

    private Element substituteTopLevel(Element element) {
      Document doc = XmlUtil.getInstance()
          .makeDocument(element.getNamespaceURI(), element.getTagName(), null);
      Element newElement = doc.getDocumentElement();
      substituteInNodeChildren(element, newElement, doc);
      return newElement;
    }

    private void substituteInNodeChildren(Node node, Node newNode, Document doc) {
      if (node instanceof Element) {
        NamedNodeMap attrs = node.getAttributes();
        NamedNodeMap newAttrs = newNode.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
          Node attr = attrs.item(i);
          Node newAttr = doc.createAttributeNS(attr.getNamespaceURI(), attr.getNodeName());
          newAttr.setNodeValue(substituteInString(attr.getNodeValue()));
          newAttrs.setNamedItemNS(newAttr);
        }
      }
      for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
        Node newChild = substituteInNode(child, doc);
        substituteInNodeChildren(child, newChild, doc);
        newNode.appendChild(newChild);
      }
    }

    private Node substituteInNode(Node node, Document doc) {
      if (node instanceof Element) {
        return doc.createElementNS(node.getNamespaceURI(), node.getNodeName());
      } else if (node instanceof Text) {
        return doc.createTextNode(substituteInString(node.getNodeValue()));
      } else if (node instanceof CDATASection) {
        return doc.createCDATASection(node.getNodeValue());
      } else if (node instanceof Comment) {
        return doc.createComment(node.getNodeValue());
      } else if (node instanceof EntityReference) {
        return doc.createEntityReference(node.getNodeName());
      } else if (node instanceof ProcessingInstruction) {
        return doc.createProcessingInstruction(node.getNodeName(), node.getNodeValue());
      } else {
        throw new IllegalArgumentException("Unknown node type: " + node.getNodeType());
      }
    }

    private String substituteInString(String original) {
      if (original == null) { return original; }
      String pattern = "https://" + MetadataEditor.GSA_HOST_MARKER;
      if (original.startsWith(pattern)) {
        return original.replace(pattern, urlPrefix);
      }
      pattern = "http://" + MetadataEditor.GSA_HOST_MARKER;
      if (original.startsWith(pattern)) {
        return original.replace(pattern, urlPrefix);
      }
      if (original.contains(MetadataEditor.GSA_ENT_CONFIG_NAME_MARKER)) {
        return original.replace(MetadataEditor.GSA_ENT_CONFIG_NAME_MARKER, gsaEntConfigName);
      }
      return original;
    }
  }
}
