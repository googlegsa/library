package adaptorlib;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/**
 * Tests for {@link DocumentHandlerTest}.
 */
public class DocumentHandlerTest {
  @Test
  public void testFormMetadataHeader() {
    Set<MetaItem> items = new HashSet<MetaItem>();
    items.add(MetaItem.isPublic());
    items.add(MetaItem.raw("test", "ing"));
    items.add(MetaItem.raw("another", "item"));
    items.add(MetaItem.raw("equals", "=="));
    String result = DocumentHandler.formMetadataHeader(new Metadata(items));
    assertEquals("another%3Ditem,equals%3D%3D%3D,google%3Aispublic%3Dtrue,"
                 + "test%3Ding", result);
  }

  @Test
  public void testPercentEncoding() {
    String encoded
        = DocumentHandler.percentEncode("AaZz-_.~`=/?+';\\/\"!@#$%^&*()[]{}ë");
    assertEquals("AaZz-_.~%60%3D%2F%3F%2B%27%3B%5C%2F%22%21%40%23%24%25%5E%26"
                 + "%2A%28%29%5B%5D%7B%7D%C3%AB", encoded);
  }
}
