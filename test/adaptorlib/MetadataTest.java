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

/** Tests for {@link MetaItem}. */
public class MetadataTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  
  private Metadata couple = makeCouple();
  private Metadata coupleB = makeCouple();
  private Metadata triple = makeTriple();
  private Metadata tripleB = makeTriple();

  @Test
  public void testEquals() {
    assertEquals(couple, coupleB);
    assertEquals(couple, new Metadata.Builder(couple).build());
    assertEquals(triple, tripleB);
    assertEquals(Metadata.EMPTY, new Metadata.Builder().build());
    assertFalse(couple.equals(triple));
    assertFalse(triple.equals(couple));
    assertFalse(Metadata.EMPTY.equals(new Object()));
    assertFalse(Metadata.EMPTY.equals(null));
  }

  @Test
  public void testHashCode() {
    assertEquals(couple.hashCode(), coupleB.hashCode());
    assertEquals(triple.hashCode(), tripleB.hashCode());
  }

  @Test
  public void testIteratorReloads() {
    int countFirstPass = 0;
    int countSecondPass = 0;
    for (MetaItem item : couple) {
      countFirstPass++;
    }
    for (MetaItem item : couple) {
      countSecondPass++;
    }
    assertEquals(countFirstPass, countSecondPass);
  }

  @Test
  public void testWithPublicAndWithAcls() {
    Metadata.Builder builder = new Metadata.Builder()
        .add(MetaItem.raw("google:aclusers", "peter,mathew"))
        .add(MetaItem.raw("google:aclgroups", "apostles"))
        .add(MetaItem.raw("google:ispublic", "true"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = builder.build(); 
  }

  @Test
  public void testNoPublicNoAcls() {
    Metadata box = new Metadata.Builder()
        .add(MetaItem.raw("X", "peter,mathew"))
        .add(MetaItem.raw("Y", "apostles"))
        .add(MetaItem.raw("Z", "true"))
        .build();
  }

  @Test
  public void testNoPublicWithAcls() {
    Metadata box = new Metadata.Builder()
        .add(MetaItem.raw("google:aclusers", "peter,mathew"))
        .add(MetaItem.raw("google:aclgroups", "apostles"))
        .add(MetaItem.raw("Z", "true"))
        .build();
  }

  @Test
  public void testWithPublicNoAcls() {
    Metadata box = new Metadata.Builder()
        .add(MetaItem.raw("X", "peter,mathew"))
        .add(MetaItem.raw("Y", "apostles"))
        .add(MetaItem.raw("google:ispublic", "true"))
        .build();
  }

  @Test
  public void testWithUsersNoGroups() {
    Metadata.Builder builder = new Metadata.Builder()
        .add(MetaItem.raw("google:aclusers", "peter,mathew"))
        .add(MetaItem.raw("Y", "apostles"))
        .add(MetaItem.raw("Z", "true"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = builder.build(); 
  }

  @Test
  public void testNoUsersWithGroups() {
    Metadata.Builder builder = new Metadata.Builder()
        .add(MetaItem.raw("X", "peter,mathew"))
        .add(MetaItem.raw("google:aclgroups", "apostles"))
        .add(MetaItem.raw("Z", "true"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = builder.build(); 
  }

  @Test
  public void testPublicCanBeTrue() {
    Metadata box = new Metadata.Builder()
        .add(MetaItem.raw("google:ispublic", "true"))
        .build();
  }

  @Test
  public void testPublicCanBeFalse() {
    Metadata box = new Metadata.Builder()
        .add(MetaItem.raw("google:ispublic", "false"))
        .build();
  }

  @Test
  public void testPublicMustBeBoolean() {
    Metadata.Builder builder = new Metadata.Builder()
        .add(MetaItem.raw("google:ispublic", "dog"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = builder.build(); 
  }

  @Test
  public void testToString() {
    assertEquals("[MetaItem(author,iceman), MetaItem(google:ispublic,true)]",
                 couple.toString());
  }

  @Test
  public void testIsEmpty() {
    assertEquals(true, Metadata.EMPTY.isEmpty());
  }

  private static Metadata makeCouple() {
    return new Metadata.Builder()
        .add(MetaItem.raw("google:ispublic", "true"))
        .add(MetaItem.raw("author", "iceman"))
        .build();
  }

  private static Metadata makeTriple() {
    return new Metadata.Builder()
        .add(MetaItem.raw("google:aclusers", "peter,mathew"))
        .add(MetaItem.raw("google:aclgroups", "apostles"))
        .add(MetaItem.raw("where", "there"))
        .build();
  }
}
