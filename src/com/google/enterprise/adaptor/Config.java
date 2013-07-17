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

package com.google.enterprise.adaptor;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.*;

/**
 * Configuration values for this program, like the GSA's hostname. Also several
 * knobs, or controls, for changing the behavior of the program.
 * <p>All available configuration:<br>
 * <style type="text/css"> td { padding-right:2em; } </style>
 * <table>
 * <tr><td align=center><b>required?</b></td>
 *     <td><b>name</b></td><td><b>meaning</b></td>
 * <tr><td> </td><td>adaptor.sendDocControlsHeader </td><td>use 
 *      X-Gsa-Doc-Controls HTTP header with namespaced ACLs.
 *      Otherwise ACLs are sent without namespace and as metadata.
 *      Defaults to false
 * <tr><td> </td><td>adaptor.fullListingSchedule </td><td> when to invoke 
 *     {@link Adaptor#getDocIds Adaptor.getDocIds}, in cron format (minute,
 *     hour,  day of month, month, day of week).  Defaults to 0 3 * * *
 * <tr><td> </td><td>adaptor.incrementalPollPeriodSecs </td><td> number
 *     of seconds between invocations of {@link
 *     PollingIncrementalAdaptor#getModifiedDocIds
 *     PollingIncrementalAdaptor.getModifiedDocIds}.    Defaults to 900
 * <tr><td> </td><td>adaptor.docContentTimeoutSecs </td><td> number of seconds
 *     adaptor has to complete sending content before it is interrupted. Timing
 *     starts when sending content starts. Defaults to 180
 * <tr><td> </td><td>adaptor.docHeaderTimeoutSecs </td><td> number of seconds
 *     adaptor has to start sending content before it is interrupted.
 *     Defaults to 30
 * <tr><td> </td><td>adaptor.pushDocIdsOnStartup </td><td> whether to invoke
 *     {@link Adaptor#getDocIds Adaptor.getDocIds} on process start
 *     (in addition to adaptor.fullListingSchedule).   Defaults to true
 * <tr><td> </td><td>docId.isUrl </td><td> say your adaptor's document ids
 *     are already URLs and avoid them being inserted into adaptor
       generated URLs.   Defaults to false
 * <tr><td> </td><td>feed.crawlImmediatelyBitEnabled </td><td> send bit telling
 *     GSA to crawl immediately.  Defaults to false
 * <tr><td> </td><td>feed.maxUrls </td><td> set max number of URLs included
 *     per feed file.    Defaults to 5000
 * <tr><td> </td><td>feed.name </td><td> source name used in feeds. Generated
 *     if not provided
 * <tr><td> </td><td>feed.noRecrawlBitEnabled </td><td> send bit telling
 *     GSA to crawl your documents only once.  Defaults to  false
 * <tr><td> </td><td>gsa.614FeedWorkaroundEnabled </td><td> enable detour
 *     around particular feed parsing failure found in GSA version 6.14 .
 *     Defaults to false
 * <tr><td> </td><td>gsa.70AuthMethodWorkaroundEnabled </td><td> send authmethod
 *     in feed files to workaround early GSA 7.0 bug. Defaults to false
 * <tr><td> </td><td>gsa.characterEncoding </td><td> character set used
 *     in feed files. Defaults to  UTF-8
 * <tr><td align="center"> yes </td><td>gsa.hostname </td><td> machine to
 *     send feed files to.  Process errors if not provided 
 * <tr><td> </td><td>gsa.samlEntityId </td><td> The SAML Entity ID that
 *     identifies the GSA. Defaults to
 *     http://google.com/enterprise/gsa/security-manager
 * <tr><td> </td><td>journal.reducedMem </td><td> avoid tracking per URL 
 *     information in RAM; suggested with over five hundred thousand documents.
 *     Defaults to true
 * <tr><td> </td><td>gsa.scoringType</td><td> type of relevance algorithm
 *      GSA utilizes to rank documents.  Either content or web.  Is sent
 *      when adaptor.sendDocControlHeader is true.  Defaults to content
 * <tr><td> </td><td>server.dashboardPort </td><td> port on adaptor's
 *     machine for accessing adaptor's dashboard.   Defaults to  5679
 * <tr><td> </td><td>server.docIdPath </td><td> part of URL preceding
 *     encoded document ids.  Defaults to  /doc/
 * <tr><td> </td><td>server.fullAccessHosts </td><td> hosts allowed access
 *     without authentication
 *     (certificates still needed when in secure mode).   Defaults to
 *     empty but implicitly contains gsa.hostname
 * <tr><td> </td><td>server.hostname </td><td>
 *     hostname of adaptor machine for URL generation. 
 *     The GSA will use this hostname to crawl the adaptor.
 *     Defaults to automatically detected hostname
 * <tr><td> </td><td>server.keyAlias </td><td> keystore alias where
 *     encryption (public and private) keys are stored.
 *     Defaults to adaptor
 * <tr><td> </td><td>server.maxWorkerThreads </td><td> number of maximum
 *     simultenous retrievals  allowed.  Defaults to 16
 * <tr><td> </td><td>server.port </td><td> retriever port.  Defaults to 5678
 * <tr><td> </td><td>server.queueCapacity </td><td> max retriever queue size.
 *     Defaults to  160
 * <tr><td> </td><td>server.reverseProxyPort </td><td> port used in
 *     retriever URLs (in case requests
 *     are routed through a reverse proxy).  Defaults to server.port
 * <tr><td> </td><td>server.reverseProxyProtocol </td><td> either http or https,
 *     depending on  proxy traffic.  Defaults to https in secure
 *     mode and http otherwise
 * <tr><td> </td><td>server.samlEntityId </td><td> The SAML Entity ID that the
 *     Adaptor will use to identity itself. Defaults to
 *     http://google.com/enterprise/gsa/adaptor
 * <tr><td> </td><td>server.secure </td><td> enables https and certificate
 *     checking. Defaults to false
 * <tr><td> </td><td>server.useCompression </td><td> compress retrieval
 *     responses. Defaults to true
 * <tr><td> </td><td>transform.pipeline </td><td> sequence of
 *     transformation steps.  Defaults to no-pipeline
 * </table>
 */
