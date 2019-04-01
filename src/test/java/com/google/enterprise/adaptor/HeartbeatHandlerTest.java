// Copyright 2016 Google Inc. All Rights Reserved.
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
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tests for {@link HeartbeatHandler}.
 */
public class HeartbeatHandlerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MockAdaptor mockAdaptor = new MockAdaptor();
  private MockDocIdCodec docIdCodec = new MockDocIdCodec();
  private DocumentHandler docHandler = createDocHandlerForAdaptor(
      mockAdaptor);
  private HeartbeatHandler handler = createHeartbeatHandlerBuilder().build();
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
    createHeartbeatHandlerBuilder().build();
  }

  @Test
  public void testNullHeartbeatDecoder() {
    thrown.expect(NullPointerException.class);
    createHeartbeatHandlerBuilder().setHeartbeatDecoder(null).build();
  }

  @Test
  public void testNullDocIdEncoder() {
    thrown.expect(NullPointerException.class);
    createHeartbeatHandlerBuilder().setDocIdEncoder(null).build();
  }

  @Test
  public void testNullDocHandler() {
    thrown.expect(NullPointerException.class);
    createHeartbeatHandlerBuilder().setDocHandler(null).build();
  }

  @Test
  public void testNullWatchdog() {
    thrown.expect(NullPointerException.class);
    createHeartbeatHandlerBuilder().setWatchdog(null).build();
  }

  @Test
  public void testSecurityPermit() throws Exception {
    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());
    UserPrivateMockAdaptor adaptor = new UserPrivateMockAdaptor();
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(adaptor).setAuthzAuthority(adaptor)
        .setSamlServiceProvider(samlServiceProvider).build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
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
    DocumentHandler docHandler = createDocHandlerForAdaptor(mockAdaptor);
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testSecurityDisallowedUser() throws Exception {
    Adaptor adaptor = new PrivateMockAdaptor();
    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(adaptor).setSamlServiceProvider(samlServiceProvider)
        .build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testAuthzSkippedWhenAllDocsMarkedAsPublic()
      throws Exception {
    Adaptor adaptor = new PrivateMockAdaptor();
    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(adaptor).setSamlServiceProvider(samlServiceProvider)
        .setMarkDocsPublic(true).build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testSecMgrDeniedDespiteAllDocsMarkedAsPublic()
      throws Exception {
    Adaptor adaptor = new PrivateMockAdaptor();
    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(adaptor).setSamlServiceProvider(samlServiceProvider)
        .setMarkDocsPublic(true).build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    ex.getRequestHeaders().add("User-Agent", "SecMgr");
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testNormal() throws Exception {
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(new byte[] {}, ex.getResponseBytes());
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
  public void testWatchdogInterruptionByDocHandler() throws Exception {
    ScheduledExecutorService executor
        = Executors.newSingleThreadScheduledExecutor();
    Watchdog watchdog = new Watchdog(executor);
    Watchdog watchdog2 = new Watchdog(executor);
    Adaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        Thread.sleep(100);
        super.getDocContent(request, response);
      }
    };
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(adaptor)
        .setWatchdog(watchdog)
        .setHeaderTimeoutMillis(1)
        .build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setWatchdog(watchdog2).setDocHandler(docHandler).build();
    try {
      handler.handle(ex);
    } finally {
      executor.shutdownNow();
    }
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testWatchdogInterruptionByHeartbeatHandler() throws Exception {
    ScheduledExecutorService executor
        = Executors.newSingleThreadScheduledExecutor();
    Watchdog watchdog = new Watchdog(executor);
    Watchdog watchdog2 = new Watchdog(executor);
    Adaptor adaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        Thread.sleep(100);
        super.getDocContent(request, response);
      }
    };
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(adaptor)
        .setWatchdog(watchdog)
        .setHeaderTimeoutMillis(100)
        .setContentTimeoutMillis(100)
        .build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setWatchdog(watchdog2).setDocHandler(docHandler)
        .setTimeoutMillis(50)
        .build();
    try {
      handler.handle(ex);
    } finally {
      executor.shutdownNow();
    }
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testWatchdogNoInterruption() throws Exception {
    ScheduledExecutorService executor
        = Executors.newSingleThreadScheduledExecutor();
    Watchdog watchdog = new Watchdog(executor);
    Watchdog watchdog2 = new Watchdog(executor);
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException, InterruptedException {
        Thread.sleep(100);
        super.getDocContent(request, response);
      }
    };
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setAuthzAuthority(mockAdaptor)
        .setWatchdog(watchdog)
        .setHeaderTimeoutMillis(150)
        .setContentTimeoutMillis(150)
        .build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setWatchdog(watchdog2).setDocHandler(docHandler)
        .setTimeoutMillis(150)
        .build();
    try {
      handler.handle(ex);
    } finally {
      executor.shutdownNow();
    }
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testIOExceptionOnDocHandler() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            throw new IOException();
          }
        };
    DocumentHandler docHandler = createDocHandlerForAdaptor(adaptor);
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    thrown.expect(IOException.class);
    handler.handle(ex);
  }

  @Test
  public void testRuntimeExceptionOnDocHandler() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            throw new RuntimeException();
          }
        };
    DocumentHandler docHandler = createDocHandlerForAdaptor(adaptor);
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    thrown.expect(RuntimeException.class);
    handler.handle(ex);
  }

  @Test
  public void testNoOutputOnDocHandler() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
          }
        };
    DocumentHandler docHandler = createDocHandlerForAdaptor(adaptor);
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
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
    DocumentHandler docHandler = createDocHandlerForAdaptor(adaptor);
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
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
    DocumentHandler docHandler = createDocHandlerForAdaptor(adaptor);
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
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
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(adaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    handler.handle(ex);
    assertEquals(204, ex.getResponseCode());
    assertNull(ex.getResponseHeaders().get("X-Gsa-Skip-Updating-Content"));
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
    DocumentHandler docHandler = createDocHandlerBuilder()
        .setAdaptor(adaptor)
        .setAuthzAuthority(adaptor)
        .setSendDocControls(true)
        .setGsaVersion("7.4.0-0")
        .build();
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());
  }

  @Test
  public void testXgsaSkippingHeaders() throws Exception {
    MockSamlServiceProvider samlServiceProvider = new MockSamlServiceProvider();
    samlServiceProvider.setUserIdentity(new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build());
    Adaptor adaptor = new UserPrivateMockAdaptor();
    DocumentHandler docHandler = new DocumentHandler(docIdCodec, docIdCodec,
        new Journal(new MockTimeProvider()), adaptor, (AuthzAuthority) adaptor,
        "localhost", new String[0], new String[0], samlServiceProvider,
        null /* metadataTransformPipeline */,
        new AclTransform(Arrays.<AclTransform.Rule>asList()),
        null /* contentTransformFactory */, false /* useCompression */,
        new MockWatchdog(), new MockPusher(), false /* sendDocControls */,
        false /* markDocsPublic */, 30000 /* headerTimeoutMillis */,
        180000 /* contentTimeoutMillis */, "content",
        false /* alwaysGiveAclsAndMetadata */, new GsaVersion("7.2.0-0")) {
          @Override
          public void handle(HttpExchange ex) throws IOException {
            // add a header that starts with "X-Gsa"
            ex.getResponseHeaders().add("X-Gsa-test", "does not show up");
            // add a header that doesn't start with "X-Gsa"
            ex.getResponseHeaders().add("Gsa-test", "shows up");
            super.handle(ex);
          }
        };
    HeartbeatHandler handler = createHeartbeatHandlerBuilder()
        .setDocHandler(docHandler).build();
    HttpExchange ex = new MockHttpExchange("HEAD", defaultPath,
        new MockHttpContext(handler, "/"));
    ex.getResponseHeaders().add("X-gsa-initial", "set before handle");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    ex = handler.getHttpExchange();
    assertEquals(200, ex.getResponseCode());
    assertNull(ex.getResponseHeaders().get("X-Gsa-Skip-Updating-Content"));
    assertNull(ex.getResponseHeaders().get("X-Gsa-test"));
    assertEquals(Collections.singletonList("shows up"),
        ex.getResponseHeaders().get("Gsa-test"));
    assertEquals(Collections.singletonList("set before handle"),
        ex.getResponseHeaders().get("X-gsa-initial"));
  }

  private DocumentHandlerBuilder createDocHandlerBuilder() {
    return new DocumentHandlerBuilder()
        .setDocIdDecoder(docIdCodec)
        .setDocIdEncoder(docIdCodec)
        .setJournal(new Journal(new MockTimeProvider()))
        .setAdaptor(new MockAdaptor())
        .setGsaHostname("localhost")
        .setWatchdog(new MockWatchdog())
        .setPusher(new MockPusher());
  }

  private DocumentHandler createDocHandlerForAdaptor(Adaptor adaptor) {
    AuthzAuthority authzAuthority = null;
    if (adaptor instanceof AuthzAuthority) {
      authzAuthority = (AuthzAuthority) adaptor;
    }
    return createDocHandlerBuilder().setAdaptor(adaptor)
        .setAuthzAuthority(authzAuthority).build();
  }

  private HeartbeatHandlerBuilder createHeartbeatHandlerBuilder() {
    return new HeartbeatHandlerBuilder()
        .setHeartbeatDecoder(docIdCodec)
        .setDocIdEncoder(docIdCodec)
        .setDocHandler(docHandler)
        .setWatchdog(new MockWatchdog());
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

  private static class HeartbeatHandlerBuilder {
    private DocIdDecoder heartbeatDecoder;
    private DocIdEncoder docIdEncoder;
    private DocumentHandler docHandler;
    private Watchdog watchdog;
    private long timeoutMillis = 30 * 1000;

    public HeartbeatHandlerBuilder setHeartbeatDecoder(
        DocIdDecoder heartbeatDecoder) {
      this.heartbeatDecoder = heartbeatDecoder;
      return this;
    }

    public HeartbeatHandlerBuilder setDocIdEncoder(DocIdEncoder docIdEncoder) {
      this.docIdEncoder = docIdEncoder;
      return this;
    }

    public HeartbeatHandlerBuilder setDocHandler(DocumentHandler docHandler) {
      this.docHandler = docHandler;
      return this;
    }

    public HeartbeatHandlerBuilder setWatchdog(Watchdog watchdog) {
      this.watchdog = watchdog;
      return this;
    }

    public HeartbeatHandlerBuilder setTimeoutMillis(long timeoutMillis) {
      this.timeoutMillis = timeoutMillis;
      return this;
    }

    public HeartbeatHandler build() {
      return new HeartbeatHandler(heartbeatDecoder, docIdEncoder, docHandler,
          watchdog, timeoutMillis);
    }
  }
}
