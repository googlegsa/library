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

import com.google.enterprise.adaptor.AuthnHandler;
import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.AuthnIdentityImpl;
import com.google.enterprise.adaptor.AuthnState;
import com.google.enterprise.adaptor.GsaCommunicationHandler;
import com.google.enterprise.adaptor.HttpClientAdapter;
import com.google.enterprise.adaptor.HttpExchangeInTransportAdapter;
import com.google.enterprise.adaptor.HttpExchangeOutTransportAdapter;
import com.google.enterprise.adaptor.SamlMetadata;
import com.google.enterprise.adaptor.Session;
import com.google.enterprise.adaptor.SessionManager;
import static org.junit.Assert.*;

import com.google.enterprise.secmgr.saml.OpenSamlUtil;

import com.sun.net.httpserver.HttpExchange;

import org.junit.*;
import org.junit.rules.ExpectedException;

import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.binding.decoding.HTTPRedirectDeflateDecoder;
import org.opensaml.saml2.binding.encoding.HTTPSOAP11Encoder;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.NameID;
import org.opensaml.xml.security.credential.Credential;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Test cases for {@link AuthnHandler}.
 */
public class AuthnHandlerTest {
  /**
   * Generated with {@code keytool -alias notadaptor -keystore
   * test/com/google/enterprise/adaptor/AuthnHandlerTest.jks -genkeypair
   * -keyalg RSA -validity 7300} and a password of {@code notchangeit}.
   */
  private static final String KEYSTORE_VALID_FILENAME
      = "test/com/google/enterprise/adaptor/AuthnHandlerTest.valid.jks";

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SessionManager<HttpExchange> sessionManager
      = new SessionManager<HttpExchange>(new MockTimeProvider(),
          new SessionManager.HttpExchangeClientStore(), 1000, 1000);
  private SamlMetadata metadata = new SamlMetadata("localhost", 80,
      "thegsa");
  private HttpClientAdapter httpClient = new HttpClientAdapter();
  private AuthnHandler handler = new AuthnHandler("localhost",
      Charset.forName("UTF-8"), sessionManager, metadata, httpClient, null);
  private MockHttpExchange ex = new MockHttpExchange("http", "GET", "/",
      new MockHttpContext(null, "/"));

  @BeforeClass
  public static void initSaml() {
    GsaCommunicationHandler.bootstrapOpenSaml();
  }

  @Test
  public void testNewSession() throws Exception {
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

    handler.handle(ex);

    //
    // We have run the method, now we need to look over the results...
    //
    assertEquals(307, ex.getResponseCode());
    URI uri = new URI(ex.getResponseHeaders().getFirst("Location"));
    assertEquals("https", uri.getScheme());
    assertEquals("thegsa", uri.getHost());
    assertEquals("/security-manager/samlauthn", uri.getPath());
    AuthnState authnState = (AuthnState) sessionManager.getSession(ex)
        .getAttribute(AuthnState.SESSION_ATTR_NAME);
    assertTrue(!authnState.isAuthenticated());

    // Act like we are the receiving end of the communication.
    MockHttpExchange remoteEx = new MockHttpExchange("https", "GET",
        uri.toString(),
        new MockHttpContext(null, "/security-manager/samlauthn"));

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
  public void testAlreadyAuthned() throws Exception {
    // Create a new authenticated session for this HttpExchange.
    Session session = sessionManager.getSession(ex, true);
    AuthnState authn = new AuthnState();
    session.setAttribute(AuthnState.SESSION_ATTR_NAME, authn);
    AuthnIdentity identity = new AuthnIdentityImpl.Builder("test").build();
    authn.authenticated(identity, Long.MAX_VALUE);
    handler.handle(ex);
    // Still should cause them to go through authn
    assertEquals(307, ex.getResponseCode());
    AuthnState authnState = (AuthnState) sessionManager.getSession(ex)
        .getAttribute(AuthnState.SESSION_ATTR_NAME);
    assertTrue(!authnState.isAuthenticated());
  }

  @Test
  public void testHead() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("http", "HEAD", "/",
                                               new MockHttpContext(null, "/"));
    handler.handle(ex);
    assertEquals(307, ex.getResponseCode());
    AuthnState authnState = (AuthnState) sessionManager.getSession(ex)
        .getAttribute(AuthnState.SESSION_ATTR_NAME);
    assertTrue(!authnState.isAuthenticated());
  }

  @Test
  public void testBadMethod() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("http", "POST", "/",
                                               new MockHttpContext(null, "/"));
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testKeyStore() throws Exception {
    Credential cred = AuthnHandler.getCredential("notadaptor",
        KEYSTORE_VALID_FILENAME, "JKS", "notchangeit");
    assertNotNull(cred);
  }

  @Test
  public void testKeyStoreInvalidType() throws Exception {
    thrown.expect(RuntimeException.class);
    Credential cred = AuthnHandler.getCredential("notadaptor",
        KEYSTORE_VALID_FILENAME, "WRONG", "notchangeit");
  }

  @Test
  public void testKeyStoreMissing() throws Exception {
    thrown.expect(java.io.FileNotFoundException.class);
    Credential cred = AuthnHandler.getCredential("notadaptor",
      "notarealfile.jks", "JKS", "notchangeit");
  }

  @Test
  public void testKeyStoreNoAlias() throws Exception {
    thrown.expect(RuntimeException.class);
    Credential cred = AuthnHandler.getCredential("notherealalias",
        KEYSTORE_VALID_FILENAME, "JKS", "notchangeit");
  }

  private String massageMessage(String response) {
    return response.replaceAll("ID=\"[^\"]+\"", "ID=\"someid\"")
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
}
