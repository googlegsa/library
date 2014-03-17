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

package com.google.enterprise.adaptor.secmgr.modules;

import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.initializeLocalEntity;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.initializePeerEntity;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.makeAction;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.makeArtifactResolve;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.makeAuthnRequest;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.makeAuthzDecisionQuery;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.makeSamlMessageContext;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.makeSubject;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.runDecoder;
import static com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil.runEncoder;
import static org.opensaml.common.xml.SAMLConstants.SAML20P_NS;
import static org.opensaml.common.xml.SAMLConstants.SAML2_REDIRECT_BINDING_URI;
import static org.opensaml.common.xml.SAMLConstants.SAML2_SOAP11_BINDING_URI;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.enterprise.adaptor.secmgr.common.AuthzStatus;
import com.google.enterprise.adaptor.secmgr.http.HttpClientInterface;
import com.google.enterprise.adaptor.secmgr.http.HttpExchange;
import com.google.enterprise.adaptor.secmgr.saml.HTTPSOAP11MultiContextDecoder;
import com.google.enterprise.adaptor.secmgr.saml.HTTPSOAP11MultiContextEncoder;
import com.google.enterprise.adaptor.secmgr.saml.HttpExchangeToInTransport;
import com.google.enterprise.adaptor.secmgr.saml.HttpExchangeToOutTransport;
import com.google.enterprise.adaptor.secmgr.saml.SamlLogUtil;

import org.joda.time.DateTime;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.binding.decoding.HTTPSOAP11Decoder;
import org.opensaml.saml2.binding.encoding.HTTPRedirectDeflateEncoder;
import org.opensaml.saml2.binding.encoding.HTTPSOAP11Encoder;
import org.opensaml.saml2.core.Action;
import org.opensaml.saml2.core.ArtifactResolve;
import org.opensaml.saml2.core.ArtifactResponse;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.AuthzDecisionQuery;
import org.opensaml.saml2.core.AuthzDecisionStatement;
import org.opensaml.saml2.core.DecisionTypeEnumeration;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.Statement;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.metadata.ArtifactResolutionService;
import org.opensaml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml2.metadata.AuthzService;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml2.metadata.SingleSignOnService;
import org.opensaml.util.URLBuilder;
import org.opensaml.ws.message.encoder.MessageEncoder;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.ws.transport.http.HTTPInTransport;
import org.opensaml.ws.transport.http.HTTPOutTransport;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.util.Pair;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A library implementing most of the functionality of a SAML service provider.
 * This library knows how to send an authentication request via the redirect
 * binding, and to receive a response via either artifact or POST binding.
 */
@ThreadSafe
public class SamlClient {
  private static final Logger LOGGER = Logger.getLogger(SamlClient.class.getName());

  private final EntityDescriptor localEntity;
  private final EntityDescriptor peerEntity;
  private final String providerName;
  private final Credential signingCredential;
  private final HttpClientInterface httpClient;
  private final int timeout;

  @GuardedBy("requestIdLock")
  private String requestId;
  private static final Object requestIdLock = new Object();

  /**
   * Create an instance of the client library.
   *
   * @param localEntity Metadata for the the local entity (the service provider
   *        using this library).
   * @param peerEntity Metadata for the peer entity (the SAML IdP).
   * @param providerName Descriptive name of the service provider.
   * @param signingCredential A credential to use for signing the outgoing
   *        request.  May be null if signing isn't needed.
   * @param httpClient An HTTP client to use for resolving any artifact returned
   *        from the IdP.  May be null if the artifact binding isn't being used.
   */
  public SamlClient(EntityDescriptor localEntity, EntityDescriptor peerEntity,
      String providerName, Credential signingCredential, HttpClientInterface httpClient) {
    this.localEntity = localEntity;
    this.peerEntity = peerEntity;
    this.providerName = providerName;
    this.signingCredential = signingCredential;
    this.httpClient = httpClient;
    timeout = -1;
  }

