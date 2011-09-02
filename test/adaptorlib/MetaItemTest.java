// Copyright 2011 Google Inc.
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

/** Tests for {@link MetaItem}. */
public class MetaItemTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testRawNotNull() {
    thrown.expect(IllegalArgumentException.class);
    MetaItem.raw(null, "mapped from null");
  }

  @Test
  public void testRawNotEmtpy() {
    thrown.expect(IllegalArgumentException.class);
    MetaItem.raw(null, "mapped from empty");
  }

  @Test
  public void testNullValue() {
    assertEquals("", MetaItem.raw("nada", null).getValue());
  }

  @Test
  public void testComparable() {
    TreeSet<MetaItem> added = new TreeSet<MetaItem>();
    added.add(MetaItem.raw("e", "from E"));
    added.add(MetaItem.raw("c", "from C"));
    added.add(MetaItem.raw("f", "from F 1"));
    added.add(MetaItem.raw("d", "from D"));
    added.add(MetaItem.raw("b", "from B"));
    added.add(MetaItem.raw("f", "from F 2"));
    added.add(MetaItem.raw("a", "from A"));
    Iterator<MetaItem> it = added.iterator();
    assertEquals("a", it.next().getName());
    assertEquals("b", it.next().getName());
    assertEquals("c", it.next().getName());
    assertEquals("d", it.next().getName());
    assertEquals("e", it.next().getName());
    MetaItem fed = it.next();
    assertEquals("f", fed.getName());
    assertEquals("from F 1", fed.getValue());
    MetaItem fed2 = it.next();
    assertEquals("f", fed2.getName());
    assertEquals("from F 2", fed2.getValue());
  }

  @Test
  public void testComma() {
    ArrayList<String> users = new ArrayList<String>();
    assertEquals("", MetaItem.permittedUsers(users).getValue());
    users.add("adam");
    assertEquals("adam", MetaItem.permittedUsers(users).getValue());
    users.add("brandon");
    assertEquals("adam,brandon", MetaItem.permittedUsers(users).getValue());
    users.add("zena");
    assertEquals("adam,brandon,zena", MetaItem.permittedUsers(users).getValue());
  }

  @Test
  public void testCommaInUserName() {
    ArrayList<String> users = new ArrayList<String>();
    users.add("ad,am");
    thrown.expect(IllegalArgumentException.class);
    MetaItem.permittedUsers(users);
  }

  @Test
  public void testCommaInGroupName() {
    ArrayList<String> groups = new ArrayList<String>();
    groups.add("ad,am12");
    thrown.expect(IllegalArgumentException.class);
    MetaItem.permittedGroups(groups);
  }

  @Test
  public void testEquals() {
    MetaItem aa = MetaItem.raw("a", "a");
    MetaItem ab = MetaItem.raw("a", "b");
    MetaItem bb = MetaItem.raw("b", "b");
    MetaItem ba = MetaItem.raw("b", "a");
    MetaItem aa2 = MetaItem.raw("a", "a");
    assertEquals(aa, aa);
    assertEquals(aa, aa2);
    assertFalse(aa.equals(ab));
    assertFalse(aa.equals(bb));
    assertFalse(aa.equals(ba));
  }

  @Test
  public void testHashCode() {
    MetaItem aa = MetaItem.raw("a", "a");
    MetaItem ab = MetaItem.raw("a", "b");
    MetaItem bb = MetaItem.raw("b", "b");
    MetaItem ba = MetaItem.raw("b", "a");
    MetaItem aa2 = MetaItem.raw("a", "a");
    assertEquals(aa.hashCode(), aa.hashCode());
    assertEquals(aa.hashCode(), aa2.hashCode());
    assertFalse(aa.hashCode() == ab.hashCode());
    assertFalse(aa.hashCode() == bb.hashCode());
    assertFalse(aa.hashCode() == ba.hashCode());
  }
}
