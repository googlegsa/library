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

package adaptorlib;

import static org.junit.Assert.*;

import com.sun.net.httpserver.HttpExchange;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Tests for {@link DocumentHandlerTest}.
 */
public class DocumentHandlerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SessionManager<HttpExchange> sessionManager
      = new SessionManager<HttpExchange>(
          new SessionManager.HttpExchangeClientStore(), 1000, 1000);
  private MockAdaptor mockAdaptor = new MockAdaptor();
  private DocumentHandler handler = createDefaultHandlerForAdaptor(mockAdaptor);
  private MockHttpExchange ex = new MockHttpExchange("http", "GET", "/",
      new MockHttpContext(null, "/"));

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
        cannedRespond(ex, 1234, "text/plain", "Testing");
      }
    };
    DocumentHandler handler = new DocumentHandler("localhost",
        Charset.forName("UTF-8"), new MockDocIdDecoder(),
        new Journal(new MockTimeProvider()), new PrivateMockAdaptor(), false,
        "localhost", new String[0], authnHandler, sessionManager, null, 0,
        false);
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
    authn.authenticated("test", Collections.<String>emptySet(), Long.MAX_VALUE);
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new UserPrivateMockAdaptor());
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
  }

  @Test
  public void testSecurityNotFound() throws Exception {
    Adaptor mockAdaptor = new MockAdaptor() {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
                                                      Set<String> groups,
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
    authn.authenticated("test", Collections.<String>emptySet(), Long.MAX_VALUE);
    DocumentHandler handler = createDefaultHandlerForAdaptor(
        new PrivateMockAdaptor());
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityFromGsa() throws Exception {
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), false, "localhost",
        new String[] {"127.0.0.3", " "}, null, sessionManager, null, 0, false);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(mockAdaptor.documentBytes, ex.getResponseBytes());
  }

  @Test
  public void testSecurityFromGsaNoWhitelist() throws Exception {
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), false, "127.0.0.3",
        new String[0], null, sessionManager, null, 0, false);
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityFromGsaAutoAddWhitelist() throws Exception {
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), true, "127.0.0.3",
        new String[0], null, sessionManager, null, 0, false);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(mockAdaptor.documentBytes, ex.getResponseBytes());
  }

  @Test
  public void testNoSuchHost() throws Exception {
    thrown.expect(RuntimeException.class);
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), false, "localhost",
        new String[] {"-no-such-host-"}, null, sessionManager, null, 0, false);
  }

  @Test
  public void testNoSuchGsaHost() throws Exception {
    thrown.expect(RuntimeException.class);
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), true, "-no-such-host-",
        new String[0], null, sessionManager, null, 0, false);
  }

  @Test
  public void testNormal() throws Exception {
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(mockAdaptor.documentBytes, ex.getResponseBytes());
  }

  @Test
  public void testNormalHead() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("http", "HEAD", "/",
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(new byte[] {}, ex.getResponseBytes());
  }

  @Test
  public void testNormalPost() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("http", "POST", "/",
        new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testTransform() throws Exception {
    final byte[] golden = new byte[] {2, 3, 4};
    final String key = "testing key";
    TransformPipeline transform = new TransformPipeline();
    transform.add(new DocumentTransform("testing") {
      @Override
      public void transform(ByteArrayOutputStream contentIn,
                            OutputStream contentOut,
                            Map<String, String> metadata,
                            Map<String, String> params) throws IOException {
        assertArrayEquals(mockAdaptor.documentBytes, contentIn.toByteArray());
        contentOut.write(golden);
        metadata.put(key, metadata.get(key).toUpperCase());
        metadata.put("docid", params.get("DocId"));
      }
    });
    mockAdaptor = new MockAdaptor() {
      @Override
      public void getDocContent(Request request, Response response)
          throws IOException {
        Set<MetaItem> metaSet = new HashSet<MetaItem>();
        metaSet.add(MetaItem.raw(key, "testing value"));
        response.setMetadata(new Metadata(metaSet));
        super.getDocContent(request, response);
      }
    };
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    DocumentHandler handler = new DocumentHandler("localhost",
        Charset.forName("UTF-8"), new MockDocIdDecoder(),
        new Journal(new MockTimeProvider()), mockAdaptor, false, "localhost",
        new String[] {"127.0.0.3"}, null, sessionManager, transform, 100,
        false);
    mockAdaptor.documentBytes = new byte[] {1, 2, 3};
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(golden, ex.getResponseBytes());
    assertEquals("docid=http%3A%2F%2Flocalhost%2F,"
                 + "testing%20key=TESTING%20VALUE",
                 ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
  }

  @Test
  public void testTransformDocumentTooLarge() throws Exception {
    TransformPipeline transform = new TransformPipeline();
    transform.add(new DocumentTransform("testing") {
      @Override
      public void transform(ByteArrayOutputStream contentIn,
                            OutputStream contentOut,
                            Map<String, String> metadata,
                            Map<String, String> params) throws IOException {
        // This is not the content we are looking for.
        contentOut.write(new byte[] {2, 3, 4});
      }
    });
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
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    DocumentHandler handler = new DocumentHandler("localhost",
        Charset.forName("UTF-8"), new MockDocIdDecoder(),
        new Journal(new MockTimeProvider()), mockAdaptor, false, "localhost",
        new String[] {"127.0.0.3"}, null, sessionManager, transform, 3, false);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(golden, ex.getResponseBytes());
    assertNull(ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
  }

  @Test
  public void testTransformDocumentTooLargeButRequired() throws Exception {
    TransformPipeline transform = new TransformPipeline();
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
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    DocumentHandler handler = new DocumentHandler("localhost",
        Charset.forName("UTF-8"), new MockDocIdDecoder(),
        new Journal(new MockTimeProvider()), mockAdaptor, false, "localhost",
        new String[] {"127.0.0.3"}, null, sessionManager, transform, 3, true);
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
    assertTrue(mockAdaptor.failedAtCorrectTime);
  }

  @Test
  public void testNullAuthzResponse() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
              Set<String> groups, Collection<DocId> ids) {
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
  public void testSetMetadataLate() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.getOutputStream();
            response.setMetadata(Metadata.EMPTY);
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    handler.handle(ex);
    assertEquals(500, ex.getResponseCode());
  }

  @Test
  public void testSmartAdaptor() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            if (!request.getDocId().equals(new DocId("http://localhost/"))) {
              response.respondNotFound();
              return;
            }
            if (!request.hasChangedSinceLastAccess(new Date(1 * 1000))) {
              response.respondNotModified();
              return;
            }
            response.setContentType("text/plain");
            response.setMetadata(Metadata.EMPTY);
            response.getOutputStream();
            // It is free to get it multiple times
            response.getOutputStream();
          }
        };
    DocumentHandler handler = createDefaultHandlerForAdaptor(adaptor);
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Thu, 1 Jan 1970 00:00:01 GMT");
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());

    MockHttpExchange ex = new MockHttpExchange("http", "GET", "/",
        new MockHttpContext(handler, "/"));
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Thu, 1 Jan 1970 00:00:00 GMT");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals("text/plain",
                 ex.getResponseHeaders().getFirst("Content-Type"));

    ex = new MockHttpExchange("http", "HEAD", "/",
                              new MockHttpContext(handler, "/"));
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());

  }

  @Test
  public void testMetadataNonGsa() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            response.setMetadata(new Metadata(Collections.singleton(
                MetaItem.raw("test", "ing"))));
            response.getOutputStream();
          }
        };
    DocumentHandler handler = new DocumentHandler(
        "localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        adaptor, false, "localhost", new String[0], null,
        sessionManager, null, 0, false);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertNull(ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
  }

  private DocumentHandler createDefaultHandlerForAdaptor(Adaptor adaptor) {
    return new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        adaptor, false, "localhost", new String[0], null,
        sessionManager, null, 0, false);
  }

  @Test
  public void testFormMetadataHeader() {
    Set<MetaItem> items = new HashSet<MetaItem>();
    items.add(MetaItem.isPublic());
    items.add(MetaItem.raw("test", "ing"));
    items.add(MetaItem.raw("another", "item"));
    items.add(MetaItem.raw("equals=", "=="));
    String result = DocumentHandler.formMetadataHeader(new Metadata(items));
    assertEquals("another=item,equals%3D=%3D%3D,google%3Aispublic=true,"
                 + "test=ing", result);
  }

  @Test
  public void testFormMetadataHeaderEmpty() {
    String header = DocumentHandler.formMetadataHeader(
        new Metadata(new HashSet<MetaItem>()));
    assertEquals("", header);
  }

  @Test
  public void testPercentEncoding() {
    String encoded = DocumentHandler.percentEncode(
        "AaZz-_.~`=/?+';\\/\"!@#$%^&*()[]{}ë\u0001");
    assertEquals("AaZz-_.~%60%3D%2F%3F%2B%27%3B%5C%2F%22%21%40%23%24%25%5E%26"
                 + "%2A%28%29%5B%5D%7B%7D%C3%AB%01", encoded);
  }

  private static class UserPrivateMockAdaptor extends MockAdaptor {
      @Override
      public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
                                                      Set<String> groups,
                                                      Collection<DocId> ids) {
        Map<DocId, AuthzStatus> result
            = new HashMap<DocId, AuthzStatus>(ids.size() * 2);
        for (DocId id : ids) {
          result.put(id,
              userIdentifier == null ? AuthzStatus.DENY : AuthzStatus.PERMIT);
        }
        return Collections.unmodifiableMap(result);
      }
    };

}
