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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;

import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

/** This class handles the communications with GSA. */
public final class GsaCommunicationHandler {
  private static final Logger log
      = Logger.getLogger(GsaCommunicationHandler.class.getName());

  private final Adaptor adaptor;
  private final Config config;
  private final Journal journal;
  private AdaptorContextImpl adaptorContext;
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
  private OneAtATimeRunnable docIdFullPusher;
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
  private AsyncDocIdSender asyncDocIdSender;
  private HttpServerScope dashboardScope;
  private Dashboard dashboard;
  private SensitiveValueCodec secureValueCodec;
  private KeyPair keyPair;
  private AclTransform aclTransform;

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
   * Start services necessary for handling outgoing requests. {@code ""} is used
   * for {@code contextPrefix} if the passed value is {@code null}.
   */
  public synchronized AdaptorContext setup(HttpServer server,
      HttpServer dashboardServer, String contextPrefix) throws IOException,
      InterruptedException {
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

    boolean secure = server instanceof HttpsServer;
    if (secure != config.isServerSecure()) {
      config.setValue("server.secure", "" + secure);
    }
    keyPair = null;
    try {
      keyPair = getKeyPair(config.getServerKeyAlias());
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
    secureValueCodec = new SensitiveValueCodec(keyPair);

    int port = server.getAddress().getPort();
    if (port != config.getServerPort()) {
        config.setValue("server.port", "" + port);
    }
    int dashboardPort = dashboardServer.getAddress().getPort();
    if (dashboardPort != config.getServerDashboardPort()) {
      config.setValue("server.dashboardPort", "" + dashboardPort);
    }

    scope = new HttpServerScope(server, contextPrefix);
    waiter = new ShutdownWaiter();

    sessionManager = new SessionManager<HttpExchange>(
          new SessionManager.HttpExchangeClientStore("sessid_" + port, secure),
          30 * 60 * 1000 /* session lifetime: 30 minutes */,
          5 * 60 * 1000 /* max cleanup frequency: 5 minutes */);

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
        config.getGsaHostname(), config.isServerSecure(), // use secure bool?
        config.getGsaCharacterEncoding());
    aclTransform = createAclTransform();
    GsaFeedFileMaker fileMaker = new GsaFeedFileMaker(docIdCodec, aclTransform,
        config.isGsa614FeedWorkaroundEnabled(),
        config.isGsa70AuthMethodWorkaroundEnabled(),
        config.isCrawlImmediatelyBitEnabled().isOverriden,
        config.isCrawlImmediatelyBitEnabled().value,
        config.isFeedNoRecrawlBitEnabled().isOverriden,
        config.isFeedNoRecrawlBitEnabled().value);
    GsaFeedFileArchiver fileArchiver =
        new GsaFeedFileArchiver(config.getFeedArchiveDirectory());
    docIdSender = new DocIdSender(fileMaker, fileSender, fileArchiver, journal,
        config, adaptor);
    asyncDocIdSender = new AsyncDocIdSender(docIdSender,
        config.getFeedMaxUrls() /* batch size */,
        5 /* max latency */, TimeUnit.MINUTES,
        2 * config.getFeedMaxUrls() /* queue size */);

    // Could be done during start(), but then we would have to save
    // dashboardServer and contextPrefix.
    dashboardScope = new HttpServerScope(dashboardServer, contextPrefix);

    // We are about to start the Adaptor, so anything available through
    // AdaptorContext or other means must be initialized at this point. Any
    // reference to 'adaptor' before this point must be done very carefully to
    // ensure it doesn't call the adaptor until after Adaptor.init() completes.
    return adaptorContext = new AdaptorContextImpl();
  }