public class Config {
  private static final Logger log = Logger.getLogger(Config.class.getName());

  /** Configuration keys whose default value is {@code null}. */
  private final Set<String> noDefaultConfig = new HashSet<String>();
  /** Default configuration values. */
  private final Properties defaultConfig = new Properties();
  /** Overriding configuration values loaded from command line. */
  // Reads require no additional locks, but modifications require lock on 'this'
  // to prevent lost updates.
  private volatile Properties config = new Properties(defaultConfig);
  /**
   * The actual config file in use, or {@code null} if none have been loaded.
   */
  private File configFile;
  private long configFileLastModified;
  private List<ConfigModificationListener> modificationListeners
      = new CopyOnWriteArrayList<ConfigModificationListener>();
  /**
   * Map from config key to computer that generates the value for the key. These
   * generated values are generally due to one value being formed from other
   * values by default.
   */
  private Map<String, ValueComputer> computeMap
      = new HashMap<String, ValueComputer>();

  public Config() {
    String hostname = null;
    try {
      hostname = InetAddress.getLocalHost().getCanonicalHostName();
    } catch (UnknownHostException ex) {
      // Ignore
    }
    addKey("server.hostname", hostname);
    addKey("server.port", "5678");
    addKey("server.reverseProxyPort", "GENERATE", new ValueComputer() {
          public String compute(String rawValue) {
            if ("GENERATE".equals(rawValue)) {
              return getValue("server.port");
            }
            return rawValue;
          }
        });
    addKey("server.reverseProxyProtocol", "GENERATE", new ValueComputer() {
          public String compute(String rawValue) {
            if ("GENERATE".equals(rawValue)) {
              return isServerSecure() ? "https" : "http";
            }
            return rawValue;
          }
        });
    addKey("server.dashboardPort", "5679");
    addKey("server.docIdPath", "/doc/");
    addKey("server.fullAccessHosts", "");
    addKey("server.secure", "false");
    addKey("server.keyAlias", "adaptor");
    addKey("server.maxWorkerThreads", "16");
    // A queue that takes one second to drain, assuming 16 threads and 100 ms
    // for each request.
    addKey("server.queueCapacity", "160");
    addKey("server.useCompression", "true");
    addKey("server.samlEntityId", "http://google.com/enterprise/gsa/adaptor");
    addKey("gsa.hostname", null);
    addKey("gsa.characterEncoding", "UTF-8");
    addKey("gsa.614FeedWorkaroundEnabled", "false");
    addKey("gsa.70AuthMethodWorkaroundEnabled", "false");
    addKey("gsa.samlEntityId",
        "http://google.com/enterprise/gsa/security-manager");
    addKey("gsa.scoringType", "content");
    addKey("docId.isUrl", "false");
    addKey("feed.name", "GENERATE", new ValueComputer() {
          public String compute(String rawValue) {
            if ("GENERATE".equals(rawValue)) {
              return "adaptor_" + getValue("server.hostname").replace('.', '-')
                  + "_" + getValue("server.port");
            }
            return rawValue;
          }
        });
    addKey("feed.noRecrawlBitEnabled", "false");
    addKey("feed.crawlImmediatelyBitEnabled", "false");
    //addKey("feed.noFollowBitEnabled", "false");
    addKey("feed.maxUrls", "5000");
    addKey("adaptor.pushDocIdsOnStartup", "true");
    // 3:00 AM every day.
    addKey("adaptor.fullListingSchedule", "0 3 * * *");
    // 15 minutes.
    addKey("adaptor.incrementalPollPeriodSecs", "900");
    addKey("adaptor.docContentTimeoutSecs", "180");
    addKey("adaptor.docHeaderTimeoutSecs", "30");
    addKey("transform.pipeline", "");
    addKey("journal.reducedMem", "true");
    addKey("adaptor.sendDocControlsHeader", "false");
  }

