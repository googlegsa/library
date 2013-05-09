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

import static org.junit.Assert.*;

import org.junit.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Test cases for {@link SamlBatchAuthzHandler}.
 */
public class SamlBatchAuthzHandlerTest {
  private static final String SOAP_HEADER
      = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<soap11:Envelope "
      +   "xmlns:soap11=\"http://schemas.xmlsoap.org/soap/envelope/\">"
      +   "<soap11:Body>";
  private static final String SOAP_FOOTER
      =   "</soap11:Body>"
      + "</soap11:Envelope>";

  private MockAdaptor adaptor = new MockAdaptor();
  private SamlMetadata samlMetadata = new SamlMetadata("localhost", 80,
      "localhost", "http://google.com/enterprise/gsa/security-manager");
  private SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
      adaptor, new MockDocIdCodec(), samlMetadata);
  private MockHttpExchange ex = new MockHttpExchange("POST", "/",
      new MockHttpContext(handler, "/"));
  private Charset charset = Charset.forName("UTF-8");

  @BeforeClass
  public static void initSaml() {
    GsaCommunicationHandler.bootstrapOpenSaml();
  }

  @Test
  public void testGet() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("GET", "/",
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testWrongPath() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("POST", "/wrong",
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testPermitAuthz() throws Exception {
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu",
                                 "Permit")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testDenyAuthz() throws Exception {
    SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
        new PrivateMockAdaptor(), new MockDocIdCodec(), samlMetadata);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu",
                                 "Deny")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testBrokenAdaptor() throws Exception {
    Adaptor adaptor = new MockAdaptor() {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
          Collection<DocId> ids) {
        return null;
      }
    };
    SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
        adaptor, new MockDocIdCodec(), samlMetadata);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu",
                                 "Deny")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testIndeterminateAdaptor() throws Exception {
    Adaptor adaptor = new MockAdaptor() {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
          Collection<DocId> ids) {
        assertEquals(1, ids.size());
        DocId docId = (DocId) ids.toArray()[0];
        return Collections.singletonMap(docId, AuthzStatus.INDETERMINATE);
      }
    };
    SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
        adaptor, new MockDocIdCodec(), samlMetadata);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu",
                                 "Deny")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testErroringAdaptor() throws Exception {
    Adaptor adaptor = new MockAdaptor() {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
          Collection<DocId> ids) {
        throw new RuntimeException("something happened");
      }
    };
    SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
        adaptor, new MockDocIdCodec(), samlMetadata);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu",
                                 "Deny")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testUnknownResourceHost() throws Exception {
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://wronghost/doc/1234",
                                     "aoeuaoeu")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://wronghost/doc/1234",
                                 "aoeuaoeu",
                                 "Indeterminate")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testUnknownResourcePort() throws Exception {
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost:81/doc/1234",
                                     "aoeuaoeu")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost:81/doc/1234",
                                 "aoeuaoeu",
                                 "Indeterminate")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testUnknownResourceScheme() throws Exception {
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("https://localhost/doc/1234",
                                     "aoeuaoeu")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("https://localhost/doc/1234",
                                 "aoeuaoeu",
                                 "Indeterminate")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testMultiRequest() throws Exception {
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu1")
        + generateAuthzDecisionQuery("http://localhost/doc/1235",
                                     "aoeuaoeu2")
        + generateAuthzDecisionQuery("http://localhost/doc/1236",
                                     "aoeuaoeu3")
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu1",
                                 "Permit")
        + generateGoldenResponse("http://localhost/doc/1235",
                                 "aoeuaoeu2",
                                 "Permit")
        + generateGoldenResponse("http://localhost/doc/1236",
                                 "aoeuaoeu3",
                                 "Permit")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }

  @Test
  public void testMultiRequestWithDifferentSubjects() throws Exception {
    String request
        = SOAP_HEADER
        + "<samlp:AuthzDecisionQuery "
        +   "ID=\"aoeuaoeu1\" "
        +   "IssueInstant=\"2009-10-20T17:52:29Z\" "
        +   "Version=\"2.0\" "
        +   "Resource=\"http://localhost/doc/1234\" "
        +   "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        +   "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<saml:Subject>"
        +     "<saml:NameID>Polly Hedra</saml:NameID>"
        +   "</saml:Subject>"
        +   "<saml:Action "
        +     "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +     "GET"
        +   "</saml:Action>"
        + "</samlp:AuthzDecisionQuery>"
        + "<samlp:AuthzDecisionQuery "
        +   "ID=\"aoeuaoeu2\" "
        +   "IssueInstant=\"2009-10-20T17:52:29Z\" "
        +   "Version=\"2.0\" "
        +   "Resource=\"http://localhost/doc/1234\" "
        +   "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        +   "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<saml:Subject>"
        +     "<saml:NameID>Some Other Polly Hedra</saml:NameID>"
        +   "</saml:Subject>"
        +   "<saml:Action "
        +     "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +     "GET"
        +   "</saml:Action>"
        + "</samlp:AuthzDecisionQuery>"
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testBadXml() throws Exception {
    String request
        = SOAP_HEADER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testNoQueries() throws Exception {
    String request
        = SOAP_HEADER + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testNoResource() throws Exception {
    String request
        = SOAP_HEADER
        + "<samlp:AuthzDecisionQuery "
        +   "ID=\"aoeuaoeu\" "
        +   "IssueInstant=\"2009-10-20T17:52:29Z\" "
        +   "Version=\"2.0\" "
        +   "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        +   "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<saml:Subject>"
        +     "<saml:NameID>Polly Hedra</saml:NameID>"
        +   "</saml:Subject>"
        +   "<saml:Action "
        +     "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +     "GET"
        +   "</saml:Action>"
        + "</samlp:AuthzDecisionQuery>"
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    System.out.println(new String(ex.getResponseBytes(), charset));
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testNoSubject() throws Exception {
    String request
        = SOAP_HEADER
        + "<samlp:AuthzDecisionQuery "
        +   "ID=\"aoeuaoeu\" "
        +   "IssueInstant=\"2009-10-20T17:52:29Z\" "
        +   "Version=\"2.0\" "
        +   "Resource=\"http://localhost/doc/1234\" "
        +   "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        +   "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<saml:Action "
        +     "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +     "GET"
        +   "</saml:Action>"
        + "</samlp:AuthzDecisionQuery>"
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    System.out.println(new String(ex.getResponseBytes(), charset));
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testNoSubjectNameId() throws Exception {
    String request
        = SOAP_HEADER
        + "<samlp:AuthzDecisionQuery "
        +   "ID=\"aoeuaoeu\" "
        +   "IssueInstant=\"2009-10-20T17:52:29Z\" "
        +   "Version=\"2.0\" "
        +   "Resource=\"http://localhost/doc/1234\" "
        +   "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        +   "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<saml:Subject/>"
        +   "<saml:Action "
        +     "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +     "GET"
        +   "</saml:Action>"
        + "</samlp:AuthzDecisionQuery>"
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    System.out.println(new String(ex.getResponseBytes(), charset));
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testEmptySubjectNameId() throws Exception {
    String request
        = SOAP_HEADER
        + "<samlp:AuthzDecisionQuery "
        +   "ID=\"aoeuaoeu\" "
        +   "IssueInstant=\"2009-10-20T17:52:29Z\" "
        +   "Version=\"2.0\" "
        +   "Resource=\"http://localhost/doc/1234\" "
        +   "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        +   "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<saml:Subject>"
        +     "<saml:NameID/>"
        +   "</saml:Subject>"
        +   "<saml:Action "
        +     "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +     "GET"
        +   "</saml:Action>"
        + "</samlp:AuthzDecisionQuery>"
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    System.out.println(new String(ex.getResponseBytes(), charset));
    assertEquals(400, ex.getResponseCode());
  }

  private String massageResponse(String response) {
    return response.replaceAll("ID=\"[^\"]+\"", "ID=\"someid\"")
        .replaceAll("IssueInstant=\"[^\"]+\"", "IssueInstant=\"sometime\"");
  }

  private String generateAuthzDecisionQuery(String resource, String id) {
    return "<samlp:AuthzDecisionQuery "
        +     "ID=\"" + id + "\" "
        +     "IssueInstant=\"2009-10-20T17:52:29Z\" "
        +     "Version=\"2.0\" "
        +     "Resource=\"" + resource + "\" "
        +     "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        +     "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +     "<saml:Subject>"
        +       "<saml:NameID>Polly Hedra</saml:NameID>"
        +     "</saml:Subject>"
        +     "<saml:Action "
        +       "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +       "GET"
        +     "</saml:Action>"
        +   "</samlp:AuthzDecisionQuery>";
  }

  private String generateGoldenResponse(String resource, String requestId,
                                        String decision) {
    return "<saml2p:Response "
        +    "ID=\"someid\" "
        +    "InResponseTo=\"" + requestId + "\" "
        +    "IssueInstant=\"sometime\" "
        +    "Version=\"2.0\" "
        +    "xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +    "<saml2:Issuer "
        +      "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
        +      "http://google.com/enterprise/gsa/adaptor"
        +    "</saml2:Issuer>"
        +    "<saml2p:Status>"
        +      "<saml2p:StatusCode "
        +        "Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>"
        +    "</saml2p:Status>"
        +    "<saml2:Assertion "
        +      "ID=\"someid\" "
        +      "IssueInstant=\"sometime\" "
        +      "Version=\"2.0\" "
        +      "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
        +      "<saml2:Issuer>"
        +        "http://google.com/enterprise/gsa/adaptor"
        +      "</saml2:Issuer>"
        +      "<saml2:Subject>"
        +        "<saml2:NameID>Polly Hedra</saml2:NameID>"
        +      "</saml2:Subject>"
        +      "<saml2:AuthzDecisionStatement "
        +        "Decision=\"" + decision + "\" "
        +        "Resource=\"" + resource + "\">"
        +        "<saml2:Action "
        +          "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +          "GET"
        +        "</saml2:Action>"
        +      "</saml2:AuthzDecisionStatement>"
        +    "</saml2:Assertion>"
        +  "</saml2p:Response>";
  }

  private InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(charset));
  }
}