  /**
   * Create an instance of the client library.
   *
   * @param localEntity Metadata for the the local entity (the service provider
   *        using this library).
   * @param peerEntity Metadata for the peer entity (the SAML IdP).
   * @param providerName Descriptive name of the service provider.
   * @param signingCredential A credential to use for signing the outgoing
   *        request.  May be null if signing isn't needed.
   * @param httpClient An HTTP client to use for resolving any artifact returned
   *        from the IdP.  May be null if the artifact binding isn't being
   * @param timeout The http socket timeout value in milliseconds.
   *        -1 means to use the httpClient default timeout.
   */
  public SamlClient(EntityDescriptor localEntity, EntityDescriptor peerEntity,
      String providerName, Credential signingCredential, HttpClientInterface httpClient,
      int timeout) {
    this.localEntity = localEntity;
    this.peerEntity = peerEntity;
    this.providerName = providerName;
    this.signingCredential = signingCredential;
    this.httpClient = httpClient;
    this.timeout = timeout;
  }

  /**
   * Get the metadata for this client's local entity.
   *
   * @return The entity descriptor for the local entity.
   */
  public EntityDescriptor getLocalEntity() {
    return localEntity;
  }

  /**
   * Get the metadata for this client's peer entity.
   *
   * @return The entity descriptor for the peer entity.
   */
  public EntityDescriptor getPeerEntity() {
    return peerEntity;
  }

  /**
   * Get the message ID of the most recent request.
   *
   * @return The message ID of the most recent request, never null.
   */
  public String getRequestId() {
    synchronized (requestIdLock) {
      Preconditions.checkNotNull(requestId);
      return requestId;
    }
  }

  /**
   * Get the metadata for this client's local POST assertion consumer service.
   *
   * @return The assertion consumer service descriptor.
   */
  public AssertionConsumerService getPostAssertionConsumerService() {
    return getAssertionConsumerService(SAMLConstants.SAML2_POST_BINDING_URI);
  }

  /**
   * Get the metadata for this client's local ARTIFACT assertion consumer service.
   *
   * @return The assertion consumer service descriptor.
   */
  public AssertionConsumerService getArtifactAssertionConsumerService() {
    return getAssertionConsumerService(SAMLConstants.SAML2_ARTIFACT_BINDING_URI);
  }

  private AssertionConsumerService getAssertionConsumerService(String binding) {
    for (AssertionConsumerService acs :
             localEntity.getSPSSODescriptor(SAML20P_NS).getAssertionConsumerServices()) {
      if (binding.equals(acs.getBinding())) {
        return acs;
      }
    }
    throw new IllegalArgumentException("No assertion consumer with binding " + binding);
  }

  /**
   * Send an AuthnRequest message to the IdP via the redirect protocol.
   *
   * @param response The HTTP response message that will be filled with the encoded redirect.
   * @throws IOException if errors occur during the encoding.
   */
  public void sendAuthnRequest(HTTPOutTransport outTransport)
      throws IOException {
    SAMLMessageContext<SAMLObject, AuthnRequest, NameID> context = makeSamlMessageContext();

    SPSSODescriptor sp = localEntity.getSPSSODescriptor(SAML20P_NS);
    initializeLocalEntity(context, localEntity, sp);
    initializePeerEntity(context, peerEntity, peerEntity.getIDPSSODescriptor(SAML20P_NS),
        SingleSignOnService.DEFAULT_ELEMENT_NAME,
        SAML2_REDIRECT_BINDING_URI);

    // Generate the request
    AuthnRequest authnRequest =
        makeAuthnRequest(context.getOutboundMessageIssuer(), new DateTime());
    authnRequest.setProviderName(providerName);
    authnRequest.setIsPassive(false);
    if (signingCredential != null) {
      authnRequest.setAssertionConsumerServiceURL(
          sp.getDefaultAssertionConsumerService().getLocation());
      authnRequest.setProtocolBinding(
          sp.getDefaultAssertionConsumerService().getBinding());
      // Must sign the message in order for ACS URL to be trusted by peer.
      context.setOutboundSAMLMessageSigningCredential(signingCredential);
    }
    authnRequest.setDestination(context.getPeerEntityEndpoint().getLocation());
    context.setOutboundSAMLMessage(authnRequest);

    // Remember the request ID for later.
    synchronized (requestIdLock) {
      requestId = authnRequest.getID();
    }

    // Not needed:
    //context.setRelayState();

    // Send the request via redirect to the user agent
    //ServletBase.initResponse(response);
    context.setOutboundMessageTransport(outTransport);
    runEncoder(new RedirectEncoder(), context);
  }

