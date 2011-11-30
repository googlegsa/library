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

/** Tests for {@link DocIdPusher.Record}. */
public class RecordTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullDocId() {
    thrown.expect(NullPointerException.class);
    new DocIdPusher.Record(null, PushAttributes.DEFAULT);
  }

  @Test
  public void testNullMetadata() {
    thrown.expect(NullPointerException.class);
    new DocIdPusher.Record(new DocId("test"), null);
  }

  @Test
  public void testEquals() {
    DocIdPusher.Record info1 = new DocIdPusher.Record(new DocId("test"),
        PushAttributes.DEFAULT);
    DocIdPusher.Record info2 = new DocIdPusher.Record(new DocId("test"),
        PushAttributes.DEFAULT);
    DocIdPusher.Record info3 = new DocIdPusher.Record(new DocId("test2"),
        PushAttributes.DEFAULT);
    DocIdPusher.Record info4 = new DocIdPusher.Record(new DocId("test"),
        new PushAttributes.Builder().setDeleteFromIndex(true).build());
    DocIdPusher.Record info5 = new DocIdPusher.Record(new DocId("test2"),
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
    String golden = "DocIdPusher.Record(DocId(a),PushAttributes(delete=false"
        + ",lastModified=null,displayUrl=null,crawlImmediately=false"
        + ",crawlOnce=false,lock=false))";
    assertEquals(golden,
        "" + new DocIdPusher.Record(new DocId("a"), PushAttributes.DEFAULT));
  }
}
