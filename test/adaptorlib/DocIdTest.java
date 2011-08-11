package adaptorlib;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

/**
 * Tests for {@link DocId}.
 */
public class DocIdTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

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
    assertFalse(id.equals("Some random object"));
    assertFalse(id.equals(null));
  }

  @Test
  public void testToString() {
    String rawId = "some docid";
    String docIdToString = new DocId(rawId).toString();
    assertTrue(docIdToString.contains(rawId));
  }

  @Test
  public void testConstructorNull() {
    thrown.expect(NullPointerException.class);
    new DocId(null);
  }

  @Test
  public void testFeedFileAction() {
    assertEquals("add", new DocId("some docid").getFeedFileAction());
  }
}
