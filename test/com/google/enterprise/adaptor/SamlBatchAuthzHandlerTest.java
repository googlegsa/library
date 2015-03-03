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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

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
  private static final String DEFAULT_SUBJECT = "Polly Hedra";

  private AuthzAuthority adaptor = new MockAdaptor();
  private SamlMetadata samlMetadata = new SamlMetadata("localhost", 80,
      "localhost", "http://google.com/enterprise/gsa/security-manager",
      "http://google.com/enterprise/gsa/adaptor");
  private SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
      adaptor, new MockDocIdCodec(), samlMetadata, Principal.DomainFormat.DNS);
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
                                     "aoeuaoeu", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", DEFAULT_SUBJECT, "Permit")
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
        new PrivateMockAdaptor(), new MockDocIdCodec(), samlMetadata,
        Principal.DomainFormat.DNS);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", DEFAULT_SUBJECT, "Deny")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }
  
  @Test
  public void testAuthzByPassword() throws Exception {
    Map<String, String> usernamePasswordMap =
        ImmutableMap.<String, String>builder()
            .put("joe@test", "p@ssw0rd")
            .put("vin@test", "test")
            .build();
    SamlBatchAuthzHandler handler =
        new SamlBatchAuthzHandler(new AuthzByPasswordMockAdaptor(
            usernamePasswordMap), new MockDocIdCodec(), samlMetadata,
            Principal.DomainFormat.DNS);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));

    // try good password leading to Permit
    String extensionStr = ""
        + "<saml2p:Extensions xmlns:goog=\"http://www.google.com/\" "
        +   "xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<goog:SecmgrCredential "
        +     "domain=\"test\" "
        +     "name=\"joe\" "
        +     "namespace=\"Default\" "
        +     "password=\"p@ssw0rd\" "
        +     "xmlns:goog=\"http://www.google.com/\"/>"
        + "</saml2p:Extensions>";
    
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu", "joe", extensionStr)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", "joe", "Permit")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
    
    // try wrong password leading to Deny
    extensionStr = ""
        + "<saml2p:Extensions xmlns:goog=\"http://www.google.com/\" "
        +   "xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<goog:SecmgrCredential "
        +     "domain=\"test\" "
        +     "name=\"vin\" "
        +     "namespace=\"Default\" "
        +     "password=\"p@ssw0rd\" "
        +     "xmlns:goog=\"http://www.google.com/\"/>"
        + "</saml2p:Extensions>";
    request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu", "vin", null)
        + SOAP_FOOTER;
    goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", "vin", "Deny")
        + SOAP_FOOTER;
    MockHttpExchange ex2 = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    ex2.setRequestBody(stringToStream(request));
    handler.handle(ex2);
    assertEquals(200, ex2.getResponseCode());
    response = new String(ex2.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }
  
  @Test
  public void testAuthzByAcl() throws Exception {
    Map<String, Acl> aclMap = new TreeMap<String, Acl>();
    Acl.Builder builder1 = new Acl.Builder()
        .setPermitGroups(Arrays.asList(new GroupPrincipal("group1@test")))
        .setDenyGroups(Arrays.asList(new GroupPrincipal("group2@test")));
    aclMap.put("doc/1234", builder1.build());
    Acl.Builder builder2 = new Acl.Builder()
        .setPermitGroups(Arrays.asList(new GroupPrincipal("group2@test")))
        .setDenyGroups(Arrays.asList(new GroupPrincipal("group1@test")));
    aclMap.put("doc/1235", builder2.build());
    
    SamlBatchAuthzHandler handler =
        new SamlBatchAuthzHandler(new AuthzByAclMockAdaptor(aclMap),
            new MockDocIdCodec(), samlMetadata, Principal.DomainFormat.DNS);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));

    String extensionStr = ""
        + "<saml2p:Extensions xmlns:goog=\"http://www.google.com/\" "
        +   "xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<goog:SecmgrCredential "
        +     "domain=\"test\" "
        +     "name=\"joe\" "
        +     "namespace=\"Default\" "
        +     "password=\"p@ssw0rd\" "
        +     "xmlns:goog=\"http://www.google.com/\">"
        +       "<goog:Group "
        +         "domain=\"test\" "
        +         "name=\"group1\" "
        +         "namespace=\"Default\" "
        +         "xmlns:goog=\"http://www.google.com/\"/>"
        +   "</goog:SecmgrCredential>"
        + "</saml2p:Extensions>";
    
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu", "joe", extensionStr)
        + generateAuthzDecisionQuery("http://localhost/doc/1235",
                                     "aoeuaoeu2", "joe", null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", "joe", "Permit")
        + generateGoldenResponse("http://localhost/doc/1235",
                                 "aoeuaoeu2", "joe", "Deny")
        + SOAP_FOOTER;
    ex.setRequestBody(stringToStream(request));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = new String(ex.getResponseBytes(), charset);
    response = massageResponse(response);
    assertEquals(goldenResponse, response);
  }
  
  @Test
  public void testAuthzByAclNetbiosFormat() throws Exception {
    Map<String, Acl> aclMap = new TreeMap<String, Acl>();
    Acl.Builder builder1 = new Acl.Builder()
        .setPermitGroups(Arrays.asList(new GroupPrincipal("test\\group1")))
        .setDenyGroups(Arrays.asList(new GroupPrincipal("test\\group2")));
    aclMap.put("doc/1234", builder1.build());
    Acl.Builder builder2 = new Acl.Builder()
        .setPermitGroups(Arrays.asList(new GroupPrincipal("test\\group2")))
        .setDenyGroups(Arrays.asList(new GroupPrincipal("test\\group1")));
    aclMap.put("doc/1235", builder2.build());
    
    SamlBatchAuthzHandler handler =
        new SamlBatchAuthzHandler(new AuthzByAclMockAdaptor(aclMap),
            new MockDocIdCodec(), samlMetadata,
            Principal.DomainFormat.NETBIOS);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));

    String extensionStr = ""
        + "<saml2p:Extensions xmlns:goog=\"http://www.google.com/\" "
        +   "xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +   "<goog:SecmgrCredential "
        +     "domain=\"test\" "
        +     "name=\"joe\" "
        +     "namespace=\"Default\" "
        +     "password=\"p@ssw0rd\" "
        +     "xmlns:goog=\"http://www.google.com/\">"
        +       "<goog:Group "
        +         "domain=\"test\" "
        +         "name=\"group1\" "
        +         "namespace=\"Default\" "
        +         "xmlns:goog=\"http://www.google.com/\"/>"
        +   "</goog:SecmgrCredential>"
        + "</saml2p:Extensions>";
    
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu", "joe", extensionStr)
        + generateAuthzDecisionQuery("http://localhost/doc/1235",
                                     "aoeuaoeu2", "joe", null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", "joe", "Permit")
        + generateGoldenResponse("http://localhost/doc/1235",
                                 "aoeuaoeu2", "joe", "Deny")
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
    AuthzAuthority adaptor = new AuthzAuthority() {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
          Collection<DocId> ids) {
        return null;
      }
    };
    SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
        adaptor, new MockDocIdCodec(), samlMetadata,
        Principal.DomainFormat.DNS);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", DEFAULT_SUBJECT, "Deny")
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
    AuthzAuthority adaptor = new AuthzAuthority() {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
          Collection<DocId> ids) {
        assertEquals(1, ids.size());
        DocId docId = (DocId) ids.toArray()[0];
        return Collections.singletonMap(docId, AuthzStatus.INDETERMINATE);
      }
    };
    SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
        adaptor, new MockDocIdCodec(), samlMetadata,
        Principal.DomainFormat.DNS);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", DEFAULT_SUBJECT, "Deny")
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
    AuthzAuthority adaptor = new AuthzAuthority() {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
          Collection<DocId> ids) {
        throw new RuntimeException("something happened");
      }
    };
    SamlBatchAuthzHandler handler = new SamlBatchAuthzHandler(
        adaptor, new MockDocIdCodec(), samlMetadata,
        Principal.DomainFormat.DNS);
    MockHttpExchange ex = new MockHttpExchange("POST", "/",
        new MockHttpContext(handler, "/"));
    String request
        = SOAP_HEADER
        + generateAuthzDecisionQuery("http://localhost/doc/1234",
                                     "aoeuaoeu", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu", DEFAULT_SUBJECT, "Deny")
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
                                     "aoeuaoeu", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://wronghost/doc/1234",
                                 "aoeuaoeu", DEFAULT_SUBJECT, "Indeterminate")
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
                                     "aoeuaoeu", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost:81/doc/1234",
                                 "aoeuaoeu", DEFAULT_SUBJECT, "Indeterminate")
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
                                     "aoeuaoeu", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("https://localhost/doc/1234",
                                 "aoeuaoeu", DEFAULT_SUBJECT, "Indeterminate")
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
                                     "aoeuaoeu1", DEFAULT_SUBJECT, null)
        + generateAuthzDecisionQuery("http://localhost/doc/1235",
                                     "aoeuaoeu2", DEFAULT_SUBJECT, null)
        + generateAuthzDecisionQuery("http://localhost/doc/1236",
                                     "aoeuaoeu3", DEFAULT_SUBJECT, null)
        + SOAP_FOOTER;
    String goldenResponse
        = SOAP_HEADER
        + generateGoldenResponse("http://localhost/doc/1234",
                                 "aoeuaoeu1", DEFAULT_SUBJECT, "Permit")
        + generateGoldenResponse("http://localhost/doc/1235",
                                 "aoeuaoeu2", DEFAULT_SUBJECT, "Permit")
        + generateGoldenResponse("http://localhost/doc/1236",
                                 "aoeuaoeu3", DEFAULT_SUBJECT, "Permit")
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
  
  private String generateAuthzDecisionQuery(String resource, String id,
      String subject, String extensions) {
    String query = "<samlp:AuthzDecisionQuery "
        +     "ID=\"" + id + "\" "
        +     "IssueInstant=\"2009-10-20T17:52:29Z\" "
        +     "Version=\"2.0\" "
        +     "Resource=\"" + resource + "\" "
        +     "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        +     "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        +     "<saml:Subject>"
        +       "<saml:NameID>" + subject + "</saml:NameID>"
        +     "</saml:Subject>"
        +     "<saml:Action "
        +       "Namespace=\"urn:oasis:names:tc:SAML:1.0:action:ghpp\">"
        +       "GET"
        +     "</saml:Action>";
    if (extensions != null) {
      query = query + extensions;
    }
    query += "</samlp:AuthzDecisionQuery>";
    
    return query;
  }

  private String generateGoldenResponse(String resource, String requestId,
                                        String subject, String decision) {
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
        +        "<saml2:NameID>" + subject + "</saml2:NameID>"
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
