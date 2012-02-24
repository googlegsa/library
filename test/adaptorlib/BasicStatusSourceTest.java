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

package adaptorlib;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

/**
 * Test cases for {@link BasicStatusSource}.
 */
public class BasicStatusSourceTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private final Status normalStatus = new Status(Status.Code.NORMAL);
  private final Status errorStatus = new Status(Status.Code.ERROR);

  @Test
  public void testNormalUsage() {
    BasicStatusSource source = new BasicStatusSource("test", normalStatus);
    assertEquals("test", source.getName());
    assertEquals(normalStatus, source.retrieveStatus());
    source.setStatus(errorStatus);
    assertEquals(errorStatus, source.retrieveStatus());
  }

  @Test
  public void testConstructorNullName() {
    thrown.expect(NullPointerException.class);
    new BasicStatusSource(null, normalStatus);
  }

  @Test
  public void testConstructorNullStatus() {
    thrown.expect(NullPointerException.class);
    new BasicStatusSource("test", null);
  }

  @Test
  public void testStatusSetNull() {
    BasicStatusSource source = new BasicStatusSource("test", normalStatus);
    thrown.expect(NullPointerException.class);
    source.setStatus(null);
  }
}
