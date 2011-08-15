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

package com.google.enterprise.secmgr.common;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utilities for finding files.
 */
public class FileUtil {

  private static String contextDirectory;
  private static String commonDirectory;

  private static final String GOOGLE3_TEST_UTIL_CLASS = "com.google.testing.util.TestUtil";
  private static final String GET_SRC_DIR_METHOD = "getSrcDir";
  private static final String GET_TMP_DIR_METHOD = "getTmpDir";
  private static final String GOOGLE3_TESTDATA_ROOT =
      "/google3/javatests/com/google/enterprise/secmgr/testdata";
  private static final String NON_GOOGLE3_TESTDATA_ROOT = "testdata/mocktestdata/";

  private static final String BEGIN_PEM_CERTIFICATE_MARKER = "-----BEGIN CERTIFICATE-----";
  private static final String END_PEM_CERTIFICATE_MARKER = "-----END CERTIFICATE-----";

  // don't instantiate
  private FileUtil() {
  }

  /**
   * Initialize the context and common directories for testing.  Chooses appropriate
   * values for them depending on the testing context.
   */
  public static void initializeTestDirectories() {
    Class<?> clazz;
    try {
      clazz = Class.forName(GOOGLE3_TEST_UTIL_CLASS);
    } catch (ClassNotFoundException e) {
      // no worries -- this just means we're not in a google3 environment
      contextDirectory = NON_GOOGLE3_TESTDATA_ROOT;
      commonDirectory = NON_GOOGLE3_TESTDATA_ROOT;
      return;
    }

    Method m1;
    Method m2;
    try {
      m1 = clazz.getDeclaredMethod(GET_SRC_DIR_METHOD);
      m2 = clazz.getDeclaredMethod(GET_TMP_DIR_METHOD);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }

    String srcDir;
    String tmpDir;
    try {
      srcDir = String.class.cast(m1.invoke(null));
      tmpDir = String.class.cast(m2.invoke(null));
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(e);
    }

    contextDirectory = srcDir + GOOGLE3_TESTDATA_ROOT;
    commonDirectory = tmpDir;
  }

  /**
   * Get the context directory.  This directory holds the config files used by
   * the security manager.
   *
   * @return The context directory as a string.
   */
  public static String getContextDirectory() {
    return contextDirectory;
  }

  /**
   * Get the common directory.  This directory holds all persistent state maintained by
   * the connector manager.
   *
   * @return The common directory as a string.
   */
  public static String getCommonDirectory() {
    // Allow override via command-line flags so that large tests can easily
    // specify a tmp directory.
    String testTmpDir = System.getProperty("google3.tmpdir");
    if (testTmpDir != null) {
      return testTmpDir;
    }
    return commonDirectory;
  }

  /**
   * Set the Context/Common directory.  To be called only from the servlet
   * initialization in a production environment.
   */
  public static void setContextDirectory(String directory) {
    contextDirectory = directory;
    commonDirectory = directory;
  }

  /**
   * Resolve relative filenames in the directory of the config files.
   *
   * @param filename A (potentially relative) filename.
   * @return An absolute File object.
   */
  public static File getContextFile(String filename) {
    Preconditions.checkNotNull(filename);
    return getContextFile(new File(filename));
  }

  /**
   * Resolve relative filenames in the directory of the config files.
   *
   * @param file A (potentially relative) File object.
   * @return An absolute File object.
   */
  public static File getContextFile(File file) {
    Preconditions.checkNotNull(file);
    return (file.isAbsolute()) ? file : new File(getContextDirectory(), file.toString());
  }

  /**
   * Resolve relative filenames in the common directory.
   *
   * @param filename A (potentially relative) filename.
   * @return An absolute File object.
   * @see #getCommonDirectory
   */
  public static File getCommonFile(String filename) {
    Preconditions.checkNotNull(filename);
    return getCommonFile(new File(filename));
  }

  /**
   * Resolve relative filenames in the common directory.
   *
   * @param file A (potentially relative) File object.
   * @return An absolute File object.
   * @see #getCommonDirectory
   */
  public static File getCommonFile(File file) {
    Preconditions.checkNotNull(file);
    return (file.isAbsolute()) ? file : new File(getCommonDirectory(), file.toString());
  }

  /**
   * Read a file's contents and return them as a string.
   *
   * @param file The file to read.
   * @return The file's contents.
   * @throws IOException if unable to read the file.
   */
  public static String readFile(File file)
      throws IOException {
    Preconditions.checkNotNull(file);
    StringBuffer accumulator = new StringBuffer();
    char[] buffer = new char[4096];
    Reader reader = null;
    try {
      reader = new FileReader(file);
      while (true) {
        int nRead = reader.read(buffer);
        if (nRead < 0) {
          break;
        }
        accumulator.append(buffer, 0, nRead);
      }
    } finally {
      if (reader != null) {
        reader.close();
      }
    }
    return accumulator.toString();
  }

  /**
   * Read a PEM-encoded certificate file and return the Base64 part as a string.
   *
   * @param file The file to read.
   * @return The certificate in Base64 encoding.
   * @throws IOException if unable to read or parse the file.
   */
  public static String readPEMCertificateFile(File file)
      throws IOException {
    String certFile = readFile(file);
    int start = certFile.indexOf(BEGIN_PEM_CERTIFICATE_MARKER);
    if (start < 0) {
      throw new IOException("Certificate file missing begin marker");
    }
    start += BEGIN_PEM_CERTIFICATE_MARKER.length();
    int end = certFile.indexOf(END_PEM_CERTIFICATE_MARKER, start);
    if (end < 0) {
      throw new IOException("Certificate file missing end marker");
    }
    return certFile.substring(start, end - 1);
  }
}
