// Copyright 2013 Google Inc. All Rights Reserved.
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

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Provides framework for adaptors to act as a stand-alone application. This
 * entails creating well-configured HttpServer instances, argument parsing,
 * shutdown handling, and more.
 */
public final class Application {
  private static final String SLEEP_PATH = "/sleep";
  static final String DEFAULT_CONFIG_FILE
      = "adaptor-config.properties";

  private static final Logger log
      = Logger.getLogger(Application.class.getName());

  private final Config config;
  private final GsaCommunicationHandler gsa;
  /**
   * An "inverted" semaphore that has permits available when stop() is running;
   * at all other times it has no permits. This allows start() to sleep on the
   * semaphore and be woken when stop() is called.
   */
  private final Semaphore shutdownSemaphore = new Semaphore(0);
  private Thread shutdownHook;
  private HttpServer primaryServer;
  private HttpServer dashboardServer;

  public Application(Adaptor adaptor, Config config) {
    this.config = config;

    gsa = new GsaCommunicationHandler(adaptor, config);
  }

  /**
   * Start necessary services for receiving requests and managing background
   * tasks. Non-daemon threads are created, so call {@link #stop} for graceful
   * manual shutdown. A shutdown hook is automatically installed that calls
   * {@code stop()}.
   * @throws IOException if failed to start
   * @throws InterruptedException if interrupted
   */
  public synchronized void start() throws IOException, InterruptedException {
    synchronized (this) {
      daemonInit();

      // The shutdown hook is purposefully not part of the deamon methods,
      // because it should only be done when running from the command line.
      shutdownHook = new Thread(new ShutdownHook(), "gsacomm-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    boolean success = false;
    try {
      daemonStart();
      success = true;
    } finally {
      log.finest("start is leaving with success=" + success);
      if (!success) {
        // Call daemonDestroy() and remove shutdown hook.
        stop(0, TimeUnit.SECONDS);
      }
    }
  }

  /**
   * Reserves resources for later use. This may be run with different
   * permissions (like as root), to reserve ports or other things that need
   * elevated privileges.
   */
  synchronized void daemonInit() throws IOException {
    if (primaryServer != null) {
      throw new IllegalStateException("Already started");
    }
    boolean success = false;
    try {
      primaryServer = createHttpServer(config);
      dashboardServer = createDashboardHttpServer(config);
      // Because once stopped the server can't be reused, we can't reuse its
      // bind()ed socket if we stop it. So although ideally we would start/stop
      // in daemonStart/daemonStop, we instead must do it in
      // daemonInit/daemonDestroy.
      primaryServer.start();
      dashboardServer.start();
      success = true;
    } finally {
      log.finest("daemonInit is leaving with success=" + success);
      if (!success) {
        daemonDestroy(0, TimeUnit.SECONDS);
      }
    }
  }

  /**
   * Really start. This must be called after a successful {@link #daemonInit}.
   */
  synchronized void daemonStart() throws IOException, InterruptedException {
    boolean success = false;
    try {
      realDaemonStart();
      log.info("doc content serving started");
      success = true;
    } finally {
      log.finest("daemonStart is leaving with success=" + success);
      if (!success) {
        daemonStop(0, TimeUnit.SECONDS);
      }
    }
  }

  private synchronized void realDaemonStart() throws IOException,
        InterruptedException {
    AdaptorContext context = gsa.setup(primaryServer, dashboardServer, null);

    long sleepDurationMillis = 8000;
    // An hour.
    long maxSleepDurationMillis = 60 * 60 * 1000;
    // Loop until 1) the adaptor starts successfully, 2) an unrecoverable
    // StartupException is thrown, 3) stop() is called, or 4) Thread.interrupt()
    // is called on this thread (which we don't do).
    // Retrying to start the adaptor is helpful in cases where it needs
    // initialization data from a repository that is temporarily down; if the
    // adaptor is running as a service, we don't want to stop starting simply
    // because another computer is down while we start (which would easily be
    // the case after a power failure).
    while (true) {
      try {
        gsa.tryToPutVersionIntoConfig();
        String adaptorType = gsa.getAdaptor().getClass().getName();
        log.log(Level.INFO, "about to init {0}", adaptorType); 
        gsa.getAdaptor().init(context);
        break;
      } catch (InterruptedException ex) {
        throw ex;
      } catch (StartupException ex) {
        throw ex;
      } catch (Exception ex) {
        log.log(Level.WARNING, "Failed to initialize adaptor", ex);
        if (shutdownSemaphore.tryAcquire(sleepDurationMillis,
              TimeUnit.MILLISECONDS)) {
          shutdownSemaphore.release();
          // Shutdown initiated.
          return;
        }
        sleepDurationMillis
            = Math.min(sleepDurationMillis * 2, maxSleepDurationMillis);
      }
    }

    gsa.start(new ShutdownHook());
  }

  /**
   * Stop processing incoming requests and background tasks, allowing graceful
   * shutdown.
   * @param time max count given for stop to complete
   * @param unit of measure for the countdown
   */
  public void stop(long time, TimeUnit unit) {
    daemonStop(time, unit);
    daemonDestroy(time, unit);
    synchronized (this) {
      if (shutdownHook != null) {
        try {
          Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ex) {
          // Already executing hook.
        }
        shutdownHook = null;
      }
    }
  }

  /**
   * Stop all the services we provide. This is the opposite of {@link
   * #daemonStart}.
   */
  void daemonStop(long time, TimeUnit unit) {
    shutdownSemaphore.release();
    try {
      synchronized (this) {
        if (primaryServer == null) {
          return;  // Already stopped.
        }
        gsa.stop(time, unit);
        try {
          gsa.getAdaptor().destroy();
        } finally {
          gsa.teardown();
        }
      }
    } finally {
      boolean interrupted = false;
      while (true) {
        try {
          shutdownSemaphore.acquire();
          break;
        } catch (InterruptedException ex) {
          interrupted = true;
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Release reserved resources. This is the opposite of {@link
   * #daemonInit}.
   */
  synchronized void daemonDestroy(long time, TimeUnit unit) {
    if (primaryServer != null) {
      httpServerShutdown(primaryServer, time, unit);
      log.finer("Completed primary server stop");
      primaryServer = null;
    }

    if (dashboardServer != null) {
      httpServerShutdown(dashboardServer, time, unit);
      log.finer("Completed dashboard stop");
      dashboardServer = null;
    }
  }

  static void httpServerShutdown(HttpServer server, long time,
      TimeUnit unit) {
    // Workaround Java Bug 7105369.
    SleepHandler sleepHandler = new SleepHandler(100 /* millis */);
    server.createContext(SLEEP_PATH, sleepHandler);
    issueSleepGetRequest(server.getAddress(), server instanceof HttpsServer);

    server.stop((int) unit.toSeconds(time));
    ((ExecutorService) server.getExecutor()).shutdownNow();
  }

  /**
   * Issues a GET request to a SleepHandler. This is used to workaround Java
   * Bug 7105369.
   *
   * <p>The bug is an issue with HttpServer where stop() waits the full amount
   * of allotted time if the serve is idle. However, if a request is being
   * handled when stop() is called, then it will return as soon as all requests
   * are processed, or the allotted time is reached.
   *
   * <p>Thus, this workaround tries to force a request to be in-procees when
   * stop() is called, so that it can return sooner. We issue a request to a
   * SleepHandler that takes a fixed amount of time to process the request
   * before calling stop(). In the event everything goes as planned, the request
   * completes after stop() has been called and allows stop() to exit quickly.
   */
  private static void issueSleepGetRequest(InetSocketAddress address,
      boolean isHttps) {
    URL url;
    try {
      url = new URL(isHttps ? "https" : "http",
          address.getAddress().getHostAddress(), address.getPort(), SLEEP_PATH);
    } catch (MalformedURLException ex) {
      log.log(Level.WARNING,
          "Unexpected error. Shutting down will be slow.", ex);
      return;
    }

    final URLConnection conn;
    try {
      conn = url.openConnection();
      conn.connect();
    } catch (ConnectException ce) {
      // We will get a Connection Refused exception if we try to shut down
      // the HttpServer before it has completed its startup. This typically
      // happens if the overall application startup fails for some reason.
      return;
    } catch (IOException ex) {
      log.log(Level.WARNING, "Error performing shutdown GET", ex);
      return;
    }
    try {
      // Provide some time for the connect() to be processed on the server.
      Thread.sleep(15);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          conn.getInputStream().close();
          log.finer("Closed shutdown GET");
        } catch (ConnectException ce) {
          // We will get a Connection Refused exception if we try to shut down
          // the HttpServer before it has completed its startup. This typically
          // happens if the overall application startup fails for some reason.
          // Why does it happen here and not in connect?
        } catch (IOException ex) {
          log.log(Level.WARNING, "Error closing stream of shutdown GET", ex);
        }
      }
    }).start();
  }

  static HttpServer createHttpServer(Config config) throws IOException {
    HttpServer server;
    if (!config.isServerSecure()) {
      server = HttpServer.create();
    } else {
      server = HttpsServer.create();
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

    int maxThreads = config.getServerMaxWorkerThreads();
    int queueCapacity = config.getServerQueueCapacity();
    BlockingQueue<Runnable> blockingQueue
        = new ArrayBlockingQueue<Runnable>(queueCapacity);
    // The Executor can't reject jobs directly, because HttpServer does not
    // appear to handle that case.
    RejectedExecutionHandler policy
        = new SuggestHandlerAbortPolicy(HttpExchanges.abortImmediately);
    Executor executor = new ThreadPoolExecutor(maxThreads, maxThreads,
        1, TimeUnit.MINUTES, blockingQueue, policy);
    server.setExecutor(executor);

    try {
      server.bind(new InetSocketAddress(config.getServerPort()), 0);
    } catch (BindException ex) {
      log.log(Level.WARNING,
          "Server port {0,number,#} is in use.", config.getServerPort());
      throw ex;
    }
    log.info("GSA host name: " + config.getGsaHostname());
    log.info("server is listening on port #" + server.getAddress().getPort());
    return server;
  }

  static HttpServer createDashboardHttpServer(Config config)
      throws IOException {
    boolean secure = config.isServerSecure();
    HttpServer server;
    if (!secure) {
      server = HttpServer.create();
    } else {
      server = HttpsServer.create();
      SSLContext defaultSslContext;
      try {
        defaultSslContext = SSLContext.getDefault();
      } catch (NoSuchAlgorithmException ex) {
        throw new RuntimeException(ex);
      }
      HttpsConfigurator httpsConf = new HttpsConfigurator(defaultSslContext);
      ((HttpsServer) server).setHttpsConfigurator(httpsConf);
    }
    // The Dashboard is on a separate port to prevent malicious HTML documents
    // in the user's repository from performing admin actions with
    // XMLHttpRequest or the like, as the HTML page will then be blocked by
    // same-origin policies.
    try {
      server.bind(new InetSocketAddress(config.getServerDashboardPort()), 0);
    } catch (BindException ex) {
      log.log(Level.WARNING,
          "Server dashboard port {0,number,#} is in use.", 
          config.getServerDashboardPort());
      throw ex;
    }

    // Use separate Executor for Dashboard to allow the administrator to
    // investigate why things are going wrong without waiting on the normal work
    // queue.
    int maxThreads = 4;
    Executor executor = new ThreadPoolExecutor(maxThreads, maxThreads,
        10, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
    server.setExecutor(executor);

    log.info("dashboard is listening on port #"
        + server.getAddress().getPort());

    return server;
  }

  /** @return the {@link GsaCommunicationHandler} used by this instance. */
  public GsaCommunicationHandler getGsaCommunicationHandler() {
    return gsa;
  }

  /** @return the {@link Config} used by this instance */
  public Config getConfig() {
    return config;
  }

  /**
   * Load default configuration file and parse command line options.
   *
   * @return unused command line arguments
   * @throws IllegalStateException when not all configuration keys have values
   */
  static String[] autoConfig(Config config, String[] args, File configFile) {
    File sysPropertiesAdditions = null;
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
      if ("adaptor.configfile".equals(parts[0])) {
        configFile = new File(parts[1]);
      } else if ("sys.properties.file".equals(parts[0])) {
        sysPropertiesAdditions = new File(parts[1]);
      } else {
        config.setValue(parts[0], parts[1]);
      }
    }
    processSystemProperties(sysPropertiesAdditions);
    loadConfigFile(config, configFile);
    config.validate();
    if (i == 0) {
      return args;
    } else {
      return Arrays.copyOfRange(args, i, args.length);
    }
  }

  private static void processSystemProperties(File extraProps) {
    if (null == extraProps) {
      log.log(Level.CONFIG, "no extra system properties to load");
      return;
    }
    try {
      InputStreamReader extras = new InputStreamReader(
          new BufferedInputStream(new FileInputStream(extraProps)),
          Charset.forName("UTF-8"));
      processSystemProperties(extras);
      extras.close();
    } catch (IOException e) {
      log.log(Level.WARNING, "could not read system properties file "
          + extraProps.getAbsolutePath(), e);
    }
  }

  private static void processSystemProperties(InputStreamReader extraProps) 
      throws IOException {
    Properties extra = new Properties();
    extra.load(extraProps);
    for (Entry<Object, Object> e : extra.entrySet()) {
      String previous = System.setProperty("" + e.getKey(), "" + e.getValue());
      log.log(Level.FINE, "sys property {0} set to {1}; was {2}",
          new Object[] {e.getKey(), e.getValue(), previous}); 
    }
  }

  /**
   * Loads the provided config file, if it exists. It squelches any errors so
   * that you are free to call it without error handling, since this is
   * typically non-fatal.
   */
  private static void loadConfigFile(Config config, File configFile) {
    if (null != configFile && configFile.exists() && configFile.isFile()) {
      try {
        config.load(configFile);
      } catch (IOException ex) {
        log.log(Level.WARNING, "could not read configuration properties file "
            + configFile, ex);
      }
    }
  }

  /**
   * Main for adaptors to utilize when wanting to act as an application. This
   * method primarily parses arguments and creates an application instance
   * before calling it's {@link #start}.
   *
   * @param adaptor to be initialized
   * @param args command line params passed to config
   * @return the application instance in use
   */
  public static Application main(Adaptor adaptor, String[] args) {
    // Setup providing content.
    try {
      Application app = daemonMain(adaptor, args);
      app.start();
      return app;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.log(Level.INFO, "Application startup interrupted.", e);
      throw new StartupException(
          "Application startup interrupted. " + e.toString());
    } catch (IOException e) {
      log.log(Level.SEVERE, "Failed to start application.", e);
      throw new StartupException(
          "Failed to start application. " + e.toString());
    } catch (RuntimeException e) {
      log.log(Level.SEVERE, "Failed to start application.", e);
      throw new StartupException(
          "Failed to start application. " + e.toString());
    }
  }

  /**
   * Performs basic bootstrapping like normal {@link #main}, but does not start
   * the application.
   */
  static Application daemonMain(Adaptor adaptor, String[] args) {
    log.info(productVersion(adaptor.getClass()));
    log.info(productVersion(Application.class));
    log.info(new Dashboard.JavaVersionStatusSource().retrieveStatus()
        .getMessage(Locale.ENGLISH));

    Config config = new Config();
    adaptor.initConfig(config);
    autoConfig(config, args, new File(DEFAULT_CONFIG_FILE));
    return new Application(adaptor, config);
  }

  static String productVersion(Class <?> clazz) {
    String title = null;
    String version = null;
    String vendor = null;
    Package pkg = clazz.getPackage();
    if (pkg != null) {
      title = pkg.getImplementationTitle();
      version = pkg.getImplementationVersion();
      vendor = pkg.getImplementationVendor();
    }
    StringBuilder builder = new StringBuilder("Product Version: ");
    builder.append((title != null) ? title : clazz.getSimpleName());
    builder.append("  ");
    builder.append((version != null) ? version : "(unknown version)");
    if (vendor != null) {
      builder.append(",  ").append(vendor);
    }
    return builder.toString();
  }

  private class ShutdownHook implements Runnable {
    @Override
    public void run() {
      stop(3, TimeUnit.SECONDS);
    }
  }

  /**
   * Executes Runnable in current thread, but only after setting a thread-local
   * object. The code that will be run, is expected to take notice of the set
   * variable and abort immediately. This is a hack.
   */
  private static class SuggestHandlerAbortPolicy
      implements RejectedExecutionHandler {
    private final ThreadLocal<Object> abortImmediately;
    private final Object signal = new Object();

    public SuggestHandlerAbortPolicy(ThreadLocal<Object> abortImmediately) {
      this.abortImmediately = abortImmediately;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      abortImmediately.set(signal);
      try {
        r.run();
      } finally {
        abortImmediately.set(null);
      }
    }
  }
}