  public Set<String> getAllKeys() {
    return config.stringPropertyNames();
  }

  /* Preferences requiring you to set them: */
  /**
   * Required to be set: GSA machine to send document ids to. This is the
   * hostname of your GSA on your network.
   */
  String getGsaHostname() {
    return getValue("gsa.hostname");
  }

  /* Preferences suggested you set them: */

  String getFeedName() {
    return getValue("feed.name");
  }

  /**
   * Suggested to be set: Local port, on this computer, onto which requests from
   * GSA come in on.
   */
  int getServerPort() {
    return Integer.parseInt(getValue("server.port"));
  }

  /**
   * The port that should be used in feed file and other references to the
   * adaptor. This does not affect the actual port the adaptor uses.
   */
  int getServerReverseProxyPort() {
    return Integer.parseInt(getValue("server.reverseProxyPort"));
  }

  /**
   * The protocol that should be used in feed files and other references to the
   * adaptor. This does not affect the actual protocol the adaptor uses.
   */
  String getServerReverseProxyProtocol() {
    return getValue("server.reverseProxyProtocol");
  }

  /**
   * Local port, on this computer, from which the dashboard is served.
   */
  int getServerDashboardPort() {
    return Integer.parseInt(getValue("server.dashboardPort"));
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
  boolean isDocIdUrl() {
    return Boolean.parseBoolean(getValue("docId.isUrl"));
  }

  /** Without changes contains InetAddress.getLocalHost().getHostName(). */
  String getServerHostname() {
    return getValue("server.hostname");
  }

  /**
   * Comma-separated list of IPs or hostnames that can retrieve content without
   * authentication checks. The GSA's hostname is implicitly in this list.
   *
   * <p>When in secure mode, clients are requested to provide a client
   * certificate. If the provided client certificate is valid and the Common
   * Name (CN) of the Subject is in this list (case-insensitively), then it is
   * given access.
   *
   * <p>In non-secure mode, the hostnames in this list are resolved to IPs at
   * startup and when a request is made from one of those IPs the client is
   * given access.
   */
  String[] getServerFullAccessHosts() {
    return getValue("server.fullAccessHosts").split(",");
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
  URI getServerBaseUri() {
    return URI.create(getServerReverseProxyProtocol() + "://"
        + getServerHostname() + ":" + getServerReverseProxyPort());
  }

  /**
   * Optional: Path below {@link #getServerBaseUri(DocId)} where documents are
   * namespaced. Generally, should be at least {@code "/"} and end with a slash.
   */
  String getServerDocIdPath() {
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
  URI getServerBaseUri(DocId docId) {
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
  boolean isServerSecure() {
    return Boolean.parseBoolean(getValue("server.secure"));
  }

  /**
   * The alias in the keystore that has the key to use for encryption.
   */
  String getServerKeyAlias() {
    return getValue("server.keyAlias");
  }

  /**
   * The maximum number of worker threads to use to respond to document
   * requests. 
   */
  int getServerMaxWorkerThreads() {
    return Integer.parseInt(getValue("server.maxWorkerThreads"));
  }

  /**
   * The maximum request queue length.
   */
  int getServerQueueCapacity() {
    return Integer.parseInt(getValue("server.queueCapacity"));
  }

  String getServerSamlEntityId() {
    return getValue("server.samlEntityId");
  }

  boolean isServerToUseCompression() {
    return Boolean.parseBoolean(getValue("server.useCompression"));
  }

  boolean sendDocControlsHeader() {
    return Boolean.parseBoolean(getValue("adaptor.sendDocControlsHeader"));
  }

  /**
   * Optional (default false): Adds no-recrawl bit with sent records in feed
   * file. If connector handles updates and deletes then GSA does not have to
   * recrawl periodically to notice that a document is changed or deleted.
   */
  boolean isFeedNoRecrawlBitEnabled() {
    return Boolean.getBoolean(getValue("feed.noRecrawlBitEnabled"));
  }

  /**
   * Optional (default false): Adds crawl-immediately bit with sent records in
   * feed file.  This bit makes the sent URL get crawl priority.
   */
  boolean isCrawlImmediatelyBitEnabled() {
    return Boolean.parseBoolean(getValue("feed.crawlImmediatelyBitEnabled"));
  }

  /**
   * Whether the default {@code main()} should automatically start pushing all
   * document ids on startup. Defaults to {@code true}.
   */
  boolean isAdaptorPushDocIdsOnStartup() {
    return Boolean.parseBoolean(getValue("adaptor.pushDocIdsOnStartup"));
  }

  /**
   * Cron-style format for describing when the adaptor should perform full
   * listings of {@code DocId}s. Multiple times can be specified by separating
   * them with a '|' (vertical bar).
   */
  String getAdaptorFullListingSchedule() {
    return getValue("adaptor.fullListingSchedule");
  }

  long getAdaptorIncrementalPollPeriodMillis() {
    return Long.parseLong(getValue("adaptor.incrementalPollPeriodSecs")) * 1000;
  }

  long getAdaptorDocHeaderTimeoutMillis() {
    return Long.parseLong(getValue("adaptor.docHeaderTimeoutSecs")) * 1000;
  }

  long getAdaptorDocContentTimeoutMillis() {
    return Long.parseLong(getValue("adaptor.docContentTimeoutSecs")) * 1000;
  }

  /**
   * Returns a list of maps correspending to each transform in the pipeline.
   * Each map is the configuration entries for that transform. The 'name'
   * configuration entry is added in each map based on the name provided by the
   * user.
   */
  synchronized List<Map<String, String>> getTransformPipelineSpec() {
    final String configKey = "transform.pipeline";
    String configValue = getValue(configKey).trim();
    if ("".equals(configValue)) {
      return Collections.emptyList();
    }
    String[] items = getValue(configKey).split(",");
    List<Map<String, String>> transforms
        = new ArrayList<Map<String, String>>(items.length);
    for (String item : items) {
      item = item.trim();
      if ("".equals(item)) {
        throw new RuntimeException("Invalid format: " + configValue);
      }
      Map<String, String> params
          = getValuesWithPrefix(configKey + "." + item + ".");
      params.put("name", item);
      transforms.add(params);
    }
    return transforms;
  }

  boolean isJournalReducedMem() {
    return Boolean.parseBoolean(getValue("journal.reducedMem"));
  }

// TODO(pjo): Implement on GSA
//  /**
//   * Optional (default false): Adds no-follow bit with sent records in feed
//   * file. No-follow means that if document content has links they are not
//   * followed.
//   */
//  boolean isNoFollowBitEnabled() {
//    return Boolean.parseBoolean(getValue("feed.noFollowBitEnabled"));
//  }

  /* Preferences expected to never change: */

  /** Provides the character encoding the GSA prefers. */
  Charset getGsaCharacterEncoding() {
    return Charset.forName(getValue("gsa.characterEncoding"));
  }

  boolean isGsa614FeedWorkaroundEnabled() {
    return Boolean.parseBoolean(getValue("gsa.614FeedWorkaroundEnabled"));
  }

  boolean isGsa70AuthMethodWorkaroundEnabled() {
    return Boolean.parseBoolean(getValue("gsa.70AuthMethodWorkaroundEnabled"));
  }

  String getGsaSamlEntityId() {
    return getValue("gsa.samlEntityId");
  }

  /**
   * Provides max number of URLs (equal to number of document ids) that are sent
   * to the GSA per feed file.
   */
  int getFeedMaxUrls() {
    return Integer.parseInt(getValue("feed.maxUrls"));
  }

  /**
   * Provides the type of algorithm GSA is to use to rank documents sent by
   * adaptor.
   */
  String getScoringType() {
    return getValue("gsa.scoringType");
  }

  /**
   * Load user-provided configuration file.
   */
  public synchronized void load(String configFile) throws IOException {
    load(new File(configFile));
  }

  /**
   * Load user-provided configuration file.
   */
  public synchronized void load(File configFile) throws IOException {
    this.configFile = configFile;
    configFileLastModified = configFile.lastModified();
    Reader reader = createReader(configFile);
    try {
      load(reader);
    } finally {
      reader.close();
    }
  }

  /**
   * Load user-provided configuration file, replacing any previously loaded file
   * configuration.
   */
  private void load(Reader configFile) throws IOException {
    Properties newConfigFileProperties = new Properties(defaultConfig);
    newConfigFileProperties.load(configFile);

    Config fakeOldConfig;
    Set<String> differentKeys;
    synchronized (this) {
      // Create replacement config.
      Properties newConfig = new Properties(newConfigFileProperties);
      for (Object o : config.keySet()) {
        newConfig.put(o, config.get(o));
      }

      // Find differences.
      differentKeys = findDifferences(config, newConfig);

      if (differentKeys.isEmpty()) {
        log.info("No configuration changes found");
        return;
      }

      validate(newConfig);

      fakeOldConfig = new Config();
      fakeOldConfig.config = config;
      this.config = newConfig;
    }
    log.info("New configuration file loaded");
    fireConfigModificationEvent(fakeOldConfig, differentKeys);
  }

  Reader createReader(File configFile) throws IOException {
    return new InputStreamReader(new BufferedInputStream(
        new FileInputStream(configFile)), Charset.forName("UTF-8"));
  }

  /**
   * @return {@code true} if configuration file was modified.
   */
  public boolean ensureLatestConfigLoaded() throws IOException {
    synchronized (this) {
      if (configFile == null || !configFile.exists() || !configFile.isFile()) {
        return false;
      }
      // Check for modifications.
      long newLastModified = configFile.lastModified();
      if (configFileLastModified == newLastModified || newLastModified == 0) {
        return false;
      }
      log.info("Noticed modified configuration file");

      load(configFile);
    }
    return true;
  }

  private Set<String> findDifferences(Properties config, Properties newConfig) {
    Set<String> differentKeys = new HashSet<String>();
    Set<String> names = new HashSet<String>();
    names.addAll(config.stringPropertyNames());
    names.addAll(newConfig.stringPropertyNames());
    for (String name : names) {
      String value = config.getProperty(name);
      String newValue = newConfig.getProperty(name);
      boolean equal = (value == null && newValue == null)
          || (value != null && value.equals(newValue));
      if (!equal) {
        differentKeys.add(name);
      }
    }
    return differentKeys;
  }

  public void validate() {
    validate(config);
  }

  private void validate(Properties config) {
    Set<String> unset = new HashSet<String>();
    for (String key : noDefaultConfig) {
      if (config.getProperty(key) == null) {
        unset.add(key);
      }
    }
    if (unset.size() != 0) {
      throw new IllegalStateException("Missing configuration values: " + unset);
    }
  }

  /**
   * Get a configuration value exactly as provided in configuration. Generally,
   * {@link #getValue} should be used instead of this method.
   *
   * @return raw non-{@code null} value of {@code key}
   * @throws IllegalStateException if {@code key} has no value
   */
  public String getRawValue(String key) {
    String value = config.getProperty(key);
    if (value == null) {
      throw new IllegalStateException(MessageFormat.format(
          "You must set configuration key ''{0}''.", key));
    }
    return value;
  }

  /**
   * Get a configuration value as computed based on the configuration. Some
   * configuration values can be generated from other values. This method
   * returns that computed configuration value instead of the raw value provided
   * in configuration. This method should be preferred over {@link
   * #getRawValue}.
   *
   * @return non-{@code null} value of {@code key}
   * @throws IllegalStateException if {@code key} has no value
   */
  public String getValue(String key) {
    String value = getRawValue(key);
    ValueComputer computer = computeMap.get(key);
    if (computer != null) {
      value = computer.compute(value);
    }
    return value;
  }

  /**
   * Gets all configuration values that begin with {@code prefix}, returning
   * them as a map with the keys having {@code prefix} removed.
   */
  public synchronized Map<String, String> getValuesWithPrefix(String prefix) {
    Map<String, String> values = new HashMap<String, String>();
    for (String key : config.stringPropertyNames()) {
      if (!key.startsWith(prefix)) {
        continue;
      }
      values.put(key.substring(prefix.length()), config.getProperty(key));
    }
    return values;
  }

  /**
   * Add configuration key. If {@code defaultValue} is {@code null}, then no
   * default value is used and the user must provide one.
   */
  public synchronized void addKey(String key, String defaultValue) {
    if (defaultConfig.containsKey(key) || noDefaultConfig.contains(key)) {
      throw new IllegalStateException("Key already added: " + key);
    }
    if (defaultValue == null) {
      noDefaultConfig.add(key);
    } else {
      defaultConfig.setProperty(key, defaultValue);
    }
  }

  synchronized void addKey(String key, String defaultValue,
      ValueComputer computer) {
    addKey(key, defaultValue);
    computeMap.put(key, computer);
  }

  /**
   * Change the default value of a preexisting configuration key. If {@code
   * defaultValue} is {@code null}, then no default is used and the user must
   * provide one.
   */
  public synchronized void overrideKey(String key, String defaultValue) {
    if (!defaultConfig.containsKey(key) && !noDefaultConfig.contains(key)) {
      log.log(Level.WARNING, "Overriding unknown configuration key: {0}", key);
    }
    defaultConfig.remove(key);
    noDefaultConfig.remove(key);
    if (defaultValue == null) {
      noDefaultConfig.add(key);
    } else {
      defaultConfig.setProperty(key, defaultValue);
    }
  }

  /**
   * Manually set a configuration value. Depending on when called, it can
   * override a user's configuration, which should be avoided.
   */
  synchronized void setValue(String key, String value) {
    config.setProperty(key, value);
  }

  void addConfigModificationListener(
      ConfigModificationListener listener) {
    modificationListeners.add(listener);
  }

  void removeConfigModificationListener(
      ConfigModificationListener listener) {
    modificationListeners.remove(listener);
  }

  private void fireConfigModificationEvent(Config oldConfig,
                                           Set<String> modifiedKeys) {
    ConfigModificationEvent ev
        = new ConfigModificationEvent(this, oldConfig, modifiedKeys);
    for (ConfigModificationListener listener : modificationListeners) {
      try {
        listener.configModified(ev);
      } catch (Exception ex) {
        log.log(Level.WARNING,
                "Unexpected exception. Consider filing a bug.", ex);
      }
    }
  }

  interface ValueComputer {
    /**
     * Computes the effective value of a configuration value provided the
     * literal value provided in configuration.
     */
    public String compute(String rawValue);
  }
}
