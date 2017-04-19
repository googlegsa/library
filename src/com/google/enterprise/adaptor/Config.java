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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Configuration values for this program, like the GSA's hostname. Also several
 * knobs, or controls, for changing the behavior of the program.
 * <p>All available configuration:<br>
 * <table>
 * <caption> All available configuration </caption>
 * <tr><td align=center><b>required?</b></td>
 *     <td><b>name</b></td><td><b>meaning</b></td>
 * <tr><td> </td><td>gsa.acceptsDocControlsHeader </td><td>use 
 *      X-Gsa-Doc-Controls HTTP header with namespaced ACLs.
 *      Otherwise ACLs are sent without namespace and as metadata.
 *      If not set, then an attempt to compute from gsa.version is made.
 *      Defaults to true for 7.2.0-0 and later, and false for earlier,
 *      as defined by gsa.version.
 * <tr><td> </td><td>adaptor.markAllDocsAsPublic </td><td> Tells GSA all
        documents are public.  Overrides all ACLs and even the setting of
        {@code Response.setSecure()}.  Defaults to false
 * <tr><td> </td><td>adaptor.fullListingSchedule </td><td> when to invoke 
 *     {@link Adaptor#getDocIds Adaptor.getDocIds}, in cron format (minute,
 *     hour,  day of month, month, day of week).  Defaults to 0 3 * * *
 * <tr><td> </td><td>adaptor.incrementalPollPeriodSecs </td><td> number
 *     of seconds between invocations of {@link
 *     PollingIncrementalLister#getModifiedDocIds
 *     PollingIncrementalLister.getModifiedDocIds}.    Defaults to 900
 * <tr><td> </td><td>adaptor.docContentTimeoutSecs </td><td> number of seconds
 *     adaptor has to complete sending content before it is interrupted. Timing
 *     starts when sending content starts. Defaults to 180
 * <tr><td> </td><td>adaptor.docHeaderTimeoutSecs </td><td> number of seconds
 *     adaptor has to start sending content before it is interrupted.
 *     Defaults to 30
 * <tr><td> </td><td>adaptor.heartbeatTimeoutSecs </td><td> number of seconds
 *     adaptor has to send a heartbeat response before it is interrupted.
 *     Defaults to same value as adaptor.docHeaderTimeoutSecs.
 * <tr><td> </td><td>adaptor.disableFullAndIncrementalListing </td><td>
 *      whether to disable calls to {@link Adaptor#getDocIds Adaptor.getDocIds}
 *      and {@link PollingIncrementalLister#getModifiedDocIds
 *      PollingIncrementalLister.getModifiedDocIds}.
 *      If {@code true}, the values of the {@code adaptor.pushDocIdsOnStartup},
 *      {@code adaptor.fullListingSchedule}, and
 *      {@code adaptor.incrementalPollPeriodSecs} properties are ignored.
 *      The {@link AsyncDocIdPusher AsyncDocIdPusher} is still active, but
 *      adaptors should restrict its use to {@link Adaptor#getDocContent
 *      Adaptor.getDocContent}.  Defaults to {@code false}.
 * <tr><td> </td><td>adaptor.pushDocIdsOnStartup </td><td> whether to invoke
 *     {@link Adaptor#getDocIds Adaptor.getDocIds} on process start
 *     (in addition to adaptor.fullListingSchedule).   Defaults to true
 * <tr><td> </td><td>adaptor.domainFormat </td><td> whether ACL names
 *     are DNS (name@domain.com), NETBIOS (domain\name),
 *     or NETBIOS_FORWARDSLASH (domain/name), or NONE (name).
 *     This format is used when adaptor needs to combine name with
 *     domain to create AuthnIdentity instances. The adaptor needs
 *     to create AuthnIdentity instances in two circumstances: (1)
 *     when it is using the GSA as an Idp and is parsing GSA authn
 *     SAML messages, and (2) when it is receiving and parsing GSA
 *     authorization requests. Defaults to DNS
 * <tr><td> </td><td>docId.isUrl </td><td> say your adaptor's document ids
 *     are already URLs and avoid them being inserted into adaptor
       generated URLs.   Defaults to false
 * <tr><td> </td><td>feed.crawlImmediatelyBitEnabled </td><td> send bit telling
 *     GSA to crawl immediately.
 *     Defaults to not overriding adaptor's decision which is typically to send
 *     updates as crawl-immediately and let GSA schedule crawl of all other ids
 * <tr><td> </td><td>feed.noRecrawlBitEnabled </td><td> send bit telling
 *     GSA to crawl your documents only once. 
 *     Defaults to not overriding adaptor's decision which is typically to send
 *     all documents as recrawlable (equivalent to value of false)
 * <tr><td> </td><td>feed.maxUrls </td><td> set max number of URLs included
 *     per feed file.    Defaults to 5000
 * <tr><td> </td><td>feed.name </td><td> source name used in feeds. Generated
 *     if not provided
 * <tr><td> </td><td>feed.archiveDirectory </td><td> specifies a directory in
 *     which all feeds sent to the GSA will be archived.  Feeds that failed to
 *     be sent to the GSA will be tagged with "FAILED" in the file name.
 *     If no directory is specified, feed files will not be saved.
 * <tr><td> </td><td>gsa.version </td><td> version number used to configure
 *     expected GSA features.  Defaults to acquiring from GSA.
 *     Uses 7.0.14-114 if acquiring fails.
 * <tr><td> </td><td>gsa.614FeedWorkaroundEnabled </td><td> enable detour
 *     around particular feed parsing failure found in GSA version 6.14 .
 *     Defaults to false
 * <tr><td> </td><td>gsa.70AuthMethodWorkaroundEnabled </td><td> send authmethod
 *     in feed files to workaround early GSA 7.0 bug. Defaults to false
 * <tr><td> </td><td>gsa.characterEncoding </td><td> character set used
 *     in feed files. Defaults to  UTF-8
 * <tr><td align="center"> yes </td><td>gsa.hostname </td><td> machine to
 *     send feed files to.  Process errors if not provided 
 * <tr><td> </td><td>gsa.admin.hostname </td><td> administrative host for
 *     the GSA. This may be different from gsa.hostname if the GSA's dedicated
 *     administrative network interface is enabled. Defaults to the same
 *     value as gsa.hostname.
 * <tr><td> </td><td>gsa.samlEntityId </td><td> The SAML Entity ID that
 *     identifies the GSA. Defaults to
 *     http://google.com/enterprise/gsa/security-manager
 * <tr><td> </td><td>journal.reducedMem </td><td> avoid tracking per URL 
 *     information in RAM; suggested with over five hundred thousand documents.
 *     Defaults to true
 * <tr><td> </td><td>gsa.scoringType</td><td> type of relevance algorithm
 *      GSA utilizes to rank documents.  Either content or web.  Is sent
 *      when gsa.acceptsDocControlsHeader is true.  Defaults to content
 * <tr><td> </td><td>server.asyncDocIdSenderQueueSize </td><td> queue size of
 *     the asynchronous DocId sender.  Defaults to 2 * feed.maxUrls
 * <tr><td> </td><td>server.dashboardPort </td><td> port on adaptor's
 *     machine for accessing adaptor's dashboard.   Defaults to  5679
 * <tr><td> </td><td>server.docIdPath </td><td> part of URL preceding
 *     encoded document ids.  Defaults to  /doc/
 * <tr><td> </td><td>server.heartbeatPath </td><td> part of URL preceding
 *     encoded heartbeat URIs for document ids.  Defaults to  /heartbeat/
 * <tr><td> </td><td>server.fullAccessHosts </td><td> hosts allowed access
 *     without authentication
 *     (certificates still needed when in secure mode).   Defaults to
 *     empty but implicitly contains gsa.hostname
 * <tr><td> </td><td>server.hostname </td><td>
 *     hostname of adaptor machine for URL generation. 
 *     The GSA will use this hostname to crawl the adaptor.
 *     Defaults to lowercase of automatically detected hostname
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
 * <tr><td> </td><td>server.httpBasic.username </td><td> enables http basic
 *     authentication on all content requests. When enabled, requests receive
 *     HTTP 401 if correct username and password are not provided.  Once 
 *     http basic authentication is satified requestor receives content and
 *     ACLs and metadata.  Defaults to empty string "" and means that http
 *     basic is not used.
 * <tr><td> </td><td>server.httpBasic.password </td><td> if http basic is
 *     enabled then this is the password that the requester must provide.
 *     Defaults to empty string "".
 * <tr><td> </td><td>server.useCompression </td><td> compress retrieval
 *     responses. Defaults to false
 * <tr><td> </td><td>transform.acl.X </td><td> where X is an integer, match
 *     and modify principals as described. Defaults no modifications
 * <tr><td> </td><td>metadata.transform.pipeline </td><td> sequence of
 *     metadata transformation steps.  Defaults to no-pipeline
 * <tr><td> </td><td>content.transform.pipeline </td><td>
 *     content transform pipeline. Defaults to no-pipeline</td></tr>
 * <tr><td> </td><td>saml.idpExpirationMillis </td><td> Expiration time
 *     sent in SAML Authentication response. Defaults to 30,000 milliseconds.
 * </table>
 */
public class Config {
  private static final Logger log = Logger.getLogger(Config.class.getName());

  private static final Pattern DATASOURCE_FORMAT
      = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_-]*");

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
      hostname = hostname.toLowerCase(Locale.ENGLISH);  // work around GSA 7.0
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
    addKey("server.skipCertCheckHosts", "");
    addKey("server.heartbeatPath", "/heartbeat/");
    addKey("server.secure", "false");
    addKey("server.httpBasic.username", "");
    addKey("server.httpBasic.password", "");
    addKey("server.keyAlias", "adaptor");
    addKey("server.maxWorkerThreads", "16");
    // A queue that takes one second to drain, assuming 16 threads and 100 ms
    // for each request.
    addKey("server.queueCapacity", "160");
    addKey("server.useCompression", "false");
    addKey("server.asyncDocIdSenderQueueSize", "GENERATE",
        new ValueComputer() {
          public String compute(String rawValue) {
            if ("GENERATE".equals(rawValue)) {
              int feedMaxUrls = Integer.parseInt(getValue("feed.maxUrls"));
              return String.valueOf(2 * feedMaxUrls);
            }
            return rawValue;
          }
        });
    addKey("server.samlEntityId", "http://google.com/enterprise/gsa/adaptor");
    addKey("gsa.hostname", null);
    addKey("gsa.admin.hostname", "");
    addKey("gsa.characterEncoding", "UTF-8");
    addKey("gsa.version", "GENERATE");
    addKey("gsa.614FeedWorkaroundEnabled", "false");
    addKey("gsa.70AuthMethodWorkaroundEnabled", "false");
    addKey("gsa.samlEntityId",
        "http://google.com/enterprise/gsa/security-manager");
    addKey("gsa.scoringType", "content");
    addKey("docId.isUrl", "false");
    addKey("feed.archiveDirectory", "");
    addKey("feed.name", "GENERATE", new ValueComputer() {
          public String compute(String rawValue) {
            if ("GENERATE".equals(rawValue)) {
              return "adaptor_" + getValue("server.hostname").replace('.', '-')
                  + "_" + getValue("server.port");
            }
            return rawValue;
          }
        });
    addKey("feed.noRecrawlBitEnabled", "");
    addKey("feed.crawlImmediatelyBitEnabled", "");
    //addKey("feed.noFollowBitEnabled", "false");
    addKey("feed.maxUrls", "5000");
    addKey("adaptor.disableFullAndIncrementalListing", "false");
    addKey("adaptor.pushDocIdsOnStartup", "true");
    addKey("adaptor.domainFormat", "DNS");
    // 3:00 AM every day.
    addKey("adaptor.fullListingSchedule", "0 3 * * *");
    // 15 minutes.
    addKey("adaptor.incrementalPollPeriodSecs", "900");
    addKey("adaptor.docContentTimeoutSecs", "180");
    addKey("adaptor.docHeaderTimeoutSecs", "30");
    addKey("adaptor.heartbeatTimeoutSecs", "");
    addKey("metadata.transform.pipeline", "");
    addKey("content.transform.pipeline", "");
    addKey("journal.reducedMem", "true");
    addKey("gsa.acceptsDocControlsHeader", "GENERATE", new ValueComputer() {
          public String compute(String rawValue) {
            if (!"GENERATE".equals(rawValue)) {
              log.log(Level.FINE,
                  "returning raw gsa.acceptsDocControlsHeader: {0}", rawValue);
              return rawValue;
            }
            String ver = getValue("gsa.version");
            if ("GENERATE".equals(ver)) {
              throw new IllegalStateException("gsa.version not yet available");
            } else {
              boolean computed = new GsaVersion(ver).isAtLeast("7.2.0-0");
              log.log(Level.FINE,
                  "gsa.acceptsDocControlsHeader computed {0}", computed);
              return "" + computed;
            }
          }
        });
    addKey("adaptor.markAllDocsAsPublic", "false");
    addKey("saml.idpExpirationMillis", "30000");
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

  /**
   * If your GSA has a dedicated administrative network interface configured,
   * this is hostname for that GSA's admin NIC.  If not set, defaults to the
   * same as gsa.hostname.
   */
  String getGsaAdminHostname() {
    String hostname = getValue("gsa.admin.hostname").trim();
    return (hostname.length() > 0) ? hostname : getGsaHostname();
  }

  String getFeedArchiveDirectory() {
    return getValue("feed.archiveDirectory");
  }

  String getFeedName() {
    return getValue("feed.name");
  }

  int getAsyncDocIdSenderQueueSize() {
    return Integer.parseInt(getValue("server.asyncDocIdSenderQueueSize"));
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

  /** Default is lowercase of InetAddress.getLocalHost().getHostName(). */
  String getServerHostname() {
    String hostname = getValue("server.hostname");
    log.log(Level.FINER, "server hostname: {0}", hostname);
    return hostname;
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
   * DANGER, use for debugging only.
   * Comma-separated list of IPs or hostnames that can skip
   * certificate checks. Used only for server.secure=true mode
   *
   * <p>When in secure mode, clients are requested to provide either (a trusted
   * client certificate, OR to be in server.skipCertCheckHosts list) AND (
   * 1. be in server.fullAccessHosts OR
   * 2. requested document is public OR
   * 3. User agent is authenticated and authorized for document)
   *
   * Only for these conditions, web browser can get document content from 
   * connector
   *
   * <p>In non-secure mode, this option is not used at all.
   */
  String[] getServerSkipCertCheckHosts() {
    return getValue("server.skipCertCheckHosts").split(",");
  }

  /**
   * Optional: Returns this host's base URI which other paths will be resolved
   * against. It is used to construct URIs to provide to the GSA for it to
   * contact this server for various services.
   *
   * <p>It contains the protocol, hostname, and port.
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
   * Optional: Path below {@link #getServerBaseUri(DocId)} where heartbeat
   * references to documents are namespaced. Generally, should be at least
   * {@code "/"} and end with a slash.
   */
  String getServerHeartbeatPath() {
    return getValue("server.heartbeatPath");
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

  String getHttpBasicUsername() {
    return getValue("server.httpBasic.username");
  }

  String getHttpBasicPassword() {
    return getValue("server.httpBasic.password");
  }

  boolean requireHttpBasicAuthn() {
    return !("".equals(getHttpBasicUsername().trim()));
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

  boolean doesGsaAcceptDocControlsHeader() {
    return Boolean.parseBoolean(getValue("gsa.acceptsDocControlsHeader"));
  }

  static class OverridableBoolean {
    final boolean isOverriden; // whether value is to be overriden
    final boolean value; // the overriding value
    private OverridableBoolean(boolean override) {
      isOverriden = true;
      value = override;
    }
    private OverridableBoolean() {
      isOverriden = false;
      value = false; // whatever
    }
  }

  /**
   * Optional: Adds crawl-immediately bit with sent records in
   * feed file.  This bit makes the sent URL get crawl priority.
   */
  OverridableBoolean isCrawlImmediatelyBitEnabled() {
    String provided = getValue("feed.crawlImmediatelyBitEnabled");
    if ("".equals(provided.trim())) {
      return new OverridableBoolean();
    }
    return new OverridableBoolean(Boolean.parseBoolean(provided));
  }

  /**
   * Optional: Adds no-recrawl bit with sent records in feed
   * file. If connector handles updates and deletes then GSA
   * does not have to recrawl periodically to notice that a
   * document is changed or deleted.
   */
  OverridableBoolean isFeedNoRecrawlBitEnabled() {
    String provided = getValue("feed.noRecrawlBitEnabled");
    if ("".equals(provided.trim())) {
      return new OverridableBoolean();
    }
    return new OverridableBoolean(Boolean.parseBoolean(provided));
  }

  /**
   * Whether the default {@code main()} should disable pushing document ids
   * synchronously. If {@code true}, {@link Adaptor#getDocIds Adaptor.getDocIds}
   * and {@link Adaptor#getModifiedDocIds Adaptor.getModifiedDocIds} are not
   * called; the values of the {@code adaptor.pushDocIdsOnStartup},
   * {@code adaptor.fullListingSchedule}, and
   * {@code adaptor.incrementalPollPeriodSecs} properties are ignored.
   * The {@link AsyncDocIdPusher AsyncDocIdPusher} is still active, but
   * adaptors should restrict its use to {@link Adaptor.getDocContent}.
   * Defaults to {@code false}.
   */
  boolean disableFullAndIncrementalListing() {
    return Boolean.parseBoolean(
        getValue("adaptor.disableFullAndIncrementalListing"));
  }

  /**
   * Whether the default {@code main()} should automatically start pushing all
   * document ids on startup. Defaults to {@code true}.
   */
  boolean isAdaptorPushDocIdsOnStartup() {
    return Boolean.parseBoolean(getValue("adaptor.pushDocIdsOnStartup"));
  }

  /**
   * Whether adaptor tells GSA all documents are public, regardless of their
   * ACLs. Defaults to {@code false}.
   */
  boolean markAllDocsAsPublic() {
    return Boolean.parseBoolean(getValue("adaptor.markAllDocsAsPublic"));
  }

  /**
   * Format for usernames and domains inside of either GSA IdP authn messages
   * or GSA authorization requests.
   */
  Principal.DomainFormat getDomainFormat() {
    String dmfmtstr = getValue("adaptor.domainFormat");
    Principal.DomainFormat dmfmt = Principal.DomainFormat.valueOf(dmfmtstr);
    return dmfmt;
  }

  /**
   * Cron-style format for describing when the adaptor should perform full
   * listings of {@code DocId}s. Multiple times can be specified by separating
   * them with a '|' (vertical bar).
   */
  String getAdaptorFullListingSchedule() {
    return getValue("adaptor.fullListingSchedule");
  }

  public long validateTimeout(String property) {
    String secondsAsString = getValue(property).trim();
    if ("0".equals(secondsAsString) || "".equals(secondsAsString) ||
        secondsAsString.startsWith("-") ) {
      throw new InvalidConfigurationException("Invalid value for " + property
          + ". Zero, empty and negative values are not accepted.");
    } else {
      try {
        return Long.parseLong(secondsAsString) * 1000;
      } catch (NumberFormatException nfe) {
        throw new InvalidConfigurationException("Invalid value for "
            + property + ". Only a numeric value is accepted.");
      }
    }
  }

  long getAdaptorIncrementalPollPeriodMillis() {
    return validateTimeout("adaptor.incrementalPollPeriodSecs");
  }

  long getAdaptorDocHeaderTimeoutMillis() {
    return validateTimeout("adaptor.docHeaderTimeoutSecs");
  }

  long getAdaptorDocContentTimeoutMillis() {
    return validateTimeout("adaptor.docContentTimeoutSecs");
  }

  long getAdaptorHeartbeatTimeoutMillis() {
    if (getValue("adaptor.heartbeatTimeoutSecs").trim().length() == 0) {
      // if heartbeatTimeoutSecs is empty, default value is docHeaderTimeoutSecs
      return getAdaptorDocHeaderTimeoutMillis();
    } else {
      return validateTimeout("adaptor.heartbeatTimeoutSecs");
    }
  }

  /**
   * Returns a list of maps correspending to each transform in the pipeline.
   * Each map is the configuration entries for that transform. The 'name'
   * configuration entry is added in each map based on the name provided by the
   * user.
   */
  List<Map<String, String>> getMetadataTransformPipelineSpec() {
    String key = "metadata.transform.pipeline";
    if ("".equals(getValue(key))) {
      key = "transform.pipeline"; // deprecated key for metadata transform
      if (null == config.getProperty(key)) {
        return Collections.emptyList();
      }
    }
    return getListOfConfigs(key);
  }

  /**
   * Returns a map configuration of the content transform pipeline.
   */
  List<Map<String, String>> getContentTransformFactorySpec() {
    return getListOfConfigs("content.transform.pipeline");
  }

  /**
   * Returns a list of maps corresponding to each item of the comma-separated
   * value of {@code key}. Each map is the configuration entries for that item
   * in the list. The 'name' configuration entry is added in each map based on
   * the name provided by the user.
   *
   * <p>As an example, provided the following config:
   * <pre><code>adaptor.servers=server1,fluttershy , rainbowDash
   *adaptor.servers.fluttershy.hostname=fluttershy
   *adaptor.servers.fluttershy.port=42
   *adaptor.servers.server1.hostname=applejack
   *adaptor.servers.server1.port=314
   *adaptor.servers.rainbowDash.hostname=rainbowdash
   *adaptor.servers.rainbowDash.port=159
   *adaptor.servers.rainbowDash.name=20% cooler
   *adaptor.servers.derpy.hostname=hooves</code></pre>
   *
   * <p>And calling:
   * <pre><code>config.getConfigList("adaptor.servers");</code></pre>
   *
   * <p>Returns:
   * <pre><code>[{
   *  "hostname": "applejack",
   *  "port": "42",
   *  "name": "server1",
   *}, {
   *  "hostname": "fluttershy",
   *  "port": "314",
   *  "name": "fluttershy",
   *}, {
   *  "hostname": "rainbowdash",
   *  "port": "159",
   *  "name": "raindowDash",
   *}]</code></pre>
   *
   * @param key whoese value specifies other keys
   * @return List of maps that are each these sub-configurations
   */
  public synchronized List<Map<String, String>> getListOfConfigs(String key) {
    String configValue = getValue(key).trim();
    if ("".equals(configValue)) {
      return Collections.emptyList();
    }
    String[] items = getValue(key).split(",");
    List<Map<String, String>> listOfMaps
        = new ArrayList<Map<String, String>>(items.length);
    for (String item : items) {
      item = item.trim();
      if ("".equals(item)) {
        throw new RuntimeException("Invalid format: " + configValue);
      }
      Map<String, String> params
          = getValuesWithPrefix(key + "." + item + ".");
      params.put("name", item);
      listOfMaps.add(params);
    }
    return listOfMaps;
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

  String getGsaVersion() {
    return getValue("gsa.version");
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

  int getSamlIdpExpirationMillis() {
    return Integer.parseInt(getValue("saml.idpExpirationMillis"));
  }

  /**
   * Load user-provided configuration file.
   * @param configFile to be loaded
   * @throws IOException if fails reading config
   */
  public synchronized void load(String configFile) throws IOException {
    load(new File(configFile));
  }

  /**
   * Load user-provided configuration file.
   * @param configFile to be loaded
   * @throws IOException if fails reading config
   */
  public synchronized void load(File configFile) throws IOException {
    this.configFile = configFile;
    configFileLastModified = configFile.lastModified();
    Reader reader = createReader(configFile);
    reader = rtrim(reader);
    try {
      load(reader);
    } finally {
      reader.close();
    }
  }

  /**
   * Load user-provided configuration file
   */
  private void load(Reader configFile) throws IOException {
    Properties newConfigFileProperties = new Properties(defaultConfig);
    newConfigFileProperties.load(configFile);

    // Create replacement config.
    Properties newConfig = new Properties(newConfigFileProperties);
    for (Object o : config.keySet()) {
      newConfig.put(o, config.get(o));
    }

    validate(newConfig);
    this.config = newConfig;
    log.info("New configuration file loaded");
  }

  Reader createReader(File configFile) throws IOException {
    return new InputStreamReader(
        new BufferedInputStream(new FileInputStream(configFile)),
        Charset.forName("UTF-8"));
  }

  // take un-escaped trailing whitespace off lines
  private Reader rtrim(Reader wrapped) throws IOException {
    BufferedReader buffed = new BufferedReader(wrapped);
    StringBuilder content = new StringBuilder();
    String line;
    while ((line = buffed.readLine()) != null) {
      content.append(rtrim(line));
      content.append('\n');
    }
    return new StringReader(content.toString());
  }

  // take un-escaped trailing whitespace off value
  private static String rtrim(String value) {
    int numCharsToTakeOff = 0;
    int end = value.length() - 1;
    while (end >= 0) {
      char cur = value.charAt(end);
      if (cur != ' ' && cur != '\t' && cur != '\f') {
        break; // cur is not whitespace; is significant; so keep it
      }
      // cur is white space; is it escaped?
      int numConsecutiveSlashes = 0; // escaped means preceded by slash
      int slashIndex = end - 1;
      while (slashIndex >= 0 && '\\' == value.charAt(slashIndex)) {
        numConsecutiveSlashes++;
        slashIndex--;
      }
      boolean isEscaped = (numConsecutiveSlashes % 2) == 1;
      if (isEscaped) {
        break; // white space is escaped; so signifcant; so keep it
      }
      // cur is an un-escaped white space; delete it
      numCharsToTakeOff++;
      end--;
    }
    if (0 != numCharsToTakeOff) {
      value = value.substring(0, value.length() - numCharsToTakeOff);
    }
    return value;
  }

  public void validate() {
    validate(config);
    if ("".equals(getGsaHostname().trim())) {
      throw new InvalidConfigurationException("gsa.hostname cannot be empty");
    }
  }

  private void validate(Properties config) {
    String explicitGsaVersion = config.getProperty("gsa.version");
    if (!"GENERATE".equals(explicitGsaVersion)) {
      log.config("explicit gsa.version=" + explicitGsaVersion);
      try {
        new GsaVersion(explicitGsaVersion);
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException(e.getMessage());
      }
    }
    
    String scoreType = getValue("gsa.scoringType");
    if (!"web".equals(scoreType) && !"content".equals(scoreType)) {
      throw new InvalidConfigurationException(
          "gsa.scoringType needs to be either web or content");
    }

    Set<String> unset = new HashSet<String>();
    for (String key : noDefaultConfig) {
      if (config.getProperty(key) == null) {
        unset.add(key);
      }
    }
    if (unset.size() != 0) {
      throw new InvalidConfigurationException(
          "Missing configuration values: " + unset);
    }

    String feedData = getValue("feed.name");
    if (feedData != null && !DATASOURCE_FORMAT.matcher(feedData).matches()) {
      throw new InvalidConfigurationException("feed.name contains illegal "
          + "characters: " + feedData + " . The first character of the "
          + "feed.name must be from this set: a-zA-Z_. The second and "
          + "remaining characters must be from this set: a-zA-Z0-9_-. "
          + "Subsequent characters can contain only alphanumeric "
          + "characters, underscores, and hyphens");
    }

    //TODO: extend validation for other configuration parameters

  }

  /**
   * Get a configuration value exactly as provided in configuration. Generally,
   * {@link #getValue} should be used instead of this method.
   *
   * @param key to lookup
   * @return raw non-{@code null} value of {@code key}
   * @throws IllegalStateException if {@code key} has no value
   */
  public String getRawValue(String key) {
    String value = config.getProperty(key);
    if (value == null) {
      throw new InvalidConfigurationException(MessageFormat.format(
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
   * @param key to lookup
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
   * Returns the current config file
   */
  File getConfigFile() {
    return configFile;
  }

  /**
   * Gets all configuration values that begin with {@code prefix}, returning
   * them as a map with the keys having {@code prefix} removed.
   * @param prefix that is to begin each key
   * @return map of all endings of prefix split into key value pairs
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
   * @param key key
   * @param defaultValue value
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
   * @param key key
   * @param defaultValue value
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

  interface ValueComputer {
    /**
     * Computes the effective value of a configuration value provided the
     * literal value provided in configuration.
     */
    public String compute(String rawValue);
  }
}
