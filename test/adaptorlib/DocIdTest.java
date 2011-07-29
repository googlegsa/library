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
    assertEquals("hash mismatch", id.hashCode(), id2.hashCode());
  }

  @Test
  public void testDocIdEqual() {
    DocId id = new DocId("procure/book3/sd7823.flx");
    DocId id2 = new DocId("procure/book3/sd7823.flx");
    assertEquals(id, id2);
    assertFalse(id.equals(new DocId("procure/book3/XYZXYZ.flx")));
  }
}