  /**
   * Decode a SAML response sent via the artifact binding.
   *
   * @param request The HTTP request containing the artifact.
   * @return The decoded response, or null if there was an error decoding the message.  In
   *     this case, a log file entry is generated, so the caller doesn't need to know the
   *     details of the failure.
   * @throws IOException for various errors related to session and metadata, or for the
   *     artifact resolution interchange.
   */
  public Response decodeArtifactResponse(URI requestUri, HTTPInTransport inTransport)
      throws IOException {
    // The OpenSAML HTTPArtifactDecoder isn't implemented, so we must manually decode the
    // artifact.
    String query = requestUri.getQuery();
    String artifact = null;
    for (String kvPair : query.split("&")) {
      String[] kv = kvPair.split("=", 2);
      if (kv.length != 2) {
        continue;
      }
      if (!"SAMLart".equals(kv[0])) {
        continue;
      }
      artifact = kv[1];
      break;
    }
    if (artifact == null) {
      LOGGER.warning("No artifact in message");
      return null;
    }

    SAMLObject message = resolveArtifact(inTransport, artifact);
    if (!(message instanceof Response)) {
      LOGGER.warning("Unable to resolve artifact");
    }
    return (Response) message;
  }

  /**
   * Resolve a SAML artifact.
   *
   * @param session The authentication session.
   * @param request The HTTP request containing the artifact, to identify the artifact
   *     resolution service to use.
   * @param artifact The artifact to resolve.
   * @return The SAML object that the artifact resolves to.
   * @throws IOException
   */
  private SAMLObject resolveArtifact(HTTPInTransport inTransport,
      String artifact)
      throws IOException {
    // Establish the SAML message context.
    SAMLMessageContext<ArtifactResponse, ArtifactResolve, NameID> context =
        makeSamlMessageContext();

    initializeLocalEntity(context, localEntity, localEntity.getSPSSODescriptor(SAML20P_NS));
    initializePeerEntity(context, peerEntity, peerEntity.getIDPSSODescriptor(SAML20P_NS),
        ArtifactResolutionService.DEFAULT_ELEMENT_NAME,
        SAML2_SOAP11_BINDING_URI);

    // Generate the request.
    context.setOutboundSAMLMessage(
        makeArtifactResolve(localEntity.getEntityID(), new DateTime(), artifact));

    // Encode the request.
    HttpExchange exchange =
        httpClient.postExchange(new URL(context.getPeerEntityEndpoint().getLocation()), null);
    try {
      HttpExchangeToOutTransport out = new HttpExchangeToOutTransport(exchange);
      try {
        context.setOutboundMessageTransport(out);
        context.setRelayState(inTransport.getHeaderValue("RelayState"));
        runEncoder(new HTTPSOAP11Encoder(), context);
      } finally {
        out.finish();
      }
    } finally {
      exchange.close();
    }

    if (timeout != -1) {
       exchange.setTimeout(timeout);
    }

    // Do HTTP exchange.
    int status = exchange.exchange();
    if (status != HttpURLConnection.HTTP_OK) {
      LOGGER.warning("Incorrect HTTP status: " + status);
      return null;
    }

    // Decode the response.
    context.setInboundMessageTransport(new HttpExchangeToInTransport(exchange));
    try {
      runDecoder(new HTTPSOAP11Decoder(), context);
    } catch (IOException e) {
      LOGGER.warning("IOException: " + e.getMessage());
      return null;
    }

    // Return the decoded response.
    ArtifactResponse artifactResponse = context.getInboundSAMLMessage();
    Preconditions.checkNotNull(artifactResponse, "Decoded SAML response is null");
    return artifactResponse.getMessage();
  }

