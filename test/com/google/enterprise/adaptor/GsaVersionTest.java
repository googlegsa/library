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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Tests for {@link GsaVersion}. */
public class GsaVersionTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private GsaVersion base = new GsaVersion("7.2.1-1");

  @Test
  public void testConstructorNotEnough() {
    thrown.expect(IllegalArgumentException.class);
    new GsaVersion("7.2.1");
  }

  @Test
  public void testConstructorBadFormat() {
    thrown.expect(IllegalArgumentException.class);
    new GsaVersion("7.2.1.1");
  }

  @Test
  public void testConstructorToomuch() {
    thrown.expect(IllegalArgumentException.class);
    new GsaVersion("7.2.1.1-1");
  }

  @Test
  public void testAtLeastWithBigger() {
    assertFalse(base.isAtLeast("7.4.0-1"));
  }

  @Test
  public void testAtLeastWithSmaller() {
    assertTrue(base.isAtLeast("6.14.36-155"));
  }

  @Test
  public void testAtLeastWithEqual() {
    assertTrue(base.isAtLeast("7.2.1-1"));
  }

  @Test
  public void testAtLeastWithWrongArgument() {
    thrown.expect(IllegalArgumentException.class);
    assertTrue(base.isAtLeast("6.p.36-155"));
  }
}
