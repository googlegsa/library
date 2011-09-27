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

import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
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
  private final Adaptor.DocIdPusher pusher = new InnerDocIdPusher();
  private final Adaptor.GetDocIdsErrorHandler defaultErrorHandler
      = new DefaultGetDocIdsErrorHandler();
  private Scheduler pushScheduler;
  private int pushingDocIds;
  private HttpServer server;
  private CircularLogRpcMethod circularLogRpcMethod;
  private Thread shutdownHook;

  public GsaCommunicationHandler(Adaptor adaptor, Config config) {
    // TODO(ejona): allow the adaptor to choose whether it wants this feature
    this.adaptor = new AutoUnzipAdaptor(adaptor);
    this.adaptor.setDocIdPusher(pusher);

    this.config = config;
    this.fileSender = new GsaFeedFileSender(config.getGsaCharacterEncoding(),
                                            config.isServerSecure());
    this.fileMaker = new GsaFeedFileMaker(this);
  }

  /** Starts listening for communications from GSA. */
  public synchronized void beginListeningForContentRequests()
      throws IOException {
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

    SessionManager<HttpExchange> sessionManager
        = new SessionManager<HttpExchange>(
          new SessionManager.HttpExchangeCookieAccess(),
          30 * 60 * 1000 /* session lifetime: 30 minutes */,
          5 * 60 * 1000 /* max cleanup frequency: 5 minutes */);
    AuthnHandler authnHandler = null;
    if (secure) {
      try {
        DefaultBootstrap.bootstrap();
      } catch (ConfigurationException ex) {
        throw new RuntimeException(ex);
      }
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
                            getJournal(), adaptor,
                            config.getServerAddResolvedGsaHostnameToGsaIps(),
                            config.getGsaHostname(), config.getServerGsaIps(),
                            authnHandler, sessionManager));

    server.createContext("/dashboard",
        createAdminSecurityHandler(new DashboardHandler(config, journal),
                                       config, sessionManager, secure));
    server.createContext("/rpc",
        createAdminSecurityHandler(createRpcHandler(), config,
                                       sessionManager, secure));
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    log.info("GSA host name: " + config.getGsaHostname());
    log.info("server is listening on port #" + port);
    shutdownHook = new Thread(new ShutdownHook(), "gsacomm-shutdown");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
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
    if (pushScheduler != null) {
      pushScheduler.cancel();
      pushScheduler = null;
    }
    if (server != null) {
      server.stop(maxDelay);
      server = null;
    }
  }

  private AdministratorSecurityHandler createAdminSecurityHandler(
      HttpHandler handler, Config config,
      SessionManager<HttpExchange> sessionManager, boolean secure) {
    return new AdministratorSecurityHandler(config.getServerHostname(),
        config.getGsaCharacterEncoding(), handler, sessionManager,
        config.getGsaHostname(), secure);
  }

  private synchronized RpcHandler createRpcHandler() {
    RpcHandler rpcHandler = new RpcHandler(config.getServerHostname(),
        config.getGsaCharacterEncoding(), this);
    rpcHandler.registerRpcMethod("startFeedPush", new StartFeedPushRpcMethod());
    circularLogRpcMethod = new CircularLogRpcMethod();
    rpcHandler.registerRpcMethod("getLog", circularLogRpcMethod);
    rpcHandler.registerRpcMethod("getConfig", new ConfigRpcMethod(config));
    rpcHandler.registerRpcMethod("getStats", new StatRpcMethod(journal));
    return rpcHandler;
  }

  /**
   * Schedule {@link Adaptor#getDocIds} to be called when defined by the {@code
   * schedule}. Equivalent to {@code beginPushingDocIds(schedule, null)}.
   *
   * @see #beginPushingDocIds(Iterator, Adaptor.GetDocIdsErrorHandler)
   */
  public void beginPushingDocIds(Iterator<Date> schedule) {
    beginPushingDocIds(schedule, null);
  }

  /**
   * Schedule {@link Adaptor#getDocIds} to be called when defined by the {@code
   * schedule}. If {@code handler} is {@code null}, then a default error handler
   * will be used.
   */
  public void beginPushingDocIds(Iterator<Date> schedule,
                                 Adaptor.GetDocIdsErrorHandler handler) {
    getPushScheduler().schedule(new PushTask(handler), schedule);
  }

  /**
   * Ensure there is a push running right now. This schedules a new push if one
   * is not already running. Returns {@code true} if it starts a new push, and
   * false otherwise.
   */
  synchronized boolean checkAndBeginPushDocIdsImmediately(
      Adaptor.GetDocIdsErrorHandler handler) {
    if (pushingDocIds > 0) {
      return false;
    }
    beginPushingDocIdsImmediately(handler);
    return true;
  }

  /**
   * Schedule a push for immediately. If there is a push already running this
   * push will be started after it.
   */
  private void beginPushingDocIdsImmediately(
      Adaptor.GetDocIdsErrorHandler handler) {
    List<Date> schedule = Collections.singletonList(new Date());
    getPushScheduler().schedule(new PushTask(handler), schedule.iterator());
  }

  private DocInfo pushSizedBatchOfDocInfos(List<DocInfo> docInfos,
                                           Adaptor.PushErrorHandler handler)
      throws InterruptedException {
    String feedSourceName = config.getFeedName();
    String xmlFeedFile = fileMaker.makeMetadataAndUrlXml(
        feedSourceName, docInfos);
    boolean keepGoing = true;
    boolean success = false;
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
    return success ? null : docInfos.get(0);
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. This method blocks
   * until all DocIds are sent or retrying failed. Equivalent to {@code
   * pushDocIds(null)}.
   */
  public void pushDocIds() throws InterruptedException {
    pushDocIds(null);
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. This method blocks
   * until all DocIds are sent or retrying failed. If {@code handler} is {@code
   * null}, then a default error handler is used.
   */
  public void pushDocIds(Adaptor.GetDocIdsErrorHandler handler)
      throws InterruptedException {
    synchronized (this) {
      pushingDocIds++;
    }
    if (handler == null) {
      handler = defaultErrorHandler;
    }
    log.info("Getting list of DocIds");
    for (int ntries = 1;; ntries++) {
      boolean keepGoing = true;
      try {
        adaptor.getDocIds(pusher);
        break; // Success
      } catch (Exception ex) {
        log.log(Level.WARNING, "Unable to retrieve DocIds from adaptor", ex);
        keepGoing = handler.handleFailedToGetDocIds(ex, ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      } else {
        return; // Bail
      }
    }
    synchronized (this) {
      pushingDocIds--;
    }
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. Generally, you should
   * use {@link #pushDocIds()} instead of this method. However, if you want to
   * push just a few DocIds to the GSA manually, this is the method to use.
   * This method blocks until all DocIds are sent or retrying failed.
   */
  private DocInfo pushDocInfos(Iterator<DocInfo> docInfos,
                               Adaptor.PushErrorHandler handler)
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

  URI formNamespacedUri(String namespace) {
    URI uri;
    try {
      uri = new URI(null, null, config.getServerBaseUri().getPath() + namespace,
                    null);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
    return config.getServerBaseUri().resolve(uri);
  }

  Journal getJournal() {
    return journal;
  }

  private Scheduler getPushScheduler() {
    if (pushScheduler == null) {
      pushScheduler = new Scheduler();
    }
    return pushScheduler;
  }

  private class InnerDocIdPusher extends AbstractDocIdPusher {
    private Adaptor.PushErrorHandler defaultErrorHandler
        = new DefaultPushErrorHandler();

    @Override
    public DocInfo pushDocInfos(Iterable<DocInfo> docInfos,
                                Adaptor.PushErrorHandler handler)
        throws InterruptedException {
      if (handler == null) {
        handler = defaultErrorHandler;
      }
      return GsaCommunicationHandler.this.pushDocInfos(docInfos.iterator(),
                                                       handler);
    }
  }

  private class PushTask extends Scheduler.Task {
    private final Adaptor.GetDocIdsErrorHandler handler;

    public PushTask(Adaptor.GetDocIdsErrorHandler handler) {
      this.handler = handler;
    }

    public void run() {
      try {
        pushDocIds(handler);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private class ShutdownHook implements Runnable {
    @Override
    public void run() {
      // Allow three seconds for things to stop.
      stop(3);
    }
  }

  class StartFeedPushRpcMethod implements RpcHandler.RpcMethod {
    @Override
    public Object run(List request) {
      boolean pushStarted = checkAndBeginPushDocIdsImmediately(null);
      if (!pushStarted) {
        throw new RuntimeException("A push is already in progress");
      }
      return 1;
    }
  }

  static class CircularLogRpcMethod implements RpcHandler.RpcMethod, Closeable {
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

    public Object run(List request) {
      TreeMap<String, String> configMap = new TreeMap<String, String>();
      for (String key : config.getAllKeys()) {
        configMap.put(key, config.getValue(key));
      }
      return configMap;
    }
  }
}
