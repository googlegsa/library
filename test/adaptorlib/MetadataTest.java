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

import java.util.Set;
import java.util.TreeSet;

/** Tests for {@link MetaItem}. */
public class MetadataTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  
  private Metadata couple = new Metadata(makeCouple());
  private Metadata coupleB = new Metadata(makeCouple());
  private Metadata triple = new Metadata(makeTriple());
  private Metadata tripleB = new Metadata(makeTriple());

  @Test
  public void testEquals() {
    assertEquals(couple, coupleB);
    assertEquals(triple, tripleB);
    assertEquals(Metadata.EMPTY, new Metadata(new TreeSet<MetaItem>()));
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
  public void testEachNameUnique() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("a", "barney"));
    items.add(MetaItem.raw("a", "frank"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testWithPublicAndWithAcls() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("google:aclusers", "peter,mathew"));
    items.add(MetaItem.raw("google:aclgroups", "apostles"));
    items.add(MetaItem.raw("google:ispublic", "true"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testNoPublicNoAcls() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("X", "peter,mathew"));
    items.add(MetaItem.raw("Y", "apostles"));
    items.add(MetaItem.raw("Z", "true"));
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testNoPublicWithAcls() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("google:aclusers", "peter,mathew"));
    items.add(MetaItem.raw("google:aclgroups", "apostles"));
    items.add(MetaItem.raw("Z", "true"));
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testWithPublicNoAcls() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("X", "peter,mathew"));
    items.add(MetaItem.raw("Y", "apostles"));
    items.add(MetaItem.raw("google:ispublic", "true"));
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testWithUsersNoGroups() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("google:aclusers", "peter,mathew"));
    items.add(MetaItem.raw("Y", "apostles"));
    items.add(MetaItem.raw("Z", "true"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testNoUsersWithGroups() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("X", "peter,mathew"));
    items.add(MetaItem.raw("google:aclgroups", "apostles"));
    items.add(MetaItem.raw("Z", "true"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testPublicCanBeTrue() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("google:ispublic", "true"));
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testPublicCanBeFalse() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("google:ispublic", "false"));
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testPublicMustBeBoolean() {
    Set<MetaItem> items = new TreeSet<MetaItem>();
    items.add(MetaItem.raw("google:ispublic", "dog"));
    thrown.expect(IllegalArgumentException.class);
    Metadata box = new Metadata(items); 
  }

  @Test
  public void testToString() {
    assertEquals("[MetaItem(author,iceman), MetaItem(google:ispublic,true)]",
                 couple.toString());
  }

  private static Set<MetaItem> makeCouple() {
    Set<MetaItem> coupleItems = new TreeSet<MetaItem>();
    coupleItems.add(MetaItem.raw("google:ispublic", "true"));
    coupleItems.add(MetaItem.raw("author", "iceman"));
    return coupleItems;
  }

  private static Set<MetaItem> makeTriple() {
    Set<MetaItem> tripleItems = new TreeSet<MetaItem>();
    tripleItems.add(MetaItem.raw("google:aclusers", "peter,mathew"));
    tripleItems.add(MetaItem.raw("google:aclgroups", "apostles"));
    tripleItems.add(MetaItem.raw("where", "there"));
    return tripleItems;
  }
}
