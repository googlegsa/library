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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates and serves a .zip file containing diagnostic information.
 *
 * <p>For example, it can include logs and a thread dump.
 */
class DownloadDumpHandler implements HttpHandler {
  private static final Logger log
      = Logger.getLogger(DownloadDumpHandler.class.getName());

  /** Used to generate the configuration output file */
  private Config config;

  /** To be used as part of the zip file name */
  private String feedName;

  /** Used to obtain statistics for one file in the .zip */
  private final StatRpcMethod statRpcMethod;

  /** Used to specify the top-level directory where logs are kept */
  private final File logsDir;

  /** Only used by the test class, to pass in a canned date */
  private final TimeProvider timeProvider;

  /** Used in handle() to generate the date portion of the zip file name;
      we are implicitly using the local time zone. */
  private final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

  /** Default to "logs/" and System time */
  public DownloadDumpHandler(Config config, String feedName,
      StatRpcMethod statRpcMethod) {
    this(config, feedName, statRpcMethod, new File("logs/"),
        new SystemTimeProvider());
  }

  @VisibleForTesting
  DownloadDumpHandler(Config config, String feedName,
      StatRpcMethod statRpcMethod, File logsDir, TimeProvider timeProvider) {
    if (null == config) {
      throw new NullPointerException();
    }
    if (null == feedName) {
      throw new NullPointerException();
    }
    if (feedName.contains("\"")) {
      throw new IllegalArgumentException(
          "feedName must not contain the \" character");
    }
    this.config = config;
    this.feedName = feedName;
    this.statRpcMethod = statRpcMethod; // OK to leave as null
    this.logsDir = logsDir;
    this.timeProvider = timeProvider;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if (!"GET".equals(requestMethod)) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
          Translation.HTTP_BAD_METHOD);
      return;
    }
    if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
          Translation.HTTP_NOT_FOUND);
      return;
    }
    String dateAsString;
    synchronized (this) {  // DateFormat.format() is not thread-safe!
      dateAsString = dateFormat.format(
          new Date(timeProvider.currentTimeMillis()));
    }
    String filename = feedName + "-" + dateAsString + ".zip";
    String contentType = "application/zip";
    ex.getResponseHeaders().set("Content-Disposition",
        "attachment; filename=\"" + filename + "\"");
    // stream the contents of the zip
    HttpExchanges.startResponse(
        ex, HttpURLConnection.HTTP_OK, contentType, true);
    OutputStream os = ex.getResponseBody();
    BufferedOutputStream bos = new BufferedOutputStream(os);
    ZipOutputStream zos = new ZipOutputStream(bos);
    generateZipContents(logsDir, zos);
    zos.close();  // NOT in a "finally" clause - connection killed on an error.
  }

  private void generateZipContents(File logsDir, ZipOutputStream zos)
      throws IOException {
    dumpLogFiles(logsDir, zos);
    dumpStackTraces(zos);
    dumpConfig(zos);
    dumpStats(zos);
    zos.flush();
  }

  private void dumpLogFiles(File logsDir, ZipOutputStream zos)
      throws IOException {
    File[] files = logsDir.listFiles();
    if (files == null) {
      log.log(Level.FINER, "Unable to find logs directory {0}", logsDir);
      return;
    }
    for (File f: files) {
      // avoid zipping the (empty) lock file
      if (f.getName().endsWith(".lck")) {
        log.log(Level.FINEST, "Skipping lock file: {0}", f.getName());
        continue;
      }
      if (!f.isFile()) {
        // TODO(myk): consider zipping up files present under subdirectories.
        log.log(Level.FINEST, "Ignoring directory entry: {0}", f.getName());
        continue;
      }
      log.log(Level.FINEST, "Adding file: {0}/{1}",
          new Object[] {logsDir, f.getName()});
      InputStream is = createInputStream(f);
      try {
        zos.putNextEntry(new ZipEntry(logsDir.toString() + "/" + f.getName()));
        IOHelper.copyStream(is, zos);
      } finally {
        is.close();
      }
      zos.closeEntry();
    }
  }

  /**
   * Output the stack trace for every running thread (sorted by thread name).
   *
   * <p>For example:
   * <p><code>
   * Thread[Reference Handler,10,system]
   *   java.lang.Object.wait(Native Method)
   *   java.lang.Object.wait(Object.java:502)
   *   java.lang.ref.Reference$ReferenceHandler.run(Reference.java:129)
   * </code><p><code>
   * Thread ...
   * </code>
   */
  private void dumpStackTraces(ZipOutputStream zos) throws IOException {
    OutputStreamWriter writer = new OutputStreamWriter(zos, "UTF-8");
    String newline = "\n"; // so our support folks always see the same results
    Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
    Map<String, StackTraceElement[]> sortedThreads =
        new TreeMap<String, StackTraceElement[]>();
    for (Map.Entry<Thread, StackTraceElement[]> me : allThreads.entrySet()) {
      sortedThreads.put(me.getKey().toString(), me.getValue());
    }
    zos.putNextEntry(new ZipEntry("threaddump.txt"));
    for (Map.Entry<String, StackTraceElement[]> me : sortedThreads.entrySet()) {
      writer.write(me.getKey());
      writer.write(newline);
      for (StackTraceElement element : me.getValue()) {
        writer.write(" ");
        writer.write("" + element);
        writer.write(newline);
      }
      writer.write(newline);
    }
    writer.flush();
    zos.closeEntry();
  }

  /**
   * Output the configuration into the diagnostics zip
   */
  private void dumpConfig(ZipOutputStream zos) throws IOException {
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(zos, "UTF-8"));
    String newline = "\n"; // so our support folks always see the same results
    TreeMap<String, String> sortedConfig = new TreeMap<String, String>();
    for (String key : config.getAllKeys()) {
      sortedConfig.put(key, config.getValue(key));
    }
    zos.putNextEntry(new ZipEntry("config.txt"));
    prettyPrintMap(writer, sortedConfig);
    writer.flush();
    zos.closeEntry();
  }

  /**
   * Output the version info and statistics into the diagnostics zip
   */
  private void dumpStats(ZipOutputStream zos) throws IOException {
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(zos, "UTF-8"));
    if (statRpcMethod == null) {
      return;  // don't generate empty stats file
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) statRpcMethod.run(null);

    zos.putNextEntry(new ZipEntry("stats.txt"));

    if (null != map.get("versionStats")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> vMap = (Map<String, Object>) map.get("versionStats");
      prettyPrintMap(writer, vMap);
    }

    if (null != map.get("simpleStats")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> sMap = (Map<String, Object>) map.get("simpleStats");
      Set<String> expectedDateAttrs = ImmutableSet.of("whenStarted",
          "lastSuccessfulFullPushStart", "lastSuccessfulFullPushEnd",
          "currentFullPushStart", "lastSuccessfulIncremementalPushStart",
          "lastSuccessfulIncremementalPushEnd", "currentIncrementalPushEnd");
      DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss Z");
      dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
      for (String key : expectedDateAttrs) {
        if (!sMap.containsKey(key)) {
          log.log(Level.INFO,
              "Did not find expected key \"{0}\" in simpleStats", key);
          continue;
        }
        Object value = sMap.get(key);
        if (value == null) {
          continue;
        }
        if (!(value instanceof Number)) {
          log.log(Level.INFO,
              "Key \"{0}\" contained non-date value \"{1}\" in simpleStats",
              new Object[] {key, value});
          continue;
        }
        long date = ((Number) value).longValue();
        if (date <= 0) {
          continue;
        }
        // It's a number > 0 -- assume it's a date.
        // Replace value in map with a formatted date.
        sMap.put(key, dateFormat.format(new Date(date)));
      }
      prettyPrintMap(writer, sMap);
    }

    writer.flush();
    zos.closeEntry();
  }

  /**
   * Pretty-prints a map
   */
  void prettyPrintMap(PrintWriter writer, Map<String, ?> map) {
    int maxKeyLength = 0;

    for (String key : map.keySet()) {
      if (key.length() > maxKeyLength) {
        maxKeyLength = key.length();
      }
    }

    String outputFormat = "%-" + (maxKeyLength + 1) + "s= %s%n";
    for (Map.Entry<String, ?> me : map.entrySet()) {
      writer.format(outputFormat, me.getKey(),
          (me.getValue() == null ? "[null]" : me.getValue().toString()));
    }
    writer.format("%n");
  }

  /**
   * Method gets overriden in test class to avoid using "real" IO.
   */
  @VisibleForTesting
  InputStream createInputStream(File file) throws IOException {
    return new FileInputStream(file);
  }
}
