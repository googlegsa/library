package adaptorlib;

import static org.junit.Assert.*;
import org.junit.Test;

import adaptorlib.MetaTaggerTransform;
import adaptorlib.TransformException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

public class MetaTaggerTransformTest {
  private static String TEST_DIR = "test/adaptorlib/";

  @Test
  public void testNoInput() throws IOException, TransformException {
    MetaTaggerTransform transform = new MetaTaggerTransform();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    String testString = "";
    contentIn.write(testString.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(testString, contentIn.toString());
    assertEquals(testString, contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testNoPattern() throws IOException, TransformException {
    MetaTaggerTransform transform = new MetaTaggerTransform();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    String testString = "Here is some input";
    contentIn.write(testString.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(testString, contentIn.toString());
    assertEquals(testString, contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testSimple() throws IOException, TransformException {
    MetaTaggerTransform transform = new MetaTaggerTransform(TEST_DIR + "testPattern1.txt");
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    String content =
        "<HTML>\n" +
        "<HEAD></head>\n" +
        "<BODY>\n" +
        "Today, John Paul gave a speach at local animal shelter. Animal lovers rejoice.\n" +
        "</BODY>\n" +
        "</HTML>\n";
    String goldenContent =
        "<HTML>\n" +
        "<HEAD>\n" +
        "<meta name=\"pope\" content=\"John Paul 2nd\" />\n" +
        "<meta name=\"city\" content=\"Mountain View\" />\n" +
        "</HEAD>\n" +
        "<BODY>\n" +
        "Today, John Paul gave a speach at local animal shelter. Animal lovers rejoice.\n" +
        "</BODY>\n" +
        "</HTML>\n";
    contentIn.write(content.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(goldenContent, contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testNoHead() throws IOException, TransformException {
    MetaTaggerTransform transform = new MetaTaggerTransform(TEST_DIR + "testPattern1.txt");
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    String content =
        "This is a document with no head element.\n" +
        "If there were a HEAD element, then the\n" +
        "transform would be inserting metadata somewhere in this doc.\n" +
        "  We should end up with the same output as input.\n";
    contentIn.write(content.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(content, contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testDuplicatePatternsInPatternFile() throws IOException, TransformException {
    MetaTaggerTransform transform = new MetaTaggerTransform(TEST_DIR + "testPatternDup.txt");
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    String content =
        "<HTML>\n" +
        "<HEAD></head>\n" +
        "<BODY>\n" +
        "Today, John Paul gave a speach at local animal shelter. Animal lovers rejoice.\n" +
        "</BODY>\n" +
        "</HTML>\n";
    String goldenContent = "<HTML>\n" +
        "<HEAD>\n" +
        "<meta name=\"pope\" content=\"John Paul 2nd\" />\n" +
        "<meta name=\"pope\" content=\"John Paul 3rd\" />\n" +
        "<meta name=\"city\" content=\"Mountain View\" />\n" +
        "</HEAD>\n" +
        "<BODY>\n" +
        "Today, John Paul gave a speach at local animal shelter. Animal lovers rejoice.\n" +
        "</BODY>\n" +
        "</HTML>\n";
    contentIn.write(content.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(goldenContent, contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }
}