  /**
   * Start servicing incoming requests. This makes use of the
   * previously-provided HttpServers and configuration.
   */
  public synchronized void start() {
    // Since the Adaptor has been started, we can now issue other calls to it.
    // Usages of 'adaptor' are completely safe after this point.
    adaptorContext.freeze();

    // Since we are white-listing particular keys for auto-update, things aren't
    // ready enough to expose to adaptors.
    /*if (adaptor instanceof ConfigModificationListener) {
      config.addConfigModificationListener(
          (ConfigModificationListener) adaptor);
    }*/

    SamlServiceProvider samlServiceProvider = null;
    if (config.isServerSecure()) {
      bootstrapOpenSaml();
      SamlMetadata metadata = new SamlMetadata(config.getServerHostname(),
          config.getServerPort(), config.getGsaHostname(),
          config.getGsaSamlEntityId(), config.getServerSamlEntityId());

      if (adaptorContext.authnAuthority != null) {
        log.config("Adaptor-based authentication supported");
        SamlIdentityProvider samlIdentityProvider = new SamlIdentityProvider(
            adaptorContext.authnAuthority, metadata, keyPair,
            config.getSamlIdpExpirationMillis());
        addFilters(scope.createContext("/samlip",
            samlIdentityProvider.getSingleSignOnHandler()));
      } else {
        log.config("Adaptor-based authentication not supported");
      }
      samlServiceProvider
          = new SamlServiceProvider(sessionManager, metadata, keyPair);
      addFilters(scope.createContext("/samlassertionconsumer",
          samlServiceProvider.getAssertionConsumer()));
      if (adaptorContext.authzAuthority != null) {
        log.config("Adaptor-based authorization supported");
        addFilters(scope.createContext("/saml-authz", new SamlBatchAuthzHandler(
            adaptorContext.authzAuthority, docIdCodec, metadata)));
      } else {
        log.config("Adaptor-based authorization not supported");
      }
    }
    scheduleExecutor = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("schedule")
        .build());
    Watchdog watchdog = new Watchdog(scheduleExecutor);

