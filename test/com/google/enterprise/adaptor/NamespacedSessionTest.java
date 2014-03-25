// Copyright 2013 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Test cases for {@link NampespacedSession}. */
public class NamespacedSessionTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Session baseSession = new HashMapSession();
  private NamespacedSession session
      = new NamespacedSession(baseSession, "prefix-");

  @Test
  public void testNullSession() {
    thrown.expect(NullPointerException.class);
    new NamespacedSession(null, "prefix-");
  }

  @Test
  public void testNullPrefix() {
    thrown.expect(NullPointerException.class);
    new NamespacedSession(baseSession, null);
  }

  @Test
  public void testNamespacing() {
    Object o = new Object();
    assertNull(session.getAttribute("item"));
    assertNull(session.removeAttribute("item"));
    session.setAttribute("item", o);
    assertSame(o, baseSession.getAttribute("prefix-item"));
    assertSame(o, session.getAttribute("item"));
    assertSame(o, session.removeAttribute("item"));
    assertNull(session.getAttribute("item"));
  }
}
