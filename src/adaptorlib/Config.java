// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package adaptorlib;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Configuration values for this program like the GSA's hostname. Also several
 * knobs, or controls, for changing the behavior of the program.
 */
public class Config {
  /** Configuration keys whose default value is {@code null}. */
  protected final Set<String> noDefaultConfig = new HashSet<String>();
  /** Default configuration values */
  protected final Properties defaultConfig = new Properties();
  /** Overriding configuration values loaded from file and command line */
  protected Properties config = new Properties(defaultConfig);
  protected static final String DEFAULT_CONFIG_FILE
      = "adaptor-config.properties";

  public Config() {
    String hostname = null;
    try {
      hostname = InetAddress.getLocalHost().getCanonicalHostName();
    } catch (UnknownHostException ex) {
      // Ignore
    }
    addKey("server.hostname", hostname);
    addKey("server.port", "5678");
    addKey("server.docIdPath", "/doc/");
    addKey("server.gsaIps", "");
    addKey("server.addResolvedGsaHostnameToGsaIps", "true");
    addKey("server.secure", "false");
    addKey("server.keyAlias", "adaptor");
    addKey("gsa.hostname", null);
    addKey("gsa.characterEncoding", "UTF-8");
    addKey("docId.isUrl", "false");
    addKey("feed.name", "testfeed");
    addKey("feed.noRecrawlBitEnabled", "false");
    addKey("feed.crawlImmediatelyBitEnabled", "false");
    //addKey("feed.noFollowBitEnabled", "false");
    addKey("feed.maxUrls", "5000");
  }

  public Set<String> getAllKeys() {
    return config.stringPropertyNames();
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
   * Whether to automatically consider "gsa.hostname" configuration value part
   * of the "server.gsaIps" list. Defaults to {@code true"}.
   *
   * @see #getServerGsaIps
   */
  public boolean getServerAddResolvedGsaHostnameToGsaIps() {
    return Boolean.parseBoolean(getValue(
        "server.addResolvedGsaHostnameToGsaIps"));
  }

  /**
   * Comma-separated list of IPs or hostnames to consider the GSA and bypass
   * authentication checks.
   */
  public String[] getServerGsaIps() {
    return getValue("server.gsaIps").split(",");
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
    String protocol = isServerSecure() ? "https" : "http";
    return URI.create(protocol + "://" + getServerHostname() + ":"
                      + getServerPort());
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
   * Whether full security should be enabled. When {@code true}, the adaptor is
   * locked down using HTTPS, checks certificates, and generally behaves in a
   * fully-secure manner. When {@code false} (default), the adaptor serves
   * content over HTTP and is unable to authenticate users (all users are
   * treated as anonymous).
   *
   * <p>The need for this setting is because when enabled, security requires a
   * reasonable amount of configuration and know-how. To provide easy
   * out-of-the-box execution, this is disabled by default.
   */
  public boolean isServerSecure() {
    return Boolean.parseBoolean(getValue("server.secure"));
  }

  /**
   * The alias in the keystore that has the key to use for encryption.
   */
  public String getServerKeyAlias() {
    return getValue("server.keyAlias");
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
   * @throws IllegalStateException when not all configuration keys have values
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
    Set<String> unset = new HashSet<String>();
    for (String key : noDefaultConfig) {
      if (config.getProperty(key) == null) {
        unset.add(key);
      }
    }
    if (unset.size() != 0) {
      throw new IllegalStateException("Missing configuration values: " + unset);
    }
    if (i == 0) {
      return args;
    } else {
      return Arrays.copyOfRange(args, i, args.length);
    }
  }

  /**
   * Get a configuration value. Never returns {@code null}.
   *
   * @throws IllegalStateException if {@code key} has no value
   */
  public String getValue(String key) {
    String value = config.getProperty(key);
    if (value == null) {
      throw new IllegalStateException(MessageFormat.format(
          "You must set configuration key ''{0}''.", key));
    }
    return value;
  }

  /**
   * Add configuration key. If defaultValue is {@code null}, then no default
   * value is used.
   */
  public void addKey(String key, String defaultValue) {
    if (defaultConfig.contains(key) || noDefaultConfig.contains(key)) {
      throw new IllegalStateException("Key already added: " + key);
    }
    if (defaultValue == null) {
      noDefaultConfig.add(key);
    } else {
      defaultConfig.setProperty(key, defaultValue);
    }
  }
}
