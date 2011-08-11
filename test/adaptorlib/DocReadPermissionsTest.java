package adaptorlib;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.HashMap;

/**
 * Tests for {@link DocReadPermissions}.
 */
public class DocReadPermissionsTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static void assertNotEquals(Object apple, Object orange) {
    assertFalse(apple.equals(orange));
  }

  @Test
  public void testConstructor() {
    DocReadPermissions perm = new DocReadPermissions("user1", "group1");
    assertEquals("user1", perm.getUsers());
    assertEquals("group1", perm.getGroups());
    assertFalse(perm.isPublic());

    perm = new DocReadPermissions(null, "group1");
    assertNull(perm.getUsers());
    assertEquals("group1", perm.getGroups());
    assertFalse(perm.isPublic());

    perm = new DocReadPermissions(" ", "group1");
    assertNull(perm.getUsers());
    assertEquals("group1", perm.getGroups());
    assertFalse(perm.isPublic());

    perm = new DocReadPermissions("user1", null);
    assertEquals("user1", perm.getUsers());
    assertNull(perm.getGroups());
    assertFalse(perm.isPublic());

    perm = new DocReadPermissions("user1", " ");
    assertEquals("user1", perm.getUsers());
    assertNull(perm.getGroups());
    assertFalse(perm.isPublic());
  }

  @Test
  public void testInvalidConstructor() {
    thrown.expect(IllegalArgumentException.class);
    new DocReadPermissions(null, " ");
  }

  @Test
  public void testHashDocReadPermissions() {
    DocReadPermissions perm = new DocReadPermissions("chad", "chap");
    DocReadPermissions perm2 = new DocReadPermissions("chad", "chap");
    DocReadPermissions perm3 = DocReadPermissions.IS_PUBLIC;
    DocReadPermissions perm4 = DocReadPermissions.IS_PUBLIC;
    assertEquals("hash mismatch", perm.hashCode(), perm2.hashCode());
    assertEquals("hash mismatch", perm3.hashCode(), perm4.hashCode());
    HashMap<Object, Object> m = new HashMap<Object, Object>();
    m.put(perm, m);
    assertEquals(m.get(perm2), m);
    m.put(perm3, m);
    assertEquals(m.get(perm4), m);
  }

  @Test
  public void testEqualDocReadPermissions() {
    DocReadPermissions perm = new DocReadPermissions("cory", "chap");
    DocReadPermissions perm2 = new DocReadPermissions("cory", "chap");
    DocReadPermissions perm3 = DocReadPermissions.USE_HEAD_REQUEST;
    DocReadPermissions perm4 = DocReadPermissions.IS_PUBLIC;
    DocReadPermissions perm5 = new DocReadPermissions(null, "chap");
    DocReadPermissions perm6 = new DocReadPermissions("cory", null);
    assertEquals(perm, perm2);
    assertEquals(perm3, perm3);
    assertNotEquals(perm3, perm4);
    assertNotEquals(perm, perm3);
    assertNotEquals(perm, perm5);
    assertNotEquals(perm, perm6);
    assertNotEquals(perm3, perm5);
    assertNotEquals(perm3, perm6);
    assertNotEquals(perm, null);
    assertNotEquals(perm, "random object");
  }

  @Test
  public void testToString() {
    String users = "user1,user2";
    String groups = "group1,group2";
    String toString = new DocReadPermissions(users, groups).toString();
    assertTrue(toString.contains(users));
    assertTrue(toString.contains(groups));
  }
}
