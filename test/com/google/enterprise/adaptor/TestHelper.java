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

import static org.junit.Assume.assumeTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for tests.
 */
public class TestHelper {
  // Prevent instantiation
  private TestHelper() {}

  private static boolean isRunningOnWindows() {
    String osName = System.getProperty("os.name");
    boolean isWindows = osName.toLowerCase().startsWith("windows");
    return isWindows;
  }

  private static boolean isRunningOnMac() {
    String osName = System.getProperty("os.name");
    boolean isMac = osName.toLowerCase().startsWith("mac");
    return isMac;
  }

  public static void assumeOsIsNotWindows() {
    assumeTrue(!isRunningOnWindows());
  }

  public static void assumeOsIsWindows() {
    assumeTrue(isRunningOnWindows());
  }

  public static void assumeOsIsNotMac() {
    assumeTrue(!isRunningOnMac());
  }

  public static List<DocId> getDocIds(Adaptor adaptor,
      Map<String, String> configEntries) throws Exception {
    final AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    final Config config = new Config();
    adaptor.initConfig(config);
    for (Map.Entry<String, String> entry : configEntries.entrySet()) {
      config.setValue(entry.getKey(), entry.getValue());
    }
    adaptor.init(new WrapperAdaptor.WrapperAdaptorContext(null) {
      @Override
      public DocIdPusher getDocIdPusher() {
        return pusher;
      }

      @Override
      public Config getConfig() {
        return config;
      }

      @Override
      public void setAuthzAuthority(AuthzAuthority authzAuthority) {}
    });
    adaptor.getDocIds(pusher);
    return pusher.getDocIds();
  }

  public static List<DocId> getDocIds(Adaptor adaptor)
      throws Exception {
    return getDocIds(adaptor, Collections.<String, String>emptyMap());
  }

  public static void initSSLKeystores() {
    /*
     * test-keys.jks created with: keytool -genkeypair -alias adaptor -keystore
     * test/test-keys.jks -keyalg RSA -validity 1000000 -storepass changeit
     * -dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown,
     * C=Unknown" -keypass changeit"
     */
    System.setProperty("javax.net.ssl.keyStore",
        TestHelper.class.getResource("/test-keys.jks").getPath());
    System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    /*
     * test-cacerts.jks created with: keytool -exportcert -alias adaptor
     * -keystore test/test-keys.jks -rfc -file tmp.crt -storepass changeit
     * keytool -importcert -keystore test/test-cacerts.jks -file tmp.crt
     * -storepass changeit -noprompt -alias adaptor rm tmp.crt
     */
    System.setProperty("javax.net.ssl.trustStore",
        TestHelper.class.getResource("/test-cacerts.jks").getPath());
    System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
  }

  /** Gets a proxy for the given class where every method is a no-op. */
  public static <T> T doNothingProxy(Class<T> clazz) {
    return clazz.cast(
        Proxy.newProxyInstance(clazz.getClassLoader(),
            new Class<?>[] { clazz },
            new InvocationHandler() {
              public Object invoke(Object proxy, Method method, Object[] args) {
                // This does not work with primitive return types.
                return null;
              }
            }));
  }
}
