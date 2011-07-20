package adaptorlib;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;
import java.util.Properties;

/**
 * Configuration values for this program like the GSA's hostname. Also several
 * knobs, or controls, for changing the behavior of the program.
 */
public class Config {
  /** Default configuration values */
  protected final Properties defaultConfig = new Properties();
  /** Overriding configuration values loaded from file and command line */
  protected Properties config = new Properties(defaultConfig);
  protected static final String DEFAULT_CONFIG_FILE
      = "adaptor-config.properties";

  public Config() {
    String hostname = null;
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException ex) {
      // Ignore
    }
    defaultConfig.setProperty("server.hostname", hostname);
    defaultConfig.setProperty("server.port", "5678");
    defaultConfig.setProperty("server.docIdPath", "/doc/");
    // No default
    //defaultConfig.setProperty("gsa.hostname", null);
    defaultConfig.setProperty("gsa.characterEncoding", "UTF-8");
    defaultConfig.setProperty("docId.isUrl", "false");
    defaultConfig.setProperty("feed.name", "testfeed");
    defaultConfig.setProperty("feed.noRecrawlBitEnabled", "false");
    defaultConfig.setProperty("feed.crawlImmediatelyBitEnabled", "false");
    //defaultConfig.setProperty("feed.noFollowBitEnabled", "false");
    defaultConfig.setProperty("feed.maxUrls", "5000");
  } 

  public Set<String> getAllKeys() {
    return config.stringPropertyNames() ;
  }

  /* Preferences requiring you to set them: */
  /**
   * Required to be set: GSA machine to send document ids to. This is the
   * hostname of your GSA on your network.
   */
  public String getGsaHostname() {
    return getValue("gsa.hostname");
  }

  /* Preferences suggested you set them: */

  public String getFeedName() {
    return getValue("feed.name");
  }

  /**
   * Suggested to be set: Local port, on this computer, onto which requests from
   * GSA come in on.
   */
  public int getServerPort() {
    return Integer.parseInt(getValue("server.port"));
  }

  /* More sophisticated preferences that can be left
   unmodified for simple deployment and initial POC: */
  /**
   * Optional (default false): If your DocIds are already valid URLs you can
   * have this method return true and they will be sent to GSA unmodified. If
   * your DocId is like http://procurement.corp.company.com/internal/011212.html
   * you can turn this true and that URL will be handed to the GSA.
   *
   * <p>By default DocIds are URL encoded and prefixed with http:// and this
   * host's name and port.
   */
  public boolean isDocIdUrl() {
    return Boolean.parseBoolean(getValue("docId.isUrl"));
  }

  /** Without changes contains InetAddress.getLocalHost().getHostName(). */
  public String getServerHostname() {
    return getValue("server.hostname");
  }

  /**
   * Optional: Returns this host's base URI which other paths will be resolved
   * against. It is used to construct URIs to provide to the GSA for it to
   * contact this server for various services. For documents (which is probably
   * what you care about), the {@link #getServerBaseUri(DocId)} version is used
   * instead.
   *
   * <p>It must contain the protocol, hostname, and port, but may optionally
   * contain a path like {@code /yourfavoritepath}. By default, the protocol,
   * hostname, and port are retrieved automatically and no path is set.
   */
  public URI getServerBaseUri() {
    return URI.create("http://" + getServerHostname() + ":" + getServerPort());
  }

  /**
   * Optional: Path below {@link #getServerBaseUri(DocId)} where documents are
   * namespaced. Generally, should be at least {@code "/"} and end with a slash.
   */
  public String getServerDocIdPath() {
    return getValue("server.docIdPath");
  }

  /**
   * Optional: Returns the host's base URI which GSA will contact for document
   * information, including document contents. By default it returns {@link
   * #getServerBaseUri()}.  However, if you would like to direct GSA's queries
   * for contents to go to other computers/binaries then you can change this
   * method.
   *
   * <p>For example, imagine that you want five binaries to serve the contents
   * of files to the GSA.  In this case you could split the document ids into
   * five categories using something like:
   *
   * <pre>String urlBeginnings[] = new String[] {
   *   "http://content-server-A:5678",
   *   "http://content-server-B:5678",
   *   "http://backup-server-A:5678",
   *   "http://backup-server-B:5678",
   *   "http://new-server:7878"
   * };
   * int shard = docId.getUniqueId().hashCode() % 5;
   * return URI.create(urlBeginnings[shard]);</pre>
   *
   * <p>Note that this URI is used in conjunction with {@link
   * #getServerDocIdPath} and the document ID to form the full URL. In addition,
   * by using {@link #getServerBaseUri()} and {@code getDocIdPath()}, we have to
   * be able to parse back the original document ID when a request comes to this
   * server.
   */
  public URI getServerBaseUri(DocId docId) {
    return getServerBaseUri();
  }

  /**
   * Optional (default false): Adds no-recrawl bit with sent records in feed
   * file. If connector handles updates and deletes then GSA does not have to
   * recrawl periodically to notice that a document is changed or deleted.
   */
  public boolean isFeedNoRecrawlBitEnabled() {
    return Boolean.getBoolean(getValue("feed.noRecrawlBitEnabled"));
  }

  /**
   * Optional (default false): Adds crawl-immediately bit with sent records in
   * feed file.  This bit makes the sent URL get crawl priority.
   */
  public boolean isCrawlImmediatelyBitEnabled() {
    return Boolean.parseBoolean(getValue("feed.crawlImmediatelyBitEnabled"));
  }

