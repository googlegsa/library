package adaptorlib;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;

/**
 * Tests for {@link Journal}.
 */
public class JournalTest {

  @Test
  public void testPushCounts() {
    DocId id  = new DocId("id1");
    DocId id2 = new DocId("id2");
    DocId id3 = new DocId("id3", DocReadPermissions.USE_HEAD_REQUEST);
    DocId id4 = new DocId("id4", DocReadPermissions.USE_HEAD_REQUEST);
    ArrayList<DocId> docs = new ArrayList<DocId>();
    docs.add(id);
    docs.add(id2);
    docs.add(id3);
    Journal.recordDocIdPush(docs);
    assertEquals(3, Journal.numUniqueDocIdsPushed());
    Journal.recordDocIdPush(docs);
    assertEquals(3, Journal.numUniqueDocIdsPushed());
    docs.add(id4);
    Journal.recordDocIdPush(docs);
    assertEquals(4, Journal.numUniqueDocIdsPushed());
  }
}
