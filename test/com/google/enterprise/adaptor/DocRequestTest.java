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

import static java.util.concurrent.TimeUnit.DAYS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Date;

/**
 * Tests for {@link DocRequest}.
 */
public class DocRequestTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testConstructor_nullDocId() {
    thrown.expect(NullPointerException.class);
    new DocRequest(null);
  }

  @Test
  public void testConstructor_nullLastAccessTime() {
    Request request = new DocRequest(new DocId("42"), null);
    assertNull(request.getLastAccessTime());
  }

  @Test
  public void testToString() {
    // Epoch plus one day is 1970 in every time zone.
    Request request =
        new DocRequest(new DocId("xyggy"), new Date(DAYS.toMillis(1L)));
    assertThat(request.toString(), containsString("xyggy"));
    assertThat(request.toString(), containsString("1970"));
  }

  @Test
  public void testHasChangedSinceLastAccess_nullLastModifiedTime() {
    Request request = new DocRequest(new DocId("42"), new Date());
    thrown.expect(NullPointerException.class);
    request.hasChangedSinceLastAccess(null);
  }

  @Test
  public void testHasChangedSinceLastAccess_nullLastAccessTime() {
    Request request = new DocRequest(new DocId("42"), null);
    assertTrue(request.hasChangedSinceLastAccess(null));
  }

  @Test
  public void testCanRespondWithNoContent_supported() {
    // Construct a request for an unchanged document.
    DocId docId = new DocId("42");
    Date lastAccessTime = new Date();
    Date lastModifiedTime = new Date(0L);

    Request request = new DocRequest(docId, lastAccessTime, true);
    assertTrue(request.canRespondWithNoContent(lastModifiedTime));
  }

  @Test
  public void testCanRespondWithNoContent_unsupported() {
    // Construct a request for an unchanged document.
    DocId docId = new DocId("42");
    Date lastAccessTime = new Date();
    Date lastModifiedTime = new Date(0L);

    Request request = new DocRequest(docId, lastAccessTime, false);
    assertFalse(request.canRespondWithNoContent(lastModifiedTime));
  }
}
