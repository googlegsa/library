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

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;

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
  private MockHttpExchange ex = new MockHttpExchange("http", "GET", defaultPath,
      new MockHttpContext(null, "/"));

  @Test
  public void testNullDocIdDecoder() {
    thrown.expect(NullPointerException.class);
    new DocumentHandler("localhost", Charset.forName("UTF-8"), null, docIdCodec,
        new Journal(new MockTimeProvider()), new PrivateMockAdaptor(),
        "localhost", new String[0], handler, sessionManager, null, 0,
        false, false);
  }

  @Test
  public void testNullDocIdEncoder() {
    thrown.expect(NullPointerException.class);
    new DocumentHandler("localhost", Charset.forName("UTF-8"), docIdCodec, null,
        new Journal(new MockTimeProvider()), new PrivateMockAdaptor(),
        "localhost", new String[0], handler, sessionManager, null, 0,
        false, false);
  }

  @Test
  public void testNullJournal() {
    thrown.expect(NullPointerException.class);
    new DocumentHandler("localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, null, new PrivateMockAdaptor(),
        "localhost", new String[0], handler, sessionManager, null, 0,
        false, false);
  }

  @Test
  public void testNullAdaptor() {
    thrown.expect(NullPointerException.class);
    new DocumentHandler("localhost",
        Charset.forName("UTF-8"), docIdCodec, docIdCodec,
        new Journal(new MockTimeProvider()), null,
        "localhost", new String[0], handler, sessionManager, null, 0,
        false, false);
  }

  @Test
  public void testNullSessionManager() {
    thrown.expect(NullPointerException.class);
    new DocumentHandler("localhost",
        Charset.forName("UTF-8"), docIdCodec, docIdCodec,
        new Journal(new MockTimeProvider()), new PrivateMockAdaptor(),
        "localhost", new String[0], handler, null, null, 0,
        false, false);
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
    AbstractHandler authnHandler
        = new AbstractHandler("localhost", Charset.forName("UTF-8")) {
      @Override
      protected void meteredHandle(HttpExchange ex) throws IOException {
        // Translation.HTTP_NOT_FOUND was randomly chosen.
        cannedRespond(ex, 1234, Translation.HTTP_NOT_FOUND);
      }
    };
    DocumentHandler handler = new DocumentHandler("localhost",
        Charset.forName("UTF-8"), docIdCodec, docIdCodec,
        new Journal(new MockTimeProvider()), new PrivateMockAdaptor(),
        "localhost", new String[0], authnHandler, sessionManager, null, 0,
        false, false);
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
    AuthnIdentity identity = new AuthnIdentityImpl.Builder("test").build();
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
    AuthnIdentity identity = new AuthnIdentityImpl.Builder("test").build();
    authn.authenticated(identity, Long.MAX_VALUE);
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityFromGsa() throws Exception {
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), "localhost",
        new String[] {remoteIp, " "}, null, sessionManager, null, 0, false,
        false);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(mockAdaptor.documentBytes, ex.getResponseBytes());
    assertEquals(Arrays.asList("", ""),
        ex.getResponseHeaders().get("X-Gsa-External-Metadata"));
  }

  @Test
  public void testSecuritySecureFromGsa() throws Exception {
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), "localhost",
        new String[0], null, sessionManager, null, 0, false, false);
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
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), "localhost",
        new String[0], null, sessionManager, null, 0, false, false);
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        null));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecuritySecureNotX500Principal() throws Exception {
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), "localhost",
        new String[0], null, sessionManager, null, 0, false, false);
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        new KerberosPrincipal("someuser@not-domain")));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecuritySecureNoCommonName() throws Exception {
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), "localhost",
        new String[0], null, sessionManager, null, 0, false, false);
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        new X500Principal("OU=Unknown, O=Unknown, C=Unknown")));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecuritySecureNotWhitelisted() throws Exception {
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), "localhost",
        new String[0], null, sessionManager, null, 0, false, false);
    MockHttpExchange httpEx = ex;
    MockHttpsExchange ex = new MockHttpsExchange(httpEx, new MockSslSession(
        new X500Principal("CN=nottrusted, OU=Unknown, O=Unknown, C=Unknown")));
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityFromGsaAutoAddWhitelist() throws Exception {
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), remoteIp,
        new String[0], null, sessionManager, null, 0, false, false);
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
    MockHttpExchange ex = new MockHttpExchange("http", "HEAD", defaultPath,
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(new byte[] {}, ex.getResponseBytes());
  }

  @Test
  public void testNormalPost() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("http", "POST", defaultPath,
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
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
          throws IOException {
        response.addMetadata(key, "testing value");
        super.getDocContent(request, response);
      }
    };
    String remoteIp = ex.getRemoteAddress().getAddress().getHostAddress();
    DocumentHandler handler = new DocumentHandler("localhost",
        Charset.forName("UTF-8"), docIdCodec, docIdCodec,
        new Journal(new MockTimeProvider()), mockAdaptor, "localhost",
        new String[] {remoteIp}, null, sessionManager, transform, 100,
        false, false);
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
    DocumentHandler handler = new DocumentHandler("localhost",
        Charset.forName("UTF-8"), docIdCodec, docIdCodec,
        new Journal(new MockTimeProvider()), mockAdaptor, "localhost",
        new String[] {remoteIp}, null, sessionManager, transform, 3, false,
        false);
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
    DocumentHandler handler = new DocumentHandler("localhost",
        Charset.forName("UTF-8"), docIdCodec, docIdCodec,
        new Journal(new MockTimeProvider()), mockAdaptor, "localhost",
        new String[] {remoteIp}, null, sessionManager, transform, 3, true,
        false);
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
    assertTrue(mockAdaptor.failedAtCorrectTime);
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
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

    MockHttpExchange ex = new MockHttpExchange("http", "GET", defaultPath,
        new MockHttpContext(handler, "/"));
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Thu, 1 Jan 1970 00:00:00 GMT");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("text/plain",
                 ex.getResponseHeaders().getFirst("Content-Type"));
    assertEquals("Thu, 01 Jan 1970 00:00:01 GMT",
                 ex.getResponseHeaders().getFirst("Last-Modified"));

    ex = new MockHttpExchange("http", "HEAD", defaultPath,
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
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        adaptor, "localhost", new String[] {remoteIp, "someUnknownHost!@#$"},
        null, sessionManager, null, 0, false, false);
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
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        adaptor, "localhost", new String[0], null,
        sessionManager, null, 0, false, false);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertNull(ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
  }

  private DocumentHandler createDefaultHandlerForAdaptor(Adaptor adaptor) {
    return new DocumentHandler("localhost", Charset.forName("UTF-8"),
        docIdCodec, docIdCodec, new Journal(new MockTimeProvider()),
        adaptor, "localhost", new String[0], null,
        sessionManager, null, 0, false, false);
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
    String result = DocumentHandler.formAclHeader(new Acl.Builder()
        .setPermitUsers(Arrays.asList("pu1", "uid=pu2,dc=com"))
        .setPermitGroups(Arrays.asList("pg1", "gid=pg2,dc=com"))
        .setDenyUsers(Arrays.asList("du1", "uid=du2,dc=com"))
        .setDenyGroups(Arrays.asList("dg1", "gid=dg2,dc=com"))
        .setInheritFrom(new DocId("some docId"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .build(), new MockDocIdCodec());
    assertEquals(golden, result);
  }

  @Test
  public void testFormAclHeaderNull() {
    assertEquals("", DocumentHandler.formAclHeader(null, new MockDocIdCodec()));
  }

  @Test
  public void testFormAclHeaderEmpty() {
    assertEquals("google%3Aacldenyusers=google%3AfakeUserToPreventMissingAcl",
        DocumentHandler.formAclHeader(Acl.EMPTY, new MockDocIdCodec()));
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

}
