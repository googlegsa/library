package adaptorlib.examples;

import adaptorlib.TransformException;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link CalaisNERTransform}.
 */
public class CalaisNERTransformTest {

  // Note: These tests expect specific entities to be detected by OpenCalais.
  // Long term, we should mock out the webservice so we're not flaky.

  @Test
  public void testRestrictedSet() throws IOException, TransformException {
    CalaisNERTransform transform = new CalaisNERTransform();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("OpenCalaisApiKey", "4ydv87zawg7tf29jzex22d9u");
    params.put("UseCalaisEntity:Person", "True");
    params.put("UseCalaisEntity:Position", "True");
    // Test that "Country" is implicitly "True" when All isn't specified.
    params.put("UseCalaisEntity:NaturalFeature", "False");

    String testInput = "<HTML><HEAD></HEAD><BODY>"
        + "Prosecutors at the trial of former Liberian President Charles Taylor"
        + " hope the testimony of supermodel Naomi Campbell "
        + " will link Taylor to the trade in illegal conflict diamonds, "
        + " which they say he used to fund a bloody civil war in Sierra Leone."
        + "</BODY></HTML>";
    String golden = "<HTML><HEAD>\n"
        + "<meta name=\"Person\" content=\"Charles Taylor\" />\n"
        + "<meta name=\"Position\" content=\"President\" />\n"
        + "<meta name=\"Person\" content=\"Naomi Campbell\" />\n"
        + "<meta name=\"Country\" content=\"Sierra Leone\" />\n"
        + "<meta name=\"Country\" content=\"Liberia\" />\n"
        + "</HEAD><BODY>Prosecutors at the trial of former Liberian President Charles Taylor"
        + " hope the testimony of supermodel Naomi Campbell "
        + " will link Taylor to the trade in illegal conflict diamonds, "
        + " which they say he used to fund a bloody civil war in Sierra Leone."
        + "</BODY></HTML>";
    contentIn.write(testInput.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    assertEquals(golden, contentOut.toString());
  }

  @Test
  public void testAllEntities() throws IOException, TransformException {
    CalaisNERTransform transform = new CalaisNERTransform();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("OpenCalaisApiKey", "4ydv87zawg7tf29jzex22d9u");
    params.put("UseCalaisEntity:All", "True");

    String testInput = "<HTML><HEAD></HEAD><BODY>"
        + "Prosecutors at the trial of former Liberian President Charles Taylor"
        + " hope the testimony of supermodel Naomi Campbell "
        + " will link Taylor to the trade in illegal conflict diamonds, "
        + " which they say he used to fund a bloody civil war in Sierra Leone."
        + "</BODY></HTML>";
    String golden = "<HTML><HEAD>\n"
        + "<meta name=\"Person\" content=\"Charles Taylor\" />\n"
        + "<meta name=\"Position\" content=\"President\" />\n"
        + "<meta name=\"Person\" content=\"Naomi Campbell\" />\n"
        + "<meta name=\"Country\" content=\"Sierra Leone\" />\n"
        + "<meta name=\"NaturalFeature\" content=\"Sierra Leone\" />\n"
        + "<meta name=\"Country\" content=\"Liberia\" />\n"
        + "</HEAD><BODY>Prosecutors at the trial of former Liberian President Charles Taylor"
        + " hope the testimony of supermodel Naomi Campbell "
        + " will link Taylor to the trade in illegal conflict diamonds, "
        + " which they say he used to fund a bloody civil war in Sierra Leone."
        + "</BODY></HTML>";
    contentIn.write(testInput.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    assertEquals(golden, contentOut.toString());
  }
}
