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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.enterprise.adaptor.SamlServiceProvider.AuthnState;
import com.google.enterprise.adaptor.secmgr.http.HttpClientInterface;
import com.google.enterprise.adaptor.secmgr.modules.SamlClient;
import com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil;

import com.sun.net.httpserver.HttpExchange;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.binding.decoding.HTTPRedirectDeflateDecoder;
import org.opensaml.saml2.binding.encoding.HTTPSOAP11Encoder;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.NameID;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test cases for {@link SamlServiceProvider}.
 */
public class SamlServiceProviderTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Charset charset = Charset.forName("UTF-8");
  private SessionManager<HttpExchange> sessionManager
      = new SessionManager<HttpExchange>(new MockTimeProvider(),
          new SessionManager.HttpExchangeClientStore(), 1000, 1000);
  private SamlMetadata metadata = new SamlMetadata("localhost", 80,
      "thegsa", "http://google.com/enterprise/gsa/security-manager",
      "http://google.com/enterprise/gsa/adaptor");
  private HttpClientAdapter httpClient = new HttpClientAdapter();
  private SamlServiceProvider serviceProvider = new SamlServiceProvider(
      sessionManager, metadata, null, httpClient);
  private MockHttpExchange ex = new MockHttpExchange("GET", "/",
      new MockHttpContext("/"));
  private MockHttpExchange exArtifact
      = new MockHttpExchange("GET", "/?SAMLart=1234someid5678",
          new MockHttpContext("/"));
  private MockHttpExchange initialEx
      = new MockHttpExchange("GET", "/doc/someid",
          new MockHttpContext("/doc/"));

  private static final String GOLDEN_ARTIFACT_RESOLVE_REQUEST
      = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<soap11:Envelope "
      +   "xmlns:soap11=\"http://schemas.xmlsoap.org/soap/envelope/\">"
      +   "<soap11:Body>"
      +     "<saml2p:ArtifactResolve "
      +       "ID=\"someid\" "
      +       "IssueInstant=\"sometime\" "
      +       "Version=\"2.0\" "
      +       "xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
      +       "<saml2:Issuer "
      +         "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
      +         "http://google.com/enterprise/gsa/adaptor"
      +       "</saml2:Issuer>"
      +       "<saml2p:Artifact>"
      +         "1234someid5678"
      +       "</saml2p:Artifact>"
      +     "</saml2p:ArtifactResolve>"
      +   "</soap11:Body>"
      + "</soap11:Envelope>";

  @BeforeClass
  public static void initSaml() {
    GsaCommunicationHandler.bootstrapOpenSaml();
  }

  @Test
  public void testHandleAuthenticationNewSession() throws Exception {
    String goldenResponse
        = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<soap11:Envelope "
        +   "xmlns:soap11=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        +   "<soap11:Body>"
        +     "<saml2p:AuthnRequest "
        +       "Destination=\"https://thegsa/security-manager/samlauthn\" "
        +       "ID=\"someid\" "
        +       "IsPassive=\"false\" "
        +       "IssueInstant=\"sometime\" "
        +       "ProviderName=\"GSA Adaptor\" "
        +       "Version=\"2.0\" "
        +       "xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +       "<saml2:Issuer "
        +         "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
        +         "http://google.com/enterprise/gsa/adaptor"
        +       "</saml2:Issuer>"
        +     "</saml2p:AuthnRequest>"
        +   "</soap11:Body>"
        + "</soap11:Envelope>";

    serviceProvider.handleAuthentication(ex);

    //
    // We have run the method, now we need to look over the results...
    //
    assertEquals(307, ex.getResponseCode());
    URI uri = new URI(ex.getResponseHeaders().getFirst("Location"));
    assertEquals("https", uri.getScheme());
    assertEquals("thegsa", uri.getHost());
    assertEquals("/security-manager/samlauthn", uri.getPath());
    assertTrue(!isAuthned(ex));

    // Act like we are the receiving end of the communication.
    MockHttpExchange remoteEx = new MockHttpExchange("GET", uri.toString(),
        new MockHttpContext("/security-manager/samlauthn"));

    SAMLMessageContext<AuthnRequest, AuthnRequest, NameID> context
        = OpenSamlUtil.makeSamlMessageContext();
    OpenSamlUtil.initializeLocalEntity(context, metadata.getPeerEntity(),
        metadata.getPeerEntity().getIDPSSODescriptor(SAMLConstants.SAML20P_NS));
    context.setInboundMessageTransport(new EnhancedInTransport(remoteEx));
    context.setOutboundMessageTransport(
        new HttpExchangeOutTransportAdapter(remoteEx));

    HTTPRedirectDeflateDecoder decoder = new RedirectDecoder();
    decoder.decode(context);
    context.setOutboundSAMLMessage(context.getInboundSAMLMessage());

    HTTPSOAP11Encoder encoder = new HTTPSOAP11Encoder();
    encoder.encode(context);

    String response = new String(remoteEx.getResponseBytes(), "UTF-8");
    response = massageMessage(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testHandleAuthenticationAlreadyAuthned() throws Exception {
    // Create a new authenticated session for this HttpExchange.
    Session session = sessionManager.getSession(ex, true);
    AuthnState authn = new AuthnState();
    session.setAttribute(SamlServiceProvider.SESSION_STATE_ATTR_NAME, authn);
    AuthnIdentity identity = new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build();
    authn.authenticated(identity, Long.MAX_VALUE);
    serviceProvider.handleAuthentication(ex);
    // Still should cause them to go through authn
    assertEquals(307, ex.getResponseCode());
    assertTrue(!isAuthned(ex));
  }

  @Test
  public void testHandleAuthenticationHead() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("HEAD", "/",
                                               new MockHttpContext("/"));
    serviceProvider.handleAuthentication(ex);
    assertEquals(307, ex.getResponseCode());
    assertTrue(!isAuthned(ex));
  }

  @Test
  public void testHandleAuthenticationBadMethod() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
                                               new MockHttpContext("/"));
    serviceProvider.handleAuthentication(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testAssertionConsumerNormal() throws Exception {
    SamlHttpClient httpClient = new SamlHttpClient() {
      @Override
      protected void handleExchange(ClientExchange ex) {
        String body = new String(ex.getRequestBody(), charset);
        body = massageMessage(body);
        assertEquals(GOLDEN_ARTIFACT_RESOLVE_REQUEST, body);

        // Generate valid response.
        String issuer = metadata.getPeerEntity().getEntityID();
        String recipient = metadata.getLocalEntity()
            .getSPSSODescriptor(SAMLConstants.SAML20P_NS)
            .getAssertionConsumerServices().get(0).getLocation();
        String audience = metadata.getLocalEntity().getEntityID();
        String response
            = "<SOAP-ENV:Envelope "
            +   "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            +   "<SOAP-ENV:Body>"
            +     "<samlp:ArtifactResponse "
            +       "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            +       "xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
            +       "ID=\"someid1\" Version=\"2.0\" "
            +       "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +       "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +       "<Issuer>" + issuer + "</Issuer>"
            +       "<samlp:Status>"
            +         "<samlp:StatusCode "
            +           "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +       "</samlp:Status>"
            +       "<samlp:Response "
            +         "ID=\"someid2\" "
            +         "Version=\"2.0\" "
            +         "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +         "<samlp:Status>"
            +           "<samlp:StatusCode "
            +             "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +         "</samlp:Status>"
            +         "<Assertion "
            +           "Version=\"2.0\" "
            +           "ID=\"someid3\" "
            +           "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +           "<Issuer>" + issuer + "</Issuer>"
            +           "<Subject>"
            +             "<NameID>CN=Polly Hedra</NameID>"
            +             "<SubjectConfirmation "
            +               "Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
            +               "<SubjectConfirmationData "
            +                 "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +                 "Recipient=\"" + recipient + "\" "
            +                 "NotOnOrAfter=\"2030-01-01T01:01:01Z\"/>"
            +             "</SubjectConfirmation>"
            +           "</Subject>"
            +           "<Conditions "
            +             "NotBefore=\"2010-01-01T01:01:01Z\">"
            +             "<AudienceRestriction>"
            +               "<Audience>" + audience + "</Audience>"
            +             "</AudienceRestriction>"
            +           "</Conditions>"
            +           "<AuthnStatement "
            +             "AuthnInstant=\"2010-01-01T01:01:01Z\"/>"
            +         "</Assertion>"
            +       "</samlp:Response>"
            +     "</samlp:ArtifactResponse>"
            +   "</SOAP-ENV:Body>"
            + "</SOAP-ENV:Envelope>";
        ex.setStatusCode(200);
        ex.setResponseStream(response.getBytes(charset));
      }
    };
    SamlClient samlClient = createSamlClient(httpClient);
    httpClient.setSamlClient(samlClient);
    issueRequest(exArtifact, initialEx, samlClient);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(303, exArtifact.getResponseCode());
    assertTrue(isAuthned(exArtifact));
    AuthnIdentity identity = serviceProvider.getUserIdentity(exArtifact);
    assertEquals("CN=Polly Hedra", identity.getUser().getName());
    assertNull(identity.getGroups());
    assertNull(identity.getPassword());
  }

  @Test
  public void testAssertionConsumerNormalWithExtension() throws Exception {
    SamlHttpClient httpClient = new SamlHttpClient() {
      @Override
      protected void handleExchange(ClientExchange ex) {
        String body = new String(ex.getRequestBody(), charset);
        body = massageMessage(body);
        assertEquals(GOLDEN_ARTIFACT_RESOLVE_REQUEST, body);

        // Generate valid response.
        String issuer = metadata.getPeerEntity().getEntityID();
        String recipient = metadata.getLocalEntity()
            .getSPSSODescriptor(SAMLConstants.SAML20P_NS)
            .getAssertionConsumerServices().get(0).getLocation();
        String audience = metadata.getLocalEntity().getEntityID();
        String response
            = "<SOAP-ENV:Envelope "
            +   "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            +   "<SOAP-ENV:Body>"
            +     "<samlp:ArtifactResponse "
            +       "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            +       "xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
            +       "ID=\"someid1\" Version=\"2.0\" "
            +       "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +       "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +       "<Issuer>" + issuer + "</Issuer>"
            +       "<samlp:Status>"
            +         "<samlp:StatusCode "
            +           "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +       "</samlp:Status>"
            +       "<samlp:Response "
            +         "ID=\"someid2\" "
            +         "Version=\"2.0\" "
            +         "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +         "<samlp:Status>"
            +           "<samlp:StatusCode "
            +             "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +         "</samlp:Status>"
            +         "<Assertion "
            +           "Version=\"2.0\" "
            +           "ID=\"someid3\" "
            +           "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +           "<Issuer>" + issuer + "</Issuer>"
            +           "<Subject>"
            +             "<NameID>CN=Polly Hedra</NameID>"
            +             "<SubjectConfirmation "
            +               "Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
            +               "<SubjectConfirmationData "
            +                 "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +                 "Recipient=\"" + recipient + "\" "
            +                 "NotOnOrAfter=\"2030-01-01T01:01:01Z\"/>"
            +             "</SubjectConfirmation>"
            +           "</Subject>"
            +           "<Conditions "
            +             "NotBefore=\"2010-01-01T01:01:01Z\">"
            +             "<AudienceRestriction>"
            +               "<Audience>" + audience + "</Audience>"
            +             "</AudienceRestriction>"
            +           "</Conditions>"
            +           "<AuthnStatement "
            +             "AuthnInstant=\"2010-01-01T01:01:01Z\">"
            +             "<AuthnContext>"
            +               "<AuthnContextClassRef>"
            +                 "urn:oasis:names:tc:SAML:2.0:ac:classes:InternetProtocolPassword"
            +               "</AuthnContextClassRef>"
            +             "</AuthnContext>"
            +           "</AuthnStatement>"
            +           "<AttributeStatement>"
            +             "<Attribute Name=\"SecurityManagerState\">"
            +               "<AttributeValue>"
            + "{"
            +   "\"version\": 1,"
            +   "\"timeStamp\": 1330042321589,"
            +   "\"sessionState\": {"
            +     "\"instructions\": ["
            +       "{"
            +         "\"operation\": \"ADD_CREDENTIAL\","
            +         "\"authority\": "
            + "\"http://google.com/enterprise/gsa/security-manager/Default\","
            +         "\"operand\": {"
            +           "\"name\": \"CN=Polly Hedra\","
            +           "\"typeName\": \"AuthnPrincipal\""
            +         "}"
            +       "},"
            +       "{"
            +         "\"operation\": \"ADD_CREDENTIAL\","
            +         "\"authority\": "
            + "\"http://google.com/enterprise/gsa/security-manager/Default\","
            +         "\"operand\": {"
            +           "\"password\": \"p0ck3t\","
            +           "\"typeName\": \"CredPassword\""
            +         "}"
            +       "},"
            +       "{"
            +         "\"operation\": \"ADD_VERIFICATION\","
            +         "\"authority\": "
            + "\"http://google.com/enterprise/gsa/security-manager/adaptor\","
            +         "\"operand\": {"
            +           "\"status\": \"VERIFIED\","
            +           "\"expirationTime\": 1330043521581,"
            +           "\"credentials\": ["
            +             "{"
            +               "\"name\": \"CN=Polly Hedra\","
            +               "\"typeName\": \"AuthnPrincipal\""
            +             "},"
            +             "{"
            +               "\"password\": \"p0ck3t\","
            +               "\"typeName\": \"CredPassword\""
            +             "}"
            +           "]"
            +         "}"
            +       "}"
            +     "]"
            +   "},"
            +   "\"pviCredentials\": {"
            +     "\"username\": \"CN=Polly HedraNot\","
            +     "\"password\": \"p0ck3t\","
            +     "\"groups\": ["
            +     "]"
            +   "},"
            +   "\"basicCredentials\": {"
            +     "\"username\": \"CN=Polly Hedra\","
            +     "\"password\": \"p0ck3t\","
            +     "\"groups\": ["
            +     "]"
            +   "},"
            +   "\"verifiedCredentials\": ["
            +     "{"
            +       "\"username\": \"CN=Polly HedraYes\","
            +       "\"password\": \"p0ck3t\","
            +       "\"groups\": ["
            +         "{"
            +           "\"name\": \"group1\","
            +           "\"namespace\": \"Default\","
            +           "\"domain\": \"test.com\""
            +         "},"
            +         "{"
            +           "\"name\": \"pollysGroup\","
            +           "\"namespace\": \"Default\","
            +           "\"domain\": \"test.com\""
            +         "}"
            +       "]"
            +     "}"
            +   "],"
            +   "\"cookies\": []"
            + "}"
            +               "</AttributeValue>"
            +             "</Attribute>"
            +           "</AttributeStatement>"
            +         "</Assertion>"
            +       "</samlp:Response>"
            +     "</samlp:ArtifactResponse>"
            +   "</SOAP-ENV:Body>"
            + "</SOAP-ENV:Envelope>";
        ex.setStatusCode(200);
        ex.setResponseStream(response.getBytes(charset));
      }
    };
    SamlClient samlClient = createSamlClient(httpClient);
    httpClient.setSamlClient(samlClient);
    issueRequest(exArtifact, initialEx, samlClient);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(303, exArtifact.getResponseCode());
    assertTrue(isAuthned(exArtifact));
    AuthnIdentity identity = serviceProvider.getUserIdentity(exArtifact);
    assertEquals("CN=Polly HedraYes", identity.getUser().getName());
    // Make sure that the information from the extensions was parsed out and
    // made available for later use.
    Set<String> groups = new HashSet<String>();
    groups.add("group1@test.com");
    groups.add("pollysGroup@test.com");
    assertEquals(GroupPrincipal.makeSet(groups), identity.getGroups());
    assertEquals("p0ck3t", identity.getPassword());
  }

  @Test
  public void testAssertionConsumerAuthnFailure() throws Exception {
    SamlHttpClient httpClient = new SamlHttpClient() {
      @Override
      protected void handleExchange(ClientExchange ex) {
        String body = new String(ex.getRequestBody(), charset);
        body = massageMessage(body);
        assertEquals(GOLDEN_ARTIFACT_RESOLVE_REQUEST, body);

        // Generate valid response for authn failed.
        String issuer = metadata.getPeerEntity().getEntityID();
        String response
            = "<SOAP-ENV:Envelope "
            +   "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            +   "<SOAP-ENV:Body>"
            +     "<samlp:ArtifactResponse "
            +       "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            +       "xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
            +       "ID=\"someid1\" Version=\"2.0\" "
            +       "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +       "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +       "<Issuer>" + issuer + "</Issuer>"
            +       "<samlp:Status>"
            +         "<samlp:StatusCode "
            +           "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +       "</samlp:Status>"
            +       "<samlp:Response "
            +         "ID=\"someid2\" "
            +         "Version=\"2.0\" "
            +         "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +         "<samlp:Status>"
            +           "<samlp:StatusCode "
            +             "Value=\"urn:oasis:names:tc:SAML:2.0:status:AuthnFailed\"/>"
            +         "</samlp:Status>"
            +       "</samlp:Response>"
            +     "</samlp:ArtifactResponse>"
            +   "</SOAP-ENV:Body>"
            + "</SOAP-ENV:Envelope>";
        ex.setStatusCode(200);
        ex.setResponseStream(response.getBytes(charset));
      }
    };
    SamlClient samlClient = createSamlClient(httpClient);
    httpClient.setSamlClient(samlClient);
    issueRequest(exArtifact, initialEx, samlClient);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(403, exArtifact.getResponseCode());
    assertTrue(!isAuthned(exArtifact));
  }

  @Test
  public void testAssertionConsumerNoAuthnResponse() throws Exception {
    SamlHttpClient httpClient = new SamlHttpClient() {
      @Override
      protected void handleExchange(ClientExchange ex) {
        String body = new String(ex.getRequestBody(), charset);
        body = massageMessage(body);
        assertEquals(GOLDEN_ARTIFACT_RESOLVE_REQUEST, body);

        // Generate valid response for artifact requesting failure.
        String issuer = metadata.getPeerEntity().getEntityID();
        String response
            = "<SOAP-ENV:Envelope "
            +   "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            +   "<SOAP-ENV:Body>"
            +     "<samlp:ArtifactResponse "
            +       "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            +       "xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
            +       "ID=\"someid1\" Version=\"2.0\" "
            +       "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +       "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +       "<Issuer>" + issuer + "</Issuer>"
            +       "<samlp:Status>"
            +         "<samlp:StatusCode "
            +           "Value=\"urn:oasis:names:tc:SAML:2.0:status:Requester\"/>"
            +       "</samlp:Status>"
            +     "</samlp:ArtifactResponse>"
            +   "</SOAP-ENV:Body>"
            + "</SOAP-ENV:Envelope>";
        ex.setStatusCode(200);
        ex.setResponseStream(response.getBytes(charset));
      }
    };
    SamlClient samlClient = createSamlClient(httpClient);
    httpClient.setSamlClient(samlClient);
    issueRequest(exArtifact, initialEx, samlClient);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(403, exArtifact.getResponseCode());
    assertTrue(!isAuthned(exArtifact));
  }

  @Test
  public void testAssertionConsumerWrongResponse() throws Exception {
    SamlHttpClient httpClient = new SamlHttpClient() {
      @Override
      protected void handleExchange(ClientExchange ex) {
        String body = new String(ex.getRequestBody(), charset);
        body = massageMessage(body);
        assertEquals(GOLDEN_ARTIFACT_RESOLVE_REQUEST, body);

        // Generate valid response for successful authn, but for the wrong
        // request.
        String issuer = metadata.getPeerEntity().getEntityID();
        String recipient = metadata.getLocalEntity()
            .getSPSSODescriptor(SAMLConstants.SAML20P_NS)
            .getAssertionConsumerServices().get(0).getLocation();
        String audience = metadata.getLocalEntity().getEntityID();
        String response
            = "<SOAP-ENV:Envelope "
            +   "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            +   "<SOAP-ENV:Body>"
            +     "<samlp:ArtifactResponse "
            +       "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            +       "xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
            +       "ID=\"someid1\" Version=\"2.0\" "
            +       "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +       "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +       "<Issuer>" + issuer + "</Issuer>"
            +       "<samlp:Status>"
            +         "<samlp:StatusCode "
            +           "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +       "</samlp:Status>"
            +       "<samlp:Response "
            +         "ID=\"someid2\" "
            +         "Version=\"2.0\" "
            +         "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +         "<samlp:Status>"
            +           "<samlp:StatusCode "
            +             "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +         "</samlp:Status>"
            +         "<Assertion "
            +           "Version=\"2.0\" "
            +           "ID=\"someid3\" "
            +           "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +           "<Issuer>" + issuer + "</Issuer>"
            +           "<Subject>"
            +             "<NameID>CN=Polly Hedra</NameID>"
            +             "<SubjectConfirmation "
            +               "Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
            +               "<SubjectConfirmationData "
            +                 "InResponseTo=\"notthis" + samlClient.getRequestId() + "\" "
            +                 "Recipient=\"" + recipient + "\" "
            +                 "NotOnOrAfter=\"2030-01-01T01:01:01Z\"/>"
            +             "</SubjectConfirmation>"
            +           "</Subject>"
            +           "<Conditions "
            +             "NotBefore=\"2010-01-01T01:01:01Z\">"
            +             "<AudienceRestriction>"
            +               "<Audience>" + audience + "</Audience>"
            +             "</AudienceRestriction>"
            +           "</Conditions>"
            +           "<AuthnStatement "
            +             "AuthnInstant=\"2010-01-01T01:01:01Z\"/>"
            +         "</Assertion>"
            +       "</samlp:Response>"
            +     "</samlp:ArtifactResponse>"
            +   "</SOAP-ENV:Body>"
            + "</SOAP-ENV:Envelope>";
        ex.setStatusCode(200);
        ex.setResponseStream(response.getBytes(charset));
      }
    };
    SamlClient samlClient = createSamlClient(httpClient);
    httpClient.setSamlClient(samlClient);
    issueRequest(exArtifact, initialEx, samlClient);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(403, exArtifact.getResponseCode());
    assertTrue(!isAuthned(exArtifact));
  }

  @Test
  public void testAssertionConsumerWrongIssuer() throws Exception {
    SamlHttpClient httpClient = new SamlHttpClient() {
      @Override
      protected void handleExchange(ClientExchange ex) {
        String body = new String(ex.getRequestBody(), charset);
        body = massageMessage(body);
        assertEquals(GOLDEN_ARTIFACT_RESOLVE_REQUEST, body);

        // Generate valid response, but from wrong issuer.
        String issuer = metadata.getPeerEntity().getEntityID();
        String recipient = metadata.getLocalEntity()
            .getSPSSODescriptor(SAMLConstants.SAML20P_NS)
            .getAssertionConsumerServices().get(0).getLocation();
        String audience = metadata.getLocalEntity().getEntityID();
        String response
            = "<SOAP-ENV:Envelope "
            +   "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            +   "<SOAP-ENV:Body>"
            +     "<samlp:ArtifactResponse "
            +       "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            +       "xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
            +       "ID=\"someid1\" Version=\"2.0\" "
            +       "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +       "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +       "<Issuer>" + issuer + "</Issuer>"
            +       "<samlp:Status>"
            +         "<samlp:StatusCode "
            +           "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +       "</samlp:Status>"
            +       "<samlp:Response "
            +         "ID=\"someid2\" "
            +         "Version=\"2.0\" "
            +         "IssueInstant=\"2010-01-01T01:01:01Z\">"
            // Add an issuer that is not the expected one.
            +         "<Issuer>notthexpected" + issuer + "</Issuer>"
            +         "<samlp:Status>"
            +           "<samlp:StatusCode "
            +             "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +         "</samlp:Status>"
            +         "<Assertion "
            +           "Version=\"2.0\" "
            +           "ID=\"someid3\" "
            +           "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +           "<Issuer>" + issuer + "</Issuer>"
            +           "<Subject>"
            +             "<NameID>CN=Polly Hedra</NameID>"
            +             "<SubjectConfirmation "
            +               "Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
            +               "<SubjectConfirmationData "
            +                 "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +                 "Recipient=\"" + recipient + "\" "
            +                 "NotOnOrAfter=\"2030-01-01T01:01:01Z\"/>"
            +             "</SubjectConfirmation>"
            +           "</Subject>"
            +           "<Conditions "
            +             "NotBefore=\"2010-01-01T01:01:01Z\">"
            +             "<AudienceRestriction>"
            +               "<Audience>" + audience + "</Audience>"
            +             "</AudienceRestriction>"
            +           "</Conditions>"
            +           "<AuthnStatement "
            +             "AuthnInstant=\"2010-01-01T01:01:01Z\"/>"
            +         "</Assertion>"
            +       "</samlp:Response>"
            +     "</samlp:ArtifactResponse>"
            +   "</SOAP-ENV:Body>"
            + "</SOAP-ENV:Envelope>";
        ex.setStatusCode(200);
        ex.setResponseStream(response.getBytes(charset));
      }
    };
    SamlClient samlClient = createSamlClient(httpClient);
    httpClient.setSamlClient(samlClient);
    issueRequest(exArtifact, initialEx, samlClient);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(403, exArtifact.getResponseCode());
    assertTrue(!isAuthned(exArtifact));
  }

  @Test
  public void testAssertionConsumerAlreadyAuthned() throws Exception {
    MockHttpClient httpClient = new MockHttpClient() {
      @Override
      protected void handleExchange(ClientExchange ex) {
        fail("No request should have been issued.");
      }
    };
    SamlClient samlClient = createSamlClient(httpClient);
    issueRequest(exArtifact, initialEx, samlClient);

    // Authenticate the session.
    Session session = sessionManager.getSession(exArtifact, false);
    AuthnState authn = (AuthnState) session
        .getAttribute(SamlServiceProvider.SESSION_STATE_ATTR_NAME);
    AuthnIdentity identity = new AuthnIdentityImpl
       .Builder(new UserPrincipal("test")).build();
    authn.authenticated(identity, Long.MAX_VALUE);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(409, exArtifact.getResponseCode());
    assertTrue(isAuthned(exArtifact));
  }

  @Test
  public void testAssertionConsumerNoSession() throws Exception {
    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(409, exArtifact.getResponseCode());
    assertTrue(!isAuthned(exArtifact));
  }

  @Test
  public void testAssertionConsumerUnrequestedAuthnResponse() throws Exception {
    AuthnState authnState = new AuthnState();
    sessionManager.getSession(exArtifact).setAttribute(
        SamlServiceProvider.SESSION_STATE_ATTR_NAME, authnState);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(500, exArtifact.getResponseCode());
    assertTrue(!isAuthned(exArtifact));
  }

  @Test
  public void testAssertionConsumerPost() throws Exception {
    MockHttpExchange ex
        = new MockHttpExchange("POST", "/?SAMLart=1234someid5678",
                               new MockHttpContext("/"));
    serviceProvider.getAssertionConsumer().handle(ex);
    assertEquals(405, ex.getResponseCode());
    assertTrue(!isAuthned(ex));
  }

  @Test
  public void testVerifiedUserOverridesSubject() throws Exception {
    SamlHttpClient httpClient = new SamlHttpClient() {
      @Override
      protected void handleExchange(ClientExchange ex) {
        String body = new String(ex.getRequestBody(), charset);
        body = massageMessage(body);
        assertEquals(GOLDEN_ARTIFACT_RESOLVE_REQUEST, body);

        // Generate valid response.
        String issuer = metadata.getPeerEntity().getEntityID();
        String recipient = metadata.getLocalEntity()
            .getSPSSODescriptor(SAMLConstants.SAML20P_NS)
            .getAssertionConsumerServices().get(0).getLocation();
        String audience = metadata.getLocalEntity().getEntityID();
        String response
            = "<SOAP-ENV:Envelope "
            +   "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            +   "<SOAP-ENV:Body>"
            +     "<samlp:ArtifactResponse "
            +       "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            +       "xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
            +       "ID=\"someid1\" Version=\"2.0\" "
            +       "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +       "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +       "<Issuer>" + issuer + "</Issuer>"
            +       "<samlp:Status>"
            +         "<samlp:StatusCode "
            +           "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +       "</samlp:Status>"
            +       "<samlp:Response "
            +         "ID=\"someid2\" "
            +         "Version=\"2.0\" "
            +         "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +         "<samlp:Status>"
            +           "<samlp:StatusCode "
            +             "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
            +         "</samlp:Status>"
            +         "<Assertion "
            +           "Version=\"2.0\" "
            +           "ID=\"someid3\" "
            +           "IssueInstant=\"2010-01-01T01:01:01Z\">"
            +           "<Issuer>" + issuer + "</Issuer>"
            +           "<Subject>"
            +             "<NameID>CN=Polly Hedra</NameID>"
            +             "<SubjectConfirmation "
            +               "Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
            +               "<SubjectConfirmationData "
            +                 "InResponseTo=\"" + samlClient.getRequestId() + "\" "
            +                 "Recipient=\"" + recipient + "\" "
            +                 "NotOnOrAfter=\"2030-01-01T01:01:01Z\"/>"
            +             "</SubjectConfirmation>"
            +           "</Subject>"
            +           "<Conditions "
            +             "NotBefore=\"2010-01-01T01:01:01Z\">"
            +             "<AudienceRestriction>"
            +               "<Audience>" + audience + "</Audience>"
            +             "</AudienceRestriction>"
            +           "</Conditions>"
            +           "<AuthnStatement "
            +             "AuthnInstant=\"2010-01-01T01:01:01Z\">"
            +             "<AuthnContext>"
            +               "<AuthnContextClassRef>"
            +                 "urn:oasis:names:tc:SAML:2.0:ac:classes:InternetProtocolPassword"
            +               "</AuthnContextClassRef>"
            +             "</AuthnContext>"
            +           "</AuthnStatement>"
            +           "<AttributeStatement>"
            +             "<Attribute Name=\"SecurityManagerState\">"
            +               "<AttributeValue>"
            + "{"
            +   "\"version\": 1,"
            +   "\"timeStamp\": 1330042321589,"
            +   "\"sessionState\": {"
            +     "\"instructions\": ["
            +       "{"
            +         "\"operation\": \"ADD_CREDENTIAL\","
            +         "\"authority\": "
            + "\"http://google.com/enterprise/gsa/security-manager/Default\","
            +         "\"operand\": {"
            +           "\"name\": \"CN=Polly Hedra\","
            +           "\"typeName\": \"AuthnPrincipal\""
            +         "}"
            +       "},"
            +       "{"
            +         "\"operation\": \"ADD_CREDENTIAL\","
            +         "\"authority\": "
            + "\"http://google.com/enterprise/gsa/security-manager/Default\","
            +         "\"operand\": {"
            +           "\"password\": \"p0ck3t\","
            +           "\"typeName\": \"CredPassword\""
            +         "}"
            +       "},"
            +       "{"
            +         "\"operation\": \"ADD_VERIFICATION\","
            +         "\"authority\": "
            + "\"http://google.com/enterprise/gsa/security-manager/adaptor\","
            +         "\"operand\": {"
            +           "\"status\": \"VERIFIED\","
            +           "\"expirationTime\": 1330043521581,"
            +           "\"credentials\": ["
            +             "{"
            +               "\"name\": \"CN=Polly Hedra\","
            +               "\"typeName\": \"AuthnPrincipal\""
            +             "},"
            +             "{"
            +               "\"password\": \"p0ck3t\","
            +               "\"typeName\": \"CredPassword\""
            +             "}"
            +           "]"
            +         "}"
            +       "}"
            +     "]"
            +   "},"
            +   "\"pviCredentials\": {"
            +     "\"username\": \"CN=Polly Hedra\","
            +     "\"password\": \"p0ck3t\","
            +     "\"groups\": ["
            +     "]"
            +   "},"
            +   "\"basicCredentials\": {"
            +     "\"username\": \"not-used\","
            +     "\"domain\": \"also-not-used\","
            +     "\"password\": \"p0ck3t\","
            +     "\"groups\": ["
            +     "]"
            +   "},"
            +   "\"verifiedCredentials\": ["
            +     "{"
            +       "\"username\": \"whale\","
            +       "\"domain\": \"ahab.net\","
            +       "\"password\": \"p0ck3t\","
            +       "\"groups\": ["
            +         "{"
            +           "\"name\": \"group1\","
            +           "\"namespace\": \"Default\","
            +           "\"domain\": \"test.com\""
            +         "},"
            +         "{"
            +           "\"name\": \"pollysGroup\","
            +           "\"namespace\": \"Default\","
            +           "\"domain\": \"test.com\""
            +         "}"
            +       "]"
            +     "}"
            +   "],"
            +   "\"cookies\": []"
            + "}"
            +               "</AttributeValue>"
            +             "</Attribute>"
            +           "</AttributeStatement>"
            +         "</Assertion>"
            +       "</samlp:Response>"
            +     "</samlp:ArtifactResponse>"
            +   "</SOAP-ENV:Body>"
            + "</SOAP-ENV:Envelope>";
        ex.setStatusCode(200);
        ex.setResponseStream(response.getBytes(charset));
      }
    };
    SamlClient samlClient = createSamlClient(httpClient);
    httpClient.setSamlClient(samlClient);
    issueRequest(exArtifact, initialEx, samlClient);

    serviceProvider.getAssertionConsumer().handle(exArtifact);
    assertEquals(303, exArtifact.getResponseCode());
    assertTrue(isAuthned(exArtifact));
    AuthnIdentity identity = serviceProvider.getUserIdentity(exArtifact);
    assertEquals("whale@ahab.net", identity.getUser().getName());
  }

  private SamlClient createSamlClient(HttpClientInterface httpClient) {
    return new SamlClient(metadata.getLocalEntity(), metadata.getPeerEntity(),
                          "Testing", null, httpClient);
  }

  private void issueRequest(HttpExchange ex, HttpExchange initialEx,
                            SamlClient samlClient) throws IOException {
    AuthnState authnState = new AuthnState();
    authnState.startAttempt(samlClient, ex.getRequestURI());
    sessionManager.getSession(ex).setAttribute(
        SamlServiceProvider.SESSION_STATE_ATTR_NAME, authnState);
    // Used to generate a request id.
    samlClient.sendAuthnRequest(new HttpExchangeOutTransportAdapter(initialEx));
  }

  private boolean isAuthned(HttpExchange ex) {
    return serviceProvider.getUserIdentity(ex) != null;
  }

  private String massageMessage(String message) {
    return message.replaceAll("ID=\"[^\"]+\"", "ID=\"someid\"")
        .replaceAll("IssueInstant=\"[^\"]+\"", "IssueInstant=\"sometime\"");
  }

  private static class EnhancedInTransport
      extends HttpExchangeInTransportAdapter {
    private Map<String, List<String>> parameters;

    public EnhancedInTransport(HttpExchange ex) {
      super(ex);
      parameters = parseParameters();
    }

    private Map<String, List<String>> parseParameters() {
      Map<String, List<String>> params = new HashMap<String, List<String>>();
      String query = ex.getRequestURI().getQuery();
      if (query == null) {
        return params;
      }
      // This is not fully correct, but good enough for the test case.
      for (String param : query.split("&")) {
        String[] split = query.split("=", 2);
        if (!params.containsKey(split[0])) {
          params.put(split[0], new LinkedList<String>());
        }
        params.get(split[0]).add(split.length == 2 ? split[1] : "");
      }
      return params;
    }

    @Override
    public String getParameterValue(String name) {
      List<String> values = getParameterValues(name);
      return values.size() == 0 ? null : values.get(0);
    }

    @Override
    public List<String> getParameterValues(String name) {
      if (parameters.containsKey(name)) {
        return parameters.get(name);
      } else {
        return Collections.emptyList();
      }
    }

    public HttpExchange getExchange() {
      return ex;
    }
  }

  private static class RedirectDecoder extends HTTPRedirectDeflateDecoder {
    @Override
    protected String getActualReceiverEndpointURI(
        SAMLMessageContext messageContext) {
      EnhancedInTransport inTransport = (EnhancedInTransport) messageContext
          .getInboundMessageTransport();
      String requestUri = inTransport.getExchange().getRequestURI().toString();
      // Remove query from URI
      return requestUri.split("\\?", 2)[0];
    }
  }

  private abstract static class SamlHttpClient extends MockHttpClient {
    protected SamlClient samlClient;

    public void setSamlClient(SamlClient samlClient) {
      this.samlClient = samlClient;
    }
  }
}
