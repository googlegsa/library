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

package com.google.enterprise.secmgr.saml;

import static com.google.enterprise.secmgr.common.XmlUtil.countChildElements;
import static com.google.enterprise.secmgr.common.XmlUtil.findChildComments;
import static com.google.enterprise.secmgr.common.XmlUtil.findChildElement;
import static com.google.enterprise.secmgr.common.XmlUtil.getElementsByQname;
import static com.google.enterprise.secmgr.common.XmlUtil.makeCommentChild;
import static com.google.enterprise.secmgr.common.XmlUtil.makeElement;
import static com.google.enterprise.secmgr.common.XmlUtil.makeElementChild;
import static com.google.enterprise.secmgr.common.XmlUtil.makeTextChild;
import static com.google.enterprise.secmgr.common.XmlUtil.makeTextElementChild;
import static com.google.enterprise.secmgr.common.XmlUtil.setConfigParams;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.enterprise.secmgr.common.XmlUtil;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.namespace.QName;

/**
 * Utilities for editing SAML metadata files.
 */
@Immutable
public class MetadataEditor {
  private static final Logger LOGGER = Logger.getLogger(MetadataEditor.class.getName());

  // SAML metadata elements.
  public static final String SAML_MD_NS = "urn:oasis:names:tc:SAML:2.0:metadata";

  private static QName mdName(String localPart) {
    return new QName(SAML_MD_NS, localPart);
  }

  public static final QName SAML_DESCRIPTOR_ENTITIES = mdName("EntitiesDescriptor");
  public static final QName SAML_DESCRIPTOR_ENTITY = mdName("EntityDescriptor");
  public static final QName SAML_DESCRIPTOR_IDP_SSO = mdName("IDPSSODescriptor");
  public static final QName SAML_DESCRIPTOR_KEY = mdName("KeyDescriptor");
  public static final QName SAML_DESCRIPTOR_PDP = mdName("PDPDescriptor");
  public static final QName SAML_DESCRIPTOR_SP_SSO = mdName("SPSSODescriptor");
  public static final QName SAML_ORGANIZATION = mdName("Organization");
  public static final QName SAML_ORGANIZATION_DISPLAY_NAME = mdName("OrganizationDisplayName");
  public static final QName SAML_ORGANIZATION_NAME = mdName("OrganizationName");
  public static final QName SAML_ORGANIZATION_URL = mdName("OrganizationURL");
  public static final QName SAML_SERVICE_ARTIFACT_RESOLUTION = mdName("ArtifactResolutionService");
  public static final QName SAML_SERVICE_ASSERTION_CONSUMER = mdName("AssertionConsumerService");
  public static final QName SAML_SERVICE_AUTHZ = mdName("AuthzService");
  public static final QName SAML_SERVICE_SINGLE_SIGN_ON = mdName("SingleSignOnService");

  // SAML metadata attributes.
  public static final String SAML_ATTR_BINDING = "Binding";
  public static final String SAML_ATTR_CACHE_DURATION = "cacheDuration";
  public static final String SAML_ATTR_ENTITY_ID = "entityID";
  public static final String SAML_ATTR_INDEX = "index";
  public static final String SAML_ATTR_IS_DEFAULT = "isDefault";
  public static final String SAML_ATTR_LOCATION = "Location";
  public static final String SAML_ATTR_NAME = "Name";
  public static final String SAML_ATTR_PROTOCOL_SUPPORT_ENUMERATION = "protocolSupportEnumeration";
  public static final String SAML_ATTR_USE = "use";
  public static final String XML_ATTR_ID = "ID";

  // SAML metadata attribute values.
  public static final String SAML_BINDING_HTTP_ARTIFACT =
      "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Artifact";
  public static final String SAML_BINDING_HTTP_POST =
      "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST";
  public static final String SAML_BINDING_HTTP_REDIRECT =
      "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect";
  public static final String SAML_BINDING_SOAP = "urn:oasis:names:tc:SAML:2.0:bindings:SOAP";
  public static final String SAML_PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
  public static final String SAML_USAGE_SIGNING = "signing";
  private static final String SAML_CACHE_DURATION = "PT1H";  // one hour

  // Entity group for SAML clients.
  public static final String SECMGR_CLIENTS_ENTITIES_NAME = "security-manager-clients";
  public static final String SECMGR_CLIENTS_ENTITIES_COMMENT = "SAML client IdPs";

  // XML Digital Signature elements.
  private static QName dsName(String localPart) {
    return new QName(XMLSignature.XMLNS, localPart, "ds");
  }

  public static final QName XMLDSIG_KEY_INFO = dsName("KeyInfo");
  public static final QName XMLDSIG_X509_DATA = dsName("X509Data");
  public static final QName XMLDSIG_X509_CERTIFICATE = dsName("X509Certificate");

