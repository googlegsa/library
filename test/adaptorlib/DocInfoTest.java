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

/** Tests for {@link DocInfo}. */
public class DocInfoTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullDocId() {
    thrown.expect(NullPointerException.class);
    new DocInfo(null, Metadata.EMPTY);
  }

  @Test
  public void testNullMetadata() {
    thrown.expect(NullPointerException.class);
    new DocInfo(new DocId("test"), null);
  }

  @Test
  public void testEquals() {
    DocInfo info1 = new DocInfo(new DocId("test"), Metadata.EMPTY);
    DocInfo info2 = new DocInfo(new DocId("test"), Metadata.EMPTY);
    DocInfo info3 = new DocInfo(new DocId("test2"), Metadata.EMPTY);
    DocInfo info4 = new DocInfo(new DocId("test"), Metadata.DELETED);
    DocInfo info5 = new DocInfo(new DocId("test2"), Metadata.DELETED);
    assertFalse(info1.equals(null));
    assertFalse(info1.equals(new Object()));
    assertEquals(info1, info2);
    assertFalse(info1.equals(info3));
    assertFalse(info1.equals(info4));
    assertFalse(info1.equals(info5));
  }

  @Test
  public void testToString() {
    assertEquals("DocInfo(DocId(a),[])",
                 new DocInfo(new DocId("a"), Metadata.EMPTY).toString());
  }
}
