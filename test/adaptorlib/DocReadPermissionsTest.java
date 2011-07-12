package adaptorlib;

import static org.junit.Assert.*;

import java.util.HashMap;
import org.junit.Test;

public class DocReadPermissionsTest {
  private static void assureHashSame(Object apple, Object orange) {
    if (apple.hashCode() != orange.hashCode()) {
      throw new RuntimeException("" + apple + " hash mismatch with "
          + orange);
    }
  }

  private static void assureDifferent(Object apple, Object orange) {
    assertFalse(apple.equals(orange));
  }

  @Test
  public void testHashDocReadPermissions() {
    DocReadPermissions perm = new DocReadPermissions("chad", "chap");
    DocReadPermissions perm2 = new DocReadPermissions("chad", "chap");
    DocReadPermissions perm3 = DocReadPermissions.IS_PUBLIC;
    DocReadPermissions perm4 = DocReadPermissions.IS_PUBLIC;
    assureHashSame(perm, perm2);
    assureHashSame(perm3, perm4);
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
    assureDifferent(perm, perm3);
    assureDifferent(perm, perm5);
    assureDifferent(perm, perm6);
    assureDifferent(perm3, perm5);
    assureDifferent(perm3, perm6);
  }
}
