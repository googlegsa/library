package adaptorlib;

import static org.junit.Assert.*;

import org.junit.Test;

import java.net.URI;

/**
 * Tests for {@link GsaCommunicationHandler}.
 */
public class GsaCommunicationHandlerTest {
  private GsaCommunicationHandler gsa
      = new GsaCommunicationHandler(new NullAdaptor(), new Config());

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

  private void decodeAndEncode(String id) {
    URI uri = gsa.encodeDocId(new DocId(id));
    assertEquals(id, gsa.decodeDocId(uri).getUniqueId());
  }

  @Test
  public void testAssortedNonDotIds() {
    decodeAndEncode("simple-id");
    decodeAndEncode("harder-id/");
    decodeAndEncode("harder-id/./");
    decodeAndEncode("harder-id///&?///");
    decodeAndEncode("");
    decodeAndEncode(" ");
    decodeAndEncode(" \n\t  ");
    decodeAndEncode("/");
    decodeAndEncode("//");
    decodeAndEncode("drop/table/now");
    decodeAndEncode("/drop/table/now");
    decodeAndEncode("//drop/table/now");
    decodeAndEncode("//d&op/t+b+e/n*w");
  }

  private static class NullAdaptor extends AbstractAdaptor {
    @Override
    public void getDocIds(DocIdPusher pusher) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void getDocContent(Request req, Response Resp) {
      throw new UnsupportedOperationException();
    }
  }
}
