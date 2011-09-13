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

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.util.*;
import java.net.URI;
import java.nio.charset.Charset;

/**
 * Tests for {@link DocumentHandlerTest}.
 */
public class DocumentHandlerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DocumentHandler handler = new DocumentHandler("localhost",
      Charset.forName("UTF-8"), new MockDocIdDecoder(),
      new Journal(new MockTimeProvider()), new MockAdaptor(), false,
      "localhost", new String[0], null, null);
  private MockHttpExchange ex = new MockHttpExchange("http", "GET", "/",
      new MockHttpContext(handler, "/"));

  @Test
  public void testSecurity() throws Exception {
    handler = new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), false, "localhost", new String[0], null,
        null);
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityFromGsa() throws Exception {
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    handler = new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), false, "localhost",
        new String[] {"127.0.0.3", " "}, null, null);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(new byte[] {1, 2, 3}, ex.getResponseBytes());
  }

  @Test
  public void testSecurityFromGsaNoWhitelist() throws Exception {
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    handler = new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), false, "127.0.0.3",
        new String[0], null, null);
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testSecurityFromGsaAutoAddWhitelist() throws Exception {
    // 127.0.0.3 is the address hard-coded in MockHttpExchange
    handler = new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), true, "127.0.0.3",
        new String[0], null, null);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(new byte[] {1, 2, 3}, ex.getResponseBytes());
  }

  @Test
  public void testNoSuchHost() throws Exception {
    thrown.expect(RuntimeException.class);
    handler = new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), false, "localhost",
        new String[] {"-no-such-host-"}, null, null);
  }

  @Test
  public void testNoSuchGsaHost() throws Exception {
    thrown.expect(RuntimeException.class);
    handler = new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        new PrivateMockAdaptor(), true, "-no-such-host-",
        new String[0], null, null);
  }

  @Test
  public void testNormal() throws Exception {
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertArrayEquals(new byte[] {1, 2, 3}, ex.getResponseBytes());
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
  public void testNullAuthzResponse() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
              Set<String> groups, Collection<DocId> ids) {
            return null;
          }
        };
    handler = createDefaultHandlerForAdaptor(adaptor);
    handler.handle(ex);
    assertEquals(403, ex.getResponseCode());
  }

  @Test
  public void testFileNotFound() throws Exception {
    MockAdaptor adaptor = new MockAdaptor() {
          @Override
          public void getDocContent(Request request, Response response)
              throws IOException {
            throw new FileNotFoundException();
          }
        };
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
    handler = createDefaultHandlerForAdaptor(adaptor);
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
              throw new FileNotFoundException();
            }
            if (!request.hasChangedSinceLastAccess(new Date(1 * 1000))) {
              response.respondNotModified();
              return;
            }
            if (!request.needDocumentContent()) {
              response.getOutputStream();
              return;
            }
            response.setContentType("text/plain");
            response.setMetadata(Metadata.EMPTY);
            response.getOutputStream();
            // It is free to get it multiple times
            response.getOutputStream();
          }
        };
    handler = createDefaultHandlerForAdaptor(adaptor);
    ex.getRequestHeaders().set("If-Modified-Since",
                               "Thu, 1 Jan 1970 00:00:01 GMT");
    handler.handle(ex);
    assertEquals(304, ex.getResponseCode());

    ex = new MockHttpExchange("http", "GET", "/",
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
    handler = new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        adaptor, false, "localhost", new String[0], null, null);
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertNull(ex.getResponseHeaders().getFirst("X-Gsa-External-Metadata"));
  }

  private DocumentHandler createDefaultHandlerForAdaptor(Adaptor adaptor) {
    return new DocumentHandler("localhost", Charset.forName("UTF-8"),
        new MockDocIdDecoder(), new Journal(new MockTimeProvider()),
        adaptor, false, "localhost", new String[0], null, null);
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

  private static class MockAdaptor extends AbstractAdaptor {
    @Override
    public void getDocIds(DocIdPusher pusher) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void getDocContent(Request request, Response response)
        throws IOException {
      response.getOutputStream().write(new byte[] {1, 2, 3});
    }
  }

  private static class PrivateMockAdaptor extends MockAdaptor {
    @Override
    public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
                                                    Set<String> groups,
                                                    Collection<DocId> ids) {
      Map<DocId, AuthzStatus> result
          = new HashMap<DocId, AuthzStatus>(ids.size() * 2);
      for (DocId id : ids) {
        result.put(id, AuthzStatus.DENY);
      }
      return Collections.unmodifiableMap(result);
    }
  }

  private static class MockDocIdDecoder implements DocIdDecoder {
    public DocId decodeDocId(URI uri) {
      return new DocId(uri.toString());
    }
  }
}