  // Entity for GSA.
  public static final String GSA_ENTITY_ID_ROOT = "http://google.com/enterprise/gsa/";
  public static final String GSA_ENTITY_COMMENT = "Description of the GSA";
  public static final String GSA_ENT_CONFIG_NAME_MARKER = "${ENT_CONFIG_NAME}";

  // Entity for security manager.
  public static final String SECMGR_ENTITY_ID_SUFFIX = "/security-manager";
  public static final String SECMGR_ENTITY_COMMENT = "Description of the Security Manager";
  static final String SECMGR_ID_FOR_ENTITY = "security-manager";

  // Google organization element.
  public static final String GOOGLE_ORGANIZATION_NAME = "google.com";
  public static final String GOOGLE_ORGANIZATION_DISPLAY_NAME = "Google Inc.";
  public static final String GOOGLE_ORGANIZATION_URL = "http://www.google.com/";

  // Endpoint URLs.
  private static final String HTTP_PROTOCOL = "http";
  private static final String HTTPS_PROTOCOL = "https";
  static final String GSA_HOST_MARKER = "$$GSA$$";
  private static final String SECMGR_WEBAPP_PATH = "/security-manager";
  private static final String LOCALHOST = "localhost";

  public static final String GSA_ASSERTION_CONSUMER_PATH = "SamlArtifactConsumer";

  public static final String SECMGR_SSO_PATH = "samlauthn";
  public static final String SECMGR_ARTIFACT_RESOLVER_PATH = "samlartifact";
  public static final String SECMGR_ASSERTION_CONSUMER_PATH = "samlassertionconsumer";
  public static final String SECMGR_AUTHZ_PATH = "samlauthz";
  public static final String X509_HEADER = "-----BEGIN CERTIFICATE-----";
  public static final String X509_FOOTER = "-----END CERTIFICATE-----";

  // Don't instantiate this class.
  private MetadataEditor() {}

  /**
   * A datatype to hold the parameters of a SAML client IdP.
   */
  @Immutable
  public static class SamlClientIdp {

    private final String id;
    private final String loginUrl;
    private final String artifactUrl;
    private final String certificate;
    private final String authzServiceUrl;

    private SamlClientIdp(String id, String loginUrl, String artifactUrl, String certificate,
        String authzServiceUrl) {
      Preconditions.checkNotNull(id);
      this.id = id;
      this.loginUrl = loginUrl;
      this.artifactUrl = artifactUrl;
      this.certificate = normalizeCertificate(certificate);
      this.authzServiceUrl = authzServiceUrl;
    }

    /**
     * Make a client with only IDP SSO components.
     *
     * @param id The entity ID for the IdP.
     * @param loginUrl The single sign-on service URL for the IdP.
     * @param artifactUrl The artifact resolution service URL for the IdP, or null if none.
     * @param certificate A PEM encoded X509 certificate, or null if none.
     * @return A new IDP SSO client.
     */
    public static SamlClientIdp makeSso(String id, String loginUrl, String artifactUrl,
        String certificate) {
      Preconditions.checkNotNull(loginUrl);
      Preconditions.checkArgument(artifactUrl != null ^ certificate != null);
      return new SamlClientIdp(id, loginUrl, artifactUrl, certificate, null);
    }

    /**
     * Make a client with only PDP components.
     *
     * @param id The entity ID for the PDP.
     * @param authzServiceUrl The URL for the PDP's AuthzService.
     * @return A new PDP client.
     */
    public static SamlClientIdp makePdp(String id, String authzServiceUrl) {
      Preconditions.checkNotNull(authzServiceUrl);
      return new SamlClientIdp(id, null, null, null, authzServiceUrl);
    }

    /**
     * @return True if this client has IdP SSO components.
     */
    public boolean hasSso() {
      return loginUrl != null && (artifactUrl != null || certificate != null);
    }

    /**
     * @return True if this client has PDP components.
     */
    public boolean hasPdp() {
      return authzServiceUrl != null;
    }

    /**
     * @return True if this client has components other than IDP SSO.
     */
    public boolean hasNonSso() {
      return hasPdp();
    }

    /**
     * @return True if this client has components other than PDP.
     */
    public boolean hasNonPdp() {
      return hasSso();
    }

    /**
     * Replace the IDP SSO components of this client with those of another.
     *
     * @param client The client to get the IDP SSO components from.
     * @return A copy of this client with its IDP SSO components replaced.
     */
    public SamlClientIdp replaceSso(SamlClientIdp client) {
      return new SamlClientIdp(id,
          client.getUrl(),
          client.getArtifactUrl(),
          client.getCertificate(),
          authzServiceUrl);
    }

    /**
     * @return A copy of this client with its IDP SSO components removed.
     */
    public SamlClientIdp removeSso() {
      return new SamlClientIdp(id, null, null, null, authzServiceUrl);
    }

