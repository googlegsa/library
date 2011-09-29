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

package adaptorlib.prebuilt;

import adaptorlib.*;

import java.io.*;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * Adaptor serving files from current directory
 */
public class FileSystemAdaptor extends AbstractAdaptor {
  private static Logger log = Logger.getLogger(FileSystemAdaptor.class.getName());
  private final File serveDir;
  private final Pattern include;
  private final Pattern exclude;

  public FileSystemAdaptor(File file, Pattern include, Pattern exclude)
      throws IOException {
    this.serveDir = file.getCanonicalFile();
    this.include = include;
    this.exclude = exclude;
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
        if (!isFileAllowed(file)) {
          continue;
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
    if (!isFileDescendantOfServeDir(file) || !isFileAllowed(file)) {
      throw new FileNotFoundException();
    }
    InputStream input = new FileInputStream(file);
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

  private boolean isFileAllowed(File file) {
    return include.matcher(file.getPath()).find()
        && !exclude.matcher(file.getPath()).find();
  }

  /** An example main for an adaptor. */
  public static void main(String a[]) throws IOException, InterruptedException {
    Config config = new Config();
    config.addKey("filesystemadaptor.src", ".");
    // White list of files to serve. Regular expression is matched against
    // full path name, case sensitively. Partial matches are allowed, so you
    // should use ^ and $ to mark beginning and end of file name if that is what
    // you expect. Understand that '.' (period) means any character in regular
    // expressions, so you should use '\.' for a literal period. When
    // configuring via a properties file, backslash is a special character, so
    // you need to use two backslashes instead of one.
    config.addKey("filesystemadaptor.include", "");
    // Black list (overrides white list) of files to not serve. See include.
    config.addKey("filesystemadaptor.exclude", "$^");
    // Note that backslashes are special characters in properties files, so you
    // need to use a double backslash to denote a single backslash.
    config.autoConfig(a);
    String source = config.getValue("filesystemadaptor.src");
    String include = config.getValue("filesystemadaptor.include");
    String exclude = config.getValue("filesystemadaptor.exclude");
    // Use DOTALL to prevent accidental newline concerns. Since files are not
    // line-based, it seems unlikely this would be the wrong setting.
    Adaptor adaptor = new FileSystemAdaptor(new File(source),
        Pattern.compile(include, Pattern.DOTALL),
        Pattern.compile(exclude, Pattern.DOTALL));
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content:
    try {
      gsa.beginListeningForContentRequests();
      log.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    // Push once at program start.
    gsa.pushDocIds();

    // Setup regular pushing of doc ids for once per day.
    gsa.beginPushingDocIds(
        new ScheduleOncePerDay(/*hour*/3, /*minute*/0, /*second*/0));
    log.info("doc id pushing has been put on schedule");
  }
}
