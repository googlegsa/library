// Copyright 2014 Google Inc. All Rights Reserved.
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
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides environment for running multiple adaptors on the same port and
 * having each instance be managed.
 */
// TODO(ejona): improve locking
// TODO(ejona): improve shutdown while starting
// TODO(ejona): improve handling of time limits
// TODO(ejona): improve state pre-condition checking
public final class Service {
  private static final Logger log = Logger.getLogger(Service.class.getName());

  private final Config config;
  private final HttpServer server;
  private final HttpServer dashboardServer;
  private final ConcurrentMap<String, Instance> instances
      = new ConcurrentHashMap<String, Instance>();
  private final Thread shutdownHook
      = new Thread(new ShutdownHook(), "service-shutdown");
  private int index;

  private Service(Config config) throws IOException {
    this.config = config;
    this.server = Application.createHttpServer(config);
    this.dashboardServer = Application.createDashboardHttpServer(config);
  }

  public synchronized Instance createInstance(String name, File jar,
      File workingDir) {
    Instance instance = new Instance(name, jar, workingDir, index++);
    if (instances.putIfAbsent(name, instance) != null) {
      throw new IllegalArgumentException("Instance by name already present: "
          + name);
    }
    instance.install();
    return instance;
  }

  public synchronized void deleteInstance(String name, long time,
      TimeUnit unit) {
    Instance instance = instances.get(name);
    if (instance == null) {
      throw new IllegalArgumentException("No instance with name: " + name);
    }
    instance.stop(time, unit);
    instance.uninstall();
    instances.remove(name, instance);
  }

  public synchronized void start() throws IOException {
    daemonInit();
    // The shutdown hook is purposefully not part of the daemon methods,
    // because it should only be done when running from the command line.
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    daemonStart();
  }

  synchronized void daemonInit() {
    server.start();
    dashboardServer.start();
  }

  synchronized void daemonStart() {
    for (Instance instance : instances.values()) {
      instance.start();
    }
  }

  public synchronized void stop(long time, TimeUnit unit) {
    daemonStop(time, unit);
    daemonDestroy(time, unit);
    try {
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
    } catch (IllegalStateException ex) {
      // Already executing hook.
    }
  }

  synchronized void daemonStop(long time, TimeUnit unit) {
    for (Instance instance : instances.values()) {
      instance.stop(time, unit);
    }
  }

  synchronized void daemonDestroy(long time, TimeUnit unit) {
    Application.httpServerShutdown(server, time, unit);
    Application.httpServerShutdown(dashboardServer, time, unit);
  }

  static Service daemonMain(String[] args) throws IOException {
    Config config = new Config();
    Application.autoConfig(config, args,
        new File(Application.DEFAULT_CONFIG_FILE));
    return new Service(config);
  }

  public static void main(String[] args) throws IOException {
    Service service = daemonMain(args);
    // TODO(ejona): decide if we want the JAR to be relative to the parent
    // process or the child process (right now it is relative to the child).
    service.createInstance("adaptor1", new File("../AdaptorTemplate.jar"),
        new File("adaptor1"));
    service.createInstance("adaptor2", new File("../AdaptorTemplate.jar"),
        new File("adaptor2"));
    service.start();
  }

  /**
   * Maybe use this as an interface to allow disabling/enabling and
   * starting/stopping of particular instances?
   */
  public final class Instance {
    private final String name;
    private final File jar;
    private final File workingDir;
    private final int index;
    private Thread running;
    private ShutdownWaiter waiter;

    private Instance(String name, File jar, File workingDir, int index) {
      if (name == null) {
        throw new NullPointerException();
      }
      if (name.contains("/") || name.startsWith(".")) {
        // Prevent '.', '..', and things containing '/' from being names.
        throw new IllegalArgumentException(
            "Name must not contain / or start with .");
      }
      if (jar == null) {
        throw new NullPointerException();
      }
      if (index < 0 || index > 10000) {
        throw new IllegalArgumentException("Index too large or small: "
            + index);
      }
      this.name = name;
      this.jar = jar;
      this.workingDir = workingDir;
      this.index = index;
    }

    private void install() {
      // TODO(ejona): add disable support, where we return Service Unavailable
      // instead of Not Found.
    }

    private void start() {
      if (running != null) {
        throw new IllegalStateException();
      }
      final int port = config.getServerPort() + 2 * (index + 1);
      final int dashboardPort = config.getServerDashboardPort()
          + 2 * (index + 1);
      final String scheme = config.isServerSecure() ? "https" : "http";
      waiter = new ShutdownWaiter();
      running = new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            // TODO(ejona): Figure out how much configuration to share.
            int ret = JavaExec.exec(jar, workingDir, Arrays.<String>asList(
                "-Dserver.port=" + port,
                "-Dserver.dashboardPort=" + dashboardPort,
                // TODO(ejona): use same security for child
                "-Dserver.secure=false",
                // TODO(ejona): REMOVE THIS HACK. WE NEED TO DO PROPER SECURITY
                // COMMUNICATION AND HANDLE X-Forwarded-For AND SIMILAR. THIS
                // DISABLES ALL SECURITY.
                "-Dserver.fullAccessHosts=127.0.0.1",
                "-Dgsa.hostname=" + config.getGsaHostname(),
                "-Dgsa.admin.hostname=" + config.getGsaAdminHostname(),
                "-Dserver.reverseProxyProtocol=" + scheme,
                "-Dserver.reverseProxyPort=" + config.getServerPort()));
            if (ret != 0) {
              log.log(Level.WARNING, "Error response code from child: " + ret);
            }
          } catch (IOException ex) {
            // TODO(ejona): add more info as to which one failed.
            log.log(Level.WARNING, "IOException in subprocess", ex);
          } catch (InterruptedException ex) {
            log.log(Level.WARNING, "Forced shutdown of child", ex);
          }
        }
      });
      running.start();

      HttpContext context = server.createContext(
          "/" + name + "/", new ReverseProxyHandler(
              URI.create("http://127.0.0.1:" + port + "/")));
      context.getFilters().add(waiter.filter());

      // TODO(ejona): When you end up visiting the dashboard, it redirects you
      // to its port. It would be nice to fix RedirectHandler to deal with that,
      // although it will require additional config parameters.
      HttpContext dashboardContext = dashboardServer.createContext(
          "/" + name + "/", new ReverseProxyHandler(
              URI.create("http://127.0.0.1:" + dashboardPort + "/")));
      dashboardContext.getFilters().add(waiter.filter());
    }

    private void stop(long time, TimeUnit unit) {
      if (running == null) {
        throw new IllegalStateException();
      }
      server.removeContext("/" + name);
      dashboardServer.removeContext("/" + name);
      try {
        waiter.shutdown(time, unit);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      // TODO(ejona): Send graceful shutdown request and join with running
      // thread, only interrupting if it times out.
      running.interrupt();
      try {
        running.join(unit.toMillis(time));
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      running = null;
    }

    private void uninstall() {
    }
  }

  private class ShutdownHook implements Runnable {
    @Override
    public void run() {
      stop(3, TimeUnit.SECONDS);
    }
  }
}
