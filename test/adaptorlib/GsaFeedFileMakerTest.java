package adaptorlib;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;
import org.junit.Test;

/** Tests for {@link GsaFeedFileMaker}. */
public class GsaFeedFileMakerTest {
  private static final DocIdEncoder ENCODER = new DocIdEncoder() {
    public URI encodeDocId(DocId docId) {
      try {
        return new URI(docId.getUniqueId());
      } catch (java.net.URISyntaxException e) {
        throw new IllegalStateException("while testing", e);
      }
    }
  };

  private GsaFeedFileMaker meker = new GsaFeedFileMaker(ENCODER);

  @Test
  public void testEmpty() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group/>\n"
        + "</gsafeed>\n";
    String xml = meker.makeMetadataAndUrlXml("t3sT", new ArrayList<DocId>());
    assertEquals(golden, xml);
  }

  @Test
  public void testSimple() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record action=\"add\" mimetype=\"text/plain\" url=\"el3ven\">\n"
        + "<metadata>\n"
        + "<meta content=\"el3ven\" name=\"displayurl\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "<record action=\"add\" mimetype=\"text/plain\" url=\"elefenta\">\n"
        + "<metadata>\n"
        + "<meta content=\"elefenta\" name=\"displayurl\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    ArrayList<DocId> ids = new ArrayList<DocId>();
    ids.add(new DocId("el3ven"));
    ids.add(new DocId("elefenta"));
    String xml = meker.makeMetadataAndUrlXml("t3sT", ids);
    assertEquals(golden, xml);
  }

  @Test
  public void testMetadata() {
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">\n"
        + "<gsafeed>\n"
        + "<!--GSA EasyConnector-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n"
        + "<record action=\"add\" mimetype=\"text/plain\" url=\"el3ven\">\n"
        + "<metadata>\n"
        + "<meta content=\"true\" name=\"google:ispublic\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "<record action=\"add\" mimetype=\"text/plain\" url=\"elefenta\">\n"
        + "<metadata>\n"
        + "<meta content=\"f000nkey\" name=\"displayurl\"/>\n"
        + "<meta content=\"in rods\" name=\"distance\"/>\n"
        + "<meta content=\"Po\" name=\"google:aclgroups\"/>\n"
        + "<meta content=\"Ty\" name=\"google:aclusers\"/>\n"
        + "</metadata>\n"
        + "</record>\n"
        + "</group>\n"
        + "</gsafeed>\n";
    ArrayList<DocId> ids = new ArrayList<DocId>();
    Set<MetaItem> item1 = Collections.singleton(MetaItem.isPublic());
    Metadata metadata1 = new Metadata(item1);
    ids.add(new DocIdWithMetadata("el3ven", metadata1));
    Set<MetaItem> items2 = new TreeSet<MetaItem>();
    items2.add(MetaItem.displayUrl("f000nkey"));
    items2.add(MetaItem.raw("distance", "in rods"));
    items2.add(MetaItem.permittedGroups(Collections.singletonList("Po")));
    items2.add(MetaItem.permittedUsers(Collections.singletonList("Ty")));
    Metadata metadata2 = new Metadata(items2);
    ids.add(new DocIdWithMetadata("elefenta", metadata2));
    String xml = meker.makeMetadataAndUrlXml("t3sT", ids);
    assertEquals(golden, xml);
  }
}
