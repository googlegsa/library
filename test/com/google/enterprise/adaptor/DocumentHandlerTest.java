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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.x500.X500Principal;

/**
 * Tests for {@link DocumentHandler}.
 */
public class DocumentHandlerTest {
  /** RFC 822 date format, as updated by RFC 1123. */
  private static final ThreadLocal<DateFormat> dateFormatRfc1123
      = new ThreadLocal<DateFormat>() {
    @Override
    protected DateFormat initialValue() {
      DateFormat df = new SimpleDateFormat(
          "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
      df.setTimeZone(TimeZone.getTimeZone("GMT"));
      return df;
    }
  };

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MockAdaptor mockAdaptor = new MockAdaptor();
  private MockDocIdCodec docIdCodec = new MockDocIdCodec();
  private DocumentHandler handler = createDefaultHandlerForAdaptor(mockAdaptor);
  private DocId defaultDocId = new DocId("test docId");
  private String defaultPath
      = docIdCodec.encodeDocId(defaultDocId).getRawPath();
  private MockHttpExchange ex = new MockHttpExchange("GET", defaultPath,
      new MockHttpContext("/"));
  private MockHttpExchange headEx = new MockHttpExchange("HEAD", defaultPath,
      new MockHttpContext("/"));
  // this address is reserved as "TEST-NET" in RFC 5737 - it's *not* our IP.
  private static final String NOT_OUR_IP_ADDRESS = "192.0.2.0";

  @Before
  public void setUp() {
    Locale locale = new Locale("en", "EN");
    Locale.setDefault(locale);
  }

  @Test
  public void testSuccessBuilder() {
    createHandlerBuilder().build();
  }

  @Test
  public void testNullDocIdDecoder() {
    thrown.expect(NullPointerException.class);
    createHandlerBuilder().setDocIdDecoder(null).build();
  }

  @Test
  public void testNullDocIdEncoder() {
    thrown.expect(NullPointerException.class);
    createHandlerBuilder().setDocIdEncoder(null).build();
  }

  @Test
  public void testNullJournal() {
    thrown.expect(NullPointerException.class);
    createHandlerBuilder().setJournal(null).build();
  }

  @Test
  public void testNullAdaptor() {
    thrown.expect(NullPointerException.class);
    createHandlerBuilder().setAdaptor(null).build();
  }

  @Test
  public void testNullWatchdog() {
    thrown.expect(NullPointerException.class);
    createHandlerBuilder().setWatchdog(null).build();
  }

  @Test
  public void testDenyForSecMgr() throws Exception {
    ex.getRequestHeaders().add("User-Agent", "SecMgr");
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityDeny() throws Exception {
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testAuthzSkippedWhenAllDocsMarkedAsPublic()
      throws Exception {
    // isUserAuthorized always returns AuthzStatus.DENY
    Adaptor adaptor = new PrivateMockAdaptor();
    AuthzAuthority authzAuthority = (AuthzAuthority) adaptor;
    DocumentHandler handler = createHandlerBuilder().setAdaptor(adaptor)
        .setAuthzAuthority(authzAuthority).setMarkDocsPublic(true).build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testSecMgrDeniedDespiteAllDocsMarkedAsPublic()
      throws Exception {
    Adaptor adaptor = new PrivateMockAdaptor();
    AuthzAuthority authzAuthority = (AuthzAuthority) adaptor;
    DocumentHandler handler = createHandlerBuilder().setAdaptor(adaptor)
        .setAuthzAuthority(authzAuthority).setMarkDocsPublic(true).build();
    ex.getRequestHeaders().add("User-Agent", "SecMgr");
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testAuthzSkippedWhenAllHostsTrusted() throws Exception {
    // isUserAuthorized always returns AuthzStatus.DENY
    Adaptor adaptor = new PrivateMockAdaptor();
    AuthzAuthority authzAuthority = (AuthzAuthority) adaptor;
    DocumentHandler handler = createHandlerBuilder().setAdaptor(adaptor)
        .setAuthzAuthority(authzAuthority)
        .setFullAccessHosts(new String[] {"0.0.0.0/0"}) // matches all hosts
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testSecurityDenyWithAuthnHandler() throws Exception {
    SamlServiceProvider samlServiceProvider = new MockSamlServiceProvider() {
      @Override
      public void handleAuthentication(HttpExchange ex) throws IOException {
        // Translation.HTTP_NOT_FOUND was randomly chosen.
        HttpExchanges.cannedRespond(ex, 1234, Translation.HTTP_NOT_FOUND);
      }
    };
    PrivateMockAdaptor adaptor = new PrivateMockAdaptor();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor).setAuthzAuthority(adaptor)
        .setSamlServiceProvider(samlServiceProvider).build();
    handler.handle(ex);
    assertEquals(1234, ex.getResponseCode());
  }

  @Test
  public void testSecurityPermit() throws Exception {
    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor).setAuthzAuthority(adaptor)
        .setSamlServiceProvider(samlServiceProvider).build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testSecurityNotFound() throws Exception {
    Adaptor mockAdaptor = new MockAdaptor() {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
          Collection<DocId> ids) {
        Map<DocId, AuthzStatus> result
            = new HashMap<DocId, AuthzStatus>(ids.size() * 2);
        for (DocId id : ids) {
          result.put(id, AuthzStatus.INDETERMINATE);
        }
        return Collections.unmodifiableMap(result);
      }

      @Override
      public void getDocContent(Request request, Response response) {
        throw new UnsupportedOperationException();
      }
    };
    DocumentHandler handler = createDefaultHandlerForAdaptor(mockAdaptor);
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testSecurityDisallowedUser() throws Exception {
    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(new PrivateMockAdaptor())
        .setSamlServiceProvider(samlServiceProvider).build();
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityFromGsa() throws Exception {
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(new PrivateMockAdaptor())
        .setFullAccessHosts(new String[] {remoteIp, " "})
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(mockAdaptor.documentBytes, ex.getResponseBytes());
    assertEquals(Arrays.asList("", ""),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  @Test
  public void testSecuritySecureFromGsa() throws Exception {
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        new X500Principal("CN=localhost, OU=Unknown, O=Unknown, C=Unknown")));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(mockAdaptor.documentBytes, ex.getResponseBytes());
    assertEquals("public",
        ex.getResponseHeaders().getFirst("X-Gsa-Serve-Security"));
  }

  @Test
  public void testSecuritySecureNoCertificate() throws Exception {
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        null));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecuritySecureNotX500Principal() throws Exception {
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        new KerberosPrincipal("someuser@not-domain")));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecuritySecureNoCommonName() throws Exception {
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        new X500Principal("OU=Unknown, O=Unknown, C=Unknown")));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecuritySecureNotWhitelisted() throws Exception {
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        new X500Principal("CN=nottrusted, OU=Unknown, O=Unknown, C=Unknown")));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityFromGsaAutoAddWhitelist() throws Exception {
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(new PrivateMockAdaptor())
        .setGsaHostname(remoteIp)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(mockAdaptor.documentBytes, ex.getResponseBytes());
  }

  @Test
  public void testNormal() throws Exception {
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(mockAdaptor.documentBytes, ex.getResponseBytes());
    assertFalse(ex.getResponseHeaders().containsKey("X-Gsa-External-Metadata"));
    assertFalse(ex.getResponseHeaders().containsKey("X-Gsa-External-Anchor"));
    assertFalse(ex.getResponseHeaders().containsKey("X-Robots-Tag"));
    assertFalse(ex.getResponseHeaders().containsKey("X-Gsa-Serve-Security"));
    assertFalse(ex.getResponseHeaders().containsKey("Last-Modified"));
  }

  @Test
  public void testNormalHead() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("HEAD", defaultPath,
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(new byte[] {}, ex.getResponseBytes());
  }

  @Test
  public void testNormalPost() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("POST", defaultPath,
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testWatchdogInterruption() throws Exception {
    ScheduledExecutorService executor
        = Executors.newSingleThreadScheduledExecutor();
    Watchdog watchdog = new Watchdog(executor);
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        Thread.sleep(100);
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setAuthzAuthority(mockAdaptor)
        .setWatchdog(watchdog)
        .setHeaderTimeoutMillis(1)
        .build();
    try {
      thrown.expect(RuntimeException.class);
      handler.handle(ex);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testWatchdogNoInterruption() throws Exception {
    ScheduledExecutorService executor
        = Executors.newSingleThreadScheduledExecutor();
    Watchdog watchdog = new Watchdog(executor);
    DocumentHandler handler = createHandlerBuilder()
        .setAuthzAuthority(new MockAdaptor())
        .setWatchdog(watchdog)
        .setHeaderTimeoutMillis(100)
        .setContentTimeoutMillis(100)
        .build();
    try {
      handler.handle(ex);
    } finally {
      executor.shutdownNow();
    }
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testTransform() throws Exception {
    final String key = "testing key";
    List<MetadataTransform> transforms
        = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform
        = new MetadataTransformPipeline(transforms, Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertFalse(handler.considerSkippingTransforms(ex));
    assertEquals(200, ex.getResponseCode());
    assertEquals("docid=test%20docId,testing%20key=TESTING%20VALUE",
                 ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
  }

  @Test
  public void testSkippingTransform() throws Exception {
  // very much like the above test, except for the remoteIp and the assertions
  // after "handler.handle(ex);"
    final String key = "testing key";
    List<MetadataTransform> transforms
        = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform
        = new MetadataTransformPipeline(transforms, Arrays.asList("t1"));

    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());

    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setMetadataTransform(transform)
        .setSamlServiceProvider(samlServiceProvider)
        .setAlwaysGiveAclsAndMetadata(true)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertTrue(handler.considerSkippingTransforms(ex));
    assertEquals(200, ex.getResponseCode());
    // Metadata Transform *not* applied - value is lower case
    assertEquals(Arrays.asList("testing%20key=testing%20value", ""),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  @Test
  public void testDroppingDocForGetRequest() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Transmission-Decision", "do-not-index");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "testing value");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
    assertEquals(null,
         ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    assertArrayEquals("Error 404: Not Found".getBytes(), ex.getResponseBytes());
  }

  @Test
  public void testDroppingDocForHeadRequest() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Transmission-Decision", "do-not-index");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "testing value");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = headEx.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(headEx);
    assertEquals(404, headEx.getResponseCode());
    assertEquals(null,
         headEx.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    // 404 response to HEAD request does not have content (it is HEAD)
    assertArrayEquals("".getBytes(), headEx.getResponseBytes());
  }

  @Test
  public void testDroppingContentForGetRequest() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Transmission-Decision", "do-not-index-content");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "TESTING VALUE");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("docid=test%20docId,test%20key=TESTING%20VALUE",
        ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    // content is dropped; we became like HEAD request
    assertArrayEquals(new byte[0], ex.getResponseBytes());
  }

  @Test
  public void testDroppingContentForHeadRequest() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Transmission-Decision", "do-not-index-content");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "TESTING VALUE");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = headEx.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(headEx);
    // content is dropped for HEAD already; we retain HEAD response
    assertEquals(200, headEx.getResponseCode());
    assertEquals("docid=test%20docId,test%20key=TESTING%20VALUE",
        headEx.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    assertArrayEquals(new byte[0], headEx.getResponseBytes());
  }

  @Test
  public void testAsIsForGetRequest() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Transmission-Decision", "as-is");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "TESTING VALUE");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("docid=test%20docId,test%20key=TESTING%20VALUE",
        ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    // content remained as-is
    assertArrayEquals(new byte[] {1, 2, 3}, ex.getResponseBytes());
  }

  @Test
  public void testAsIsForHeadRequest() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Transmission-Decision", "as-is");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "TESTING VALUE");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = headEx.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(headEx);
    assertEquals(200, headEx.getResponseCode());
    assertEquals("docid=test%20docId,test%20key=TESTING%20VALUE",
        headEx.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    // content remained as-is
    assertArrayEquals(new byte[0], headEx.getResponseBytes());
  }

  @Test
  public void testForcedDecision() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Forced-Transmission-Decision", "as-is");
        params.put("Transmission-Decision", "do-not-index");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "TESTING VALUE");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("docid=test%20docId,test%20key=TESTING%20VALUE",
        ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    // content remained as-is
    assertArrayEquals(new byte[] {1, 2, 3}, ex.getResponseBytes());
  }

  @Test
  public void testDroppingDocAfterNoContent() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Transmission-Decision", "do-not-index");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "testing value");
        response.respondNoContent();
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
    assertEquals(null,
         ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    // The 204 that would not have content becomes a 404 with content
    assertArrayEquals("Error 404: Not Found".getBytes(), ex.getResponseBytes());
  }

  @Test
  public void testDroppingContentAfterNoContent() throws Exception {
    List<MetadataTransform> transforms = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        params.put("Transmission-Decision", "do-not-index-content");
        metadata.set("docid", params.get("DocId"));
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("test key", "TESTING VALUE");
        response.respondNoContent();
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertEquals(204, ex.getResponseCode());
    assertEquals("docid=test%20docId,test%20key=TESTING%20VALUE",
        ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
    assertArrayEquals(new byte[0], ex.getResponseBytes());
  }

  @Test
  public void testAclTransform() throws Exception {
    AclTransform aclTransform = new AclTransform(Arrays.asList(
        new AclTransform.Rule(
            new AclTransform.MatchData(null, "u1", null, null),
            new AclTransform.MatchData(null, "u2", null, null))));
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.setAcl(new Acl.Builder()
            .setPermitUsers(Arrays.asList(
                new UserPrincipal("u1"), new UserPrincipal("u3")))
            .build());
        super.getDocContent(request, response);
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setAclTransform(aclTransform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    assertFalse(handler.considerSkippingTransforms(ex));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("google%3Aaclusers=u2,google%3Aaclusers=u3",
                 ex.getResponseHeaders().get("X-Gsa-External-Metadata").get(1));
  }

  @Test
  public void testSkippingAclTransform() throws Exception {
    AclTransform aclTransform = new AclTransform(Arrays.asList(
        new AclTransform.Rule(
            new AclTransform.MatchData(null, "u1", null, null),
            new AclTransform.MatchData(null, "u2", null, null))));

    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());

    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.setAcl(new Acl.Builder()
            .setPermitUsers(Arrays.asList(
                new UserPrincipal("u1"), new UserPrincipal("u3")))
            .build());
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setAclTransform(aclTransform)
        .setSamlServiceProvider(samlServiceProvider)
        .setAlwaysGiveAclsAndMetadata(true)
        .build();
    assertTrue(handler.considerSkippingTransforms(ex));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    // ACL Transform *not* applied
    assertEquals(Arrays.asList("", "google%3Aaclusers=u1,google%3Aaclusers=u3"),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  @Test
  public void testContentTransform() throws Exception {
    ContentTransformFactory contentTransformFactory =
        new ContentTransformFactory(
        new ArrayList<Map<String, String>>() {
          {
            add(new HashMap<String, String>() {
              {
                put("class", SampleDocumentContentTransform.class.getName());
              }
            });
          }
        });
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(final Request request, final Response response)
          throws IOException, InterruptedException {
        response.setContentType("image/jpeg");
        OutputStream os = response.getOutputStream();
        os.write("some cool stuff".getBytes(Charsets.UTF_8));
        os.close();
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[]{remoteIp})
        .setContentTransformPipeline(contentTransformFactory)
        .build();
    assertFalse(handler.considerSkippingTransforms(ex));
    handler.handle(ex);
    assertEquals("some changed stuff", new String(ex.getResponseBytes()));
  }

  private static class SampleDocumentContentTransform
      extends ContentTransform {
    public SampleDocumentContentTransform(Map<String, String> config,
                                          Metadata metadata,
                                          String contentType,
                                          OutputStream originalStream) {
      super(config, metadata, contentType, originalStream);
    }
    @Override
    public void write(final byte[] b) throws IOException {
      if (contentType.equals("image/jpeg")) {
        super.write("some changed stuff".getBytes(Charsets.UTF_8));
      } else {
        super.write(b);
      }
    }
  }

  @Test
  public void testSkippingContentTransform() throws Exception {
    ContentTransformFactory contentTransformFactory =
        new ContentTransformFactory(
        new ArrayList<Map<String, String>>() {
          {
            add(new HashMap<String, String>() {
              {
                put("class", SampleDocumentContentTransform.class.getName());
              }
            });
          }
        });
    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());

    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.setContentType("image/jpeg");
        OutputStream os = response.getOutputStream();
        os.write("some unchanged stuff".getBytes(Charsets.UTF_8));
        os.close();
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setContentTransformPipeline(contentTransformFactory)
        .setSamlServiceProvider(samlServiceProvider)
        .build();
    assertTrue(handler.considerSkippingTransforms(ex));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    // content transform not applied
    assertEquals("some unchanged stuff", new String(ex.getResponseBytes()));
  }

  @Test
  public void testContentTransformOrder() throws Exception {
    ContentTransformFactory contentTransformFactory =
        new ContentTransformFactory(
            new ArrayList<Map<String, String>>() {
            {
                add(new HashMap<String, String>() {{
                    put("class", Write1.class.getName());
                }});
                add(new HashMap<String, String>() {{
                  put("class", Write2.class.getName());
                }});
                add(new HashMap<String, String>() {{
                  put("class", Write3.class.getName());
                }});
            }
        });
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(final Request request, final Response response)
          throws IOException, InterruptedException {
        response.setContentType("image/jpeg");
        OutputStream os = response.getOutputStream();
        os.write("0".getBytes(Charsets.UTF_8));
        os.close();
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[]{remoteIp})
        .setContentTransformPipeline(contentTransformFactory)
        .build();
    handler.handle(ex);
    assertEquals("3", new String(ex.getResponseBytes()));
  }

  private static class Write1 extends ContentTransform {
    public Write1(Map<String, String> config, Metadata metadata,
        String contentType, OutputStream originalStream) {
      super(config, metadata, contentType, originalStream);
    }
    @Override
    public void write(final byte[] b) throws IOException {
      if ('0' != b[0]) {
        throw new IllegalStateException("Write1 not called first");
      }
      super.write("1".getBytes(Charsets.UTF_8));
    }
  }

  private static class Write2 extends ContentTransform {
    public Write2(Map<String, String> config, Metadata metadata,
        String contentType, OutputStream originalStream) {
      super(config, metadata, contentType, originalStream);
    }
    @Override
    public void write(final byte[] b) throws IOException {
      if ('1' != b[0]) {
        throw new IllegalStateException("Write2 not called second");
      }
      super.write("2".getBytes(Charsets.UTF_8));
    }
  }

  private static class Write3 extends ContentTransform {
    public Write3(Map<String, String> config, Metadata metadata,
        String contentType, OutputStream originalStream) {
      super(config, metadata, contentType, originalStream);
    }
    @Override
    public void write(final byte[] b) throws IOException {
      if ('2' != b[0]) {
        throw new IllegalStateException("Write3 not called third");
      }
      super.write("3".getBytes(Charsets.UTF_8));
    }
  }

  // TODO(myk): Move all ContentTransformFactory tests into their own class.
  @Test
  public void testContentTransformChain() throws Exception {
    ContentTransformFactory contentTransformFactory =
        new ContentTransformFactory(
            new ArrayList<Map<String, String>>() {
              {
                add(new HashMap<String, String>() {{
                    put("class", DocumentContentTransformA.class.getName());
                }});
                add(new HashMap<String, String>() {{
                    put("class", DocumentContentTransformB.class.getName());
                }});
                add(new HashMap<String, String>() {{
                    put("class", DocumentContentTransformC.class.getName());
                }});
              }
        });
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(final Request request, final Response response)
          throws IOException, InterruptedException {
        response.setContentType("image/jpeg");
        OutputStream os = response.getOutputStream();
        os.write("some cool stuff".getBytes(Charsets.UTF_8));
        os.close();
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[]{remoteIp})
        .setContentTransformPipeline(contentTransformFactory)
        .build();
    handler.handle(ex);
    assertEquals("text/plain",
        contentTransformFactory.calculateResultingContentType("image/jpeg"));
    assertEquals("foo", contentTransformFactory.calculateContentType("foo", 0));
    assertEquals("image/gif",
        contentTransformFactory.calculateContentType("image/jpeg", 1));
    assertEquals("", contentTransformFactory.calculateContentType("null", 1));
    assertEquals("image/gif",
        contentTransformFactory.calculateContentType("image/jpeg", 2));
    assertEquals("text/plain",
        contentTransformFactory.calculateContentType("image/jpeg", 3));
    try {
      String s = contentTransformFactory.calculateContentType("-1", -1);
      fail("Didn't catch expected AssertionError");
    } catch (AssertionError ae) {
      assertTrue(ae.getMessage().contains("must be non-negative"));
    }
    try {
      String s = contentTransformFactory.calculateContentType("image/jpeg", 10);
      fail("Didn't catch expected AssertionError");
    } catch (AssertionError ae) {
      assertTrue(ae.getMessage().contains("only 3 transform(s) present"));
    }
    assertEquals("some stuff: image/jpeg", new String(ex.getResponseBytes()));
  }

  private static class DocumentContentTransformA extends ContentTransform {

    public DocumentContentTransformA(Map<String, String> config,
        Metadata metadata, String contentType, OutputStream originalStream) {
      super(config, metadata, contentType, originalStream);
      assertEquals("image/jpeg", contentType);
    }

    public static String getContentTypeOutputForContentTypeInput(String ctIn) {
      if ("image/jpeg".equalsIgnoreCase(ctIn)) {
        return "image/gif";
      }
      if ("null".equalsIgnoreCase(ctIn)) {
        return null;
      }
      fail("DocumentContentTransformA got the wrong input type -- " + ctIn);
      return null; // to avoid "error: missing return statement"
    }

    @Override
    public void write(final byte[] b) throws IOException {
      super.write(("some stuff: " + contentType).getBytes(Charsets.UTF_8));
    }
  }

  private static class DocumentContentTransformB extends ContentTransform {

    public DocumentContentTransformB(Map<String, String> config,
        Metadata metadata, String contentType, OutputStream originalStream) {
      super(config, metadata, contentType, originalStream);
      assertEquals("image/gif", contentType);
    }

    // this class has no getContentTypeOutputForContentTypeInput() method.
  }

  private static class DocumentContentTransformC extends ContentTransform {

    public DocumentContentTransformC(Map<String, String> config,
        Metadata metadata, String contentType, OutputStream originalStream) {
      super(config, metadata, contentType, originalStream);
      assertEquals("image/gif", contentType); // the input contentType
    }

    public static String getContentTypeOutputForContentTypeInput(String ctIn) {
      if ("image/gif".equalsIgnoreCase(ctIn)) {
        return "text/plain";
      }
      fail("DocumentContentTransformC got the wrong input type -- " + ctIn);
      return null; // to avoid "error: missing return statement"
    }
  }

  @Test
  public void testNullAuthzResponse() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public Map<DocId, AuthzStatus> isUserAuthorized(
              AuthnIdentity identity, Collection<DocId> ids) {
            return null;
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testFileNotFound() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.respondNotFound();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testIOException() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            throw new IOException();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(IOException.class);
    handler.handle(ex);
  }

  @Test
  public void testRuntimeException() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            throw new RuntimeException();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testNoOutput() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(IOException.class);
    handler.handle(ex);
  }

  @Test
  public void testNotModified() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.respondNotModified();            
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());
  }

  @Test
  public void testNotModifiedThenOutputStream() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.respondNotModified();
            response.getOutputStream();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testNoContentGSARequest() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.respondNoContent();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)      
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .build();
    handler.handle(ex);
    assertEquals(204, ex.getResponseCode());
    assertEquals(Collections.singletonList("true"),
        ex.getResponseHeaders().get("X-Gsa-Skip-Updating-Content"));
  }

  @Test
  public void testNoContentNonGSARequest() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.respondNoContent();
          }
        };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .build();
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());
  }
  
  @Test
  public void testCanRespondWithNoContentNonGSARequest() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            if (request.canRespondWithNoContent(new Date(1 * 1000))) {
              response.respondNoContent();
              return;
            } else {
              throw new UnsupportedOperationException();
            }
          }
        };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setSendDocControls(true)       
        .build();
    ex.getRequestHeaders().set("If-Modified-Since",
        "Thu, 1 Jan 1970 00:00:01 GMT");
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());
  }
  
  @Test
  public void testCanRespondWithNoContentGSARequest() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            if (request.canRespondWithNoContent(new Date(1 * 1000))) {
              response.respondNoContent();
              return;
            } else {
              throw new UnsupportedOperationException();
            }
          }
        };
        String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
        DocumentHandler handler = createHandlerBuilder()
            .setAdaptor(adaptor)      
            .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
            .setSendDocControls(true)
            .setGsaVersion("7.4.0-0")
            .build();
    ex.getRequestHeaders().set("If-Modified-Since",
        "Thu, 1 Jan 1970 00:00:01 GMT");
    handler.handle(ex);
    assertEquals(204, ex.getResponseCode());
  }
  
  @Test
  public void testCanRespondWithNoContentPre74GSARequest() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            if (request.canRespondWithNoContent(new Date(1 * 1000))) {
              response.respondNoContent();
              return;
            } else {
              response.setContentType("text/plain");
              response.setLastModified(new Date(1 * 1000));
              response.addMetadata("not", "important");
              response.setAcl(Acl.EMPTY);
              response.getOutputStream();              
              response.getOutputStream().close();
            }
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .setGsaVersion("7.0.0-0")
        .build();
    ex.getRequestHeaders().set("If-Modified-Since",
        "Thu, 1 Jan 1970 00:00:01 GMT");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }
  
  @Test
  public void testCanRespondWithNoContentPre74NonGSARequest() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            if (request.canRespondWithNoContent(new Date(1 * 1000))) {
              response.respondNoContent();
              return;
            } else {
              throw new UnsupportedOperationException();
            }
          }
        };    
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)       
        .setSendDocControls(true)
        .setAuthzAuthority(adaptor)
        .setGsaVersion("7.0.0-0")
        .build();
    ex.getRequestHeaders().set("If-Modified-Since",
        "Thu, 1 Jan 1970 00:00:01 GMT");
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());
  }
  
  @Test
  public void testCanRespondWithNoContentWithChangedContent() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            if (request.canRespondWithNoContent(new Date(1 * 1000))) {
              throw new UnsupportedOperationException();
            } else {
              response.setContentType("text/plain");
              response.setLastModified(new Date(1 * 1000));
              response.addMetadata("not", "important");
              response.setAcl(Acl.EMPTY);
              response.getOutputStream();              
              response.getOutputStream().close();
            }
          }
        };    
    DocumentHandler handler = createHandlerBuilder()
            .setAdaptor(adaptor)
            .setAuthzAuthority(adaptor)
            .setSendDocControls(true)
            .setGsaVersion("7.0.0-0")
            .build();
    ex.getRequestHeaders().set("If-Modified-Since",
        "Thu, 1 Jan 1970 00:00:00 GMT");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testNoContentWithUpdatedMetadataAndAcls() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.addMetadata("DocTitle", "updated");
            response.setAcl(new Acl.Builder()
                .setInheritFrom(new DocId("parent"))
                .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
                .build());
            response.respondNoContent();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .build();
    handler.handle(ex);
    assertEquals(204, ex.getResponseCode());
    assertTrue(ex.getResponseHeaders().get("X-Gsa-Doc-Controls")
        .contains("acl=%7B%22inherit_from%22%3A%22http%3A%5C%2F%5C%2F"
            + "localhost%5C%2Fparent%22%2C%22"
            + "inheritance_type%22%3A%22PARENT_OVERRIDES%22%7D"));
    assertTrue(ex.getResponseHeaders().get("X-gsa-external-metadata")
        .contains("DocTitle=updated"));
    assertEquals(Collections.singletonList("true"),
        ex.getResponseHeaders().get("X-Gsa-Skip-Updating-Content"));
  }

  @Test
  public void testNoContentThenOutputStream() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.respondNoContent();
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .build();
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testMetadataTransformWithLastModified() throws Exception {
    final Date oDate = new Date(0);
    final Date nDate = new Date();
    MockAdaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.setLastModified(oDate);
        response.respondNoContent();
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[]{remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .setMetadataTransform(new MetadataTransformPipeline(
            Lists.newArrayList(new MetadataTransform() {
          @Override
          public void transform(Metadata metadata, Map<String, String> params) {
            assertEquals(oDate.getTime(),
                Long.parseLong(params.get("Last-Modified-Millis-UTC")));
            params.put("Last-Modified-Millis-UTC", "" + nDate.getTime());
          }
        }), Lists.newArrayList("sample-transformer")))
        .build();
    handler.handle(ex);
    assertEquals(dateFormatRfc1123.get().format(nDate),
        ex.getResponseHeaders().getFirst("Last-modified"));
  }

  @Test
  public void testMetadataTransformWithDisplayURL() throws Exception {
    final String oDu = "http://google.com/display-url";
    MockAdaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        try {
          response.setDisplayUrl(new URI(oDu));
        } catch (URISyntaxException e) {
          fail(e.getMessage());
        }
        response.getOutputStream().write("wohoo".getBytes(Charsets.UTF_8));
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .setMetadataTransform(new MetadataTransformPipeline(
            Lists.newArrayList(new MetadataTransform() {
          @Override
          public void transform(Metadata metadata, Map<String, String> params) {
            final String du = params.get("Display-URL");
            assertEquals(oDu, du);
            params.put("Display-URL", "https://google.com/display-url");
          }
        }), Lists.newArrayList("sample-transformer")))
        .build();
    handler.handle(ex);
    assertEquals("display_url=https%3A%2F%2Fgoogle.com%2Fdisplay-url",
        ex.getResponseHeaders().get("X-gsa-doc-controls").get(1));
  }

  @Test
  public void testOutputStreamThenNotModified() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.respondNotModified();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testOutputStreamThenNoContent() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.respondNoContent();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .build();
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testOutputStreamTwice() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.getOutputStream();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testSetContentTypeLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setContentType("text/plain");
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetLastModifiedLast() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setLastModified(new Date(0));
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetMetadataLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.addMetadata("not", "important");
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetAclLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setAcl(Acl.EMPTY);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetSecureLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setSecure(true);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testAddAnchorLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.addAnchor(URI.create("http://h/"), null);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetNoIndexLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setNoIndex(true);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetNoFollowLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setNoFollow(true);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetNoArchiveLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setNoArchive(true);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetDisplayUrlLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setDisplayUrl(null);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetCrawlOnceLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setCrawlOnce(true);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetLockLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setLock(true);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testSetNoIndexFollowArchiveFalse() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setNoIndex(false);
            response.setNoFollow(false);
            response.setNoArchive(false);
            response.getOutputStream();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertFalse(ex.getResponseHeaders().containsKey("X-Robots-Tag"));
  }

  @Test
  public void testSmartAdaptor() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            if (!request.getDocId().equals(defaultDocId)) {
              response.respondNotFound();
              return;
            }
            // For convenience, check that the unique id is part of the
            // request's toString().
            assertTrue(request.toString().contains(defaultDocId.getUniqueId()));
            if (!request.hasChangedSinceLastAccess(new Date(1 * 1000))) {
              response.respondNotModified();
              return;
            }
            response.setContentType("text/plain");
            response.setLastModified(new Date(1 * 1000));
            response.addMetadata("not", "important");
            response.setAcl(Acl.EMPTY);
            response.getOutputStream();
            // It is free to get it multiple times. Is free to close() stream.
            // Other tests choose not to close the stream.
            response.getOutputStream().close();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Thu, 1 Jan 1970 00:00:01 GMT");
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());

    MockHttpExchange ex = new MockHttpExchange("GET", defaultPath,
        new MockHttpContext(handler, "/"));
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Thu, 1 Jan 1970 00:00:00 GMT");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("text/plain",
                 ex.getResponseHeaders().getFirst("Content-Type"));
    assertEquals("Thu, 01 Jan 1970 00:00:01 GMT",
                 ex.getResponseHeaders().getFirst("Last-Modified"));

    ex = new MockHttpExchange("HEAD", defaultPath,
                              new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());

  }

  @Test
  public void testMetadataGsa() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.addMetadata("test", "ing");
            response.setAcl(new Acl.Builder()
                .setInheritFrom(new DocId("testing")).build());
            response.setSecure(true);
            response.addAnchor(URI.create("http://test/"), null);
            response.addAnchor(URI.create("ftp://host/path?val=1"),
                "AaZz09,=-%");
            response.setNoIndex(true);
            response.setNoFollow(true);
            response.setNoArchive(true);
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals(Arrays.asList("test=ing", "google%3Aaclinheritfrom="
          + "http%3A%2F%2Flocalhost%2Ftesting"),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
    assertEquals("secure",
        ex.getResponseHeaders().getFirst("X-Gsa-Serve-Security"));
    assertEquals("http%3A%2F%2Ftest%2F,"
        + "AaZz09%2C%3D-%25=ftp%3A%2F%2Fhost%2Fpath%3Fval%3D1",
        ex.getResponseHeaders().getFirst("X-Gsa-External-Anchor"));
    List<String> robots = ex.getResponseHeaders().get("X-Robots-Tag");
    assertTrue(robots.contains("noindex"));
    assertTrue(robots.contains("nofollow"));
    assertTrue(robots.contains("noarchive"));
  }

  @Test
  public void testMetadataNonGsa() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.addMetadata("test", "ing");
            response.setAcl(new Acl.Builder()
                .setInheritFrom(new DocId("testing")).build());
            response.getOutputStream();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertNull(ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
  }

  private DocumentHandlerBuilder createHandlerBuilder() {
    return new DocumentHandlerBuilder()
        .setDocIdDecoder(docIdCodec)
        .setDocIdEncoder(docIdCodec)
        .setJournal(new Journal(new MockTimeProvider()))
        .setAdaptor(new MockAdaptor())
        .setGsaHostname("localhost")
        .setWatchdog(new MockWatchdog())
        .setPusher(new MockPusher());
  }

  private DocumentHandler createDefaultHandlerForAdaptor(Adaptor adaptor) {
    AuthzAuthority authzAuthority = null;
    if (adaptor instanceof AuthzAuthority) {
      authzAuthority = (AuthzAuthority) adaptor;
    }
    return createHandlerBuilder().setAdaptor(adaptor)
        .setAuthzAuthority(authzAuthority).build();
  }

  @Test
  public void testFormMetadataHeader() {
    Metadata metadata = new Metadata();
    metadata.add("test", "ing");
    metadata.add("another", "item");
    metadata.add("equals=", "==");
    String result = DocumentHandler.formMetadataHeader(metadata);
    assertEquals("another=item,equals%3D=%3D%3D,test=ing", result);
  }

  @Test
  public void testFormMetadataHeaderEmpty() {
    String header = DocumentHandler.formMetadataHeader(new Metadata());
    assertEquals("", header);
  }

  private static UserPrincipal u(String name) {
    return new UserPrincipal(name);
  }

  private static UserPrincipal u(String name, String ns) {
    return new UserPrincipal(name, ns);
  }

  private static GroupPrincipal g(String name) {
    return new GroupPrincipal(name);
  }

  private static GroupPrincipal g(String name, String ns) {
    return new GroupPrincipal(name, ns);
  }

  @Test
  public void testFormAclHeader() {
    final String golden
        = "google%3Aaclusers=pu1,google%3Aaclusers=uid%3Dpu2%2Cdc%3Dcom,"
        + "google%3Aaclgroups=gid%3Dpg2%2Cdc%3Dcom,google%3Aaclgroups=pg1,"
        + "google%3Aacldenyusers=du1,"
        + "google%3Aacldenyusers=uid%3Ddu2%2Cdc%3Dcom,"
        + "google%3Aacldenygroups=dg1,"
        + "google%3Aacldenygroups=gid%3Ddg2%2Cdc%3Dcom,"
        // The space is encoded as %20 by URI, and then that string is encoded
        // by percentEncode to give %2520.
        + "google%3Aaclinheritfrom=http%3A%2F%2Flocalhost%2Fsome%2520docId,"
        + "google%3Aaclinheritancetype=parent-overrides";
    String result = DocumentHandler.formUnqualifiedAclHeader(new Acl.Builder()
        .setPermitUsers(Arrays.asList(u("pu1"), u("uid=pu2,dc=com")))
        .setPermitGroups(Arrays.asList(g("pg1"), g("gid=pg2,dc=com")))
        .setDenyUsers(Arrays.asList(u("du1"), u("uid=du2,dc=com")))
        .setDenyGroups(Arrays.asList(g("dg1"), g("gid=dg2,dc=com")))
        .setInheritFrom(new DocId("some docId"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .build(), new MockDocIdCodec());
    assertEquals(golden, result);
  }

  @Test
  public void testFormUnqualifiedAclHeaderNull() {
    assertEquals("", DocumentHandler
        .formUnqualifiedAclHeader(null, new MockDocIdCodec()));
   }

  @Test
  public void testFormUnqualifiedAclHeaderEmpty() {
    DocIdEncoder enc = new MockDocIdCodec();
    assertEquals("google%3Aacldenyusers=google%3AfakeUserToPreventMissingAcl",
        DocumentHandler.formUnqualifiedAclHeader(Acl.EMPTY, enc));
  }

  @Test
  public void testFormNamespacedAclHeaderNull() {
    assertEquals("", DocumentHandler
        .formNamespacedAclHeader(null, new MockDocIdCodec()));
  }

  @Test
  public void testFormNamespacedAclHeaderEmpty() {
    DocIdEncoder enc = new MockDocIdCodec();
    String golden = "{\"entries\":[{"
        + "\"access\":\"deny\""
        + ","
        + "\"name\":\"google:fakeUserToPreventMissingAcl\""
        + ","
        + "\"scope\":\"user\""
        + "}]}";
    String aclHeader = DocumentHandler.formNamespacedAclHeader(Acl.EMPTY, enc);
    assertEquals(golden, aclHeader);
  }

  @Test
  public void testFormNamespacedAclHeaderFragment() {
    DocIdEncoder enc = new MockDocIdCodec();
    String golden = "{\"inherit_from\":\"http:\\/\\/localhost\\/t?f?=$%25\"}";
    String aclHeader = DocumentHandler.formNamespacedAclHeader(new Acl.Builder()
        .setInheritFrom(new DocId("t"), "f?=$%").build(), enc);
    assertEquals(golden, aclHeader);
  }

  @Test
  public void testFormNamespacedAclHeaderBusy() {
    DocIdEncoder enc = new MockDocIdCodec();
    String golden = "{"
        + "\"entries\":["
            + "{\"access\":\"permit\","
                + "\"case_sensitivity_type\":\"everything_case_insensitive\","
                + "\"name\":\"pg1@d.g\",\"scope\":\"group\"},"
            + "{\"access\":\"permit\","
                + "\"case_sensitivity_type\":\"everything_case_insensitive\","
                + "\"name\":\"gid=pg2,dc=m\",\"namespace\":\"ns\","
                + "\"scope\":\"group\"},"
            + "{\"access\":\"deny\","
                + "\"case_sensitivity_type\":\"everything_case_insensitive\","
                + "\"name\":\"gid=dg2,dc=com\",\"scope\":\"group\"},"
            + "{\"access\":\"deny\","
                + "\"case_sensitivity_type\":\"everything_case_insensitive\","
                + "\"name\":\"dg1@d.g\",\"namespace\":\"ns\","
                + "\"scope\":\"group\"},"
            + "{\"access\":\"permit\","
                + "\"case_sensitivity_type\":\"everything_case_insensitive\","
                + "\"name\":\"uid=pu2,dc=m\",\"scope\":\"user\"},"
            + "{\"access\":\"permit\","
                + "\"case_sensitivity_type\":\"everything_case_insensitive\","
                + "\"name\":\"pu1@d.g\",\"namespace\":\"ns\","
                + "\"scope\":\"user\"},"
            + "{\"access\":\"deny\","
                + "\"case_sensitivity_type\":\"everything_case_insensitive\","
                + "\"name\":\"du1@d.g\",\"scope\":\"user\"},"
            + "{\"access\":\"deny\","
                + "\"case_sensitivity_type\":\"everything_case_insensitive\","
                + "\"name\":\"uid=du2,dc=com\",\"namespace\":\"ns\","
                + "\"scope\":\"user\"}"
        + "],"
        + "\"inherit_from\":\"http:\\/\\/localhost\\/some%20docId\","
        + "\"inheritance_type\":\"PARENT_OVERRIDES\""
        + "}";

    Acl busyAcl = new Acl.Builder()
        .setPermitUsers(Arrays.asList(u("pu1@d.g", "ns"), u("uid=pu2,dc=m")))
        .setPermitGroups(Arrays.asList(g("pg1@d.g"), g("gid=pg2,dc=m", "ns")))
        .setDenyUsers(Arrays.asList(u("du1@d.g"), u("uid=du2,dc=com", "ns")))
        .setDenyGroups(Arrays.asList(g("dg1@d.g", "ns"), g("gid=dg2,dc=com")))
        .setInheritFrom(new DocId("some docId"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setEverythingCaseInsensitive()
        .build();
    String aclHeader = DocumentHandler.formNamespacedAclHeader(busyAcl, enc);
    assertEquals(golden, aclHeader);
  }

  @Test
  public void testDisplayUrlHeader() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            try {
              response.setDisplayUrl(new URI("http://www.google.com"));
            } catch (URISyntaxException urie) {
              throw new RuntimeException(urie);
            }
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[]{remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertTrue(ex.getResponseHeaders().get("X-Gsa-Doc-Controls")
        .contains("display_url=http%3A%2F%2Fwww.google.com"));
  }

  @Test
  public void testLockHeaderTrueSent() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setLock(true);
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[]{remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertTrue(ex.getResponseHeaders().get("X-Gsa-Doc-Controls")
        .contains("lock=true"));
  }

  @Test
  public void testScoringTypeSent() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[]{remoteIp})
        .setSendDocControls(true)
        .setScoringType("guess!")
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertTrue(ex.getResponseHeaders().get("X-Gsa-Doc-Controls")
        .contains("scoring=guess!"));
  }

  @Test
  public void testLockHeaderFalseSent() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setLock(false);
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[]{remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertTrue(ex.getResponseHeaders().get("X-Gsa-Doc-Controls")
        .contains("lock=false"));
  }

  @Test
  public void testCrawlOnceHeaderTrueSent() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setCrawlOnce(true);
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[]{remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertTrue(ex.getResponseHeaders().get("X-Gsa-Doc-Controls")
        .contains("crawl_once=true"));
  }

  @Test
  public void testCrawlOnceHeaderFalseSent() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setCrawlOnce(false);
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[]{remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertTrue(ex.getResponseHeaders().get("X-Gsa-Doc-Controls")
        .contains("crawl_once=false"));
  }

  @Test
  public void testAclMeansServeSecurity() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setAcl(new Acl.Builder()
                .setInheritFrom(new DocId("testing")).build());
            response.setSecure(false);
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[]{remoteIp, "someUnknownHost!@#$"})
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("secure",
        ex.getResponseHeaders().getFirst("X-Gsa-Serve-Security"));
  }

  @Test
  public void testMarkPublicOverridesAclDocControlsVersion() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setAcl(new Acl.Builder()
                .setPermitUsers(Arrays.asList(
                    new UserPrincipal("usr2"), new UserPrincipal("usr", "ns")))
                .setInheritFrom(new DocId("testing")).build());
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setMarkDocsPublic(true)
        .setSendDocControls(true)
        .setFullAccessHosts(new String[] {remoteIp})
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("public",
        ex.getResponseHeaders().getFirst("X-Gsa-Serve-Security"));
    assertEquals("acl=",
        ex.getResponseHeaders().get("X-Gsa-Doc-Controls").get(0));
  }

  @Test
  public void testMarkPublicOverridesAclMetadataVersion() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setAcl(new Acl.Builder()
                .setPermitUsers(Arrays.asList(
                    new UserPrincipal("usr2"), new UserPrincipal("usr", "ns")))
                .setInheritFrom(new DocId("testing")).build());
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setMarkDocsPublic(true)
        .setFullAccessHosts(new String[] {remoteIp})
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("public",
        ex.getResponseHeaders().getFirst("X-Gsa-Serve-Security"));
    assertEquals("",
        ex.getResponseHeaders().get("X-Gsa-External-Metadata").get(0));
  }

  @Test
  public void testMarkPublicOverridesExplicitSecure() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setSecure(true);
            response.getOutputStream();
          }
        };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setMarkDocsPublic(true)
        .setSendDocControls(true)
        .setFullAccessHosts(new String[] {remoteIp})
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("public",
        ex.getResponseHeaders().getFirst("X-Gsa-Serve-Security"));
  }

  @Test
  public void testEmulatedFields() throws Exception {
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    MockAdaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException {
        response.setDisplayUrl(URI.create("http://example.com"));
        response.setCrawlOnce(true);
        response.setLock(true);
        response.getOutputStream();
      }
    };
    DocumentHandler.AsyncPusher pusher = new DocumentHandler.AsyncPusher() {
      @Override
      public boolean asyncPushItem(DocIdSender.Item item) {
        assertTrue(item instanceof DocIdPusher.Record);
        DocIdPusher.Record record = (DocIdPusher.Record) item;
        assertEquals(URI.create("http://example.com"), record.getResultLink());
        assertTrue(record.isToBeCrawledOnce());
        assertTrue(record.isToBeLocked());
        return true;
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setPusher(pusher)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testEmulatedAcls() throws Exception {
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    final AtomicReference<Acl> providedAcl = new AtomicReference<Acl>();
    MockAdaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException {
        response.setAcl(providedAcl.get());
        response.getOutputStream();
      }
    };
    DocumentHandlerBuilder builder = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp});
    DocumentHandler handler;

    providedAcl.set(new Acl.Builder()
        .setPermitUsers(Arrays.asList(
            new UserPrincipal("user2"), new UserPrincipal("user", "ns")))
        .build());
    handler = builder.setPusher(new DocumentHandler.AsyncPusher() {
          @Override
          public boolean asyncPushItem(DocIdSender.Item item) {
            assertTrue(item instanceof DocIdSender.AclItem);
            DocIdSender.AclItem aclItem = (DocIdSender.AclItem) item;
            assertEquals(defaultDocId, aclItem.getDocId());
            assertEquals("generated", aclItem.getDocIdFragment());
            assertEquals(new Acl.Builder(providedAcl.get())
                .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
                .build(),
                aclItem.getAcl());
            return true;
          }
        })
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals(Arrays.asList("", "google%3Aaclinheritfrom="
          + "http%3A%2F%2Flocalhost%2Ftest%2520docId%3Fgenerated"),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));

    providedAcl.set(new Acl.Builder()
        .setDenyUsers(Arrays.asList(new UserPrincipal("user", "ns")))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .build());
    handler = builder.setPusher(new DocumentHandler.AsyncPusher() {
          @Override
          public boolean asyncPushItem(DocIdSender.Item item) {
            assertTrue(item instanceof DocIdSender.AclItem);
            DocIdSender.AclItem aclItem = (DocIdSender.AclItem) item;
            assertEquals(defaultDocId, aclItem.getDocId());
            assertEquals("generated", aclItem.getDocIdFragment());
            assertEquals(providedAcl.get(), aclItem.getAcl());
            return true;
          }
        })
        .build();
    ex = new MockHttpExchange("GET", defaultPath, new MockHttpContext("/"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals(Arrays.asList("", "google%3Aaclinheritfrom="
          + "http%3A%2F%2Flocalhost%2Ftest%2520docId%3Fgenerated,"
          + "google%3Aaclinheritancetype=parent-overrides"),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));

    providedAcl.set(new Acl.Builder()
        .setPermitGroups(Arrays.asList(new GroupPrincipal("group", "ns")))
        .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
        .build());
    handler = builder.setPusher(new DocumentHandler.AsyncPusher() {
          @Override
          public boolean asyncPushItem(DocIdSender.Item item) {
            assertTrue(item instanceof DocIdSender.AclItem);
            DocIdSender.AclItem aclItem = (DocIdSender.AclItem) item;
            assertEquals(defaultDocId, aclItem.getDocId());
            assertEquals("generated", aclItem.getDocIdFragment());
            assertEquals(providedAcl.get(), aclItem.getAcl());
            return true;
          }
        })
        .build();
    ex = new MockHttpExchange("GET", defaultPath, new MockHttpContext("/"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals(Arrays.asList("", "google%3Aaclinheritfrom="
          + "http%3A%2F%2Flocalhost%2Ftest%2520docId%3Fgenerated,"
          + "google%3Aaclinheritancetype=child-overrides"),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));

    providedAcl.set(new Acl.Builder()
        .setDenyGroups(Arrays.asList(new GroupPrincipal("group", "ns")))
        .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
        .build());
    handler = builder.setPusher(new DocumentHandler.AsyncPusher() {
          @Override
          public boolean asyncPushItem(DocIdSender.Item item) {
            assertTrue(item instanceof DocIdSender.AclItem);
            DocIdSender.AclItem aclItem = (DocIdSender.AclItem) item;
            assertEquals(defaultDocId, aclItem.getDocId());
            assertEquals("generated", aclItem.getDocIdFragment());
            assertEquals(providedAcl.get(), aclItem.getAcl());
            return true;
          }
        })
        .build();
    ex = new MockHttpExchange("GET", defaultPath, new MockHttpContext("/"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals(Arrays.asList("", "google%3Aaclinheritfrom="
          + "http%3A%2F%2Flocalhost%2Ftest%2520docId%3Fgenerated,"
          + "google%3Aaclinheritancetype=child-overrides"),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));

  }

  @Test
  public void testEmulatedAclsFail() throws Exception {
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    final Acl providedAcl = new Acl.Builder()
        .setDenyGroups(Arrays.asList(new GroupPrincipal("group")))
        .setInheritanceType(Acl.InheritanceType.AND_BOTH_PERMIT)
        .setEverythingCaseInsensitive()
        .build();
    MockAdaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException {
        response.setAcl(providedAcl);
        response.getOutputStream();
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setPusher(new DocumentHandler.AsyncPusher() {
          @Override
          public boolean asyncPushItem(DocIdSender.Item item) {
            fail("Should not have been called");
            return false;
          }
        })
        .setFullAccessHosts(new String[] {remoteIp})
        .build();
    ex = new MockHttpExchange("GET", defaultPath, new MockHttpContext("/"));
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testFormAnchorHeaderEmpty() {
    assertEquals("", DocumentHandler.formAnchorHeader(
        Collections.<URI>emptyList(), Collections.<String>emptyList()));
  }

  @Test
  public void testPercentEncoding() {
    String encoded = DocumentHandler.percentEncode(
        "AaZz09-_.~`=/?+';\\/\"!@#$%^&*()[]{}ë\u0001");
    assertEquals("AaZz09-_.~%60%3D%2F%3F%2B%27%3B%5C%2F%22%21%40%23%24%25%5E%26"
                 + "%2A%28%29%5B%5D%7B%7D%C3%AB%01", encoded);
  }

  // tests for hostsSkipCertCheck

  // server.fullAccessHosts=NOT_OUR_IP_ADDRESS
  // server.skipCertCheckHosts=NOT_OUR_IP_ADDRESS
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=untrusted
  // result: 403, no headers
  @Test
  public void testHttpsConnectionFromUntrustedCN() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=untrusted, OU=Unknown, O=Unknown, C=Unknown")));
    
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("testing key", "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setSkipCertHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};

    handler.handle(httpsEx);

    // response code 403
    // requestor's certificate CN is no equal to "localhost"
    assertEquals(403, httpsEx.getResponseCode());
    assertNull(httpsEx.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  // server.fullAccessHosts=NOT_OUR_IP_ADDRESS
  // server.skipCertCheckHosts=127.0.0.3
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=untrusted
  // result: 403, no headers
  @Test
  public void testIpSkipsCertCheckButIpIsNotFullAccess() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=untrusted, OU=Unknown, O=Unknown, C=Unknown")));

    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("testing key", "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setSkipCertHosts(new String[] {"127.0.0.3"})
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};

    handler.handle(httpsEx);

    // response code 403 - SkipCertHosts contains requestor IP
    // this is not enough to return document content (document is not public, no user is provided)
    assertEquals(403, httpsEx.getResponseCode());
    assertNull(httpsEx.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  // server.fullAccessHosts=127.0.0.3
  // server.skipCertCheckHosts=127.0.0.3
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=untrusted
  // result: 200, headers are returned
  @Test
  public void testIpSkipsCertCheckAndIpIsFullAccess() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=untrusted, OU=Unknown, O=Unknown, C=Unknown")));

    final String key = "testing key";
    List<MetadataTransform> transforms
        = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {"127.0.0.3"})
        .setSkipCertHosts(new String[] {"127.0.0.3"})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};

    handler.handle(httpsEx);

    // response code 200 - requestor's certificate is not equal to "localhost"
    // but setSkipCertHosts contains requestor IP AND setFullAccessHosts 
    // permits GSA headers
    assertEquals(200, httpsEx.getResponseCode());
    assertEquals(Arrays.asList("testing%20key=TESTING%20VALUE", ""),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  // server.fullAccessHosts=127.0.0.0/8
  // server.skipCertCheckHosts=127.0.0.0/8
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=untrusted
  // result: 403, no headers
  @Test
  public void testIpSkipsCertCheckAndIpIsFullAccessRange() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=untrusted, OU=Unknown, O=Unknown, C=Unknown")));
    
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("testing key", "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {"127.0.0.0/8"})
        .setSkipCertHosts(new String[] {"127.0.0.3/8"})
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};

    handler.handle(httpsEx);

    // response code 403 due
    // setSkipCertHosts doesn't support networks
    assertEquals(403, httpsEx.getResponseCode());
    assertNull(ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  // server.fullAccessHosts=127.0.0.3
  // server.skipCertCheckHosts=NOT_OUR_IP_ADDRESS
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=localhost
  // result: 200, headers are returned
  @Test
  public void testSecureConnectionFromTrustedCn() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=localhost, OU=Unknown, O=Unknown, C=Unknown")));

    final String key = "testing key";
    List<MetadataTransform> transforms
        = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {"127.0.0.3"})
        .setSkipCertHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};

    handler.handle(httpsEx);

    // response code 200 - requestor's certificate CN is equal to "localhost"
    // and setFullAccessHosts permits GSA headers
    assertEquals(200, httpsEx.getResponseCode());
    assertEquals(Arrays.asList("testing%20key=TESTING%20VALUE", ""),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  // server.fullAccessHosts=NOT_OUR_IP_ADDRESS
  // server.skipCertCheckHosts=NOT_OUR_IP_ADDRESS
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=localhost
  // result: 200, headers are returned
  @Test
  public void testSecureConnectionFromTrustedCnWithoutIpCheck()
      throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=localhost, OU=Unknown, O=Unknown, C=Unknown")));
  
    final String key = "testing key";
    List<MetadataTransform> transforms
        = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setSkipCertHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
  
    handler.handle(httpsEx);
  
    // response code 200 - requestor's certificate CN is equal to "localhost"
    assertEquals(200, httpsEx.getResponseCode());
    assertEquals(Arrays.asList("testing%20key=TESTING%20VALUE", ""),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
    }

  // server.fullAccessHosts=127.0.0.3
  // server.skipCertCheckHosts=NOT_OUR_IP_ADDRESS
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=untrusted
  // result: 403, no headers
  @Test
  public void testSecureConnectionFromUntrustedCnFullAccessIPDoesNotMatter()
      throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=untrusted, OU=Unknown, O=Unknown, C=Unknown")));

    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata("testing key", "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setFullAccessHosts(new String[] {"127.0.0.3"})
        .setSkipCertHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};

    handler.handle(httpsEx);

    // response code 403 - adaptor does not trusts to CN=untrusted
    // and setSkipCertHosts doesn't contains requestor IP
    assertEquals(403, httpsEx.getResponseCode());
    // X-Gsa-External-Metadata not provided
    assertNull(httpsEx.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  // server.fullAccessHosts=NOT_OUR_IP_ADDRESS
  // server.skipCertCheckHosts=127.0.0.3
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=untrusted
  // result: 200, no headers
  @Test
  public void testUntrustedCnGetsPublicDocButNoHeaders() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=untrusted, OU=Unknown, O=Unknown, C=Unknown")));

    final String key = "testing key";
    List<MetadataTransform> transforms
        = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));

    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setMarkDocsPublic(true)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setSkipCertHosts(new String[] {"127.0.0.3"})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};

    handler.handle(httpsEx);

    // response code 200 - requestor is in skipCertHosts, doc is public
    assertEquals(200, httpsEx.getResponseCode());
    // no headers provided, since requestor is not in fullAccessHosts
    assertNull(httpsEx.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  // server.fullAccessHosts=NOT_OUR_IP_ADDRESS
  // server.skipCertCheckHosts=NOT_OUR_IP_ADDRESS
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=localhost
  // result: 200, headers are returned
  @Test
  public void testTrustedCNGetsPublicDoc() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=localhost, OU=Unknown, O=Unknown, C=Unknown")));
  
    final String key = "testing key";
    List<MetadataTransform> transforms
        = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));
  
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setMarkDocsPublic(true)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setSkipCertHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
  
    handler.handle(httpsEx);
  
    // response code 200:
    // requestor's certificate CN is equal to "localhost"
    assertEquals(200, httpsEx.getResponseCode());
    assertEquals(Arrays.asList("testing%20key=TESTING%20VALUE", ""),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  // server.fullAccessHosts=NOT_OUR_IP_ADDRESS
  // server.skipCertCheckHosts=127.0.0.3
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=untrusted
  // saml returns PERMIT
  // result: 200, no headers
  @Test
  public void testSkipCertCheckButNotFullAccessHost() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=untrusted, OU=Unknown, O=Unknown, C=Unknown")));

    final String key = "testing key";
    List<MetadataTransform> transforms
        = new LinkedList<MetadataTransform>();
    transforms.add(new MetadataTransform() {
      @Override
      public void transform(Metadata metadata, Map<String, String> params) {
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
      }
    });
    MetadataTransformPipeline transform = new MetadataTransformPipeline(transforms,
        Arrays.asList("t1"));

    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());
    
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setSamlServiceProvider(samlServiceProvider)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setSkipCertHosts(new String[] {"127.0.0.3"})
        .setMetadataTransform(transform)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};

    handler.handle(httpsEx);

    // response code 200 - user has access to this document
    assertEquals(200, httpsEx.getResponseCode());
    assertNull(httpsEx.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }
  
  // server.fullAccessHosts=NOT_OUR_IP_ADDRESS
  // server.skipCertCheckHosts=127.0.0.3
  // requestor's certificate CN must be equal to "localhost"
  // requestor ip=127.0.0.3
  // requestor CN=untrusted
  // saml returns DENY
  // result: 403, no headers
  @Test
  public void testIpSkipsCertCheckUserIsDenied() throws Exception {
    MockHttpsExchange httpsEx = new MockHttpsExchange(ex, new MockSslSession(
        new X500Principal("CN=untrusted, OU=Unknown, O=Unknown, C=Unknown")));

    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());    

    PrivateMockAdaptor adaptor = new PrivateMockAdaptor();

    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setSamlServiceProvider(samlServiceProvider)
        .setFullAccessHosts(new String[] {NOT_OUR_IP_ADDRESS})
        .setSkipCertHosts(new String[] {"127.0.0.3"})
        .build();

    handler.handle(httpsEx);

    // response code 403 - user doesn't have access to this document
    assertEquals(403, httpsEx.getResponseCode());
  }

  private static class UserPrivateMockAdaptor extends MockAdaptor {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity identity,
          Collection<DocId> ids) {
        Map<DocId, AuthzStatus> result
            = new HashMap<DocId, AuthzStatus>(ids.size() * 2);
        for (DocId id : ids) {
          result.put(id,
              identity == null ? AuthzStatus.DENY : AuthzStatus.PERMIT);
        }
        return Collections.unmodifiableMap(result);
      }
    };

  private static class MockPusher implements DocumentHandler.AsyncPusher {
    @Override
    public boolean asyncPushItem(DocIdSender.Item item) {
      fail("Should not have been called");
      return false;
    }
  }

  private static class MockSamlServiceProvider extends SamlServiceProvider {
    static {
      GsaCommunicationHandler.bootstrapOpenSaml();
    }

    private AuthnIdentity identity;

    public MockSamlServiceProvider() {
      super(new SessionManager<HttpExchange>(
            new SessionManager.HttpExchangeClientStore(), 1000, 1000),
          new SamlMetadata("localhost", 80,
            "thegsa", "http://google.com/enterprise/gsa/security-manager",
            "http://google.com/enterprise/gsa/adaptor"),
          /* KeyPair = */ null,
          Principal.DomainFormat.DNS);
    }

    @Override
    public HttpHandler getAssertionConsumer() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AuthnIdentity getUserIdentity(HttpExchange ex) {
      return identity;
    }

    public void setUserIdentity(AuthnIdentity identity) {
      this.identity = identity;
    }

    @Override
    public void handleAuthentication(HttpExchange ex) throws IOException {
      throw new UnsupportedOperationException();
    }
  }

  private static class DocumentHandlerBuilder {
    private DocIdDecoder docIdDecoder;
    private DocIdEncoder docIdEncoder;
    private Journal journal;
    private Adaptor adaptor;
    private AuthzAuthority authzAuthority;
    private String gsaHostname;
    private String[] fullAccessHosts = new String[0];
    private String[] skipCertHosts = new String[0];
    private SamlServiceProvider samlServiceProvider;
    private MetadataTransformPipeline transform;
    private ContentTransformFactory contentTransformPipeline;
    private AclTransform aclTransform
        = new AclTransform(Arrays.<AclTransform.Rule>asList());
    private int transformMaxBytes;
    private boolean transformRequired;
    private boolean useCompression;
    private Watchdog watchdog;
    private DocumentHandler.AsyncPusher pusher;
    private boolean sendDocControls;
    private boolean markDocsPublic;
    private long headerTimeoutMillis = 30 * 1000;
    private long contentTimeoutMillis = 180 * 1000;
    private String scoring = "content";
    private boolean alwaysGiveAclsAndMetadata = false;
    private GsaVersion gsaVersion = new GsaVersion("7.2.0-0");
    
    public DocumentHandlerBuilder setDocIdDecoder(DocIdDecoder docIdDecoder) {
      this.docIdDecoder = docIdDecoder;
      return this;
    }

    public DocumentHandlerBuilder setDocIdEncoder(DocIdEncoder docIdEncoder) {
      this.docIdEncoder = docIdEncoder;
      return this;
    }

    public DocumentHandlerBuilder setJournal(Journal journal) {
      this.journal = journal;
      return this;
    }

    public DocumentHandlerBuilder setAdaptor(Adaptor adaptor) {
      this.adaptor = adaptor;
      return this;
    }

    public DocumentHandlerBuilder setAuthzAuthority(
        AuthzAuthority authzAuthority) {
      this.authzAuthority = authzAuthority;
      return this;
    }

    public DocumentHandlerBuilder setGsaHostname(String gsaHostname) {
      this.gsaHostname = gsaHostname;
      return this;
    }

    public DocumentHandlerBuilder setFullAccessHosts(String[] fullAccessHosts) {
      this.fullAccessHosts = fullAccessHosts;
      return this;
    }

    public DocumentHandlerBuilder setSkipCertHosts(String[] skipCertHosts) {
      this.skipCertHosts = skipCertHosts;
      return this;
    }

    public DocumentHandlerBuilder setSamlServiceProvider(
        SamlServiceProvider samlServiceProvider) {
      this.samlServiceProvider = samlServiceProvider;
      return this;
    }

    public DocumentHandlerBuilder setMetadataTransform(
        MetadataTransformPipeline transform) {
      this.transform = transform;
      return this;
    }

    public DocumentHandlerBuilder setContentTransformPipeline(
        ContentTransformFactory contentTransformPipeline) {
      this.contentTransformPipeline = contentTransformPipeline;
      return this;
    }

    public DocumentHandlerBuilder setAclTransform(AclTransform aclTransform) {
      this.aclTransform = aclTransform;
      return this;
    }

    public DocumentHandlerBuilder setUseCompression(boolean useCompression) {
      this.useCompression = useCompression;
      return this;
    }

    public DocumentHandlerBuilder setWatchdog(Watchdog watchdog) {
      this.watchdog = watchdog;
      return this;
    }

    public DocumentHandlerBuilder setPusher(
        DocumentHandler.AsyncPusher pusher) {
      this.pusher = pusher;
      return this;
    }

    public DocumentHandlerBuilder setSendDocControls(boolean sendDocControls) {
      this.sendDocControls = sendDocControls;
      return this;
    }

    public DocumentHandlerBuilder setMarkDocsPublic(boolean markDocsPublic) {
      this.markDocsPublic = markDocsPublic;
      return this;
    }

    public DocumentHandlerBuilder setHeaderTimeoutMillis(
        long headerTimeoutMillis) {
      this.headerTimeoutMillis = headerTimeoutMillis;
      return this;
    }

    public DocumentHandlerBuilder setContentTimeoutMillis(
        long contentTimeoutMillis) {
      this.contentTimeoutMillis = contentTimeoutMillis;
      return this;
    }

    public DocumentHandlerBuilder setScoringType(String scoringType) {
      this.scoring = scoringType;
      return this;
    }
    
    public DocumentHandlerBuilder setGsaVersion(String gsaVersion) {
      this.gsaVersion = new GsaVersion(gsaVersion);
      return this;
    }

    public DocumentHandlerBuilder setAlwaysGiveAclsAndMetadata(
        boolean alwaysGiveAclsAndMetadata) {
      this.alwaysGiveAclsAndMetadata = alwaysGiveAclsAndMetadata;
      return this;
    }

    public DocumentHandler build() {
      return new DocumentHandler(docIdDecoder, docIdEncoder, journal, adaptor,
          authzAuthority, gsaHostname, fullAccessHosts, skipCertHosts, samlServiceProvider,
          transform, aclTransform, contentTransformPipeline, useCompression,
          watchdog, pusher, sendDocControls, markDocsPublic,
          headerTimeoutMillis, contentTimeoutMillis, scoring,
          alwaysGiveAclsAndMetadata, gsaVersion);
    }
  }
}
