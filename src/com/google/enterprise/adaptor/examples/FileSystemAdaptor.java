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

package com.google.enterprise.adaptor.examples;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.IOHelper;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.prebuilt.RecursiveFileIterator;

import java.io.*;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Simple example adaptor that serves files from the local filesystem.
 */
public class FileSystemAdaptor extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(FileSystemAdaptor.class.getName());

  private File serveDir;

  @Override
  public void initConfig(Config config) {
    // Setup default configuration values. The user is allowed to override them.

    // Create a new configuration key for letting the user configure this
    // adaptor.
    config.addKey("filesystemadaptor.src", ".");
    // Change the default to automatically provide unzipped zip contents to the
    // GSA.
    config.overrideKey("adaptor.autoUnzip", "true");
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    // Process configuration.
    String source = context.getConfig().getValue("filesystemadaptor.src");
    serveDir = new File(source).getCanonicalFile();
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws IOException,
      InterruptedException {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    String parent = serveDir.toString();
    try {
      for (File file : new RecursiveFileIterator(serveDir)) {
        String name = file.toString();
        if (!name.startsWith(parent)) {
          throw new IllegalStateException(
              "Internal problem: the file's path does not begin with parent.");
        }
        // +1 for slash
        name = name.substring(parent.length() + 1);
        mockDocIds.add(new DocId(name));
      }
    } catch (RecursiveFileIterator.WrappedIOException ex) {
      throw ex.getCause();
    }
    pusher.pushDocIds(mockDocIds);
  }

  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    File file = new File(serveDir, id.getUniqueId()).getCanonicalFile();
    // The DocId provided by Request.getDocId() MUST NOT be trusted. Here we
    // try to verify that this file is allowed to be served.
    if (!isFileDescendantOfServeDir(file)) {
      resp.respondNotFound();
      return;
    }
    InputStream input;
    try {
      input = new FileInputStream(file);
    } catch (FileNotFoundException ex) {
      resp.respondNotFound();
      return;
    }
    try {
      IOHelper.copyStream(input, resp.getOutputStream());
    } finally {
      input.close();
    }
  }

  private boolean isFileDescendantOfServeDir(File file) {
    while (file != null) {
      if (file.equals(serveDir)) {
        return true;
      }
      file = file.getParentFile();
    }
    return false;
  }

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new FileSystemAdaptor(), args);
  }
}