  /**
   * Send a single SAML-standard authorization request.
   *
   * @param urlString The URL for which access is being authorized.
   * @param username The username to test for access.
   * @return The authorization status.
   * @throws IOException if there are any I/O errors during authorization.
   */
  public AuthzStatus sendAuthzRequest(String urlString, String username)
      throws IOException {
    Preconditions.checkNotNull(urlString);
    Preconditions.checkNotNull(username);

    SAMLMessageContext<Response, AuthzDecisionQuery, NameID> context = makeAuthzContext();
    HttpExchange exchange = makeAuthzExchange(context);
    try {

      HttpExchangeToOutTransport out = new HttpExchangeToOutTransport(exchange);
      setupAuthzQuery(context, urlString, username, new DateTime(), out, new HTTPSOAP11Encoder());
      out.finish();

      // Do HTTP exchange
      int status = exchange.exchange();
      if (!isGoodHttpStatus(status)) {
        throw new IOException("Incorrect HTTP status: " + status);
      }

      // Decode the response
      HttpExchangeToInTransport in = new HttpExchangeToInTransport(exchange);
      context.setInboundMessageTransport(in);
      runDecoder(new HTTPSOAP11Decoder(), context);

    } finally {
      exchange.close();
    }

    DecodedAuthzResponse response = decodeAuthzResponse(context.getInboundSAMLMessage(), username);
    if (response == null) {
      return AuthzStatus.INDETERMINATE;
    }
    if (!urlString.equals(response.resource)) {
      throw new IOException("Wrong resource received (expected '" + urlString
          + "'): '" + response.resource + "'");
    }
    return response.status;
  }

  /**
   * Send a (nonstandard) multiple SAML authorization request.
   *
   * @param urlStrings The URLs for which access is being authorized.
   * @param username The username to test for access.
   * @return The authorization responses.
   * @throws IOException if there are any I/O errors during authorization.
   */
  public AuthzResult sendMultiAuthzRequest(Collection<String> urlStrings,
      String username)
      throws IOException {
    Preconditions.checkNotNull(urlStrings);
    Preconditions.checkNotNull(username);

    if (urlStrings.isEmpty()) {
      return AuthzResult.makeIndeterminate(urlStrings);
    }

    // Establish the SAML message context.
    SAMLMessageContext<Response, AuthzDecisionQuery, NameID> context = makeAuthzContext();

    HTTPSOAP11MultiContextEncoder encoder = new HTTPSOAP11MultiContextEncoder();
    HttpExchange exchange = makeAuthzExchange(context);
    try {
      HttpExchangeToOutTransport out = new HttpExchangeToOutTransport(exchange);
      DateTime now = new DateTime();

      for (String urlString : urlStrings) {
        setupAuthzQuery(context, urlString, username, now, out, encoder);
      }
      try {
        encoder.finish();
      } catch (MessageEncodingException e) {
        throw new IOException(e);
      }
      out.finish();

      // Do HTTP exchange
      int status = exchange.exchange();
      if (!isGoodHttpStatus(status)) {
        throw new IOException("Incorrect HTTP status: " + status);
      }

      // Decode the responses
      HttpExchangeToInTransport in = new HttpExchangeToInTransport(exchange);
      context.setInboundMessageTransport(in);
      HTTPSOAP11MultiContextDecoder decoder = new HTTPSOAP11MultiContextDecoder();

      AuthzResult.Builder builder = AuthzResult.builder(urlStrings);
      while (true) {
        try {
          runDecoder(decoder, context);
        } catch (IndexOutOfBoundsException e) {
          // normal indication that there are no more messages to decode
          break;
        }
        DecodedAuthzResponse response
            = decodeAuthzResponse(context.getInboundSAMLMessage(), username);
        if (response != null) {
          if (urlStrings.contains(response.resource)) {
            builder.put(response.resource, response.status);
          } else {
            LOGGER.warning("Unknown resource received: '" + response.resource + "'");
          }
        }
      }
      return builder.build();

    } finally {
      exchange.close();
    }
  }

  private SAMLMessageContext<Response, AuthzDecisionQuery, NameID> makeAuthzContext() {
    // Establish the SAML message context.
    SAMLMessageContext<Response, AuthzDecisionQuery, NameID> context = makeSamlMessageContext();
    initializeLocalEntity(context, localEntity);
    initializePeerEntity(context, peerEntity,
        peerEntity.getPDPDescriptor(SAMLConstants.SAML20P_NS),
        AuthzService.DEFAULT_ELEMENT_NAME,
        SAML2_SOAP11_BINDING_URI);
    return context;
  }

  private HttpExchange makeAuthzExchange(
      SAMLMessageContext<Response, AuthzDecisionQuery, NameID> context)
      throws IOException {
    return httpClient.postExchange(new URL(context.getPeerEntityEndpoint().getLocation()), null);
  }

