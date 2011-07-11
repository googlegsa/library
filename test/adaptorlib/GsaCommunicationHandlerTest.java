package adaptorlib;

import static org.junit.Assert.*;

import java.net.URI;
import org.junit.Test;

public class GsaCommunicationHandlerTest {
  private GsaCommunicationHandler gsa
      = new GsaCommunicationHandler(null, new Config());

  @Test
  public void testRelativeDot() {
    String docId = ".././hi/.h/";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertFalse(uriStr.contains("/../"));
    assertFalse(uriStr.contains("/./"));
    assertTrue(uriStr.contains("/hi/.h/"));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDot() {
    String docId = ".";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("..."));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testDoubleDot() {
    String docId = "..";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("...."));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testNotToBeConfusedDots() {
    String docId = "...";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("....."));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testNotToBeChanged() {
    String docId = "..safe../.h/h./..h/h..";
    URI uri = gsa.encodeDocId(new DocId(docId));
    String uriStr = uri.toString();
    assertTrue(uriStr.contains(docId));
    assertEquals(docId, gsa.decodeDocId(uri).getUniqueId());
  }
}
