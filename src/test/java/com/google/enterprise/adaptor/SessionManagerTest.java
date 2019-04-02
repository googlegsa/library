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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.sun.net.httpserver.HttpExchange;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Test cases for {@link SessionManager}.
 */
public class SessionManagerTest {
  private MockTimeProvider timeProvider = new MockTimeProvider();
  private SessionManager<Reference> sessionManager
      = new SessionManager<Reference>(
          timeProvider, new ReferenceClientStore(), 1000, 500);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testGetSessionNormal() {
    Reference ref1 = new Reference();
    Reference ref2 = new Reference();

    Session sess1 = sessionManager.getSession(ref1, false);
    assertNull(sess1);

    sess1 = sessionManager.getSession(ref1);
    Session sess2 = sessionManager.getSession(ref2);
    assertNotNull(sess1);
    assertNotNull(sess2);
    assertNotSame(sess1, sess2);
  }

  @Test
  public void testSessionExpires() {
    Reference ref1 = new Reference();
    Session sess1 = sessionManager.getSession(ref1);
    assertNotNull(sess1);

    timeProvider.time += 1000;
    sess1 = sessionManager.getSession(ref1, false);
    assertNull(sess1);
  }

  @Test
  public void testSessionExpiresDuringCreate() {
    Reference ref1 = new Reference();
    Session sess1 = sessionManager.getSession(ref1);
    assertNotNull(sess1);

    timeProvider.time += 1000;
    // Create a new session to allow checking for expired sessions.
    sessionManager.getSession(new Reference());
    assertEquals(1, sessionManager.getSessionCount());

    sess1 = sessionManager.getSession(ref1, false);
    assertNull(sess1);
  }

  @Test
  public void testSessionNotExpiredDuringCreate() {
    // Create a trash session, just so we can verify that the cleanup ran.
    sessionManager.getSession(new Reference());

    timeProvider.time += 500;
    Reference ref1 = new Reference();
    Session sess1 = sessionManager.getSession(ref1);
    assertNotNull(sess1);
    assertEquals(2, sessionManager.getSessionCount());

    timeProvider.time += 500;
    // Create a new session to allow checking for expired sessions.
    sessionManager.getSession(new Reference());
    assertEquals(2, sessionManager.getSessionCount());

    Session sess2 = sessionManager.getSession(ref1, false);
    assertSame(sess1, sess2);
  }

  @Test
  public void testCreateAfterSessionExpires() {
    Reference ref1 = new Reference();
    Session sess1 = sessionManager.getSession(ref1);
    assertNotNull(sess1);

    timeProvider.time += 1000;
    Session sess2 = sessionManager.getSession(ref1);
    assertNotSame(sess1, sess2);
  }

  @Test
  public void testHttpExchangeClientStoreInitNull() {
    thrown.expect(NullPointerException.class);
    new SessionManager.HttpExchangeClientStore(null);
  }

  @Test
  public void testHttpExchangeClientStoreCookies() {
    SessionManager.ClientStore<HttpExchange> clientStore1
        = new SessionManager.HttpExchangeClientStore("test1");
    SessionManager.ClientStore<HttpExchange> clientStore2
        = new SessionManager.HttpExchangeClientStore("test2");
    SessionManager.ClientStore<HttpExchange> clientStore3
        = new SessionManager.HttpExchangeClientStore("notfound");
    HttpExchange ex = new MockHttpExchange("GET", "/", null);
    ex.getRequestHeaders().set("Cookie", "test1=value1; value; test2=value2");
    assertEquals("value1", clientStore1.retrieve(ex));
    assertEquals("value2", clientStore2.retrieve(ex));
    assertNull(clientStore3.retrieve(ex));
  }

  private static class ReferenceClientStore
      implements SessionManager.ClientStore<Reference> {
    public String retrieve(Reference clientState) {
      return (String) clientState.ref;
    }

    public void store(Reference clientState, String value) {
      clientState.ref = value;
    }
  }

  private static class Reference {
    public Object ref;
  }
}
