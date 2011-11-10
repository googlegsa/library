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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import it.sauronsoftware.cron4j.InvalidPatternException;
import it.sauronsoftware.cron4j.Scheduler;

import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/** This class handles the communications with GSA. */
public class GsaCommunicationHandler implements DocIdEncoder, DocIdDecoder {
  private static final Logger log
      = Logger.getLogger(GsaCommunicationHandler.class.getName());

  private final Adaptor adaptor;
  private final Config config;
  private final GsaFeedFileSender fileSender;
  private final GsaFeedFileMaker fileMaker;
  private final Journal journal = new Journal();
  private final DocIdPusher pusher = new InnerDocIdPusher();
  /**
   * Generic scheduler. Available for other uses, but necessary for running
   * {@link docIdSender}
   */
  private Scheduler scheduler = new Scheduler();
  /**
   * Runnable to be called for doing a full push of {@code DocId}s. It only
   * permits one invocation at a time. If multiple simultaneous invocations
   * occur, all but the first will log a warning and return immediately.
   */
  private OneAtATimeRunnable docIdSender = new OneAtATimeRunnable(
      new PushRunnable(), new AlreadyRunningRunnable());
  /**
   * Schedule identifier for {@link #sendDocIds}.
   */
  private String sendDocIdsSchedId;
  private HttpServer server;
  private HttpServer dashboardServer;
  private CircularLogRpcMethod circularLogRpcMethod;
  private Thread shutdownHook;
  private Timer configWatcherTimer = new Timer("configWatcher", true);
  private IncrementalAdaptorPoller incrementalAdaptorPoller;
  private StatusMonitor monitor = new StatusMonitor();

  public GsaCommunicationHandler(Adaptor adaptor, Config config) {
    this.adaptor = adaptor;
    this.config = config;
    this.fileSender = new GsaFeedFileSender(config.getGsaCharacterEncoding(),
                                            config.isServerSecure());
    this.fileMaker = new GsaFeedFileMaker(this);

    monitor.addSource(new LastPushStatusSource(journal));
    monitor.addSource(new RetrieverStatusSource(journal));
    monitor.addSource(new GsaCrawlingStatusSource(journal));
  }

  /**
   * Overrides the default {@link GetDocIdsErrorHandler}.
   */
  public void setGetDocIdsErrorHandler(GetDocIdsErrorHandler handler) {
    ((PushRunnable) docIdSender.getRunnable()).setGetDocIdsErrorHandler(handler);
  }

  /** Starts listening for communications from GSA. */
  public synchronized void start() throws Exception {
    if (server != null) {
      throw new IllegalStateException("Already listening");
    }

    int port = config.getServerPort();
    boolean secure = config.isServerSecure();
    InetSocketAddress addr = new InetSocketAddress(port);
    if (!secure) {
      server = HttpServer.create(addr, 0);
    } else {
      server = HttpsServer.create(addr, 0);
      try {
        HttpsConfigurator httpsConf
            = new HttpsConfigurator(SSLContext.getDefault()) {
              public void configure(HttpsParameters params) {
                SSLParameters sslParams
                    = getSSLContext().getDefaultSSLParameters();
                // Allow verifying the GSA and other trusted computers.
                sslParams.setWantClientAuth(true);
                params.setSSLParameters(sslParams);
              }
            };
        ((HttpsServer) server).setHttpsConfigurator(httpsConf);
      } catch (java.security.NoSuchAlgorithmException ex) {
        throw new RuntimeException(ex);
      }
    }
    // If the port is zero, then the OS chose a port for us. This is mainly
    // useful during testing.
    port = server.getAddress().getPort();
    config.setValue("server.port", "" + port);
    Executor executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);

    SessionManager<HttpExchange> sessionManager
        = new SessionManager<HttpExchange>(
          new SessionManager.HttpExchangeClientStore("sessid_" + port, secure),
          30 * 60 * 1000 /* session lifetime: 30 minutes */,
          5 * 60 * 1000 /* max cleanup frequency: 5 minutes */);
    AuthnHandler authnHandler = null;
    if (secure) {
      bootstrapOpenSaml();
      SamlMetadata metadata = new SamlMetadata(config.getServerHostname(),
          config.getServerPort(), config.getGsaHostname());

      server.createContext("/samlassertionconsumer",
          new SamlAssertionConsumerHandler(config.getServerHostname(),
            config.getGsaCharacterEncoding(), sessionManager));
      authnHandler = new AuthnHandler(config.getServerHostname(),
          config.getGsaCharacterEncoding(), sessionManager,
          config.getServerKeyAlias(), metadata);
      server.createContext("/saml-authz", new SamlBatchAuthzHandler(
          config.getServerHostname(), config.getGsaCharacterEncoding(),
          adaptor, this, metadata));
    }
    server.createContext(config.getServerBaseUri().getPath()
        + config.getServerDocIdPath(),
        new DocumentHandler(config.getServerHostname(),
                            config.getGsaCharacterEncoding(), this,
                            journal, adaptor,
                            config.getServerAddResolvedGsaHostnameToGsaIps(),
                            config.getGsaHostname(), config.getServerGsaIps(),
                            authnHandler, sessionManager, null));
    server.start();

