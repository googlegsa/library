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

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Central creation of objects necessary for the dashboard.
 */
class Dashboard {
  private static final Logger log
      = Logger.getLogger(Dashboard.class.getName());

  private final Config config;
  private final Journal journal;
  private final CircularLogRpcMethod circularLogRpcMethod
      = new CircularLogRpcMethod();
  private final GsaCommunicationHandler gsaCommHandler;
  private final SessionManager<HttpExchange> sessionManager;
  private final RpcHandler rpcHandler;
  private final StatRpcMethod statRpcMethod;

  public Dashboard(Config config, GsaCommunicationHandler gsaCommHandler,
                   Journal journal, SessionManager<HttpExchange> sessionManager,
                   SensitiveValueCodec secureValueCodec, Adaptor adaptor,
                   List<StatusSource> adaptorSources) {
    this.config = config;
    this.gsaCommHandler = gsaCommHandler;
    this.journal = journal;
    this.sessionManager = sessionManager;

    List<StatusSource> sources = new LinkedList<StatusSource>();
    sources.add(new LastPushStatusSource(journal));
    sources.add(new RetrieverStatusSource(journal));
    sources.add(new GsaCrawlingStatusSource(journal));
    sources.addAll(adaptorSources);

    rpcHandler = new RpcHandler(sessionManager);
    rpcHandler.registerRpcMethod("startFeedPush", new StartFeedPushRpcMethod());
    rpcHandler.registerRpcMethod("startIncrementalFeedPush",
        new StartIncrementalFeedPushRpcMethod());
    rpcHandler.registerRpcMethod("getLog", circularLogRpcMethod);
    rpcHandler.registerRpcMethod("getConfig", new ConfigRpcMethod(config));
    rpcHandler.registerRpcMethod("getStatuses", new StatusRpcMethod(sources));
    rpcHandler.registerRpcMethod("checkForUpdatedConfig",
        new CheckForUpdatedConfigRpcMethod(gsaCommHandler));
    rpcHandler.registerRpcMethod("encodeSensitiveValue",
        new EncodeSensitiveValueMethod(secureValueCodec));
    statRpcMethod = new StatRpcMethod(journal, adaptor,
        gsaCommHandler.isAdaptorIncremental(), config.getConfigFile());
    rpcHandler.registerRpcMethod("getStats", statRpcMethod);
  }

  /** Starts listening for connections to the dashboard. */
  public void start(HttpServerScope scope) {
    boolean secure = config.isServerSecure();
    HttpHandler dashboardHandler = new DashboardHandler();
    HttpContext dashboardContext = addFilters(scope.createContext("/dashboard",
        createAdminSecurityHandler(dashboardHandler, config, sessionManager,
                                   secure)));
    addFilters(scope.createContext("/rpc", createAdminSecurityHandler(
        rpcHandler, config, sessionManager, secure)));
    addFilters(scope.createContext("/diagnostics-support.zip",
        createAdminSecurityHandler(new DownloadDumpHandler(config,
            config.getFeedName().replace('_', '-'), statRpcMethod),
            config, sessionManager, secure)));
    addFilters(scope.createContext("/",
        new RedirectHandler(dashboardContext.getPath())));

    circularLogRpcMethod.start();
  }

  private AdministratorSecurityHandler createAdminSecurityHandler(
      HttpHandler handler, Config config,
      SessionManager<HttpExchange> sessionManager, boolean secure) {
    return new AdministratorSecurityHandler(handler, sessionManager,
        config.getGsaHostname(), secure);
  }

  public void stop() {
    circularLogRpcMethod.close();
  }

  private HttpContext addFilters(HttpContext context) {
    return gsaCommHandler.addFilters(context);
  }

  private class StartFeedPushRpcMethod implements RpcHandler.RpcMethod {
    @Override
    public Object run(List request) {
      return gsaCommHandler.checkAndScheduleImmediatePushOfDocIds();
    }
  }

  private class StartIncrementalFeedPushRpcMethod
      implements RpcHandler.RpcMethod {

    @Override
    public Object run(List request) {
      return gsaCommHandler.checkAndScheduleIncrementalPushOfDocIds();
    }
  }

