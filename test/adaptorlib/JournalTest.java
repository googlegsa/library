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
    Journal journal = new Journal();
    DocId id  = new DocId("id1");
    DocId id2 = new DocId("id2");
    DocId id3 = new DocId("id3");
    DocId id4 = new DocId("id4");
    ArrayList<DocId> docs = new ArrayList<DocId>();
    docs.add(id);
    docs.add(id2);
    docs.add(id3);
    journal.recordDocIdPush(docs);
    assertEquals(3, journal.numUniqueDocIdsPushed());
    journal.recordDocIdPush(docs);
    assertEquals(3, journal.numUniqueDocIdsPushed());
    docs.add(id4);
    journal.recordDocIdPush(docs);
    assertEquals(4, journal.numUniqueDocIdsPushed());
  }
}