    int dashboardPort = config.getServerDashboardPort();
    InetSocketAddress dashboardAddr = new InetSocketAddress(dashboardPort);
    if (!secure) {
      dashboardServer = HttpServer.create(dashboardAddr, 0);
    } else {
      dashboardServer = HttpsServer.create(dashboardAddr, 0);
      HttpsConfigurator httpsConf
          = new HttpsConfigurator(SSLContext.getDefault());
      ((HttpsServer) dashboardServer).setHttpsConfigurator(httpsConf);
    }
    // If the port is zero, then the OS chose a port for us. This is mainly
    // useful during testing.
    dashboardPort = dashboardServer.getAddress().getPort();
    config.setValue("server.dashboardPort", "" + dashboardPort);
    dashboardServer.setExecutor(executor);
    HttpHandler dashboardHandler = new DashboardHandler(
        config.getServerHostname(), config.getGsaCharacterEncoding());
    dashboardServer.createContext("/dashboard",
        createAdminSecurityHandler(dashboardHandler, config, sessionManager,
                                   secure));
    dashboardServer.createContext("/rpc",
        createAdminSecurityHandler(createRpcHandler(sessionManager, monitor),
            config, sessionManager, secure));
    dashboardServer.createContext("/",
        new RedirectHandler(config.getServerHostname(),
            config.getGsaCharacterEncoding(), "/dashboard"));
    dashboardServer.start();

    log.info("GSA host name: " + config.getGsaHostname());
    log.info("server is listening on port #" + port);
    log.info("dashboard is listening on port #" + dashboardPort);
    shutdownHook = new Thread(new ShutdownHook(), "gsacomm-shutdown");
    Runtime.getRuntime().addShutdownHook(shutdownHook);

    adaptor.init(new AdaptorContextImpl());

    config.addConfigModificationListener(new GsaConfigModListener());
    // Since we are white-listing particular keys for auto-update, things aren't
    // ready enough to expose to adaptors.
    /*if (adaptor instanceof ConfigModificationListener) {
      config.addConfigModificationListener(
          (ConfigModificationListener) adaptor);
    }*/

    if (adaptor instanceof PollingIncrementalAdaptor) {
      incrementalAdaptorPoller = new IncrementalAdaptorPoller(
          (PollingIncrementalAdaptor) adaptor, pusher);
      incrementalAdaptorPoller.start(
          config.getAdaptorIncrementalPollPeriodMillis());
    }

    scheduler.start();
    sendDocIdsSchedId = scheduler.schedule(
        config.getAdaptorFullListingSchedule(), docIdSender);

