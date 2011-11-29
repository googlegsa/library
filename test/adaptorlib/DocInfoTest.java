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

/** Tests for {@link DocIdPusher.DocInfo}. */
public class DocInfoTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullDocId() {
    thrown.expect(NullPointerException.class);
    new DocIdPusher.DocInfo(null, PushAttributes.DEFAULT);
  }

  @Test
  public void testNullMetadata() {
    thrown.expect(NullPointerException.class);
    new DocIdPusher.DocInfo(new DocId("test"), null);
  }

  @Test
  public void testEquals() {
    DocIdPusher.DocInfo info1 = new DocIdPusher.DocInfo(new DocId("test"),
        PushAttributes.DEFAULT);
    DocIdPusher.DocInfo info2 = new DocIdPusher.DocInfo(new DocId("test"),
        PushAttributes.DEFAULT);
    DocIdPusher.DocInfo info3 = new DocIdPusher.DocInfo(new DocId("test2"),
        PushAttributes.DEFAULT);
    DocIdPusher.DocInfo info4 = new DocIdPusher.DocInfo(new DocId("test"),
        new PushAttributes.Builder().setDeleteFromIndex(true).build());
    DocIdPusher.DocInfo info5 = new DocIdPusher.DocInfo(new DocId("test2"),
        new PushAttributes.Builder().setDeleteFromIndex(true).build());
    assertFalse(info1.equals(null));
    assertFalse(info1.equals(new Object()));
    assertEquals(info1, info2);
    assertFalse(info1.equals(info3));
    assertFalse(info1.equals(info4));
    assertFalse(info1.equals(info5));
  }

  @Test
  public void testToString() {
    String golden = "DocIdPusher.DocInfo(DocId(a),PushAttributes(delete=false"
        + ",lastModified=null,displayUrl=null,crawlImmediately=false"
        + ",crawlOnce=false,lock=false,noFollow=false))";
    assertEquals(golden,
        "" + new DocIdPusher.DocInfo(new DocId("a"), PushAttributes.DEFAULT));
  }
}
