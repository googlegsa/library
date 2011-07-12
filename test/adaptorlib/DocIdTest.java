package adaptorlib;

import static org.junit.Assert.*;

import java.util.HashMap;
import org.junit.Test;

public class DocIdTest {
  private static void assureHashSame(Object apple, Object orange) {
    if (apple.hashCode() != orange.hashCode()) {
      throw new RuntimeException("" + apple + " hash mismatch with "
          + orange);
    }
  }

  @Test
  public void testDocIdHash() {
    DocId id = new DocId("procure/book3/sd7823.flx");
    DocId id2 = new DocId("procure/book3/sd7823.flx");
    DocId id3 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    DocId id4 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    assureHashSame(id,id2);
    assureHashSame(id3,id4);
    HashMap<Object,Object> m = new HashMap<Object,Object>();
    m.put(id, m);
    if (m.get(id2) != m) {
      throw new RuntimeException("key mismatch: " + id2);
    }
    m.put(id3, m);
    if (m.get(id4) != m) {
      throw new RuntimeException("key mismatch: " + id4);
    }
  }

  @Test
  public void testDocIdEqual() {
    DocId id = new DocId("procure/book3/sd7823.flx");
    DocId id2 = new DocId("procure/book3/sd7823.flx");
    DocId id3 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    DocId id4 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    assertEquals(id, id2);
    assertEquals(id3, id4);
    assertFalse(id.equals(id3));
    assertFalse(id3.equals(new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.IS_PUBLIC)));
    assertFalse(id3.equals(new DocId("procure/book3/XYZXYZ.flx",
        DocReadPermissions.USE_HEAD_REQUEST)));
    // assertNotEquals(id3, new DeletedDocId("procure/book3/sd7823.flx"));
    // assertNotEquals(new DeletedDocId("procure/book3/sd7823.flx"), id3);
  }
}