  static class CircularLogRpcMethod implements RpcHandler.RpcMethod,
      Closeable {
    private final CircularBufferHandler circularLog
        = new CircularBufferHandler();

    /**
     * Installs a log handler; to uninstall handler, call {@link #close}.
     */
    public void start() {
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
    private final List<StatusSource> sources;

    public StatusRpcMethod(List<StatusSource> sources) {
      this.sources = Collections.unmodifiableList(
          new ArrayList<StatusSource>(sources));
    }

    public Map<StatusSource, Status> retrieveStatuses() {
      Map<StatusSource, Status> statuses
          = new LinkedHashMap<StatusSource, Status>(sources.size() * 2);
      for (StatusSource source : sources) {
        statuses.put(source, source.retrieveStatus());
      }
      return statuses;
    }

    @Override
    public Object run(List request) {
      Map<StatusSource, Status> statuses = retrieveStatuses();
      List<Object> flatStatuses = new ArrayList<Object>(statuses.size());
      // TODO(ejona): choose locale based on Accept-Languages.
      Locale locale = Locale.ENGLISH;
      for (Map.Entry<StatusSource, Status> me : statuses.entrySet()) {
        Map<String, String> obj = new TreeMap<String, String>();
        obj.put("source", me.getKey().getName(locale));
        obj.put("code", me.getValue().getCode().name());
        obj.put("message", me.getValue().getMessage(locale));
        flatStatuses.add(obj);
      }
      return flatStatuses;
    }
  }

  static class CheckForUpdatedConfigRpcMethod implements RpcHandler.RpcMethod {
    private final GsaCommunicationHandler gsaComm;

    public CheckForUpdatedConfigRpcMethod(GsaCommunicationHandler gsaComm) {
      this.gsaComm = gsaComm;
    }

    @Override
    public Object run(List request) {
      return gsaComm.ensureLatestConfigLoaded();
    }
  }

  static class EncodeSensitiveValueMethod implements RpcHandler.RpcMethod {
    private final SensitiveValueCodec secureValueCodec;

    public EncodeSensitiveValueMethod(SensitiveValueCodec secureValueCodec) {
      this.secureValueCodec = secureValueCodec;
    }

    /**
     * Requires two parameters: string to encode and security level.
     */
    @Override
    public Object run(List request) {
      if (request.size() < 2) {
        throw new IllegalArgumentException("Required parameters are: string to "
            + "encode and security level");
      }
      String readable = (String) request.get(0);
      if (readable == null) {
        throw new NullPointerException("String to encode must not be null");
      }
      String securityString = (String) request.get(1);
      if (securityString == null) {
        throw new NullPointerException("Security level must not be null");
      }
      SensitiveValueCodec.SecurityLevel security;
      try {
        security = SensitiveValueCodec.SecurityLevel.valueOf(securityString);
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("Unknown security level", ex);
      }
      return secureValueCodec.encodeValue(readable, security);
    }
  }

  static class LastPushStatusSource implements StatusSource {
    private final Journal journal;

    public LastPushStatusSource(Journal journal) {
      this.journal = journal;
    }

    @Override
    public Status retrieveStatus() {
      switch (journal.getLastFullPushStatus()) {
        case SUCCESS:
          return new TranslationStatus(Status.Code.NORMAL);
        case INTERRUPTION:
          return new TranslationStatus(Status.Code.WARNING,
              Translation.STATUS_FEED_INTERRUPTED);
        case FAILURE:
        default:
          return new TranslationStatus(Status.Code.ERROR);
      }
    }

    @Override
    public String getName(Locale locale) {
      return Translation.STATUS_FEED.toString(locale);
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
      return new TranslationStatus(code, Translation.STATUS_ERROR_RATE_RATE,
          (int) Math.ceil(rate * 100));
    }

    @Override
    public String getName(Locale locale) {
      return Translation.STATUS_ERROR_RATE.toString(locale);
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
        return new TranslationStatus(Status.Code.NORMAL);
      } else {
        return new TranslationStatus(Status.Code.WARNING,
            Translation.STATUS_CRAWLING_NO_ACCESSES_IN_PAST_DAY);
      }
    }

    @Override
    public String getName(Locale locale) {
      return Translation.STATUS_CRAWLING.toString(locale);
    }
  }
}
