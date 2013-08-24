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

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;

import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** This class handles the communications with GSA. */
public final class GsaCommunicationHandler {
  private static final Logger log
      = Logger.getLogger(GsaCommunicationHandler.class.getName());

  private final Adaptor adaptor;
  private final Config config;
  private final Journal journal;
  /**
   * Cron-style scheduler. Available for other uses, but necessary for
   * scheduling {@link docIdFullPusher}. Tasks should execute quickly, to allow
   * shutting down promptly.
   */
  private CronScheduler scheduler;
  /**
   * Runnable to be called for doing a full push of {@code DocId}s. It only
   * permits one invocation at a time. If multiple simultaneous invocations
   * occur, all but the first will log a warning and return immediately.
   */
  private final OneAtATimeRunnable docIdFullPusher = new OneAtATimeRunnable(
      new PushRunnable(), new AlreadyRunningRunnable());
  /**
   * Runnable to be called for doing incremental feed pushes. It is only
   * set if the Adaptor supports incremental updates. Otherwise, it's null.
   */
  private OneAtATimeRunnable docIdIncrementalPusher;
  /**
   * Schedule identifier for {@link #sendDocIds}.
   */
  private Future<?> sendDocIdsFuture;
  private HttpServerScope scope;
  private SessionManager<HttpExchange> sessionManager;
  /**
   * Executor for scheduling tasks in the future. These tasks <em>must</em>
   * complete quickly, as the executor purposely is single-threaded.
   *
   * <p>The reason tasks must finish quickly is that the
   * ScheduledExecutorService implementation provided by Java is a fixed-size
   * thread pool. In addition, fixed-rate (as opposed to fixed-delay) events
   * "pile up" when the runnable takes a long time to complete. Thus, using the
   * scheduleExecutor for longer processing effectively requires a dedicated
   * thread (because the pool is fixed-size) as well as runs into the event
   * "pile up" issue.
   */
  private ScheduledExecutorService scheduleExecutor;
  /**
   * Executor for performing work in the background. This executor is general
   * purpose and is commonly used in conjunction with {@link #scheduleExecutor}.
   */
  private ExecutorService backgroundExecutor;
  private DocIdCodec docIdCodec;
  private DocIdSender docIdSender;
  private Dashboard dashboard;
  private SensitiveValueCodec secureValueCodec;
  private SamlIdentityProvider samlIdentityProvider;
  /**
   * Used to stop startup prematurely. This allows cancelling an already-running
   * start(). If start fails, a stale shuttingDownLatch can remain, thus it does
   * not provide any information as to whether a start() call is running.
   */
  private volatile CountDownLatch shuttingDownLatch;
  /**
   * Used to stop startup prematurely. When greater than 0, start() should abort
   * immediately because stop() is currently processing. This allows cancelling
   * new start() calls before stop() is done processing.
   */
  private final AtomicInteger shutdownCount = new AtomicInteger();
  private ShutdownWaiter waiter;
  private final List<Filter> commonFilters = Arrays.asList(new Filter[] {
    new AbortImmediatelyFilter(),
    new LoggingFilter(),
    new InternalErrorFilter(),
  });

  public GsaCommunicationHandler(Adaptor adaptor, Config config) {
    this.adaptor = adaptor;
    this.config = config;

    journal = new Journal(config.isJournalReducedMem());
  }

  /**
   * Starts listening for communications from GSA. {@code contextPrefix}
   */
  public synchronized void start(HttpServer server, HttpServer dashboardServer)
      throws IOException, InterruptedException {
    start(server, dashboardServer, null);
  }

