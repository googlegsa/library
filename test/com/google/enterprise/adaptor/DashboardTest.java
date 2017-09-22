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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test cases for {@link Dashboard}.
 */
public class DashboardTest {
  private static final Locale locale = Locale.ENGLISH;

  @Test
  public void testLogRpcMethod() {
    String golden = "Testing\n";
    Dashboard.CircularLogRpcMethod method
        = new Dashboard.CircularLogRpcMethod();
    method.start();
    try {
      Logger logger = Logger.getLogger("");
      Level origLevel = logger.getLevel();
      logger.setLevel(Level.FINEST);
      Logger.getLogger("").finest("Testing");
      logger.setLevel(origLevel);
      String str = (String) method.run(null);
      str = str.replaceAll("\r\n", "\n");
      assertTrue(str.endsWith(golden));
    } finally {
      method.close();
    }
  }

  @Test
  public void testConfigRpcMethod() {
    Map<String, String> golden = new HashMap<String, String>();
    golden.put("gsa.characterEncoding", "UTF-8");
    golden.put("server.hostname", "localhost");

    MockConfig config = new MockConfig();
    config.setKey("gsa.characterEncoding", "UTF-8");
    config.setKey("server.hostname", "localhost");
    Dashboard.ConfigRpcMethod method = new Dashboard.ConfigRpcMethod(config);
    Map map = (Map) method.run(null);
    assertEquals(golden, map);
  }

  @Test
  public void testStatusRpcMethod() {
    List<Map<String, Object>> golden = new ArrayList<Map<String, Object>>();
    {
      Map<String, Object> goldenObj = new HashMap<String, Object>();
      goldenObj.put("source", "mock");
      goldenObj.put("code", "NORMAL");
      goldenObj.put("message", "fine");
      golden.add(goldenObj);
    }

    List<StatusSource> sources = new ArrayList<StatusSource>();
    Status status = new MockStatus(Status.Code.NORMAL, "fine");
    MockStatusSource source = new MockStatusSource("mock", status);
    sources.add(source);
    Dashboard.StatusRpcMethod method = new Dashboard.StatusRpcMethod(sources);
    List list = (List) method.run(null);
    assertEquals(golden, list);
  }

