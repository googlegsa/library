package adaptorlib;

import static org.junit.Assert.*;
import org.junit.Test;

import adaptorlib.TableGeneratorTransform;
import adaptorlib.TransformException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

public class TableGeneratorTransformTest {
  private static String TEST_DIR = "src/adaptorlib/tests/";

  @Test
  public void testNoInput() throws IOException, TransformException {
    TableGeneratorTransform transform = new TableGeneratorTransform();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals("<HTML><HEAD></HEAD><BODY></BODY></HTML>", contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testFull() throws IOException, TransformException {
    TableGeneratorTransform transform = new TableGeneratorTransform();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    String csv = "header1,\"\"\"header2\"\"\",\"This is a header\n" +
        "with a newline\",header4\n" +
        "This is the first field of the second record,\"This field has\n" +
        "a newline\",,field4";
    String goldenOutput =
        "<HTML><HEAD></HEAD><BODY><table border=\"1\">\n" +
        "<tr>\n" +
        "<td>header1</td>\n" +
        "<td>\"header2\"</td>\n" +
        "<td>This is a header\n" +
        "with a newline</td>\n" +
        "<td>header4</td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        "<td>This is the first field of the second record</td>\n" +
        "<td>This field has\n" +
        "a newline</td>\n" +
        "<td></td>\n" +
        "<td>field4</td>\n" +
        "</tr>\n" +
        "</table></BODY></HTML>";
    contentIn.write(csv.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(goldenOutput, contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }
}
