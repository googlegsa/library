// Copyright 2017 Google Inc. All Rights Reserved.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Unit tests for {@link ValidatedUri}. */
public class ValidatedUriTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullUrl() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri(null);
  }

  @Test
  public void testEmptyUrl() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("");
  }

  @Test
  public void testNoProtocol() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("//foo/bar");
  }

  @Test
  public void testUnknownProtocol() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("unknown://foo/bar");
  }

  @Test
  public void testBadProtocol() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("https//foo/bar");
  }

  @Test
  public void testNoHost() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("http://");
  }

  @Test
  public void testNoPath() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("http://foo:80");
  }

  @Test
  public void testRelativeUri() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("foo/bar");
  }

  @Test
  public void testMessageFormatRemnants() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("http://message_format/foo/{0}");
  }

  @Test
  public void testNakedIPv6Address() throws Exception {
    thrown.expect(URISyntaxException.class);
    new ValidatedUri("http://::1/foo/bar");
  }

  @Test
  public void testBracketedIPv6Address() throws Exception {
    assertEquals(new URI("http://[::1]/foo/bar"),
        new ValidatedUri("http://[::1]/foo/bar").getUri());
  }

  @Test
  public void testRootPath() throws Exception {
    assertEquals(new URI("http://foo:80/"),
        new ValidatedUri("http://foo:80/").getUri());
  }

  @Test
  public void testGetUri() throws Exception {
    assertEquals(new URI("http://example.com/foo/bar"),
        new ValidatedUri("http://example.com/foo/bar").getUri());
  }

  @Test
  public void testReachableHost() throws Exception {
    List<String> messages = new ArrayList<String>();
    captureLogMessages(ValidatedUri.class, "is not reachable", messages);
    new ValidatedUri("http://127.0.0.1/foo/bar").logIfHostIsNotReachable();
    assertEquals(0, messages.size());
  }

  @Test
  public void testUnreachableHost() throws Exception {
    List<String> messages = new ArrayList<String>();
    captureLogMessages(ValidatedUri.class, "is not reachable", messages);
    new ValidatedUri("http://unknown-host/foo/bar").logIfHostIsNotReachable();
    assertEquals(1, messages.size());
  }

  /**
   * Enables logging and captures matching log messages. The messages
   * will not be localized or formatted.
   *
   * @param clazz the class to enable logging for
   * @param substring capture log messages containing this substring
   * @param output captured messages will be added to this collection
   */
  public static void captureLogMessages(Class<?> clazz,
      final String substring, final Collection<? super String> output) {
    Logger logger = Logger.getLogger(clazz.getName());
    logger.setLevel(Level.ALL);

    logger.addHandler(new Handler() {
        @Override public void close() {}
        @Override public void flush() {}

        @Override public void publish(LogRecord record) {
          if (record.getMessage().contains(substring)) {
            output.add(record.getMessage());
          }
        }
      });
  }

}
