// Copyright 2012 Google Inc. All Rights Reserved.
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

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.*;

/**
 * Test cases for {@link AuthnIdentityImpl}.
 */
public class AuthnIdentityImplTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullUsername() {
    thrown.expect(NullPointerException.class);
    new AuthnIdentityImpl.Builder(null);
  }

  @Test
  public void testSetAllConstruction() {
    AuthnIdentity identity = new AuthnIdentityImpl.Builder("testing")
        .setPassword("pass").setGroups(Collections.singleton("group"))
        .build();
    assertEquals("testing", identity.getUsername());
    assertEquals("pass", identity.getPassword());
    assertEquals(Collections.singleton("group"), identity.getGroups());
  }

  @Test
  public void testDefaults() {
    AuthnIdentity identity = new AuthnIdentityImpl.Builder("testing").build();
    assertEquals("testing", identity.getUsername());
    assertNull(identity.getPassword());
    assertNull(identity.getGroups());
  }

  @Test
  public void testImmutable() {
    HashSet<String> groups = new HashSet<String>();
    groups.add("group");
    AuthnIdentity identity = new AuthnIdentityImpl.Builder("testing")
        .setGroups(groups).build();
    groups.add("anotherGroup");
    assertEquals(Collections.singleton("group"), identity.getGroups());
  }
}