  private void setupAuthzQuery(SAMLMessageContext<Response, AuthzDecisionQuery, NameID> context,
      String urlString, String username, DateTime instant, HTTPOutTransport out,
      MessageEncoder encoder)
      throws IOException {
    AuthzDecisionQuery query
        = makeAuthzDecisionQuery(
            context.getOutboundMessageIssuer(),
            instant,
            makeSubject(username),
            urlString,
            makeAction(Action.HTTP_GET_ACTION, Action.GHPP_NS_URI));
    LOGGER.info(SamlLogUtil.xmlMessage("AuthzDecisionQuery", query));
    context.setOutboundSAMLMessage(query);
    context.setOutboundMessageTransport(out);
    runEncoder(encoder, context);
  }

  private DecodedAuthzResponse decodeAuthzResponse(Response response, String username)
      throws IOException {
    LOGGER.info(SamlLogUtil.xmlMessage("response", response));

    String statusValue = response.getStatus().getStatusCode().getValue();
    if (!StatusCode.SUCCESS_URI.equals(statusValue)) {
      LOGGER.info("Unsuccessful response received: " + statusValue);
      return null;
    }

    List<Assertion> assertions = response.getAssertions();
    if (assertions.size() != 1) {
      LOGGER.warning("Wrong number of assertions received (expected 1): " + assertions.size());
      return null;
    }
    Assertion assertion = assertions.get(0);

    String responseUsername = assertion.getSubject().getNameID().getValue();
    if (!username.equals(responseUsername)) {
      LOGGER.warning("Wrong username received (expected '" + username + "'): '"
          + responseUsername + "'");
      return null;
    }

    List<Statement> statements = assertion.getStatements();
    if (statements.size() != 1) {
      LOGGER.warning("Wrong number of statements received (expected 1): " + statements.size());
      return null;
    }
    Statement statement = statements.get(0);

    AuthzDecisionStatement authzDecisionStatement = AuthzDecisionStatement.class.cast(statement);
    return new DecodedAuthzResponse(
        authzDecisionStatement.getResource(),
        mapDecision(authzDecisionStatement.getDecision()));
  }

  private static final class DecodedAuthzResponse {
    public final String resource;
    public final AuthzStatus status;

    public DecodedAuthzResponse(String resource, AuthzStatus status) {
      this.resource = resource;
      this.status = status;
    }
  }

  private AuthzStatus mapDecision(DecisionTypeEnumeration decision) {
    if (decision == DecisionTypeEnumeration.PERMIT) {
      return AuthzStatus.PERMIT;
    } else if (decision == DecisionTypeEnumeration.DENY) {
      return AuthzStatus.DENY;
    } else {
      return AuthzStatus.INDETERMINATE;
    }
  }

  /**
   * A tweaked redirect encoder that preserves query parameters from the endpoint URL.
   */
  private static final class RedirectEncoder extends HTTPRedirectDeflateEncoder {

    RedirectEncoder() {
      super();
    }

    @Override
    protected String buildRedirectURL(@SuppressWarnings("rawtypes") SAMLMessageContext context,
        String endpointUrl, String message)
        throws MessageEncodingException {
      String encodedUrl = super.buildRedirectURL(context, endpointUrl, message);

      // Get the query parameters from the endpoint URL.
      List<Pair<String, String>> endpointParams = new URLBuilder(endpointUrl).getQueryParams();
      if (endpointParams.isEmpty()) {
        // If none, we're finished.
        return encodedUrl;
      }

      URLBuilder builder = new URLBuilder(encodedUrl);
      List<Pair<String, String>> samlParams = builder.getQueryParams();

      // Merge the endpoint params with the SAML params.
      Map<String, String> params = Maps.newHashMap();
      for (Pair<String, String> entry : endpointParams) {
        params.put(entry.getFirst(), entry.getSecond());
      }
      for (Pair<String, String> entry : samlParams) {
        params.put(entry.getFirst(), entry.getSecond());
      }

      // Copy the merged params back into the result.
      samlParams.clear();
      for (Map.Entry<String, String> entry : params.entrySet()) {
        samlParams.add(new Pair<String, String>(entry.getKey(), entry.getValue()));
      }
      return builder.buildURL();
    }
  }

  public static boolean isGoodHttpStatus(int status) {
    return status == HttpURLConnection.HTTP_OK
        || status == HttpURLConnection.HTTP_PARTIAL;
  }
}
