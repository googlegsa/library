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
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * Adaptor serving files from current directory
 */
public class FileSystemAdaptor extends AbstractAdaptor {
  private static final String CONFIG_SRC = "filesystemadaptor.src";
  private static final String CONFIG_INCLUDE = "filesystemadaptor.include";
  private static final String CONFIG_EXCLUDE = "filesystemadaptor.exclude";

  private static Logger log
      = Logger.getLogger(FileSystemAdaptor.class.getName());

  private File serveDir;
  private Pattern include;
  private Pattern exclude;

  @Override
  public void initConfig(Config config) {
    config.addKey(CONFIG_SRC, ".");
    // White list of files to serve. Regular expression is matched against
    // full path name, case sensitively. Partial matches are allowed, so you
    // should use ^ and $ to mark beginning and end of file name if that is what
    // you expect. Understand that '.' (period) means any character in regular
    // expressions, so you should use '\.' for a literal period. When
    // configuring via a properties file, backslash is a special character, so
    // you need to use two backslashes instead of one.
    // This default matches everything.
    config.addKey(CONFIG_INCLUDE, "");
    // Black list (overrides white list) of files to not serve. See include.
    // This default matches nothing (meaning nothing is excluded).
    config.addKey(CONFIG_EXCLUDE, "$^");
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    Config config = context.getConfig();
    String source = config.getValue(CONFIG_SRC);
    this.serveDir = new File(source).getCanonicalFile();

    // Use DOTALL to prevent accidental newline concerns. Since files are not
    // line-based, it seems unlikely this would be the wrong setting.
    String strInclude = config.getValue(CONFIG_INCLUDE);
    include = Pattern.compile(strInclude, Pattern.DOTALL);

    String strExclude = config.getValue(CONFIG_EXCLUDE);
    exclude = Pattern.compile(strExclude, Pattern.DOTALL);
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
    if (!file.exists() || !isFileDescendantOfServeDir(file)
        || !isFileAllowed(file)) {
      throw new FileNotFoundException();
    }
    if (!req.hasChangedSinceLastAccess(new Date(file.lastModified()))) {
      resp.respondNotModified();
      return;
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

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new FileSystemAdaptor(), args);
  }
}
