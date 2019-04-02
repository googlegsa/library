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

import org.opensaml.Configuration;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.SAMLObjectBuilder;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.metadata.ArtifactResolutionService;
import org.opensaml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml2.metadata.SingleSignOnService;
import org.opensaml.xml.XMLObjectBuilderFactory;

import javax.xml.namespace.QName;

/**
 * Manual generation of SAML metadata.
 *
 * <p>This does not load from an XML file because our configuration is very
 * static and pre-defined. In addition, we have to perform replacements within
 * the configuration based on our own configuration.
 */
class SamlMetadata {
  private final EntityDescriptor localEntity;
  private final EntityDescriptor peerEntity;
  private final XMLObjectBuilderFactory objectBuilderFactory =
      Configuration.getBuilderFactory();

  public SamlMetadata(String hostname, int port, String gsaHostname,
      String gsaEntityId, String adaptorEntityId) {
    localEntity = createLocalEntity(hostname, port, adaptorEntityId);
    peerEntity = createPeerEntity(gsaHostname, gsaEntityId);
  }

  private EntityDescriptor createLocalEntity(String hostname, int port,
      String adaptorEntityId) {
    EntityDescriptor ed = makeSamlObject(EntityDescriptor.DEFAULT_ELEMENT_NAME);
    ed.setEntityID(adaptorEntityId);

    SPSSODescriptor spsso = makeSamlObject(
        SPSSODescriptor.DEFAULT_ELEMENT_NAME);
    ed.getRoleDescriptors().add(spsso);
    spsso.addSupportedProtocol(SAMLConstants.SAML20P_NS);

    AssertionConsumerService acs = makeSamlObject(
        AssertionConsumerService.DEFAULT_ELEMENT_NAME);
    spsso.getAssertionConsumerServices().add(acs);
    acs.setBinding(SAMLConstants.SAML2_ARTIFACT_BINDING_URI);
    acs.setLocation(
        "https://" + hostname + ":" + port + "/samlassertionconsumer");

    IDPSSODescriptor idpsso = makeSamlObject(
        IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    ed.getRoleDescriptors().add(idpsso);
    idpsso.addSupportedProtocol(SAMLConstants.SAML20P_NS);

    SingleSignOnService ssos = makeSamlObject(
        SingleSignOnService.DEFAULT_ELEMENT_NAME);
    idpsso.getSingleSignOnServices().add(ssos);
    ssos.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
    ssos.setLocation("https://" + hostname + ":" + port + "/samlip");

    return ed;
  }

  private EntityDescriptor createPeerEntity(String gsaHostname,
      String gsaEntityId) {
    EntityDescriptor ed = makeSamlObject(EntityDescriptor.DEFAULT_ELEMENT_NAME);
    ed.setEntityID(gsaEntityId);

    IDPSSODescriptor idpsso = makeSamlObject(
        IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    ed.getRoleDescriptors().add(idpsso);
    idpsso.addSupportedProtocol(SAMLConstants.SAML20P_NS);

    SingleSignOnService ssos = makeSamlObject(
        SingleSignOnService.DEFAULT_ELEMENT_NAME);
    idpsso.getSingleSignOnServices().add(ssos);
    ssos.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
    ssos.setLocation("https://" + gsaHostname + "/security-manager/samlauthn");

    ArtifactResolutionService ars = makeSamlObject(
        ArtifactResolutionService.DEFAULT_ELEMENT_NAME);
    idpsso.getArtifactResolutionServices().add(ars);
    ars.setBinding(SAMLConstants.SAML2_SOAP11_BINDING_URI);
    ars.setLocation(
        "https://" + gsaHostname + "/security-manager/samlartifact");

    SPSSODescriptor spsso = makeSamlObject(
        SPSSODescriptor.DEFAULT_ELEMENT_NAME);
    ed.getRoleDescriptors().add(spsso);
    idpsso.addSupportedProtocol(SAMLConstants.SAML20P_NS);

    // TODO(ejona): Loop through all full access hosts.
    AssertionConsumerService acs = makeSamlObject(
        AssertionConsumerService.DEFAULT_ELEMENT_NAME);
    spsso.getAssertionConsumerServices().add(acs);
    acs.setBinding(SAMLConstants.SAML2_ARTIFACT_BINDING_URI);
    acs.setLocation(
        "https://" + gsaHostname + "/security-manager/samlassertionconsumer");

    acs = makeSamlObject(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
    spsso.getAssertionConsumerServices().add(acs);
    acs.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
    acs.setLocation(
        "https://" + gsaHostname + "/security-manager/samlassertionconsumer");

    return ed;
  }

  @SuppressWarnings("unchecked")
  private <T extends SAMLObject> SAMLObjectBuilder<T> makeSamlObjectBuilder(
      QName name) {
    return (SAMLObjectBuilder<T>) objectBuilderFactory.getBuilder(name);
  }

  private <T extends SAMLObject> T makeSamlObject(QName name) {
    SAMLObjectBuilder<T> builder = makeSamlObjectBuilder(name);
    return builder.buildObject();
  }

  public EntityDescriptor getLocalEntity() {
    return localEntity;
  }

  public EntityDescriptor getPeerEntity() {
    return peerEntity;
  }
}
