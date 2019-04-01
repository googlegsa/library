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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests for {@link DocId}.
 */
public class DocIdTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testDocIdHash() {
    DocId id = new DocId("procure/book3/sd7823.flx");
    DocId id2 = new DocId("procure/book3/sd7823.flx");
    assertEquals("hash mismatch", id.hashCode(), id2.hashCode());
  }

  @Test
  public void testDocIdEqual() {
    DocId id = new DocId("procure/book3/sd7823.flx");
    DocId id2 = new DocId("procure/book3/sd7823.flx");
    assertEquals(id, id2);
    assertFalse(id.equals(new DocId("procure/book3/XYZXYZ.flx")));
    assertFalse(id.equals("Some random object"));
    assertFalse(id.equals(null));
  }

  @Test
  public void testToString() {
    String rawId = "some docid";
    String docIdToString = new DocId(rawId).toString();
    assertTrue(docIdToString.contains(rawId));
  }

  @Test
  public void testConstructorNull() {
    thrown.expect(NullPointerException.class);
    new DocId(null);
  }
}
