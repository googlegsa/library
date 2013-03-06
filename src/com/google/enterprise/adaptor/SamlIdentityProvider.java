// Copyright 2013 Google Inc. All Rights Reserved.
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

import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeAssertion;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeAttribute;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeAttributeStatement;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeAttributeValue;

import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeAudienceRestriction;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeAuthnStatement;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeConditions;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeResponse;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeStatus;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeStatusCode;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeStatusMessage;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeSubject;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeSubjectConfirmation;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeSubjectConfirmationData;
import static com.google.enterprise.secmgr.saml.OpenSamlUtil.makeSuccessfulStatus;

import com.google.enterprise.secmgr.saml.OpenSamlUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.log.JdkLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.joda.time.DateTime;
import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.binding.AuthnResponseEndpointSelector;
import org.opensaml.saml2.binding.decoding.HTTPRedirectDeflateDecoder;
import org.opensaml.saml2.binding.encoding.HTTPPostEncoder;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AuthnContext;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml2.metadata.Endpoint;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml2.metadata.RoleDescriptor;
import org.opensaml.saml2.metadata.SPSSODescriptor;
import org.opensaml.ws.message.decoder.MessageDecodingException;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.xml.security.SecurityException;
import org.opensaml.xml.security.SecurityHelper;
import org.opensaml.xml.security.credential.Credential;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.util.*;
import java.util.logging.*;

/**
 * Provides ability to recieve and respond to SAML authn requests.
 */
class SamlIdentityProvider {
  private static final Logger log
      = Logger.getLogger(SamlIdentityProvider.class.getName());
  private static final VelocityEngine velocityEngine;