// TODO(pjo): Implement on GSA
//  /**
//   * Optional (default false): Adds no-follow bit with sent records in feed
//   * file. No-follow means that if document content has links they are not
//   * followed.
//   */
//  public boolean isNoFollowBitEnabled() {
//    return Boolean.parseBoolean(getValue("feed.noFollowBitEnabled"));
//  }

  /* Preferences expected to never change: */

  /** Provides the character encoding the GSA prefers. */
  public Charset getGsaCharacterEncoding() {
    return Charset.forName(getValue("gsa.characterEncoding"));
  }

  /**
   * Provides max number of URLs (equal to number of document ids) that are sent
   * to the GSA per feed file.
   */
  public int getFeedMaxUrls() {
    return Integer.parseInt(getValue("feed.maxUrls"));
  }

  /**
   * Load user-provided configuration file.
   */
  public void load(String configFile) throws IOException {
    load(new File(configFile));
  }

  /**
   * Load user-provided configuration file.
   */
  public void load(File configFile) throws IOException {
    load(new InputStreamReader(new FileInputStream(configFile),
                               Charset.forName("UTF-8")));
  }

  /**
   * Load user-provided configuration file.
   */
  public void load(Reader configFile) throws IOException {
    config.load(configFile);
  }

  /**
   * Loads {@code adaptor-config.properties} in the current directory, if it
   * exists. It squelches any errors so that you are free to call it without
   * error handling, since this is typically non-fatal.
   */
  public void loadDefaultConfigFile() {
    File confFile = new File(DEFAULT_CONFIG_FILE);
    if (confFile.exists() && confFile.isFile()) {
      try {
        load(confFile);
      } catch (IOException ex) {
        System.err.println("Exception when reading " + DEFAULT_CONFIG_FILE);
        ex.printStackTrace(System.err);
      }
    }
  }

  /**
   * Load default configuration file and parse command line options.
   *
   * @return unused command line arguments
   */
  public String[] autoConfig(String[] args) {
    loadDefaultConfigFile();
    int i;
    for (i = 0; i < args.length; i++) {
      if (!args[i].startsWith("-D")) {
        break;
      }
      String arg = args[i].substring(2);
      String[] parts = arg.split("=", 2);
      if (parts.length < 2) {
        break;
      }
      config.setProperty(parts[0], parts[1]);
    }
    if (i == 0) {
      return args;
    } else {
      return Arrays.copyOfRange(args, i, args.length);
    }
  }

  /**
   * Get a configuration value, without thrown an exception if it is unset.
   */
  public String getPossiblyUnsetValue(String key) {
    return config.getProperty(key);
  }

  /**
   * Get a configuration value, using {@code default} if it is unset.
   */
  public String getValueOrDefault(String key, String defaultValue) {
    String value = getPossiblyUnsetValue(key);
    return (value == null) ? defaultValue : value;
  }

  /**
   * Get a configuration value. Never returns {@code null}.
   *
   * @throws IllegalStateException if {@code key} has no value
   */
  public String getValue(String key) {
    String value = getPossiblyUnsetValue(key);
    if (value == null) {
      throw new IllegalStateException(MessageFormat.format(
          "You must set configuration key ''{0}''.", key));
    }
    return value;
  }
}