    /**
     * Replace the PDP components of this client with those of another.
     *
     * @param client The client to get the PDP components from.
     * @return A copy of this client with its PDP components replaced.
     */
    public SamlClientIdp replacePdp(SamlClientIdp client) {
      return new SamlClientIdp(id,
          loginUrl,
          artifactUrl,
          certificate,
          client.getAuthzServiceUrl());
    }

    /**
     * @return A copy of this client with its PDP components removed.
     */
    public SamlClientIdp removePdp() {
      return new SamlClientIdp(id, loginUrl, artifactUrl, certificate, null);
    }

    /**
     * @return The entity ID of the SAML client.
     */
    public String getId() {
      return id;
    }

    /**
     * @return The single-sign-on entry URL for the SAML client.
     */
    public String getUrl() {
      return loginUrl;
    }

    /**
     * @return The artifact-resolution entry URL for the SAML client, or null if none.
     */
    public String getArtifactUrl() {
      return artifactUrl;
    }

    /**
     * @return The signing certificate for the SAML client, or null if none.
     */
    public String getCertificate() {
      return certificate;
    }

    /**
     * @return The AuthzService URL for the SAML client.
     */
    public String getAuthzServiceUrl() {
      return authzServiceUrl;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(id, loginUrl, artifactUrl, certificate, authzServiceUrl);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) { return true; }
      if (!(obj instanceof SamlClientIdp)) { return false; }
      SamlClientIdp other = (SamlClientIdp) obj;
      return Objects.equal(id, other.id)
          && Objects.equal(loginUrl, other.loginUrl)
          && Objects.equal(artifactUrl, other.artifactUrl)
          && Objects.equal(certificate, other.certificate)
          && Objects.equal(authzServiceUrl, other.authzServiceUrl);
    }
  }

  /**
   * Get the SAML client IdPs from metadata.
   *
   * @param document The metadata as a {@link Document}.
   * @return A list of SAML client IdP descriptors.
   * @throws IllegalArgumentException if metadata can't be parsed.
   */
  public static List<SamlClientIdp> getSamlClientsInMetadata(Document document) {
    List<SamlClientIdp> clients = Lists.newArrayList();
    Set<String> idsSeen = Sets.newHashSet();
    Element entities = getSamlClients(document);
    if (entities != null) {
      NodeList nodes = getElementsByQname(entities, SAML_DESCRIPTOR_ENTITY);
      for (int index = 0; index < nodes.getLength(); index++) {
        Element element = Element.class.cast(nodes.item(index));
        SamlClientIdp client;
        try {
          client = parseClient(element, idsSeen);
        } catch (IllegalArgumentException e) {
          LOGGER.log(Level.WARNING, "Unable to parse SAML client spec: ", e);
          client = null;
        }
        if (client != null) {
          clients.add(client);
        }
      }
    }
    return clients;
  }

  private static SamlClientIdp parseClient(Element element, Set<String> idsSeen) {
    String id = element.getAttribute(SAML_ATTR_ENTITY_ID);
    Preconditions.checkArgument(!idsSeen.contains(id), "Duplicate entity ID in metadata: %s", id);
    idsSeen.add(id);
    SamlClientIdp sso = parseSso(id, findChildElement(element, SAML_DESCRIPTOR_IDP_SSO, false));
    SamlClientIdp pdp = parsePdp(id, findChildElement(element, SAML_DESCRIPTOR_PDP, false));
    return
        (sso == null) ? pdp
        : (pdp == null) ? sso
        : sso.replacePdp(pdp);
  }

  private static SamlClientIdp parseSso(String id, Element ssoRole) {
    if (ssoRole == null) {
      return null;
    }
    Element sso = findChildElement(ssoRole, SAML_SERVICE_SINGLE_SIGN_ON, true);
    Element ars = findChildElement(ssoRole, SAML_SERVICE_ARTIFACT_RESOLUTION, false);
    Element kd = findChildElement(ssoRole, SAML_DESCRIPTOR_KEY, false);
    return SamlClientIdp.makeSso(id,
        sso.getAttribute(SAML_ATTR_LOCATION),
        (ars == null) ? null : ars.getAttribute(SAML_ATTR_LOCATION),
        (kd == null) ? null : keyDescriptorCertificate(kd));
  }

  private static SamlClientIdp parsePdp(String id, Element pdp) {
    if (pdp == null) {
      return null;
    }
    Element authz = findChildElement(pdp, SAML_SERVICE_AUTHZ, true);
    return SamlClientIdp.makePdp(id,
        (authz == null) ? null : authz.getAttribute(SAML_ATTR_LOCATION));
  }

  /**
   * Set the SAML client IdPs in given metadata to the given set.  Removes any
   * clients already in the metadata, then inserts the given clients.
   *
   * @param document The metadata as a {@link Document}.  This document is
   *     modified to contain the new clients.
   * @param clients A set of SAML client IdP descriptors; null means delete all.
   * @throws IOException if metadata can't be parsed or serialized.
   */
  public static void setSamlClientsInMetadata(Document document, Collection<SamlClientIdp> clients)
      throws IOException {
    if (clients == null) {
      deleteSamlClients(document);
      return;
    }
    Set<String> idsSeen = Sets.newHashSet();
    checkSamlClients(idsSeen, clients);
    deleteSamlClients(document);
    for (SamlClientIdp client : clients) {
      addIdpEntity(document, client);
    }
  }

  /**
   * Add the given SAML client IdPs to the given metadata.
   *
   * @param document The metadata as a {@link Document}.  This document is
   *     modified to contain the new clients.
   * @param clients A set of SAML client IdP descriptors; never null.
   * @throws IOException if metadata can't be parsed or serialized.
   */
  public static void addSamlClientsToMetadata(Document document, Collection<SamlClientIdp> clients)
      throws IOException {
    Set<String> idsSeen = Sets.newHashSet();
    for (SamlClientIdp client : getSamlClientsInMetadata(document)) {
      idsSeen.add(client.getId());
    }
    checkSamlClients(idsSeen, clients);
    for (SamlClientIdp client : clients) {
      addIdpEntity(document, client);
    }
  }

  private static void checkSamlClients(Set<String> idsSeen, Collection<SamlClientIdp> clients) {
    for (SamlClientIdp client : clients) {
      Preconditions.checkArgument(!idsSeen.contains(client.getId()),
          "Duplicate entity ID in clients: %s", client.getId());
      idsSeen.add(client.getId());
    }
  }

  /**
   * Find a SAML client with a given entity ID.
   *
   * @param entityId The entity ID to look for.
   * @param clients The clients to look through.
   * @return The client with that ID, or null if none.
   */
  public static SamlClientIdp findSamlClient(String entityId, Iterable<SamlClientIdp> clients) {
    for (SamlClientIdp client : clients) {
      if (entityId.equals(client.getId())) {
        return client;
      }
    }
    return null;
  }

  // **************** Versions of the above using XML strings ****************

  /**
   * Get the SAML client IdPs from metadata.
   *
   * @param metadata The metadata as XML String.
   * @return A list of SAML client IdP descriptors.
   * @throws IOException if metadata can't be parsed.
   */
  public static List<SamlClientIdp> getSamlClientsInMetadata(String metadata)
      throws IOException {
    return getSamlClientsInMetadata(stringToMetadataDocument(metadata));
  }

  /**
   * Set the SAML client IdPs in given metadata to the given set.  Removes any
   * clients already in the metadata, then inserts the given clients.
   *
   * @param origMetadata The original metadata as an XML String.
   * @param clients The SAML client IdP descriptors; null means delete all.
   * @return The new metadata as XML String.
   * @throws IOException if metadata can't be parsed or serialized.
   */
  public static String setSamlClientsInMetadata(String origMetadata,
      Collection<SamlClientIdp> clients)
      throws IOException {
    Document document = stringToMetadataDocument(origMetadata);
    setSamlClientsInMetadata(document, clients);
    return metadataDocumentToString(document);
  }

  /**
   * Add the given SAML client IdPs to the given metadata.
   *
   * @param origMetadata The original metadata as an XML String.
   * @param clients The SAML client IdP descriptors; never null.
   * @return The new metadata as an XML String.
   * @throws IOException if metadata can't be parsed or serialized.
   */
  public static String addSamlClientsToMetadata(String origMetadata,
      Collection<SamlClientIdp> clients)
      throws IOException {
    Document document = stringToMetadataDocument(origMetadata);
    addSamlClientsToMetadata(document, clients);
    return metadataDocumentToString(document);
  }

  /**
   * Parse a metadata string into an XML document.
   *
   * @param metadata The metadata as an XML string.
   * @return The SAML metadata document.
   * @throws IOException if the metadata can't be parsed.
   */
  public static Document stringToMetadataDocument(String metadata)
      throws IOException {
    return readMetadataDocument(new StringReader(metadata));
  }

  /**
   * Convert a metadata XML document into a string.
   *
   * @param metadata The SAML metadata document.
   * @return The metadata as an XML string.
   * @throws IOException if the metadata can't be converted.
   */
  public static String metadataDocumentToString(Document metadata)
      throws IOException {
    return XmlUtil.getInstance().buildXmlString(metadata);
  }

  /**
   * Read SAML metadata and return it as a DOM document.
   *
   * @param input The Reader to read from.
   * @return The SAML metadata document.
   * @throws IOException if the document can't be parsed.
   */
  public static Document readMetadataDocument(Reader input)
      throws IOException {
    Document document = XmlUtil.getInstance().readXmlDocument(input);
    setConfigParams(document);
    return document;
  }

  /**
   * Write a SAML metadata document as XML.
   *
   * @param document The SAML metadata document to write.
   * @param writer The writer to write it to.
   * @throws IOException if the document can't be serialized.
   */
  public static void writeMetadataDocument(Document document, Writer writer)
      throws IOException {
    XmlUtil.getInstance().writeXmlDocument(document, writer);
  }

  /**
   * Read SAML metadata and return it as a DOM document.
   *
   * @param file The file to read from.
   * @return The SAML metadata document.
   * @throws IOException if the document can't be parsed.
   */
  public static Document readMetadataDocument(File file)
      throws IOException {
    Reader reader = new FileReader(file);
    try {
      return readMetadataDocument(reader);
    } finally {
      reader.close();
    }
  }

  /**
   * Write a SAML metadata document as XML.
   *
   * @param document The SAML metadata document to write.
   * @param file The file to write it to.
   * @throws IOException if the document can't be serialized.
   */
  public static void writeMetadataDocument(Document document, File file)
      throws IOException {
    Writer writer = new FileWriter(file);
    try {
      writeMetadataDocument(document, writer);
    } finally {
      writer.close();
    }
  }

  /**
   * Make a default metadata document for the security manager, when it is installed
   * onboard the GSA.  This metadata uses the token "$$GSA$$" to represent the hostname of
   * the GSA; this token will be dynamically replaced with the real hostname on each
   * request.  URLs that identify endpoints for connections between the GSA and the
   * security manager use "localhost".
   *
   * @return An XML document representing the metadata.
   */
  public static Document makeOnboardSecurityManagerMetadata() {
    Document document = XmlUtil.getInstance()
        .makeDocument(SAML_DESCRIPTOR_ENTITIES.getNamespaceURI(),
            SAML_DESCRIPTOR_ENTITIES.getLocalPart(),
            null);
    setConfigParams(document);
    Element entities = document.getDocumentElement();
    entities.setAttribute(SAML_ATTR_CACHE_DURATION, SAML_CACHE_DURATION);
    String gsaEntityId = GSA_ENTITY_ID_ROOT + GSA_ENT_CONFIG_NAME_MARKER;
    makeGsaEntity(entities, gsaEntityId);
    makeSmEntity(entities, gsaEntityId + SECMGR_ENTITY_ID_SUFFIX);
    return document;
  }

  /**
   * Make a SAML EntityDescriptor element for the GSA.
   *
   * @param entities The SAML EntitiesDescriptor element to put the element in.
   * @param id The SAML entity ID for the new element.
   * @return The EntityDescriptor as a DOM element.
   */
  private static Element makeGsaEntity(Element entities, String id) {
    Element entity = makeTopLevelEntity(entities, id, GSA_ENTITY_COMMENT);

    makeRole(entity, SAML_DESCRIPTOR_SP_SSO);
    makeAssertionConsumer(entity,
                          SAML_BINDING_HTTP_ARTIFACT,
                          makeGsaUrl(GSA_HOST_MARKER, GSA_ASSERTION_CONSUMER_PATH));

    makeGoogleOrganization(entity);

    return entity;
  }

  /**
   * Make a URL for the GSA.
   *
   * @param host The hostname to use.
   * @param path The URL path.
   * @return The URL as a string.
   */
  private static String makeGsaUrl(String host, String path) {
    URL url;
    try {
      url = new URL(HTTPS_PROTOCOL, host, -1, path);
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
    return url.toString();
  }

  /**
   * Make a SAML EntityDescriptor element for the security manager.
   *
   * @param entities The SAML EntitiesDescriptor element to put the new element in.
   * @param id The SAML entity ID for the new element.
   * @return The EntityDescriptor as a DOM element.
   */
  private static Element makeSmEntity(Element entities, String id) {
    Element entity = makeTopLevelEntity(entities, id, SECMGR_ENTITY_COMMENT);
    entity.setAttribute(XML_ATTR_ID, SECMGR_ID_FOR_ENTITY);

    makeRole(entity, SAML_DESCRIPTOR_IDP_SSO);
    makeSingleSignOn(entity, makeSecurityManagerUrl(GSA_HOST_MARKER, SECMGR_SSO_PATH));
    makeArtifactResolver(entity, makeSecurityManagerUrl(LOCALHOST, SECMGR_ARTIFACT_RESOLVER_PATH));

    makeRole(entity, SAML_DESCRIPTOR_SP_SSO);
    makeAssertionConsumer(entity,
                          SAML_BINDING_HTTP_POST,
                          makeSecurityManagerUrl(GSA_HOST_MARKER, SECMGR_ASSERTION_CONSUMER_PATH));
    makeAssertionConsumer(entity,
                          SAML_BINDING_HTTP_ARTIFACT,
                          makeSecurityManagerUrl(GSA_HOST_MARKER, SECMGR_ASSERTION_CONSUMER_PATH));

    makePdp(entity, makeSecurityManagerUrl(LOCALHOST, SECMGR_AUTHZ_PATH));

    makeGoogleOrganization(entity);
    return entity;
  }

  /**
   * Make a URL for a security manager servlet.
   *
   * @param host The hostname to use.
   * @param servletPath The part of the path that's specific to the servlet.
   * @return The URL as a string.
   */
  private static String makeSecurityManagerUrl(String host, String servletPath) {
    String path = SECMGR_WEBAPP_PATH + "/" + servletPath;
    URL url;
    try {
      if (LOCALHOST.equals(host)) {
        url = new URL(HTTP_PROTOCOL, host, path);
      } else {
        url = new URL(HTTPS_PROTOCOL, host, path);
      }
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
    return url.toString();
  }

  /**
   * Make a SAML Organization element for Google.
   *
   * @param entity The SAML Entity element to put the new element in.
   * @return The Organization as a DOM element.
   */
  private static Element makeGoogleOrganization(Element entity) {
    Element organization = makeElement(entity, SAML_ORGANIZATION);
    makeTextElementChild(organization, SAML_ORGANIZATION_NAME, GOOGLE_ORGANIZATION_NAME);
    makeTextElementChild(organization, SAML_ORGANIZATION_DISPLAY_NAME,
        GOOGLE_ORGANIZATION_DISPLAY_NAME);
    makeTextElementChild(organization, SAML_ORGANIZATION_URL, GOOGLE_ORGANIZATION_URL);
    return organization;
  }

  /**
   * Add an external SAML client IdP to a security manager metadata document.  If there is
   * already an entity with the same ID, it is replaced.
   *
   * @param document A security manager metadata document.
   * @param client A SAML client IdP descriptor.
   */
  public static void addIdpEntity(Document document, SamlClientIdp client) {
    Element clients = getSamlClients(document);
    if (clients == null) {
      clients = makeSamlClients(document);
    }
    Element entity = makeEntity(clients, client.getId());
    if (client.hasSso()) {
      Element role = makeRole(entity, SAML_DESCRIPTOR_IDP_SSO);
      makeSingleSignOn(entity, client.getUrl());
      String artifactUrl = normalizeString(client.getArtifactUrl());
      if (artifactUrl != null) {
        makeArtifactResolver(entity, artifactUrl);
      }
      if (client.getCertificate() != null) {
        makeKeyDescriptor(role, client.getCertificate());
      }
    }
    if (client.hasPdp()) {
      makePdp(entity, client.getAuthzServiceUrl());
    }
  }

  private static String normalizeString(String s) {
    if (s == null) {
      return s;
    }
    s = s.trim();
    return s.isEmpty() ? null : s;
  }

  /**
   * Get the SAML EntitiesDescriptor that holds the client entities.
   *
   * @param document The SAML metadata document to look in.
   * @return The EntitiesDescriptor element, or null if no such.
   */
  public static Element getSamlClients(Document document) {
    NodeList nodes =
        getElementsByQname(document.getDocumentElement(), SAML_DESCRIPTOR_ENTITIES);
    for (int index = 0; index < nodes.getLength(); index++) {
      Element element = Element.class.cast(nodes.item(index));
      if (SECMGR_CLIENTS_ENTITIES_NAME.equals(element.getAttribute(SAML_ATTR_NAME))) {
        return element;
      }
    }
    return null;
  }

  /**
   * Make an empty SAML EntitiesDescriptor to hold client entities.
   *
   * @param document The SAML metadata document to look in.
   * @return The new EntitiesDescriptor as a DOM element.
   */
  private static Element makeSamlClients(Document document) {
    return makeTopLevelEntities(document.getDocumentElement(),
        SECMGR_CLIENTS_ENTITIES_NAME, SECMGR_CLIENTS_ENTITIES_COMMENT);
  }

  /**
   * Remove the SAML client entities descriptor, if there is one.
   *
   * @param document The SAML metadata document to look in.
   */
  private static void deleteSamlClients(Document document) {
    Element oldClients = getSamlClients(document);
    if (oldClients != null) {
      oldClients.getParentNode().removeChild(oldClients);
    }
    for (Comment comment :
             findChildComments(
                 document.getDocumentElement(),
                 SECMGR_CLIENTS_ENTITIES_COMMENT)) {
      comment.getParentNode().removeChild(comment);
    }
  }


  // Endpoint constructors.

  /**
   * Make a SAML SingleSignOn element.
   *
   * @param entity The SAML EntityDescriptor to put the new element in.
   * @param url The endpoint URL for the new element.
   * @return The new SingleSignOn as a DOM element.
   */
  private static Element makeSingleSignOn(Element entity, String url) {
    return makeEndpoint(findRole(entity, SAML_DESCRIPTOR_IDP_SSO),
        SAML_SERVICE_SINGLE_SIGN_ON, SAML_BINDING_HTTP_REDIRECT, url);
  }

  /**
   * Make a SAML ArtifactResolver element.
   *
   * @param entity The SAML EntityDescriptor to put the new element in.
   * @param url The endpoint URL for the new element.
   * @return The new ArtifactResolver as a DOM element.
   */
  private static Element makeArtifactResolver(Element entity, String url) {
    return makeIndexedEndpoint(findRole(entity, SAML_DESCRIPTOR_IDP_SSO),
        SAML_SERVICE_ARTIFACT_RESOLUTION, SAML_BINDING_SOAP, url);
  }

  /**
   * Make a SAML AssertionConsumer element.
   *
   * @param entity The SAML EntityDescriptor to put the new element in.
   * @param url The endpoint URL for the new element.
   * @return The new AssertionConsumer as a DOM element.
   */
  private static Element makeAssertionConsumer(Element entity, String binding, String url) {
    return makeIndexedEndpoint(findRole(entity, SAML_DESCRIPTOR_SP_SSO),
        SAML_SERVICE_ASSERTION_CONSUMER, binding, url);
  }

  /**
   * Make a SAML PDPDescriptor element.
   *
   * @param entity The SAML EntityDescriptor to put the new element in.
   * @param url The endpoint URL for the new element.
   * @return The new PDPDescriptor as a DOM element.
   */
  private static Element makePdp(Element entity, String url) {
    Element role = makeRole(entity, SAML_DESCRIPTOR_PDP);
    makeEndpoint(role, SAML_SERVICE_AUTHZ, SAML_BINDING_SOAP, url);
    return role;
  }

  /**
   * Find a role element with a given name.
   *
   * @param entity The SAML EntityDescriptor to look in.
   * @param qname The qname of the role element to look for.
   * @return The specified role element.
   * @throws IllegalArgumentException if there's no such child.
   */
  private static Element findRole(Element entity, QName qname) {
    return findChildElement(entity, qname, true);
  }


  // Element constructors.

  /**
   * Make a new top-level SAML EntitiesDescriptor element.
   *
   * @param entities The SAML EntitiesDescriptor to put the new element in.
   * @param name The name of the new element -- will be put in the name attribute.
   * @param comment A descriptive comment that will be added as an XML comment.
   * @return The new EntitiesDescriptor as a DOM element.
   */
  private static Element makeTopLevelEntities(Element entities, String name, String comment) {
    makeTextChild(entities, "\n");
    makeCommentChild(entities, " " + comment + " ");
    makeTextChild(entities, "\n");
    Element result = makeEntities(entities, name);
    makeTextChild(entities, "\n");
    return result;
  }

  /**
   * Make a new SAML EntitiesDescriptor element.
   *
   * @param entities The SAML EntitiesDescriptor to put the new element in.
   * @param name The name of the new element -- will be put in the name attribute.
   * @return The new EntitiesDescriptor as a DOM element.
   */
  private static Element makeEntities(Element entities, String name) {
    Element child = makeElementChild(entities, SAML_DESCRIPTOR_ENTITIES);
    child.setAttribute(SAML_ATTR_NAME, name);
    return child;
  }

  /**
   * Make a new top-level SAML EntityDescriptor element.
   *
   * @param entities The SAML EntitiesDescriptor to put the new element in.
   * @param id The id of the new element -- will be put in the ID attribute.
   * @param comment A descriptive comment that will be added as an XML comment.
   * @return The new EntityDescriptor as a DOM element.
   */
  private static Element makeTopLevelEntity(Element entities, String id, String comment) {
    makeTextChild(entities, "\n");
    makeCommentChild(entities, " " + comment + " ");
    makeTextChild(entities, "\n");
    Element entity = makeEntity(entities, id);
    makeTextChild(entities, "\n");
    return entity;
  }

  /**
   * Make a new SAML EntityDescriptor element.
   *
   * @param entities The SAML EntitiesDescriptor to put the new element in.
   * @param id The id of the new element -- will be put in the ID attribute.
   * @return The new EntityDescriptor as a DOM element.
   */
  private static Element makeEntity(Element entities, String id) {
    Element old = findEntity(entities, id);
    Element entity = makeElement(entities, SAML_DESCRIPTOR_ENTITY);
    entity.setAttribute(SAML_ATTR_ENTITY_ID, id);
    if (old == null) {
      entities.appendChild(entity);
    } else {
      entities.replaceChild(entity, old);
    }
    return entity;
  }

  /**
   * Find a SAML EntityDescriptor element.
   *
   * @param entities The SAML EntitiesDescriptor to look in.
   * @param id The id of the element to look for.
   * @return The specified EntityDescriptor, or null if no such.
   */
  private static Element findEntity(Element entities, String id) {
    NodeList nodes = getElementsByQname(entities, SAML_DESCRIPTOR_ENTITY);
    for (int index = 0; index < nodes.getLength(); index++) {
      Element element = Element.class.cast(nodes.item(index));
      if (id.equals(element.getAttribute(SAML_ATTR_ENTITY_ID))) {
        return element;
      }
    }
    return null;
  }

  /**
   * Make a new SAML role.
   *
   * @param entity the SAML EntityDescriptor to put the new role in.
   * @param qname The qname of the new role.
   * @return The new role as a DOM element.
   */
  private static Element makeRole(Element entity, QName qname) {
    Element role = makeElementChild(entity, qname);
    role.setAttribute(SAML_ATTR_PROTOCOL_SUPPORT_ENUMERATION, SAML_PROTOCOL);
    return role;
  }

  /**
   * Make a new SAML endpoint.
   *
   * @param role the SAML role to put the new endpoint in.
   * @param qname The qname of the new endpoint.
   * @param binding The SAML binding specifier.
   * @param url The endpoing URL.
   * @return The new endpoint as a DOM element.
   */
  private static Element makeEndpoint(Element role, QName qname, String binding, String url) {
    Element endpoint = makeElementChild(role, qname);
    endpoint.setAttribute(SAML_ATTR_BINDING, binding);
    endpoint.setAttribute(SAML_ATTR_LOCATION, url);
    return endpoint;
  }

  /**
   * Make a new SAML indexed endpoint.
   *
   * @param role the SAML role to put the new endpoint in.
   * @param qname The qname of the new endpoint.
   * @param binding The SAML binding specifier.
   * @param url The endpoing URL.
   * @return The new endpoint as a DOM element.
   */
  private static Element makeIndexedEndpoint(
      Element role, QName qname, String binding, String url) {
    int nChildren = countChildElements(role, qname);
    Element endpoint = makeEndpoint(role, qname, binding, url);
    endpoint.setAttribute(SAML_ATTR_INDEX, Integer.toString(nChildren));
    endpoint.setAttribute(SAML_ATTR_IS_DEFAULT, (nChildren == 0) ? "true" : "false");
    return endpoint;
  }

  /**
   * Make a new SAML key descriptor.
   *
   * @param role the SAML role to put the new descriptor in.
   * @param certificate The X509 certificate that the descriptor will contain.
   * @return The new key descriptor as a DOM element.
   */
  private static Element makeKeyDescriptor(Element role, String certificate) {
    Element keyDescriptor = makeElementChild(role, SAML_DESCRIPTOR_KEY);
    keyDescriptor.setAttribute(SAML_ATTR_USE, SAML_USAGE_SIGNING);
    Element keyInfo = makeElementChild(keyDescriptor, XMLDSIG_KEY_INFO);
    Element x509Data = makeElementChild(keyInfo, XMLDSIG_X509_DATA);
    makeTextElementChild(x509Data, XMLDSIG_X509_CERTIFICATE, normalizeCertificate(certificate));
    return keyDescriptor;
  }

  /**
   * Get the certificate out of a SAML key descriptor.  Works only with key descriptors
   * created by #makeKeyDescriptor().
   *
   * @param keyDescriptor The SAML key descriptor to extract from.
   * @return The extracted certificate, or null if none such.
   */
  private static String keyDescriptorCertificate(Element keyDescriptor) {
    Element keyInfo = findChildElement(keyDescriptor, XMLDSIG_KEY_INFO, false);
    if (keyInfo == null) { return null; }
    Element x509Data = findChildElement(keyInfo, XMLDSIG_X509_DATA, false);
    if (x509Data == null) { return null; }
    Element x509Certificate = findChildElement(x509Data, XMLDSIG_X509_CERTIFICATE, false);
    if (x509Certificate == null) { return null; }
    NodeList nodes = x509Certificate.getChildNodes();
    if (nodes.getLength() == 1 && nodes.item(0).getNodeType() == Node.TEXT_NODE) {
      return normalizeCertificate(nodes.item(0).getNodeValue());
    }
    return null;
  }

  /**
   * Convert a PEM certificate to normalized form.
   *
   * @param s The certificate to normalize.
   * @return The normalized certificate.
   */
  public static String normalizeCertificate(String s) {
    if (s == null) {
      return s;
    }
    List<String> lines = Lists.newArrayList();
    for (String line : s.trim().split("\n+")) {
      lines.add(line.trim());
    }
    if (lines.size() == 0) {
      return null;
    }
    // Trim off X509 header/footer if present.
    if (X509_HEADER.equals(lines.get(0))
        && X509_FOOTER.equals(lines.get(lines.size() - 1))) {
      lines.remove(lines.size() - 1);
      lines.remove(0);
    }
    StringBuilder builder = new StringBuilder();
    for (String line : lines) {
      builder.append(line);
      builder.append("\n");
    }
    return builder.toString();
  }
}