  static {
    velocityEngine = new VelocityEngine();
    velocityEngine.addProperty("resource.loader", "classloader");
    velocityEngine.addProperty("classloader.resource.loader.class",
        ClasspathResourceLoader.class.getName());
    velocityEngine.addProperty("runtime.log.logsystem.class",
        JdkLogChute.class.getName());
    try {
      velocityEngine.init();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private final AuthnAdaptor adaptor;
  /** Credentials to use to sign messages. */
  private final Credential cred;
  private final SamlMetadata metadata;
  private final SsoHandler ssoHandler = new SsoHandler();

  public SamlIdentityProvider(AuthnAdaptor adaptor, SamlMetadata metadata,
      KeyPair key) {
    if (adaptor == null || metadata == null) {
      throw new NullPointerException();
    }
    this.adaptor = adaptor;
    this.metadata = metadata;
    this.cred = (key == null) ? null
        : SecurityHelper.getSimpleCredential(key.getPublic(), key.getPrivate());
  }

  public void respond(HttpExchange ex,
      SAMLMessageContext<AuthnRequest, Response, NameID> context,
      AuthnIdentity identity) throws IOException {
    Response samlResponse = createResponse(context, identity);

    context.setOutboundSAMLMessage(samlResponse);
    context.setOutboundMessageTransport(
        new HttpExchangeOutTransportAdapter(ex));

    String responseBinding = context.getPeerEntityEndpoint().getBinding();
    if (!SAMLConstants.SAML2_POST_BINDING_URI.equals(responseBinding)) {
      throw new IllegalStateException("Unknown SAML binding: "
          + responseBinding);
    }
    try {
      new HTTPPostEncoder(velocityEngine, "/templates/saml2-post-binding.vm")
          .encode(context);
    } catch (MessageEncodingException e) {
      throw new IOException("Failed to encode SAML response", e);
    }
    ex.getResponseBody().flush();
    ex.getResponseBody().close();
    ex.close();
  }

  private Response createResponse(
      SAMLMessageContext<AuthnRequest, Response, NameID> context,
      AuthnIdentity identity) {
    String recipient = context.getPeerEntityEndpoint().getLocation();
    String audience = context.getInboundMessageIssuer();
    String inResponseTo = context.getInboundSAMLMessage().getID();
    String issuer = context.getLocalEntityId();
    DateTime now = new DateTime();
    // Expiration time is 30 seconds in the future.
    DateTime expirationTime = now.plusMillis(30 * 1000);

    if (identity == null) {
      return makeResponse(issuer, now,
          makeStatus(
              makeStatusCode(StatusCode.RESPONDER_URI),
              makeStatusMessage("Could not authenticate user")),
          inResponseTo);
    }

    if (identity.getUser().getName().isEmpty()) {
      throw new IllegalArgumentException("Username must not be empty");
    }

    Attribute groupsAttribute = makeAttribute("member-of");
    Iterable<GroupPrincipal> groups = identity.getGroups();
    if (groups == null) {
      groups = Collections.emptySet();
    }
    for (GroupPrincipal group : groups) {
      String name = group.getName();
      groupsAttribute.getAttributeValues().add(makeAttributeValue(name));
    }

    return makeResponse(issuer, now, makeSuccessfulStatus(), inResponseTo,
        makeAssertion(issuer, now,
          makeSubject(identity.getUser().getName(),
            makeSubjectConfirmation(OpenSamlUtil.BEARER_METHOD,
              makeSubjectConfirmationData(recipient, expirationTime,
                inResponseTo))),
          makeConditions(now, expirationTime,
            makeAudienceRestriction(audience)),
          makeAuthnStatement(now, AuthnContext.IP_PASSWORD_AUTHN_CTX),
          makeAttributeStatement(groupsAttribute)));
  }

  public HttpHandler getSingleSignOnHandler() {
    return ssoHandler;
  }

  private class SsoHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
      if (!"GET".equals(ex.getRequestMethod())) {
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
            Translation.HTTP_BAD_METHOD);
        return;
      }
      if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
            Translation.HTTP_NOT_FOUND);
        return;
      }
      // Setup SAML context.
      SAMLMessageContext<AuthnRequest, Response, NameID> context
          = OpenSamlUtil.makeSamlMessageContext();
      context.setLocalEntityId(metadata.getLocalEntity().getEntityID());
      context.setLocalEntityMetadata(metadata.getLocalEntity());
      context.setLocalEntityRole(IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
      context.setLocalEntityRoleMetadata(
          getFirst(metadata.getLocalEntity().getRoleDescriptors(
              IDPSSODescriptor.DEFAULT_ELEMENT_NAME)));
      context.setOutboundMessageIssuer(metadata.getLocalEntity().getEntityID());
      context.setOutboundSAMLMessageSigningCredential(cred);

      context.setInboundMessageTransport(
          new HttpExchangeInTransportAdapter(ex));
      // Decode request.
      try {
        new RequestUriRedirectDeflateDecoder(HttpExchanges.getRequestUri(ex))
            .decode(context);
      } catch (MessageDecodingException e) {
        log.log(Level.INFO, "Error decoding message", e);
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
            Translation.HTTP_BAD_REQUEST_ERROR_DECODING);
        return;
      } catch (SecurityException e) {
        log.log(Level.WARNING, "Security error while decoding message", e);
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
            Translation.HTTP_BAD_REQUEST_SECURITY_ERROR);
        return;
      }

      Endpoint peerEndpoint = selectEndpoint(context);
      if (peerEndpoint == null) {
        log.log(Level.INFO,
            "Error decoding message: could not determine peerEndpoint");
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
            Translation.HTTP_BAD_REQUEST_ERROR_DECODING);
        return;
      }
      context.setPeerEntityEndpoint(peerEndpoint);

      adaptor.authenticateUser(ex, new AuthnCallback(context));
    }

    private Endpoint selectEndpoint(
        SAMLMessageContext<AuthnRequest, ?, ?> context) {
      AuthnResponseEndpointSelector selector
          = new AuthnResponseEndpointSelector();
      selector.setEndpointType(
          AssertionConsumerService.DEFAULT_ELEMENT_NAME);
      selector.getSupportedIssuerBindings()
          .add(SAMLConstants.SAML2_POST_BINDING_URI);

      String peerEntityId = context.getInboundMessageIssuer();
      EntityDescriptor entityDescriptor = null;
      RoleDescriptor roleDescriptor = null;
      // TODO(ejona): Support additional peer entities other than a single GSA.
      if (peerEntityId != null
          && peerEntityId.equals(metadata.getPeerEntity().getEntityID())) {
        entityDescriptor = metadata.getPeerEntity();
        roleDescriptor = getFirst(entityDescriptor.getRoleDescriptors(
            SPSSODescriptor.DEFAULT_ELEMENT_NAME));
      }

      selector.setSamlRequest(context.getInboundSAMLMessage());
      selector.setEntityMetadata(entityDescriptor);
      selector.setEntityRoleMetadata(roleDescriptor);

      return selector.selectEndpoint();
    }

    private <V> V getFirst(List<V> list) {
      return list.isEmpty() ? null : list.get(0);
    }
  }

  private class AuthnCallback implements AuthnAdaptor.Callback {
    private final SAMLMessageContext<AuthnRequest, Response, NameID> context;

    public AuthnCallback(
        SAMLMessageContext<AuthnRequest, Response, NameID> context) {
      this.context = context;
    }

    @Override
    public void userAuthenticated(HttpExchange ex, AuthnIdentity identity)
        throws IOException {
      respond(ex, context, identity);
    }
  }

  private static class RequestUriRedirectDeflateDecoder
      extends HTTPRedirectDeflateDecoder {
    private final String requestUri;

    /**
     * @param requestUri the URI the client used to make the request
     */
    public RequestUriRedirectDeflateDecoder(URI requestUri) {
      try {
        // Remove query parameters from URI.
        requestUri = new URI(requestUri.getScheme(), requestUri.getAuthority(),
            requestUri.getPath(), null, null);
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e);
      }
      this.requestUri = requestUri.toASCIIString();
    }

    @Override
    protected String getActualReceiverEndpointURI(
        SAMLMessageContext messageContext) {
      // This method in HTTPRedirectDeflateDecoder is hard-coded for use with
      // HttpServletRequestAdapter only, which we aren't using.
      return requestUri;
    }
  }
}
