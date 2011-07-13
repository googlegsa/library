package adaptorlib;

import static org.junit.Assert.*;

import java.util.HashMap;
import org.junit.Test;

public class DocReadPermissionsTest {

  private static void assertNotEquals(Object apple, Object orange) {
    assertFalse(apple.equals(orange));
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
    DocReadPermissions perm4 = DocReadPermissions.USE_HEAD_REQUEST;
    DocReadPermissions perm5 = new DocReadPermissions(null, "chap");
    DocReadPermissions perm6 = new DocReadPermissions("cory", null);
    assertEquals(perm, perm2);
    assertEquals(perm3, perm4);
    assertNotEquals(perm, perm3);
    assertNotEquals(perm, perm5);
    assertNotEquals(perm, perm6);
    assertNotEquals(perm3, perm5);
    assertNotEquals(perm3, perm6);
  }
}
