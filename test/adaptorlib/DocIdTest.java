package adaptorlib;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link DocId}.
 */
public class DocIdTest {

  @Test
  public void testDocIdHash() {
    DocId id = new DocId("procure/book3/sd7823.flx");
    DocId id2 = new DocId("procure/book3/sd7823.flx");
    DocId id3 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    DocId id4 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    assertEquals("hash mismatch", id.hashCode(), id2.hashCode());
    assertEquals("hash mismatch", id3.hashCode(), id4.hashCode());
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
  }
}
