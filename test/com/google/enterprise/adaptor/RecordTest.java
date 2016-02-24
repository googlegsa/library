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
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.Date;

/** Tests for {@link DocIdPusher.Record}. */
public class RecordTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullDocId() {
    thrown.expect(NullPointerException.class);
    DocId nullId = null;
    new DocIdPusher.Record.Builder(nullId);
  }

  @Test
  public void testSetNullDocId() {
    DocIdPusher.Record.Builder builder
        = new DocIdPusher.Record.Builder(new DocId(""));
    thrown.expect(NullPointerException.class);
    builder.setDocId(null);
  }

  @Test
  public void testEquals() {
    DocIdPusher.Record info1 = new DocIdPusher.Record.Builder(
        new DocId("test")).build();
    DocIdPusher.Record info2 = new DocIdPusher.Record.Builder(
        new DocId("test")).build();
    DocIdPusher.Record info3 = new DocIdPusher.Record.Builder(
        new DocId("test2")).build();
    DocIdPusher.Record info4 = new DocIdPusher.Record.Builder(
        new DocId("test")).setDeleteFromIndex(true).build();
    DocIdPusher.Record info5 = new DocIdPusher.Record.Builder(
        new DocId("test2")).setDeleteFromIndex(true).build();
    assertFalse(info1.equals(null));
    assertFalse(info1.equals(new Object()));
    assertEquals(info1, info2);
    assertFalse(info1.equals(info3));
    assertFalse(info1.equals(info4));
    assertFalse(info1.equals(info5));

    DocIdPusher.Record defaults = new DocIdPusher.Record.Builder(new DocId(""))
        .build();
    DocIdPusher.Record delete = new DocIdPusher.Record.Builder(defaults)
        .setDeleteFromIndex(true).build();
    DocIdPusher.Record immediately = new DocIdPusher.Record.Builder(defaults)
        .setCrawlImmediately(true).build();
    DocIdPusher.Record once = new DocIdPusher.Record.Builder(defaults)
        .setCrawlOnce(true).build();
    DocIdPusher.Record lock = new DocIdPusher.Record.Builder(defaults)
        .setLock(true).build();
    DocIdPusher.Record modified = new DocIdPusher.Record.Builder(defaults)
        .setLastModified(new Date()).build();
    DocIdPusher.Record modified2 = new DocIdPusher.Record.Builder(defaults)
        .setLastModified(new Date(0)).build();
    DocIdPusher.Record link = new DocIdPusher.Record.Builder(defaults)
        .setResultLink(URI.create("http://localhost/something")).build();
    Metadata m = new Metadata();
    m.add("foo", "bar");
    DocIdPusher.Record link2 = new DocIdPusher.Record.Builder(link)
        .setMetadata(m).build();
    m = new Metadata();
    DocIdPusher.Record link3 = new DocIdPusher.Record.Builder(link)
        .setMetadata(m).addMetadata("foo", "bar").build();
    m = new Metadata();
    DocIdPusher.Record link4 = new DocIdPusher.Record.Builder(link)
        .setMetadata(m).addMetadata("foo", "baz").build();
    assertFalse(defaults.equals(delete));
    assertFalse(defaults.equals(immediately));
    assertFalse(defaults.equals(once));
    assertFalse(defaults.equals(lock));
    assertFalse(defaults.equals(modified));
    assertFalse(modified.equals(defaults));
    assertFalse(modified.equals(modified2));
    assertFalse(defaults.equals(link));
    assertFalse(link.equals(link2));
    assertFalse(link.equals(link3));
    assertFalse(link.equals(link4));
    assertEquals(link2, link3);
    assertFalse(link2.equals(link4));
    assertFalse(link3.equals(link4));
    assertNull(link.getMetadata());
    assertEquals("[foo=bar]", link2.getMetadata().toString());
    assertEquals("[foo=bar]", link3.getMetadata().toString());
    assertEquals("[foo=baz]", link4.getMetadata().toString());
  }

  @Test
  public void testToString() {
    String golden = "Record(docid=a,delete=false"
        + ",lastModified=null,resultLink=null,crawlImmediately=false"
        + ",crawlOnce=false,lock=false)";
    assertEquals(golden,
        "" + new DocIdPusher.Record.Builder(new DocId("a")).build());
  }

  @Test
  public void testToStringWithMetadata() {
    String golden = "Record(docid=a,delete=false"
        + ",lastModified=null,resultLink=null,crawlImmediately=false"
        + ",crawlOnce=false,lock=false,metadata=[foo=bar])";
    assertEquals(golden,
        "" + new DocIdPusher.Record.Builder(new DocId("a"))
            .addMetadata("foo", "bar").build());
  }

  @Test
  public void testToStringWithEmptyMetadata() {
    String golden = "Record(docid=a,delete=false"
        + ",lastModified=null,resultLink=null,crawlImmediately=false"
        + ",crawlOnce=false,lock=false,metadata=[])";
    assertEquals(golden,
        "" + new DocIdPusher.Record.Builder(new DocId("a"))
            .setMetadata(new Metadata()).build());
  }

  @Test
  public void testNPEonNullMetadataKey() {
    thrown.expect(NullPointerException.class);
    DocIdPusher.Record record = new DocIdPusher.Record.Builder(new DocId(""))
        .addMetadata(null, "value").build();
  }

  @Test
  public void testNPEonNullMetadataValue() {
    thrown.expect(NullPointerException.class);
    DocIdPusher.Record record = new DocIdPusher.Record.Builder(new DocId(""))
        .addMetadata("key", null).build();
  }
}
