package adaptorlib;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
/** Configuration values for this program like the GSA's hostname.
  Also several knobs, or controls, for changing the behaviour
  of the program. */
public class Config {
  private static Logger LOG
      = Logger.getLogger(Config.class.getName());

  /* Preferences requiring you to set them: */
  /** Required to be set: GSA machine to send
    document ids to. This is the hostname of
    your GSA on your network. */
  static String getGsaHostname() {
    return "stdiom39";
  }

  /* Preferences suggested you set them: */
  /** Suggested to be set: Local port, on this
   computer, onto which requests from GSA come in on. */
  public static int getLocalPort() {
    return 5678;
  }

  /* More sophisticated preferences that can be left 
   unmodified for simple deployment and initial POC: */
  /** Optional (default false):
   If your DocIds are already valid URLs you can
   have this method return true and they will
   be sent to GSA unmodified. If your DocId is like
   http://procurement.corp.company.com/internal/011212.html"
   you can turn this true and that URL will be handed to
   the GSA.
   <p> By default DocIds are URL encoded and prefixed
   with http:// and this host's name and port.  */
  static boolean passDocIdToGsaWithoutModification() {
    return false;
  }

  /** Optional:
    This host's URL beginning, consisting of protocol
    and hostname and port, likely already has a good
    default.  Used by GSA to get in touch with this program.
    The GSA requests information about a document id you gave.
    The document ids are put at the end of this URL beginning
    (encoded if necessary) to create full URLs.  */
  static String getUrlBeginning() {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      return "http://" + hostname + ":" + getLocalPort();
    } catch(UnknownHostException ex) {
      throw new RuntimeException(
          "Could not automatically determine service URL.", ex);
    }
  }

  /**
   * {@link #getUrlBeginning} provided as a URI.
   */
  static URI getBaseUri() {
    return URI.create(Config.getUrlBeginning());
  }

  /**
   * Path below {@link #getUrlBeginning(DocId)} where documents are namespaced.
   * Should be at least "/".
   */
  static String getDocIdPath() {
    return "/doc/";
  }

  /** Optional:
    Returns the host and port which GSA will contact for
    document information including document contents, looks like
    http://my.computer.hostname.com:5678 .  By
    default it equates with getUrlBeginning().  However
    if you would like to direct GSA's queries for contents
    to go to other computers/binaries then you can change
    this method.
    <p> For example imagine that you want five binaries
    to serve the contents of files to the GSA.  In this
    case you could split the document ids into five
    categories using something like:<pre>
    DocId inputId = new DocId("procurement/lastmonth/2323423432.sta");
    String urlBeginnings[] = new String[] {
      "http://content-server-A:5678/",
      "http://content-server-B:5678/",
      "http://backup-server-A:5678/",
      "http://backup-server-B:5678/",
      "http://new-server:7878/"
    };
    int shard = inputId.getUniqueId().hashCode() % 5;
    return urlBeginnings[shard];
    </pre>
   */
  static String getUrlBeginning(DocId docId) {
    return getUrlBeginning();
  }

  static URI getBaseUri(DocId docId) {
    return URI.create(getUrlBeginning(docId));
  }

  /** Optional (default false):
   Adds no-recrawl bit with sent records in feed file.
   If connector handles updates and deletes 
   then GSA does not have to recrawl periodically to 
   notice that a document is changed or deleted. */ 
  static boolean useNoRecrawlBit() {
    return false;
  }

  /** Optional (default false):
    Adds crawl-immediately bit with sent records
    in feed file.  This bit makes the sent URL get crawl
    priority. */ 
  static boolean useCrawlImmediatelyBit() {
    return false;
  }

  /** Optional (default false):
    Adds no-follow bit with sent records in feed file.
    No-follow means that if document content has links
    they are not followed. */
  static boolean useNoFollowBit() {
    return false;
  }

  /** Optional: GsaCommunicationHandler.pushDocIds had
    a failure connecting with GSA to send a batch.  The
    thrown exception is provided as well the number of times
    that this batch was attempted to be sent. Return true
    to retry, perhaps after a Thread.sleep() of some time. */
  static boolean handleFailedToConnect(
      GsaFeedFileSender.FailedToConnect ftc, int ntries) {
    if (ntries > 12) {
      throw new RuntimeException(ftc);
    }
    try {
      Thread.sleep(5000 * ntries);
      return true;
    } catch (InterruptedException e) {
      LOG.log(Level.WARNING, "", e);
      return false;
    }
  }

  /** Optional: GsaCommunicationHandler.pushDocIds had
    a failure writing to the GSA while sending a batch.  The
    thrown exception is provided as well the number of times
    that this batch was attempted to be sent. Return true
    to retry, perhaps after a Thread.sleep() of some time. */
  static boolean handleFailedToConnect(
      GsaFeedFileSender.FailedWriting fw, int ntries) {
    if (ntries > 12) {
      throw new RuntimeException(fw);
    }
    try {
      Thread.sleep(5000 * ntries);
      return true;
    } catch (InterruptedException e) {
      LOG.log(Level.WARNING, "", e);
      return false;
    }
  }

  /** Optional: GsaCommunicationHandler.pushDocIds had
    a failure reading response from GSA.  The
    thrown exception is provided as well the number of times
    that this batch was attempted to be sent. Return true
    to retry, perhaps after a Thread.sleep() of some time. */
  static boolean handleFailedToConnect(
      GsaFeedFileSender.FailedReadingReply fr, int ntries) {
    if (ntries > 12) {
      throw new RuntimeException(fr);
    }
    try {
      Thread.sleep(5000 * ntries);
      return true;
    } catch (InterruptedException e) {
      LOG.log(Level.WARNING, "", e);
      return false;
    }
  }

  /* Preferences expected to never change: */

  /** Provides the character encoding the GSA prefers. */
  public static String getGsaCharacterEncodingName() {
    return "UTF-8";
  }

  /** Provides the character encoding the GSA prefers. */
  public static Charset getGsaCharacterEncoding() {
    return Charset.forName(getGsaCharacterEncodingName());
  }

  /** Provides max number of URLs (equal to number of
    document ids) that are sent to the GSA per feed file. */
  static int getUrlsPerFeedFile() {
    return 5000;
  }
}
