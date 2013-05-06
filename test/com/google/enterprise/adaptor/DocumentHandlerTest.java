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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.x500.X500Principal;

/**
 * Tests for {@link DocumentHandler}.
 */
public class DocumentHandlerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SessionManager<HttpExchange> sessionManager
      = new SessionManager<HttpExchange>(
          new SessionManager.HttpExchangeClientStore(), 1000, 1000);
  private MockAdaptor mockAdaptor = new MockAdaptor();
  private MockDocIdCodec docIdCodec = new MockDocIdCodec();
  private DocumentHandler handler = createDefaultHandlerForAdaptor(mockAdaptor);
  private DocId defaultDocId = new DocId("test docId");
  private String defaultPath
      = docIdCodec.encodeDocId(defaultDocId).getRawPath();
  private MockHttpExchange ex = new MockHttpExchange("GET", defaultPath,
      new MockHttpContext("/"));

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
  public void testNullSessionManager() {
    thrown.expect(NullPointerException.class);
    createHandlerBuilder().setSessionManager(null).build();
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
  public void testSecurityDenyWithAuthnHandler() throws Exception {
    HttpHandler authnHandler = new HttpHandler() {
      @Override
      public void handle(HttpExchange ex) throws IOException {
        // Translation.HTTP_NOT_FOUND was randomly chosen.
        HttpExchanges.cannedRespond(ex, 1234, Translation.HTTP_NOT_FOUND);
      }
    };
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(new PrivateMockAdaptor())
        .setAuthnHandler(authnHandler).build();
    handler.handle(ex);
    assertEquals(1234, ex.getResponseCode());
  }

  @Test
  public void testSecurityDenySession() throws Exception {
    // Create a new session for this HttpExchange.
    Session session = sessionManager.getSession(ex, true);
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new UserPrivateMockAdaptor());
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityDenyUnauthSession() throws Exception {
    // Create a new unauthenticated session for this HttpExchange.
    Session session = sessionManager.getSession(ex, true);
    AuthnState authn = new AuthnState();
    session.setAttribute(AuthnState.SESSION_ATTR_NAME, authn);
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new UserPrivateMockAdaptor());
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityPermit() throws Exception {
    // Create a new authenticated session for this HttpExchange.
    Session session = sessionManager.getSession(ex, true);
    AuthnState authn = new AuthnState();
    session.setAttribute(AuthnState.SESSION_ATTR_NAME, authn);
    AuthnIdentity identity = new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build();
    authn.authenticated(identity, Long.MAX_VALUE);
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new UserPrivateMockAdaptor());
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
    // Create a new authenticated session for this HttpExchange.
    Session session = sessionManager.getSession(ex, true);
    AuthnState authn = new AuthnState();
    session.setAttribute(AuthnState.SESSION_ATTR_NAME, authn);
    AuthnIdentity identity = new AuthnIdentityImpl
        .Builder(new UserPrincipal("test")).build();
    authn.authenticated(identity, Long.MAX_VALUE);
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
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
    Watchdog watchdog = new Watchdog(1, executor);
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
        .setWatchdog(watchdog)
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
    Watchdog watchdog = new Watchdog(100, executor);
    DocumentHandler handler = createHandlerBuilder()
        .setWatchdog(watchdog)
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
    final byte[] golden = new byte[] {2, 3, 4};
    final String key = "testing key";
    List<DocumentTransform> transforms = new LinkedList<DocumentTransform>();
    transforms.add(new AbstractDocumentTransform() {
      @Override
      public void transform(ByteArrayOutputStream contentIn,
                            OutputStream contentOut,
                            Metadata metadata,
                            Map<String, String> params) throws IOException {
        assertArrayEquals(mockAdaptor.documentBytes, contentIn.toByteArray());
        contentOut.write(golden);
        metadata.set(key, metadata.getOneValue(key).toUpperCase());
        metadata.set("docid", params.get("DocId"));
      }
    });
    TransformPipeline transform = new TransformPipeline(transforms);
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
        .setTransform(transform)
        .setTransformMaxBytes(100)
        .build();
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(golden, ex.getResponseBytes());
    assertEquals("docid=test%20docId,testing%20key=TESTING%20VALUE",
                 ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
  }

  @Test
  public void testTransformDocumentTooLarge() throws Exception {
    List<DocumentTransform> transforms = new LinkedList<DocumentTransform>();
    transforms.add(new AbstractDocumentTransform() {
      @Override
      public void transform(ByteArrayOutputStream contentIn,
                            OutputStream contentOut,
                            Metadata metadata,
                            Map<String, String> params) throws IOException {
        // This is not the content we are looking for.
        contentOut.write(new byte[] {2, 3, 4});
      }
    });
    TransformPipeline transform = new TransformPipeline(transforms);
    final byte[] golden = new byte[] {-1, 2, -3, 4, 5};
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException {
        OutputStream os = response.getOutputStream();
        // Just for the heck of it, test using the single byte version.
        os.write(golden[0]);
        os.write(golden[1]);
        // Write out too much content for the buffer to hold here.
        os.write(golden, 2, golden.length - 2 - 1);
        os.write(golden, golden.length - 1, 1);
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setTransform(transform)
        .setTransformMaxBytes(3)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(golden, ex.getResponseBytes());
    assertEquals(Arrays.asList("", ""),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  @Test
  public void testTransformDocumentTooLargeButRequired() throws Exception {
    TransformPipeline transform = new TransformPipeline(
        Collections.<DocumentTransform>emptyList());
    class CheckFailAdaptor extends MockAdaptor {
      public boolean failedAtCorrectTime = false;

      @Override
      public void getDocContent(Request request, Response response)
          throws IOException {
        OutputStream os = response.getOutputStream();
        os.write(new byte[] {-1, 2, -3});
        failedAtCorrectTime = true;
        // Write out too much content for the buffer to hold here.
        os.write(4);
        failedAtCorrectTime = false;
      }
    };
    CheckFailAdaptor mockAdaptor = new CheckFailAdaptor();
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = createHandlerBuilder()
        .setAdaptor(mockAdaptor)
        .setFullAccessHosts(new String[] {remoteIp})
        .setTransform(transform)
        .setTransformMaxBytes(3)
        .setTransformRequired(true)
        .build();
    thrown.expect(IOException.class);
    try {
      handler.handle(ex);
    } finally {
      assertTrue(mockAdaptor.failedAtCorrectTime);
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
        .setSessionManager(sessionManager)
        .setWatchdog(new MockWatchdog())
        .setPusher(new MockPusher());
  }

  private DocumentHandler createDefaultHandlerForAdaptor(Adaptor adaptor) {
    return createHandlerBuilder().setAdaptor(adaptor).build();
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
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
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
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
        .setSendDocControls(true)
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertTrue(ex.getResponseHeaders().get("X-Gsa-Doc-Controls")
        .contains("lock=true"));
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
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
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
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
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
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
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
        .setFullAccessHosts(new String[] {remoteIp, "someUnknownHost!@#$"})
        .build();
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("secure",
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
      public void asyncPushItem(DocIdSender.Item item) {
        assertTrue(item instanceof DocIdPusher.Record);
        DocIdPusher.Record record = (DocIdPusher.Record) item;
        assertEquals(URI.create("http://example.com"), record.getResultLink());
        assertTrue(record.isToBeCrawledOnce());
        assertTrue(record.isToBeLocked());
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
          public void asyncPushItem(DocIdSender.Item item) {
            assertTrue(item instanceof DocIdSender.AclItem);
            DocIdSender.AclItem aclItem = (DocIdSender.AclItem) item;
            assertEquals(defaultDocId, aclItem.getDocId());
            assertEquals("generated", aclItem.getDocIdFragment());
            assertEquals(new Acl.Builder(providedAcl.get())
                .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
                .build(),
                aclItem.getAcl());
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
          public void asyncPushItem(DocIdSender.Item item) {
            assertTrue(item instanceof DocIdSender.AclItem);
            DocIdSender.AclItem aclItem = (DocIdSender.AclItem) item;
            assertEquals(defaultDocId, aclItem.getDocId());
            assertEquals("generated", aclItem.getDocIdFragment());
            assertEquals(providedAcl.get(), aclItem.getAcl());
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
          public void asyncPushItem(DocIdSender.Item item) {
            assertTrue(item instanceof DocIdSender.AclItem);
            DocIdSender.AclItem aclItem = (DocIdSender.AclItem) item;
            assertEquals(defaultDocId, aclItem.getDocId());
            assertEquals("generated", aclItem.getDocIdFragment());
            assertEquals(providedAcl.get(), aclItem.getAcl());
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
          public void asyncPushItem(DocIdSender.Item item) {
            assertTrue(item instanceof DocIdSender.AclItem);
            DocIdSender.AclItem aclItem = (DocIdSender.AclItem) item;
            assertEquals(defaultDocId, aclItem.getDocId());
            assertEquals("generated", aclItem.getDocIdFragment());
            assertEquals(providedAcl.get(), aclItem.getAcl());
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
          public void asyncPushItem(DocIdSender.Item item) {
            fail("Should not have been called");
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
    public void asyncPushItem(DocIdSender.Item item) {
      fail("Should not have been called");
    }
  }

  private static class DocumentHandlerBuilder {
    private DocIdDecoder docIdDecoder;
    private DocIdEncoder docIdEncoder;
    private Journal journal;
    private Adaptor adaptor;
    private String gsaHostname;
    private String[] fullAccessHosts = new String[0];
    private HttpHandler authnHandler;
    private SessionManager<HttpExchange> sessionManager;
    private TransformPipeline transform;
    private int transformMaxBytes;
    private boolean transformRequired;
    private boolean useCompression;
    private Watchdog watchdog;
    private DocumentHandler.AsyncPusher pusher;
    private boolean sendDocControls;

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

    public DocumentHandlerBuilder setGsaHostname(String gsaHostname) {
      this.gsaHostname = gsaHostname;
      return this;
    }

    public DocumentHandlerBuilder setFullAccessHosts(String[] fullAccessHosts) {
      this.fullAccessHosts = fullAccessHosts;
      return this;
    }

    public DocumentHandlerBuilder setAuthnHandler(HttpHandler authnHandler) {
      this.authnHandler = authnHandler;
      return this;
    }

    public DocumentHandlerBuilder setSessionManager(
        SessionManager<HttpExchange> sessionManager) {
      this.sessionManager = sessionManager;
      return this;
    }

    public DocumentHandlerBuilder setTransform(TransformPipeline transform) {
      this.transform = transform;
      return this;
    }

    public DocumentHandlerBuilder setTransformMaxBytes(int transformMaxBytes) {
      this.transformMaxBytes = transformMaxBytes;
      return this;
    }

    public DocumentHandlerBuilder setTransformRequired(
        boolean transformRequired) {
      this.transformRequired = transformRequired;
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

    public DocumentHandler build() {
      return new DocumentHandler(docIdDecoder, docIdEncoder, journal, adaptor,
          gsaHostname, fullAccessHosts, authnHandler, sessionManager, transform,
          transformMaxBytes, transformRequired, useCompression, watchdog,
          pusher, sendDocControls);
    }
  }

  /** percentDecode method is defined in this test file */
  @Test
  public void testPercentDecoder() {
    assertEquals("" + ((char) 10), percentDecode("%0A"));
    for (char c = 0; c < 256; c++) {
      String encoded = DocumentHandler.percentEncode("" + c);
      String decoded = percentDecode(encoded);
      if (!decoded.equals("" + c)) {
        int n = c;
        throw new AssertionError("failed to encode/decode `" + n
            + "' `" + c + "' `" + encoded + "' `" + decoded + "'");
      }
    }
    String decoded = percentDecode("AaZz09-_.~"
        + "%60%3D%2F%3F%2B%27%3B%5C%2F%22%21%40%23%24%25%5E%26"
        + "%2A%28%29%5B%5D%7B%7D%C3%AB%01");
    assertEquals("AaZz09-_.~`=/?+';\\/\"!@#$%^&*()[]{}ë\u0001", decoded);
  }
 
  private static int hexToInt(byte b) {
    if (b >= '0' && b <= '9') {
      return (byte) (b - '0');
    } else if (b >= 'a' && b <= 'f') {
      return (byte) (b - 'a') + 10;
    } else if (b >= 'A' && b <= 'F') {
      return (byte) (b - 'A') + 10;
    } else {
      throw new IllegalArgumentException("invalid hex byte: " + b);
    }
  }

  private static String percentDecode(String encoded) {
    try {
      byte bytes[] = encoded.getBytes("ASCII");
      ByteArrayOutputStream decoded = percentDecode(bytes);
      return decoded.toString("UTF-8"); 
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException(uee);
    }
  }

  private static ByteArrayOutputStream percentDecode(byte encoded[]) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int i = 0;
    while (i < encoded.length) {
      byte b = encoded[i];
      if (b == '%') {
        int iNeeded = i + 2;  // need two more bytes
        if (iNeeded >= encoded.length) {
          throw new IllegalArgumentException("ends too early");
        }
        int highOrder = hexToInt(encoded[i + 1]);
        int lowOrder = hexToInt(encoded[i + 2]);
        int byteInInt = (highOrder << 4) | lowOrder;
        b = (byte) byteInInt;  // chops top bytes; could make negative
        i += 3;
      } else if ((b >= 'a' && b <= 'z')
          || (b >= 'A' && b <= 'Z')
          || (b >= '0' && b <= '9')
          || b == '-' || b == '_' || b == '.' || b == '~') {
        // pass through
        i++; 
      } else {
        throw new IllegalArgumentException("not percent encoded");
      }
      out.write(b);
    }
    return out;
  }
}