  @Test
  public void testJavaVersionStatusSource() {
    Dashboard.JavaVersionStatusSource source =
        new Dashboard.JavaVersionStatusSource();

    // Unix JVM (version) tests: minimum version that passes: 1.7.0_9
    Status status = source.retrieveStatus("1.6.0-google", /*isWindows=*/ false);

    assertNotNull(source.getName(locale));
    assertEquals(Status.Code.ERROR, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.5.0-google", /*isWindows=*/ false);
    assertEquals(Status.Code.ERROR, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0-google", /*isWindows=*/ false);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_9"));

    status = source.retrieveStatus("1.7.0.1", /*isWindows=*/ false);
    assertEquals(Status.Code.ERROR, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0.6", /*isWindows=*/ false);
    assertEquals(Status.Code.ERROR, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0.9", /*isWindows=*/ false);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0.9_beta2", /*isWindows=*/ false);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    // test lexically-less but numerically-greater component
    status = source.retrieveStatus("1.7.0.10", /*isWindows=*/ false);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0.81", /*isWindows=*/ false);
    assertEquals(Status.Code.NORMAL, status.getCode());

    status = source.retrieveStatus("1.7.0.81_beta2", /*isWindows=*/ false);
    assertEquals(Status.Code.NORMAL, status.getCode());

    status = source.retrieveStatus("1.7.1-google", /*isWindows=*/ false);
    assertEquals(Status.Code.NORMAL, status.getCode());

    status = source.retrieveStatus("1.8.0", /*isWindows=*/ false);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.8.0_20"));

    status = source.retrieveStatus("1.8.0_10", /*isWindows=*/ false);
    assertEquals(Status.Code.ERROR, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.8.0_20"));

    // test current dev version
    status = source.retrieveStatus("1.8.0_112-google", /*isWindows=*/ false);
    assertEquals(Status.Code.NORMAL, status.getCode());

    status = source.retrieveStatus("9", /*isWindows=*/ false);
    assertEquals(Status.Code.NORMAL, status.getCode());

    status = source.retrieveStatus("9.0.1", /*isWindows=*/ false);
    assertEquals(Status.Code.NORMAL, status.getCode());

    // Windows JVM (version) tests: minimum version that passes: 1.7.0_9
    status = source.retrieveStatus("1.6.0-google", /*isWindows=*/ true);
    assertEquals(Status.Code.ERROR, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0-google", /*isWindows=*/ true);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_9"));

    status = source.retrieveStatus("1.7.0.6", /*isWindows=*/ true);
    assertEquals(Status.Code.ERROR, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0.9", /*isWindows=*/ true);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0.9_1", /*isWindows=*/ true);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    // test lexically-less but numerically-greater component
    status = source.retrieveStatus("1.7.0.10", /*isWindows=*/ true);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.7.0_80"));

    status = source.retrieveStatus("1.7.0.80", /*isWindows=*/ true);
    assertEquals(Status.Code.NORMAL, status.getCode());

    status = source.retrieveStatus("1.8.0", /*isWindows=*/ true);
    assertEquals(Status.Code.WARNING, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.8.0_20"));

    status = source.retrieveStatus("1.8.0_10", /*isWindows=*/ true);
    assertEquals(Status.Code.ERROR, status.getCode());
    assertThat(status.getMessage(locale), containsString("version 1.8.0_20"));

    // test current dev version
    status = source.retrieveStatus("1.8.0_144", /*isWindows=*/ true);
    assertEquals(Status.Code.NORMAL, status.getCode());

    // test an unexpected variation on a Java 9 version
    status = source.retrieveStatus("1.9.0_1", /*isWindows=*/ true);
    assertEquals(Status.Code.NORMAL, status.getCode());

  }

  @Test
  public void testLastPushStatusSource() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final AtomicReference<Journal.CompletionStatus> ref
        = new AtomicReference<Journal.CompletionStatus>();
    final Journal journal = new Journal(timeProvider) {
      @Override
      public CompletionStatus getLastFullPushStatus() {
        return ref.get();
      }
    };
    StatusSource source = new Dashboard.LastPushStatusSource(journal);
    assertNotNull(source.getName(locale));
    Status status;

    ref.set(Journal.CompletionStatus.SUCCESS);
    status = source.retrieveStatus();
    assertEquals(Status.Code.NORMAL, status.getCode());

    ref.set(Journal.CompletionStatus.INTERRUPTION);
    status = source.retrieveStatus();
    assertEquals(Status.Code.WARNING, status.getCode());

    ref.set(Journal.CompletionStatus.FAILURE);
    status = source.retrieveStatus();
    assertEquals(Status.Code.ERROR, status.getCode());
  }

  @Test
  public void testRetrieverStatusSource() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final AtomicReference<Double> errorRate = new AtomicReference<Double>();
    final Journal journal = new Journal(timeProvider) {
      @Override
      public double getRetrieverErrorRate(long maxCount) {
        return errorRate.get();
      }
    };
    StatusSource source = new Dashboard.RetrieverStatusSource(journal);
    assertNotNull(source.getName(locale));
    Status status;

    errorRate.set(0.);
    status = source.retrieveStatus();
    assertEquals(Status.Code.NORMAL, status.getCode());
    assertNotNull(status.getMessage(locale));

    errorRate.set(Dashboard.RetrieverStatusSource.WARNING_THRESHOLD);
    status = source.retrieveStatus();
    assertEquals(Status.Code.WARNING, status.getCode());
    assertNotNull(status.getMessage(locale));

    errorRate.set(Dashboard.RetrieverStatusSource.ERROR_THRESHOLD);
    status = source.retrieveStatus();
    assertEquals(Status.Code.ERROR, status.getCode());
    assertNotNull(status.getMessage(locale));
  }

  @Test
  public void testGsaCrawlingStatusSource() {
    final MockTimeProvider timeProvider = new MockTimeProvider();
    final AtomicBoolean gsaCrawled = new AtomicBoolean();
    final Journal journal = new Journal(timeProvider) {
      @Override
      public boolean hasGsaCrawledWithinLastDay() {
        return gsaCrawled.get();
      }
    };
    StatusSource source = new Dashboard.GsaCrawlingStatusSource(journal);
    assertNotNull(source.getName(locale));
    Status status;

    gsaCrawled.set(true);
    status = source.retrieveStatus();
    assertEquals(Status.Code.NORMAL, status.getCode());

    gsaCrawled.set(false);
    status = source.retrieveStatus();
    assertEquals(Status.Code.WARNING, status.getCode());
  }
}
