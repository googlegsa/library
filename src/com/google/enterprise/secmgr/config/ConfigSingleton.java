// Copyright 2008 Google Inc.
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

package com.google.enterprise.secmgr.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.enterprise.secmgr.common.FileUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.io.File;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A singleton class to access configured parameters.
 */
@Singleton
@ThreadSafe
public class ConfigSingleton {
  private static final Logger LOGGER = Logger.getLogger(ConfigSingleton.class.getName());

  @Inject private static Injector injector;
  @Inject private static ConfigSingleton instance;
  @GuardedBy("class") private static SecurityManagerConfig configOverride = null;
  @GuardedBy("class") private static Gson gson;
  private static final LocalObservable observable = new LocalObservable();

  private static final class LocalObservable extends Observable {
    @Override
    protected void setChanged() {
      super.setChanged();
    }
  }

  private final ConfigCodec configCodec;
  private final String configFilename;
  /** The modification time of the configuration file when last read. */
  @GuardedBy("this") private long configTime;
  /** The parsed configuration file. */
  @GuardedBy("this") private SecurityManagerConfig config;

  @Inject
  private ConfigSingleton(ConfigCodec configCodec, @Named("configFile") String configFilename) {
    Preconditions.checkNotNull(configCodec);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(configFilename));
    this.configCodec = configCodec;
    this.configFilename = configFilename;
    resetInternal();
  }

  /**
   * @return The application's Guice injector.
   */
  public static Injector getInjector() {
    return injector;
  }

  /**
   * A convenience method that invokes the injector.
   *
   * @param clazz The class to instantiate.
   * @return An instance of the given class.
   */
  public static <T> T getInstance(Class<T> clazz) {
    return injector.getInstance(clazz);
  }

  /**
   * A convenience method that invokes the injector.
   *
   * @param type The type to instantiate.
   * @return An instance of the given type.
   */
  public static <T> T getInstance(TypeLiteral<T> type) {
    return injector.getInstance(Key.get(type));
  }

  @VisibleForTesting
  public static synchronized void reset() {
    configOverride = null;
    instance.resetInternal();
  }

  private synchronized void resetInternal() {
    configTime = 0;
    config = null;
  }

  /**
   * Adds an observer that's notified of config changes.
   */
  public static void addObserver(Observer observer) {
    observable.addObserver(observer);
  }

  /**
   * Gets an observable that's notified of config changes.
   */
  public static Observable getObservable() {
    return observable;
  }

  static void setChanged() {
    observable.setChanged();
    observable.notifyObservers();
  }

  public static synchronized void setGsonRegistrations(GsonRegistrations registrations) {
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    registrations.register(builder);
    gson = builder.create();
  }

  /** A type to use for passing in Gson registrations. */
  public interface GsonRegistrations {
    public void register(GsonBuilder builder);
  }

  public static synchronized Gson getGson() {
    Preconditions.checkNotNull(gson);
    return gson;
  }

  /**
   * @return The current configuration.
   * @throws IOException if there are I/O errors reading the configuration.
   */
  public static synchronized SecurityManagerConfig getConfig()
      throws IOException {
    return (configOverride != null) ? configOverride : getConfigNoOverride();
  }

  @VisibleForTesting
  public static synchronized SecurityManagerConfig getConfigNoOverride()
      throws IOException {
    return instance.getConfigInternal();
  }

  @VisibleForTesting
  public static synchronized void setConfig(SecurityManagerConfig config) {
    configOverride = config;
    setChanged();
  }

  private synchronized SecurityManagerConfig getConfigInternal()
      throws IOException {
    File file = FileUtil.getContextFile(configFilename);
    // Check the config file's mod time; if it hasn't changed since the last
    // successful read, use the cached value.  Otherwise, try reading the config
    // file.  Go around the loop until the mod time before the read and the mod
    // time after the read are the same.  This detects changes to the file
    // during the read.
    while (true) {
      long time = file.lastModified();
      if (time == 0) {
        throw new IOException("No such file: " + file);
      }
      if (time == configTime) {
        break;
      }
      try {
        config = configCodec.readConfig(file);
      } catch (ConfigException e) {
        LOGGER.log(Level.SEVERE, "Error parsing config file. Returning default config.", e);
        config = SecurityManagerConfig.makeDefault();
      }
      configTime = time;
      LOGGER.fine("Config:\n" + config);
      observable.setChanged();
    }
    observable.notifyObservers();
    return config;
  }

  /** @see SecurityManagerConfig#getAclGroupsFilename */
  public static String getAclGroupsFilename() throws IOException {
    return getConfig().getAclGroupsFilename();
  }

  /** @see SecurityManagerConfig#getAclUrlsFilename */
  public static String getAclUrlsFilename() throws IOException {
    return getConfig().getAclUrlsFilename();
  }

  /** @see SecurityManagerConfig#getCertificateAuthoritiesFilename */
  public static String getCertificateAuthoritiesFilename() throws IOException {
    return getConfig().getCertificateAuthoritiesFilename();
  }

  /** @see SecurityManagerConfig#getConnectorManagerUrls */
  public static Iterable<String> getConnectorManagerUrls() throws IOException {
    return getConfig().getConnectorManagerUrls();
  }

  /** @see SecurityManagerConfig#getDenyRulesFilename */
  public static String getDenyRulesFilename() throws IOException {
    return getConfig().getDenyRulesFilename();
  }

  /** @see SecurityManagerConfig#getGlobalBatchRequestTimeout */
  public static Float getGlobalBatchRequestTimeout() throws IOException {
    return getConfig().getGlobalBatchRequestTimeout();
  }

  /** @see SecurityManagerConfig#getGlobalSingleRequestTimeout */
  public static Float getGlobalSingleRequestTimeout() throws IOException {
    return getConfig().getGlobalSingleRequestTimeout();
  }

  /** @see SecurityManagerConfig#getSamlMetadataFilename */
  public static String getSamlMetadataFilename() throws IOException {
    return getConfig().getSamlMetadataFilename();
  }

  /** @see SecurityManagerConfig#getServerCertificateFilename */
  public static String getServerCertificateFilename() throws IOException {
    return getConfig().getServerCertificateFilename();
  }

  /** @see SecurityManagerConfig#getSigningCertificateFilename */
  public static String getSigningCertificateFilename() throws IOException {
    return getConfig().getSigningCertificateFilename();
  }

  /** @see SecurityManagerConfig#getSigningKeyFilename */
  public static String getSigningKeyFilename() throws IOException {
    return getConfig().getSigningKeyFilename();
  }

  /** @see SecurityManagerConfig#getStunnelPort */
  public static int getStunnelPort() throws IOException {
    return getConfig().getStunnelPort();
  }
}