    // The cachedThreadPool implementation created here is considerably better
    // than using ThreadPoolExecutor. ThreadPoolExecutor does not create threads
    // as would be expected from a thread pool.
    backgroundExecutor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("background")
        .build());
    backgroundExecutor.execute(waiter.runnable(asyncDocIdSender.worker()));
    DocumentHandler docHandler = new DocumentHandler(
        docIdCodec, docIdCodec, journal, adaptor, adaptorContext.authzAuthority,
        config.getGsaHostname(),
        config.getServerFullAccessHosts(),
        samlServiceProvider, createTransformPipeline(), aclTransform,
        config.isServerToUseCompression(), watchdog,
        asyncDocIdSender, 
        config.doesGsaAcceptDocControlsHeader(),
        config.markAllDocsAsPublic(),
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
    docIdFullPusher = new OneAtATimeRunnable(
       new PushRunnable(adaptorContext.fullExceptionHandler),
       new AlreadyRunningRunnable());
    sendDocIdsFuture = scheduler.schedule(
        config.getAdaptorFullListingSchedule(),
        waiter.runnable(new BackgroundRunnable(docIdFullPusher)));
    if (config.isAdaptorPushDocIdsOnStartup()) {
      log.info("Pushing once at program start");
      checkAndScheduleImmediatePushOfDocIds();
    }

    if (adaptorContext.pollingIncrementalLister != null) {
      docIdIncrementalPusher = new OneAtATimeRunnable(
          new IncrementalPushRunnable(adaptorContext.pollingIncrementalLister,
            adaptorContext.incrExceptionHandler),
          new AlreadyRunningRunnable());

      scheduleExecutor.scheduleAtFixedRate(
          waiter.runnable(new BackgroundRunnable(docIdIncrementalPusher)),
          0,
          config.getAdaptorIncrementalPollPeriodMillis(),
          TimeUnit.MILLISECONDS);
    }

    dashboard = new Dashboard(config, this, journal, sessionManager,
        secureValueCodec, adaptor, adaptorContext.statusSources);
    dashboard.start(dashboardScope);
  }
   
  void tryToPutVersionIntoConfig() throws IOException {
    try {
      if ("GENERATE".equals(config.getGsaVersion())) {  // is not set
        GsaVersion ver = GsaVersion.get(config.getGsaHostname(),
            config.isServerSecure());
        config.overrideKey("gsa.version", "" + ver);
      }
    } catch (FileNotFoundException fne) {
      // we're talking to an older GSA that cannot tell us its version.
      log.log(Level.FINE, "gsa didn't provide version", fne);
      config.setValue("gsa.version", "7.0.14-114");
    } catch (IllegalArgumentException iae) {
      // we're talking to a GSA whose version we don't understand 
      log.log(Level.FINE, "gsa provided incomprehensible version", iae);
      config.setValue("gsa.version", "7.0.14-114");
    } catch (IOException ioe) {
      throw handleGsaException(config.getGsaHostname(), ioe);
    }
  }

  private TransformPipeline createTransformPipeline() {
    return createTransformPipeline(config.getTransformPipelineSpec());
  }

  @VisibleForTesting
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

  private AclTransform createAclTransform() {
    return createAclTransform(config.getValuesWithPrefix("transform.acl."));
  }

  @VisibleForTesting
  static AclTransform createAclTransform(Map<String, String> aclConfigRaw) {
    Map<Integer, String> aclConfig = new TreeMap<Integer, String>();
    for (Map.Entry<String, String> me : aclConfigRaw.entrySet()) {
      try {
        aclConfig.put(Integer.parseInt(me.getKey()), me.getValue());
      } catch (NumberFormatException ex) {
        // Don't insert into map.
        log.log(Level.FINE, "Ignorning transform.acl.{0} because {0} is not an "
            + "integer", me.getKey());
      }
    }

    List<AclTransform.Rule> rules = new LinkedList<AclTransform.Rule>();
    for (String value : aclConfig.values()) {
      String[] parts = value.split(";", 2);
      if (parts.length != 2) {
        log.log(Level.WARNING, "Could not find semicolon in acl transform: {0}",
            value);
        continue;
      }
      AclTransform.MatchData search = parseAclTransformMatchData(parts[0]);
      AclTransform.MatchData replace = parseAclTransformMatchData(parts[1]);
      if (search == null || replace == null) {
        log.log(Level.WARNING,
            "Could not parse acl transform rule: {0}", value);
        continue;
      }
      if (replace.isGroup != null) {
        log.log(Level.WARNING,
            "Replacement cannot change type. Failed in rule: {0}", value);
        continue;
      }
      rules.add(new AclTransform.Rule(search, replace));
    }
    return new AclTransform(rules);
  }

  private static AclTransform.MatchData parseAclTransformMatchData(String s) {
    String[] decls = s.split(",", -1);
    if (decls.length == 1 && decls[0].trim().equals("")) {
      // No declarations are required
      return new AclTransform.MatchData(null, null, null, null);
    }
    Boolean isGroup = null;
    String name = null;
    String domain = null;
    String namespace = null;
    for (String decl : decls) {
      String parts[] = decl.split("=", 2);
      if (parts.length != 2) {
        log.log(Level.WARNING,
            "Could not find \"=\" in \"{0}\" as part of \"{1}\"",
            new Object[] {decl, s});
        return null;
      }
      String key = parts[0].trim();
      String value = parts[1];
      if (key.equals("type")) {
        if (value.equals("group")) {
          isGroup = true;
        } else if (value.equals("user")) {
          isGroup = false;
        } else {
          log.log(Level.WARNING, "Unknown type \"{0}\" as part of \"{1}\"",
              new Object[] {value, s});
          return null;
        }
      } else if (key.equals("name")) {
        name = value;
      } else if (key.equals("domain")) {
        domain = value;
      } else if (key.equals("namespace")) {
        namespace = value;
      } else {
        log.log(Level.WARNING, "Unknown key \"{0}\" as part of \"{1}\"",
            new Object[] {key, s});
        return null;
      }
    }
    return new AclTransform.MatchData(isGroup, name, domain, namespace);
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
   * Stops servicing incoming requests, allowing up to {@code maxDelay} seconds
   * for things to shutdown. After called, no requests will be sent to the
   * Adaptor.
   *
   * @return {@code true} if shutdown cleanly, {@code false} if requests may
   *     still be processing
   */
  public synchronized boolean stop(long time, TimeUnit unit) {
    boolean clean = true;
    if (adaptorContext != null) {
      adaptorContext.freeze();
    }
    if (scope != null) {
      scope.close();
      scope = new HttpServerScope(
          scope.getHttpServer(), scope.getContextPrefix());
    }
    if (scheduleExecutor != null) {
      // Post-Adaptor.init() resources need to be stopped.
      dashboardScope.close();
      dashboardScope = new HttpServerScope(
          dashboardScope.getHttpServer(), dashboardScope.getContextPrefix());

      scheduleExecutor.shutdownNow();
      scheduleExecutor = null;

      backgroundExecutor.shutdownNow();
      backgroundExecutor = null;

      scheduler = null;
      sendDocIdsFuture = null;

      docIdIncrementalPusher = null;

      dashboard.stop();
      dashboard = null;

      // Clear references set by Adaptor via AdaptorContext.
      docIdFullPusher = null;
      docIdIncrementalPusher = null;

      try {
        clean = clean & waiter.shutdown(time, unit);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        clean = false;
      }
      waiter = new ShutdownWaiter();
    }
    return clean;
  }

  /**
   * Stop services necessary for handling outgoing requests. This call
   * invalidates the {@link AdaptorContext} returned from {@link #setup}.
   */
  public synchronized void teardown() {
    scope = null;
    dashboardScope = null;
    keyPair = null;
    aclTransform = null;
    waiter = null;

    // Wait until after adaptor.destroy() to shutdown things accessible by
    // AdaptorContext, so that the AdaptorContext is usable until the very
    // end.
    secureValueCodec = null;
    sessionManager = null;
    docIdCodec = null;
    docIdSender = null;
    adaptorContext = null;
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
   * support incremental polling (implements {@link PollingIncrementalLister}.
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

  boolean isAdaptorIncremental() {
    if (adaptorContext == null || adaptorContext.mutable) {
      throw new IllegalStateException("Can only be used after init()");
    }
    return adaptorContext.pollingIncrementalLister != null;
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

  synchronized void rescheduleFullListing(String schedule) {
    if (sendDocIdsFuture == null) {
      return;
    }
    try {
      scheduler.reschedule(sendDocIdsFuture, schedule);
    } catch (IllegalArgumentException ex) {
      log.log(Level.WARNING, "Invalid schedule pattern", ex);
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

  /** Wrap certain GSA communication problems with more descriptive messages. */
  static IOException handleGsaException(String gsa, IOException e) {
    if (e instanceof ConnectException) {
      return new IOException("Failed to connect to the GSA at " + gsa + " . "
          + "Please verify that the gsa.hostname configuration property "
          + "is correct and the GSA is online, and is configured to accept "
          + "feeds from this computer.", e);
    } else if (e instanceof UnknownHostException) {
      return new IOException("Failed to locate the GSA at " + gsa + " . "
          + "Please verify that the gsa.hostname configuration property "
          + "is correct.", e);
    } else if (e instanceof SSLException) {
      return new IOException("Failed to connect to the GSA at " + gsa + " . "
          + "Please verify that the your SSL Certificates are properly "
          + "configured for secure communication with the GSA.", e);
    } else {
      return e;
    }
  }

  /**
   * Runnable that calls {@link DocIdSender#pushDocIds}.
   */
  private class PushRunnable implements Runnable {
    private final ExceptionHandler handler;

    public PushRunnable(ExceptionHandler handler) {
      this.handler = handler;
    }

    @Override
    public void run() {
      try {
        docIdSender.pushFullDocIdsFromAdaptor(handler);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Runnable that performs incremental feed push.
   */
  private class IncrementalPushRunnable implements Runnable {
    private final ExceptionHandler handler;
    private final PollingIncrementalLister incrementalLister;

    public IncrementalPushRunnable(PollingIncrementalLister incrementalLister,
        ExceptionHandler handler) {
      this.incrementalLister = incrementalLister;
      this.handler = handler;
    }

    @Override
    public void run() {
      try {
        docIdSender.pushIncrementalDocIdsFromAdaptor(
            incrementalLister, handler);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        log.log(Level.WARNING, "Exception during incremental polling", ex);
      }
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

  /**
   * This class is thread-safe.
   */
  private class AdaptorContextImpl implements AdaptorContext {
    private boolean mutable = true;
    private ExceptionHandler fullExceptionHandler
        = ExceptionHandlers.defaultHandler();
    private ExceptionHandler incrExceptionHandler
        = ExceptionHandlers.defaultHandler();
    private final List<StatusSource> statusSources
        = new ArrayList<StatusSource>();
    private PollingIncrementalLister pollingIncrementalLister;
    private AuthnAuthority authnAuthority;
    private AuthzAuthority authzAuthority;

    private synchronized void freeze() {
      mutable = false;
    }

    @Override
    public Config getConfig() {
      return config;
    }

    @Override
    public DocIdPusher getDocIdPusher() {
      return docIdSender;
    }

    @Override
    public AsyncDocIdPusher getAsyncDocIdPusher() {
      return asyncDocIdSender;
    }

    @Override
    public DocIdEncoder getDocIdEncoder() {
      return docIdCodec;
    }

    @Override
    public synchronized void addStatusSource(StatusSource source) {
      if (!mutable) {
        throw new IllegalStateException("After init()");
      }
      statusSources.add(source);
    }

    @Override
    public synchronized void setGetDocIdsFullErrorHandler(
        ExceptionHandler handler) {
      if (!mutable) {
        throw new IllegalStateException("After init()");
      }
      if (handler == null) {
        throw new NullPointerException();
      }
      fullExceptionHandler = handler;
    }

    @Override
    public synchronized ExceptionHandler getGetDocIdsFullErrorHandler() {
      return fullExceptionHandler;
    }

    @Override
    public synchronized void setGetDocIdsIncrementalErrorHandler(
        ExceptionHandler handler) {
      if (!mutable) {
        throw new IllegalStateException("After init()");
      }
      if (handler == null) {
        throw new NullPointerException();
      }
      incrExceptionHandler = handler;
    }

    @Override
    public synchronized ExceptionHandler getGetDocIdsIncrementalErrorHandler() {
      return incrExceptionHandler;
    }

    @Override
    public SensitiveValueDecoder getSensitiveValueDecoder() {
      return secureValueCodec;
    }

    @Override
    public synchronized HttpContext createHttpContext(String path,
        HttpHandler handler) {
      if (!mutable) {
        throw new IllegalStateException("After init()");
      }
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

    @Override
    public synchronized void setPollingIncrementalLister(
        PollingIncrementalLister lister) {
      if (!mutable) {
        throw new IllegalStateException("After init()");
      }
      pollingIncrementalLister = lister;
    }

    @Override
    public synchronized void setAuthnAuthority(AuthnAuthority authnAuthority) {
      if (!mutable) {
        throw new IllegalStateException("After init()");
      }
      this.authnAuthority = authnAuthority;
    }

    @Override
    public synchronized void setAuthzAuthority(AuthzAuthority authzAuthority) {
      if (!mutable) {
        throw new IllegalStateException("After init()");
      }
      this.authzAuthority = authzAuthority;
    }
  }
}
