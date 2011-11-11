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
import java.util.ArrayList;
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
public class GsaCommunicationHandler {
  private static final Logger log
      = Logger.getLogger(GsaCommunicationHandler.class.getName());

  private final Adaptor adaptor;
  private final Config config;
  private final Journal journal = new Journal();
  /**
   * Generic scheduler. Available for other uses, but necessary for running
   * {@link docIdFullPusher}
   */
  private Scheduler scheduler = new Scheduler();
  /**
   * Runnable to be called for doing a full push of {@code DocId}s. It only
   * permits one invocation at a time. If multiple simultaneous invocations
   * occur, all but the first will log a warning and return immediately.
   */
  private final OneAtATimeRunnable docIdFullPusher;
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
  private final DocIdCodec docIdCodec;
  private final DocIdSender docIdSender;

  public GsaCommunicationHandler(Adaptor adaptor, Config config) {
    this.adaptor = adaptor;
    this.config = config;

    docIdCodec = new DocIdCodec(config);
    GsaFeedFileSender fileSender = new GsaFeedFileSender(
        config.getGsaCharacterEncoding(), config.isServerSecure());
    GsaFeedFileMaker fileMaker = new GsaFeedFileMaker(docIdCodec);
    docIdSender
        = new DocIdSender(fileMaker, fileSender, journal, config, adaptor);
    docIdFullPusher = new OneAtATimeRunnable(
        new PushRunnable(), new AlreadyRunningRunnable());

    monitor.addSource(new LastPushStatusSource(journal));
    monitor.addSource(new RetrieverStatusSource(journal));
    monitor.addSource(new GsaCrawlingStatusSource(journal));
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
          adaptor, docIdCodec, metadata));
    }
    server.createContext(config.getServerBaseUri().getPath()
        + config.getServerDocIdPath(),
        new DocumentHandler(config.getServerHostname(),
                            config.getGsaCharacterEncoding(), docIdCodec,
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
          (PollingIncrementalAdaptor) adaptor, docIdSender);
      incrementalAdaptorPoller.start(
          config.getAdaptorIncrementalPollPeriodMillis());
    }

    scheduler.start();
    sendDocIdsSchedId = scheduler.schedule(
        config.getAdaptorFullListingSchedule(), docIdFullPusher);

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
    docIdFullPusher.stop();
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
    return docIdFullPusher.runInNewThread() != null;
  }

  /**
   * Runnable that calls {@link DocIdSender#pushDocIds}.
   */
  private class PushRunnable implements Runnable {
    private volatile GetDocIdsErrorHandler handler
        = new DefaultGetDocIdsErrorHandler();

    @Override
    public void run() {
      try {
        docIdSender.pushDocIdsFromAdaptor(handler);
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

    public GetDocIdsErrorHandler getGetDocIdsErrorHandler() {
      return handler;
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
          return new Status(Status.Code.NORMAL);
        case INTERRUPTION:
          return new Status(Status.Code.WARNING, "Push was interrupted");
        case FAILURE:
        default:
          return new Status(Status.Code.ERROR);
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
      Status.Code code;
      if (rate >= ERROR_THRESHOLD) {
        code = Status.Code.ERROR;
      } else if (rate >= WARNING_THRESHOLD) {
        code = Status.Code.WARNING;
      } else {
        code = Status.Code.NORMAL;
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
      if (journal.hasGsaCrawledWithinLastDay()) {
        return new Status(Status.Code.NORMAL);
      } else {
        return new Status(Status.Code.WARNING,
            "No accesses within the past day");
      }
    }

    @Override
    public String getName() {
      return "GSA Crawling";
    }
  }

  /**
   * This class is thread-safe.
   */
  private class AdaptorContextImpl implements AdaptorContext {
    @Override
    public Config getConfig() {
      return config;
    }

    @Override
    public DocIdPusher getDocIdPusher() {
      return docIdSender;
    }

    @Override
    public DocIdEncoder getDocIdEncoder() {
      return docIdCodec;
    }

    @Override
    public void addStatusSource(StatusSource source) {
      monitor.addSource(source);
    }

    @Override
    public void removeStatusSource(StatusSource source) {
      monitor.removeSource(source);
    }

    @Override
    public void setGetDocIdsErrorHandler(GetDocIdsErrorHandler handler) {
      ((PushRunnable) docIdFullPusher.getRunnable())
          .setGetDocIdsErrorHandler(handler);
    }

    @Override
    public GetDocIdsErrorHandler getGetDocIdsErrorHandler() {
      return ((PushRunnable) docIdFullPusher.getRunnable())
          .getGetDocIdsErrorHandler();
    }
  }
}