    long period = config.getConfigPollPeriodMillis();
    configWatcherTimer.schedule(new ConfigWatcher(config), period, period);
  }

  // Useful as a separate method during testing.
  static void bootstrapOpenSaml() {
    try {
      DefaultBootstrap.bootstrap();
    } catch (ConfigurationException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Stop the current services, allowing up to {@code maxDelay} seconds for
   * things to shutdown.
   */
  public synchronized void stop(int maxDelay) {
    if (circularLogRpcMethod != null) {
      circularLogRpcMethod.close();
      circularLogRpcMethod = null;
    }
    if (shutdownHook != null) {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      } catch (IllegalStateException ex) {
        // Already executing hook.
      }
      shutdownHook = null;
    }
    scheduler.deschedule(sendDocIdsSchedId);
    sendDocIdsSchedId = null;
    // Stop sendDocIds before scheduler, because scheduler blocks until all
    // tasks are completed. We want to interrupt sendDocIds so that the
    // scheduler stops within a reasonable amount of time.
    docIdSender.stop();
    if (scheduler.isStarted()) {
      scheduler.stop();
    }
    if (incrementalAdaptorPoller != null) {
      incrementalAdaptorPoller.cancel();
    }
    if (server != null) {
      server.stop(maxDelay);
      server = null;
    }
    if (dashboardServer != null) {
      dashboardServer.stop(1);
      dashboardServer = null;
    }
    adaptor.destroy();
  }

  private AdministratorSecurityHandler createAdminSecurityHandler(
      HttpHandler handler, Config config,
      SessionManager<HttpExchange> sessionManager, boolean secure) {
    return new AdministratorSecurityHandler(config.getServerHostname(),
        config.getGsaCharacterEncoding(), handler, sessionManager,
        config.getGsaHostname(), secure);
  }

  private synchronized RpcHandler createRpcHandler(
      SessionManager<HttpExchange> sessionManager, StatusMonitor monitor) {
    RpcHandler rpcHandler = new RpcHandler(config.getServerHostname(),
        config.getGsaCharacterEncoding(), sessionManager);
    rpcHandler.registerRpcMethod("startFeedPush", new StartFeedPushRpcMethod());
    circularLogRpcMethod = new CircularLogRpcMethod();
    rpcHandler.registerRpcMethod("getLog", circularLogRpcMethod);
    rpcHandler.registerRpcMethod("getConfig", new ConfigRpcMethod(config));
    rpcHandler.registerRpcMethod("getStats", new StatRpcMethod(journal));
    rpcHandler.registerRpcMethod("getStatuses", new StatusRpcMethod(monitor));
    return rpcHandler;
  }

  /**
   * Ensure there is a push running right now. This schedules a new push if one
   * is not already running. Returns {@code true} if it starts a new push, and
   * false otherwise.
   */
  public boolean checkAndScheduleImmediatePushOfDocIds() {
    return docIdSender.runInNewThread() != null;
  }

  private DocInfo pushSizedBatchOfDocInfos(List<DocInfo> docInfos,
                                           PushErrorHandler handler)
      throws InterruptedException {
    String feedSourceName = config.getFeedName();
    String xmlFeedFile = fileMaker.makeMetadataAndUrlXml(
        feedSourceName, docInfos);
    boolean keepGoing = true;
    boolean success = false;
    log.log(Level.INFO, "Pushing batch of {0} DocIds to GSA", docInfos.size());
    for (int ntries = 1; keepGoing; ntries++) {
      try {
        log.info("Sending feed to GSA host name: " + config.getGsaHostname());
        fileSender.sendMetadataAndUrl(config.getGsaHostname(), feedSourceName,
                                      xmlFeedFile);
        keepGoing = false;  // Sent.
        success = true;
      } catch (GsaFeedFileSender.FailedToConnect ftc) {
        log.log(Level.WARNING, "Unable to connect to the GSA", ftc);
        keepGoing = handler.handleFailedToConnect(
            (Exception) ftc.getCause(), ntries);
      } catch (GsaFeedFileSender.FailedWriting fw) {
        log.log(Level.WARNING, "Unable to write request to the GSA", fw);
        keepGoing = handler.handleFailedWriting(
            (Exception) fw.getCause(), ntries);
      } catch (GsaFeedFileSender.FailedReadingReply fr) {
        log.log(Level.WARNING, "Unable to read reply from GSA", fr);
        keepGoing = handler.handleFailedReadingReply(
            (Exception) fr.getCause(), ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      }
    }
    if (success) {
      log.info("Pushing batch succeeded");
    } else {
      log.log(Level.WARNING, "Gave up. First item in list: {0}",
              docInfos.get(0));
    }
    log.info("Finished pushing batch");
    return success ? null : docInfos.get(0);
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. This method blocks
   * until all DocIds are sent or retrying failed.
   */
  private void pushDocIds(GetDocIdsErrorHandler handler)
      throws InterruptedException {
    if (handler == null) {
      throw new NullPointerException();
    }
    log.info("Beginning full push of DocIds");
    journal.recordFullPushStarted();
    for (int ntries = 1;; ntries++) {
      boolean keepGoing = true;
      try {
        adaptor.getDocIds(pusher);
        break; // Success
      } catch (InterruptedException ex) {
        // Stop early.
        journal.recordFullPushInterrupted();
        log.info("Interrupted. Aborted full push of DocIds");
        throw ex;
      } catch (Exception ex) {
        log.log(Level.WARNING, "Unable to retrieve DocIds from adaptor", ex);
        keepGoing = handler.handleFailedToGetDocIds(ex, ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      } else {
        journal.recordFullPushFailed();
        log.warning("Gave up. Failed full push of DocIds");
        return; // Bail
      }
    }
    journal.recordFullPushSuccessful();
    log.info("Completed full pushing DocIds");
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. Generally, you should
   * use {@link #pushDocIds()} instead of this method. However, if you want to
   * push just a few DocIds to the GSA manually, this is the method to use.
   * This method blocks until all DocIds are sent or retrying failed.
   */
  private DocInfo pushDocInfos(Iterator<DocInfo> docInfos,
                               PushErrorHandler handler)
      throws InterruptedException {
    log.log(Level.INFO, "Pushing DocIds");
    final int max = config.getFeedMaxUrls();
    while (docInfos.hasNext()) {
      List<DocInfo> batch = new ArrayList<DocInfo>();
      for (int j = 0; j < max; j++) {
        if (!docInfos.hasNext()) {
          break;
        }
        batch.add(docInfos.next());
      }
      log.log(Level.INFO, "Pushing group of {0} DocIds", batch.size());
      DocInfo failedId = pushSizedBatchOfDocInfos(batch, handler);
      if (failedId != null) {
        log.info("Failed to push all ids. Failed on docId: " + failedId);
        return failedId;
      }
      journal.recordDocIdPush(batch);
    }
    log.info("Pushed DocIds");
    return null;
  }

  public URI encodeDocId(DocId docId) {
    if (config.isDocIdUrl()) {
      return URI.create(docId.getUniqueId());
    } else {
      URI base = config.getServerBaseUri(docId);
      URI resource;
      String uniqueId = docId.getUniqueId();
      // Add two dots to any sequence of only dots. This is to allow "/../" and
      // "/./" within DocIds.
      uniqueId = uniqueId.replaceAll("(^|/)(\\.+)(?=$|/)", "$1$2..");
      try {
        resource = new URI(null, null, base.getPath()
                           + config.getServerDocIdPath() + uniqueId, null);
      } catch (URISyntaxException ex) {
        throw new IllegalStateException(ex);
      }
      return base.resolve(resource);
    }
  }

  /** Given a URI that was used in feed file, convert back to doc id. */
  public DocId decodeDocId(URI uri) {
    if (config.isDocIdUrl()) {
      return new DocId(uri.toString());
    } else {
      String basePath = config.getServerBaseUri().getPath();
      String id = uri.getPath().substring(basePath.length()
          + config.getServerDocIdPath().length());
      // Remove two dots from any sequence of only dots. This is to remove the
      // addition we did in {@link #encodeDocId}.
      id = id.replaceAll("(^|/)(\\.+)\\.\\.(?=$|/)", "$1$2");
      return new DocId(id);
    }
  }

  private class InnerDocIdPusher extends AbstractDocIdPusher {
    private PushErrorHandler defaultErrorHandler
        = new DefaultPushErrorHandler();

    @Override
    public DocInfo pushDocInfos(Iterable<DocInfo> docInfos,
                                PushErrorHandler handler)
        throws InterruptedException {
      if (handler == null) {
        handler = defaultErrorHandler;
      }
      return GsaCommunicationHandler.this.pushDocInfos(docInfos.iterator(),
                                                       handler);
    }
  }

  /**
   * Runnable that calls {@link #pushDocIds}.
   */
  private class PushRunnable implements Runnable {
    private GetDocIdsErrorHandler handler = new DefaultGetDocIdsErrorHandler();

    @Override
    public void run() {
      try {
        pushDocIds(handler);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }

    public void setGetDocIdsErrorHandler(GetDocIdsErrorHandler handler) {
      if (handler == null) {
        throw new NullPointerException();
      }
      this.handler = handler;
    }
  }

  /**
   * Runnable that logs an error that {@link PushRunnable} is already executing.
   */
  private class AlreadyRunningRunnable implements Runnable {
    @Override
    public void run() {
      log.warning("Skipping scheduled push of docIds. The previous invocation "
                  + "is still running.");
    }
  }

  private class ShutdownHook implements Runnable {
    @Override
    public void run() {
      // Allow three seconds for things to stop.
      stop(3);
    }
  }

  private class StartFeedPushRpcMethod implements RpcHandler.RpcMethod {
    @Override
    public Object run(List request) {
      boolean pushStarted = checkAndScheduleImmediatePushOfDocIds();
      if (!pushStarted) {
        throw new RuntimeException("A push is already in progress");
      }
      return 1;
    }
  }

  static class CircularLogRpcMethod implements RpcHandler.RpcMethod,
      Closeable {
    private final CircularBufferHandler circularLog
        = new CircularBufferHandler();

    /**
     * Installs a log handler; to uninstall handler, call {@link #close}.
     */
    public CircularLogRpcMethod() {
      LogManager.getLogManager().getLogger("").addHandler(circularLog);
    }

    @Override
    public Object run(List request) {
      return circularLog.writeOut();
    }

    @Override
    public void close() {
      LogManager.getLogManager().getLogger("").removeHandler(circularLog);
    }
  }

  static class ConfigRpcMethod implements RpcHandler.RpcMethod {
    private final Config config;

    public ConfigRpcMethod(Config config) {
      this.config = config;
    }

    @Override
    public Object run(List request) {
      TreeMap<String, String> configMap = new TreeMap<String, String>();
      for (String key : config.getAllKeys()) {
        configMap.put(key, config.getValue(key));
      }
      return configMap;
    }
  }

  static class StatusRpcMethod implements RpcHandler.RpcMethod {
    private final StatusMonitor monitor;

    public StatusRpcMethod(StatusMonitor monitor) {
      this.monitor = monitor;
    }

    @Override
    public Object run(List request) {
      Map<StatusSource, Status> statuses = monitor.retrieveStatuses();
      List<Object> flatStatuses = new ArrayList<Object>(statuses.size());
      for (Map.Entry<StatusSource, Status> me : statuses.entrySet()) {
        Map<String, String> obj = new TreeMap<String, String>();
        obj.put("source", me.getKey().getName());
        obj.put("code", me.getValue().getCode().name());
        obj.put("message", me.getValue().getMessage());
        flatStatuses.add(obj);
      }
      return flatStatuses;
    }
  }

  private class GsaConfigModListener implements ConfigModificationListener {
    @Override
    public void configModified(ConfigModificationEvent ev) {
      Set<String> modifiedKeys = ev.getModifiedKeys();
      synchronized (GsaCommunicationHandler.this) {
        if (modifiedKeys.contains("adaptor.fullListingSchedule")
            && sendDocIdsSchedId != null) {
          String schedule = ev.getNewConfig().getAdaptorFullListingSchedule();
          try {
            scheduler.reschedule(sendDocIdsSchedId, schedule);
          } catch (InvalidPatternException ex) {
            log.log(Level.WARNING, "Invalid schedule pattern", ex);
          }
        }
      }
    }
  }

  private static class ConfigWatcher extends TimerTask {
    private Config config;

    public ConfigWatcher(Config config) {
      this.config = config;
    }

    @Override
    public void run() {
      try {
        config.ensureLatestConfigLoaded();
      } catch (Exception ex) {
        log.log(Level.WARNING, "Error while trying to reload configuration",
                ex);
      }
    }
  }

  static class LastPushStatusSource implements StatusSource {
    private final Journal journal;

    public LastPushStatusSource(Journal journal) {
      this.journal = journal;
    }

    @Override
    public Status retrieveStatus() {
      switch (journal.getLastPushStatus()) {
        case SUCCESS:
          return new Status(StatusCode.NORMAL);
        case INTERRUPTION:
          return new Status(StatusCode.WARNING, "Push was interrupted");
        case FAILURE:
        default:
          return new Status(StatusCode.ERROR);
      }
    }

    @Override
    public String getName() {
      return "Feed Pushing";
    }
  }

  static class RetrieverStatusSource implements StatusSource {
    static final double ERROR_THRESHOLD = 1. / 8.;
    static final double WARNING_THRESHOLD = 1. / 16.;
    private static final int MAX_COUNT = 1000;

    private final Journal journal;

    public RetrieverStatusSource(Journal journal) {
      this.journal = journal;
    }

    @Override
    public Status retrieveStatus() {
      double rate = journal.getRetrieverErrorRate(MAX_COUNT);
      StatusCode code;
      if (rate >= ERROR_THRESHOLD) {
        code = StatusCode.ERROR;
      } else if (rate >= WARNING_THRESHOLD) {
        code = StatusCode.WARNING;
      } else {
        code = StatusCode.NORMAL;
      }
      return new Status(code,
          "Error rate: " + (int) Math.ceil(rate * 100) + "%");
    }

    @Override
    public String getName() {
      return "Retriever Error Rate";
    }
  }

  static class GsaCrawlingStatusSource implements StatusSource {
    private final Journal journal;

    public GsaCrawlingStatusSource(Journal journal) {
      this.journal = journal;
    }

    @Override
    public Status retrieveStatus() {
      if (journal.getGsaCrawled()) {
        return new Status(StatusCode.NORMAL);
      } else {
        return new Status(StatusCode.WARNING,
            "No accesses within the past day");
      }
    }

    @Override
    public String getName() {
      return "GSA Crawling";
    }
  }

  private class AdaptorContextImpl implements AdaptorContext {
    @Override
    public Config getConfig() {
      return config;
    }

    @Override
    public DocIdPusher getDocIdPusher() {
      return pusher;
    }
  }
}