  /**
   * Starts listening for communications from GSA. {@code ""} is used for
   * {@code contextPrefix} if the passed value is {@code null}.
   */
  public synchronized void start(HttpServer server, HttpServer dashboardServer,
      String contextPrefix) throws IOException, InterruptedException {
    if (this.scope != null) {
      throw new IllegalStateException("Already listening");
    }
    if (server == null || dashboardServer == null) {
      throw new NullPointerException();
    }
    if (contextPrefix == null) {
      contextPrefix = "";
    }
    if (server instanceof HttpsServer
        != dashboardServer instanceof HttpsServer) {
      throw new IllegalArgumentException(
          "Both servers must be HttpServers or both HttpsServers");
    }
    shuttingDownLatch = new CountDownLatch(1);
    if (shutdownCount.get() > 0) {
      shuttingDownLatch = null;
      return;
    }

    boolean secure = server instanceof HttpsServer;
    if (secure != config.isServerSecure()) {
      config.setValue("server.secure", "" + secure);
    }
    KeyPair key = null;
    try {
      key = getKeyPair(config.getServerKeyAlias());
    } catch (IOException ex) {
      // The exception is only fatal if we are in secure mode.
      if (secure) {
        throw ex;
      }
    } catch (RuntimeException ex) {
      // The exception is only fatal if we are in secure mode.
      if (secure) {
        throw ex;
      }
    }
    secureValueCodec = new SensitiveValueCodec(key);

    int port = server.getAddress().getPort();
    if (port != config.getServerPort()) {
        config.setValue("server.port", "" + port);
    }

    scope = new HttpServerScope(server, contextPrefix);
    waiter = new ShutdownWaiter();

    scheduleExecutor = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("schedule")
        .build());
    // The cachedThreadPool implementation created here is considerably better
    // than using ThreadPoolExecutor. ThreadPoolExecutor does not create threads
    // as would be expected from a thread pool.
    backgroundExecutor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("background")
        .build());

    sessionManager = new SessionManager<HttpExchange>(
          new SessionManager.HttpExchangeClientStore("sessid_" + port, secure),
          30 * 60 * 1000 /* session lifetime: 30 minutes */,
          5 * 60 * 1000 /* max cleanup frequency: 5 minutes */);

    config.addConfigModificationListener(new GsaConfigModListener());

    URI baseUri = config.getServerBaseUri();
    URI docUri;
    try {
      docUri = new URI(null, null, contextPrefix + config.getServerDocIdPath(),
          null);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Invalid prefix or docIdPath", ex);
    }
    docIdCodec = new DocIdCodec(baseUri.resolve(docUri), config.isDocIdUrl());
    GsaFeedFileSender fileSender = new GsaFeedFileSender(
        config.getGsaHostname(), config.isServerSecure(),
        config.getGsaCharacterEncoding());
    GsaFeedFileMaker fileMaker = new GsaFeedFileMaker(docIdCodec,
        config.isGsa614FeedWorkaroundEnabled(),
        config.isGsa70AuthMethodWorkaroundEnabled());
    docIdSender
        = new DocIdSender(fileMaker, fileSender, journal, config, adaptor);

    dashboard = new Dashboard(config, this, journal, sessionManager,
        secureValueCodec, adaptor);

    // We are about to start the Adaptor, so anything available through
    // AdaptorContext or other means must be initialized at this point. Any
    // reference to 'adaptor' before this point must be done very carefully to
    // ensure it doesn't call the adaptor until after Adaptor.init() completes.

    long sleepDurationMillis = 1000;
    // An hour.
    long maxSleepDurationMillis = 60 * 60 * 1000;
    // Loop until 1) the adaptor starts successfully, 2) stop() is called, or
    // 3) Thread.interrupt() is called on this thread (which we don't do).
    // Retrying to start the adaptor is helpful in cases where it needs
    // initialization data from a repository that is temporarily down; if the
    // adaptor is running as a service, we don't want to stop starting simply
    // because another computer is down while we start (which would easily be
    // the case after a power failure).
    while (true) {
      try {
        adaptor.init(new AdaptorContextImpl());
        break;
      } catch (InterruptedException ex) {
        throw ex;
      } catch (Exception ex) {
        log.log(Level.WARNING, "Failed to initialize adaptor", ex);
        if (shuttingDownLatch.await(sleepDurationMillis,
              TimeUnit.MILLISECONDS)) {
          // Shutdown initiated.
          break;
        }
        sleepDurationMillis
            = Math.min(sleepDurationMillis * 2, maxSleepDurationMillis);
        ensureLatestConfigLoaded();
      }
    }

    // Since the Adaptor has been started, we can now issue other calls to it.
    // Usages of 'adaptor' are completely safe after this point.

    // Since we are white-listing particular keys for auto-update, things aren't
    // ready enough to expose to adaptors.
    /*if (adaptor instanceof ConfigModificationListener) {
      config.addConfigModificationListener(
          (ConfigModificationListener) adaptor);
    }*/

    SamlServiceProvider samlServiceProvider = null;
    if (secure) {
      bootstrapOpenSaml();
      SamlMetadata metadata = new SamlMetadata(config.getServerHostname(),
          config.getServerPort(), config.getGsaHostname(),
          config.getGsaSamlEntityId(), config.getServerSamlEntityId());

      if (adaptor instanceof AuthnAdaptor) {
        log.config("Adaptor is an AuthnAdaptor; enabling adaptor-based "
            + "authentication");
        samlIdentityProvider = new SamlIdentityProvider(
            (AuthnAdaptor) adaptor, metadata, key);
        addFilters(scope.createContext("/samlip",
            samlIdentityProvider.getSingleSignOnHandler()));
      } else {
        log.config("Adaptor is not an AuthnAdaptor; not enabling adaptor-based "
            + "authentication");
      }
      samlServiceProvider
          = new SamlServiceProvider(sessionManager, metadata, key);
      addFilters(scope.createContext("/samlassertionconsumer",
          samlServiceProvider.getAssertionConsumer()));
      addFilters(scope.createContext("/saml-authz", new SamlBatchAuthzHandler(
          adaptor, docIdCodec, metadata)));
    }
    Watchdog watchdog = new Watchdog(scheduleExecutor);
    AsyncDocIdSender asyncDocIdSender = new AsyncDocIdSender(docIdSender,
        config.getFeedMaxUrls() /* batch size */,
        5 /* max latency */, TimeUnit.MINUTES,
        2 * config.getFeedMaxUrls() /* queue size */);
    backgroundExecutor.execute(waiter.runnable(asyncDocIdSender.worker()));
    DocumentHandler docHandler = new DocumentHandler(
        docIdCodec, docIdCodec, journal, adaptor,
        config.getGsaHostname(),
        config.getServerFullAccessHosts(),
        samlServiceProvider, createTransformPipeline(),
        config.isServerToUseCompression(), watchdog,
        asyncDocIdSender, 
        config.sendDocControlsHeader(),
        config.getAdaptorDocHeaderTimeoutMillis(),
        config.getAdaptorDocContentTimeoutMillis(),
        config.getScoringType());
    String handlerPath = config.getServerBaseUri().getPath()
        + config.getServerDocIdPath();
    addFilters(scope.createContext(handlerPath, docHandler));

    // Start communicating with other services. As a general rule, by this time
    // we want all services we provide to be up and running. However, note that
    // the adaptor may have started sending feeds as soon as we called init(),
    // and that is "okay." In addition, the HttpServer we were provided may not
    // have been started yet.

    scheduler = new CronScheduler(scheduleExecutor);
    sendDocIdsFuture = scheduler.schedule(
        config.getAdaptorFullListingSchedule(),
        waiter.runnable(new BackgroundRunnable(docIdFullPusher)));
    if (config.isAdaptorPushDocIdsOnStartup()) {
      log.info("Pushing once at program start");
      checkAndScheduleImmediatePushOfDocIds();
    }

    if (adaptor instanceof PollingIncrementalAdaptor) {
      docIdIncrementalPusher = new OneAtATimeRunnable(
          new IncrementalPushRunnable((PollingIncrementalAdaptor) adaptor),
          new AlreadyRunningRunnable());

      scheduleExecutor.scheduleAtFixedRate(
          waiter.runnable(new BackgroundRunnable(docIdIncrementalPusher)),
          0,
          config.getAdaptorIncrementalPollPeriodMillis(),
          TimeUnit.MILLISECONDS);
    }

    dashboard.start(dashboardServer, contextPrefix);

    shuttingDownLatch = null;
  }

  private TransformPipeline createTransformPipeline() {
    return createTransformPipeline(config.getTransformPipelineSpec());
  }

  static TransformPipeline createTransformPipeline(
      List<Map<String, String>> pipelineConfig) {
    List<DocumentTransform> elements = new LinkedList<DocumentTransform>();
    List<String> names = new LinkedList<String>();
    for (Map<String, String> element : pipelineConfig) {
      final String name = element.get("name");
      final String confPrefix = "transform.pipeline." + name + ".";
      String factoryMethodName = element.get("factoryMethod");
      if (factoryMethodName == null) {
        throw new RuntimeException(
            "Missing " + confPrefix + "factoryMethod configuration setting");
      }
      int sepIndex = factoryMethodName.lastIndexOf(".");
      if (sepIndex == -1) {
        throw new RuntimeException("Could not separate method name from class "
            + "name");
      }
      String className = factoryMethodName.substring(0, sepIndex);
      String methodName = factoryMethodName.substring(sepIndex + 1);
      log.log(Level.FINE, "Split {0} into class {1} and method {2}",
          new Object[] {factoryMethodName, className, methodName});
      Class<?> klass;
      try {
        klass = Class.forName(className);
      } catch (ClassNotFoundException ex) {
        throw new RuntimeException(
            "Could not load class for transform " + name, ex);
      }
      Method method;
      try {
        method = klass.getDeclaredMethod(methodName, Map.class);
      } catch (NoSuchMethodException ex) {
        throw new RuntimeException("Could not find method " + methodName
            + " on class " + className, ex);
      }
      log.log(Level.FINE, "Found method {0}", new Object[] {method});
      Object o;
      try {
        o = method.invoke(null, Collections.unmodifiableMap(element));
      } catch (Exception ex) {
        throw new RuntimeException("Failure while running factory method "
            + factoryMethodName, ex);
      }
      if (!(o instanceof DocumentTransform)) {
        throw new ClassCastException(o.getClass().getName()
            + " is not an instance of DocumentTransform");
      }
      DocumentTransform transform = (DocumentTransform) o;
      elements.add(transform);
      names.add(name);
    }
    // If we created an empty pipeline, then we don't need the pipeline at all.
    return elements.size() > 0 ? new TransformPipeline(elements, names) : null;
  }

  /**
   * Retrieve our default KeyPair from the default keystore. The key should have
   * the same password as the keystore.
   */
  private static KeyPair getKeyPair(String alias) throws IOException {
    final String keystoreKey = "javax.net.ssl.keyStore";
    final String keystorePasswordKey = "javax.net.ssl.keyStorePassword";
    String keystore = System.getProperty(keystoreKey);
    String keystoreType = System.getProperty("javax.net.ssl.keyStoreType",
                                             KeyStore.getDefaultType());
    String keystorePassword = System.getProperty(keystorePasswordKey);

    if (keystore == null) {
      throw new NullPointerException("You must set " + keystoreKey);
    }
    if (keystorePassword == null) {
      throw new NullPointerException("You must set " + keystorePasswordKey);
    }

    return getKeyPair(alias, keystore, keystoreType, keystorePassword);
  }

  static KeyPair getKeyPair(String alias, String keystoreFile,
      String keystoreType, String keystorePasswordStr) throws IOException {
    PrivateKey privateKey;
    PublicKey publicKey;
    try {
      KeyStore ks = KeyStore.getInstance(keystoreType);
      InputStream ksis = new FileInputStream(keystoreFile);
      char[] keystorePassword = keystorePasswordStr == null ? null
          : keystorePasswordStr.toCharArray();
      try {
        ks.load(ksis, keystorePassword);
      } catch (NoSuchAlgorithmException ex) {
        throw new RuntimeException(ex);
      } catch (CertificateException ex) {
        throw new RuntimeException(ex);
      } finally {
        ksis.close();
      }
      Key key = null;
      try {
        key = ks.getKey(alias, keystorePassword);
      } catch (NoSuchAlgorithmException ex) {
        throw new RuntimeException(ex);
      } catch (UnrecoverableKeyException ex) {
        throw new RuntimeException(ex);
      }
      if (key == null) {
        throw new IllegalStateException("Could not find key for alias '"
                                        + alias + "'");
      }
      privateKey = (PrivateKey) key;
      publicKey = ks.getCertificate(alias).getPublicKey();
    } catch (KeyStoreException ex) {
      throw new RuntimeException(ex);
    }
    return new KeyPair(publicKey, privateKey);
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
  public void stop(long time, TimeUnit unit) {
    // Prevent new start()s.
    shutdownCount.incrementAndGet();
    try {
      CountDownLatch latch = shuttingDownLatch;
      if (latch != null) {
        // Cause existing start() to begin cancelling.
        latch.countDown();
      }
      realStop(time, unit);
    } finally {
      // Permit new start()s.
      shutdownCount.decrementAndGet();
    }
  }

  private synchronized void realStop(long time, TimeUnit unit) {
    if (scope != null) {
      scope.close();
    }
    if (dashboard != null) {
      dashboard.stop();
    }
    if (scheduleExecutor != null) {
      scheduleExecutor.shutdownNow();
    }
    if (backgroundExecutor != null) {
      backgroundExecutor.shutdownNow();
    }
    if (waiter != null) {
      try {
        waiter.shutdown(time, unit);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
    dashboard.clearStatusSources();
    try {
      adaptor.destroy();
    } finally {
      // Wait until after adaptor.destroy() to set things to null, so that the
      // AdaptorContext is usable until the very end.
      sendDocIdsFuture = null;
      scheduler = null;
      scope = null;
      dashboard = null;
      scheduleExecutor = null;
      backgroundExecutor = null;
      waiter = null;
      sessionManager = null;
      docIdCodec = null;
    }
  }

  /**
   * Ensure there is a push running right now. This schedules a new push if one
   * is not already running. Returns {@code true} if it starts a new push, and
   * {@code false} otherwise.
   */
  public boolean checkAndScheduleImmediatePushOfDocIds() {
    if (docIdFullPusher.isRunning()) {
      return false;
    }
    // This check-then-execute permits a race between checking and starting the
    // runnable, but it shouldn't be a major issue since the caller wanted a
    // push to start right now, and one "just started."
    backgroundExecutor.execute(waiter.runnable(docIdFullPusher));
    return true;
  }

  /**
   * Perform an push of incremental changes. This works only for adaptors that
   * support incremental polling (implements {@link PollingIncrementalAdaptor}.
   */
  public synchronized boolean checkAndScheduleIncrementalPushOfDocIds() {
    if (docIdIncrementalPusher == null) {
      throw new IllegalStateException(
          "This adaptor does not support incremental push");
    }

    if (docIdIncrementalPusher.isRunning()) {
      return false;
    }
    // This permits a race between checking and starting the runnable, but it
    // shouldn't be a major issue since the caller wanted a push to start right
    // now, and one "just started."
    backgroundExecutor.execute(waiter.runnable(docIdIncrementalPusher));
    return true;
  }

  boolean ensureLatestConfigLoaded() {
    try {
      return config.ensureLatestConfigLoaded();
    } catch (Exception ex) {
      log.log(Level.WARNING, "Error while trying to reload configuration",
              ex);
      return false;
    }
  }

  /** The adaptor instance being used. */
  public Adaptor getAdaptor() {
    return adaptor;
  }

  HttpContext addFilters(HttpContext context) {
    context.getFilters().add(waiter.filter());
    context.getFilters().addAll(commonFilters);
    return context;
  }

  /**
   * Runnable that calls {@link DocIdSender#pushDocIds}.
   */
  private class PushRunnable implements Runnable {
    private volatile ExceptionHandler handler
        = ExceptionHandlers.defaultHandler();

    @Override
    public void run() {
      try {
        docIdSender.pushFullDocIdsFromAdaptor(handler);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }

    public void setGetDocIdsErrorHandler(ExceptionHandler handler) {
      if (handler == null) {
        throw new NullPointerException();
      }
      this.handler = handler;
    }

    public ExceptionHandler getGetDocIdsErrorHandler() {
      return handler;
    }
  }

  /**
   * Runnable that performs incremental feed push.
   */
  private class IncrementalPushRunnable implements Runnable {
    private volatile ExceptionHandler handler
        = ExceptionHandlers.defaultHandler();
    private PollingIncrementalAdaptor adaptor;

    public IncrementalPushRunnable(PollingIncrementalAdaptor adaptor) {
      this.adaptor = adaptor;
    }

    @Override
    public void run() {
      try {
        docIdSender.pushIncrementalDocIdsFromAdaptor(handler);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        log.log(Level.WARNING, "Exception during incremental polling", ex);
      }
    }

    public void setGetDocIdsErrorHandler(ExceptionHandler handler) {
      if (handler == null) {
        throw new NullPointerException();
      }
      this.handler = handler;
    }

    public ExceptionHandler getGetDocIdsErrorHandler() {
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

  /**
   * Runnable that when invoked executes the delegate with {@link
   * #backgroundExecutor} and then returns before completion. That implies that
   * uses of this class must ensure they do not add an instance directly to
   * {@link #backgroundExecutor}, otherwise an odd infinite loop will occur.
   */
  private class BackgroundRunnable implements Runnable {
    private final Runnable delegate;

    public BackgroundRunnable(Runnable delegate) {
      this.delegate = delegate;
    }

    @Override
    public void run() {
      // Wrap with waiter.runnable() every time instead of in constructor to aid
      // auditing the code for "ShutdownWaiter correctness."
      backgroundExecutor.execute(waiter.runnable(delegate));
    }
  }

  private class GsaConfigModListener implements ConfigModificationListener {
    @Override
    public void configModified(ConfigModificationEvent ev) {
      Set<String> modifiedKeys = ev.getModifiedKeys();
      synchronized (GsaCommunicationHandler.this) {
        if (modifiedKeys.contains("adaptor.fullListingSchedule")
            && sendDocIdsFuture != null) {
          String schedule = ev.getNewConfig().getAdaptorFullListingSchedule();
          try {
            scheduler.reschedule(sendDocIdsFuture, schedule);
          } catch (IllegalArgumentException ex) {
            log.log(Level.WARNING, "Invalid schedule pattern", ex);
          }
        }
      }

      // List of "safe" keys that can be updated without a restart.
      List<String> safeKeys = Arrays.asList("adaptor.fullListingSchedule");
      // Set of "unsafe" keys that have been modified.
      Set<String> modifiedKeysRequiringRestart
          = new HashSet<String>(modifiedKeys);
      modifiedKeysRequiringRestart.removeAll(safeKeys);
      // If there are modified "unsafe" keys, then we restart things to make
      // sure all the code is up-to-date with the new values.
      if (!modifiedKeysRequiringRestart.isEmpty()) {
        log.warning("Unsafe configuration keys modified. To ensure a sane "
                    + "state, the adaptor is restarting.");
        HttpServer existingServer = scope.getHttpServer();
        HttpServer existingDashboardServer
            = dashboard.getScope().getHttpServer();
        stop(3, TimeUnit.SECONDS);
        try {
          start(existingServer, existingDashboardServer);
        } catch (Exception ex) {
          log.log(Level.SEVERE, "Automatic restart failed", ex);
          throw new RuntimeException(ex);
        }
      }
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
      dashboard.addStatusSource(source);
    }

    @Override
    public void setGetDocIdsFullErrorHandler(ExceptionHandler handler) {
      ((PushRunnable) docIdFullPusher.getRunnable())
          .setGetDocIdsErrorHandler(handler);
    }

    @Override
    public ExceptionHandler getGetDocIdsFullErrorHandler() {
      return ((PushRunnable) docIdFullPusher.getRunnable())
          .getGetDocIdsErrorHandler();
    }

    @Override
    public void setGetDocIdsIncrementalErrorHandler(
        ExceptionHandler handler) {
      ((PushRunnable) docIdFullPusher.getRunnable())
          .setGetDocIdsErrorHandler(handler);
    }

    @Override
    public ExceptionHandler getGetDocIdsIncrementalErrorHandler() {
      return ((PushRunnable) docIdFullPusher.getRunnable())
          .getGetDocIdsErrorHandler();
    }

    @Override
    public SensitiveValueDecoder getSensitiveValueDecoder() {
      return secureValueCodec;
    }

    @Override
    public HttpContext createHttpContext(String path, HttpHandler handler) {
      return addFilters(scope.createContext(path, handler));
    }

    @Override
    public Session getUserSession(HttpExchange ex, boolean create) {
      Session session = sessionManager.getSession(ex, create);
      if (session == null) {
        return null;
      }
      final String wrappedSessionName = "wrapped-session";
      Session nsSession;
      synchronized (session) {
        nsSession = (Session) session.getAttribute(wrappedSessionName);
        if (nsSession == null) {
          nsSession = new NamespacedSession(session, "adaptor-impl-");
          session.setAttribute(wrappedSessionName, nsSession);
        }
      }
      return nsSession;
    }
  }
}
