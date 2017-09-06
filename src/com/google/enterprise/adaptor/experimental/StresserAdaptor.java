// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor.experimental;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Result of running this adaptor:
 *   1. The depth and number of documents is configured with adaptor.branches
 *   2. The files are at the leaves
 *   3. Default is depth of 10 of CHILD_OVERRIDES 
 *   4. There is a "specialUser" who has access to roughly 100 files and will
 *      be denied access to all other files. The 100 files are uniformly
 *      distributed around all leaves.
 *   5. For all the files/directories other than the 100 files which the
 *      special user has access to, 10001 in total will be marked as public.
 *      The rest will have 2 random permitted users and 2 unique denied users.
 *      The users' names are uniquely made up, e.g., user1, user123. Note 
 *      that the unique users get changed with every recrawl.
 *   6. crawlOnce is set to true
 */
public class StresserAdaptor extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(StresserAdaptor.class.getName());
  private static Charset encoding = Charset.forName("UTF-8");

  /*
   * 267 means roughly 20KB. Each file has 267 lines and each line contains
   * approximately 75 characters and a newline.  So the file is roughly 20KB.
   */
  private static final int NUMBER_OF_LINES = 267;

  private static final String DICTIONARY_FILE = "/usr/share/dict/words";

  private final String specialUser = "specialUser";
  private final int specialFileCount = 100;
  private final int publicFileCount = 10001;

  private List<Integer> branches = null;
  private int totalNumberOfFiles;
  private int depth; // of folders 
  private int aclChainLength; // == depth + 1 because root at depth 0 has ACL

  private static String[] dictionary = null;
  private static final Random rand = new Random();
  private static String getRandomWord() {
    return dictionary[rand.nextInt(dictionary.length)];
  }

  // Pre-computed format string to be used in making up url to work around
  // "infinte space detection" of a GSA.
  private String padByLength[];

  @Override
  public void initConfig(Config config) {
    config.addKey("adaptor.branches", null);
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    Config config = context.getConfig();

    parseBranches(config.getValue("adaptor.branches"));

 //   if (branches == null) {
 //                           // 4 *5 *5 *10 *10 *10 *10 *10 *10 = 100M
 //     branches = Arrays.asList(4, 5, 5, 10, 10, 10, 10, 10, 10);
 //   }

    if (branches.size() <= 0) {
      throw new IllegalStateException("bad folder structure");
    }

    totalNumberOfFiles = 1;
    for (int i = 0; i < branches.size(); i++) {
      totalNumberOfFiles *= branches.get(i);
    }
    depth = branches.size();
    aclChainLength = branches.size() + 1;

    padByLength = new String[branches.size() + 1];
    padByLength[0] = "";
    for (int i = 1; i <= branches.size(); i++) {
      padByLength[i] = padByLength[i - 1] + "x";
    }

    initDictionary();
  }

  private void parseBranches(String configBranches)
      throws NumberFormatException, IllegalStateException {
    configBranches = configBranches.replaceAll("\\s+", "");
    String str[] = configBranches.split(",", 0); // drop trailing empties
    if (str.length == 0) {
      return;
    }
    this.branches = new ArrayList<Integer>(str.length);
    for (int i = 0; i < str.length; i++) {
      this.branches.add(Integer.parseInt(str[i]));
      if (this.branches.get(i) <= 0) {
        throw new IllegalStateException("branch [" + i
            + "] can not be negative or zero");
      }
    }
    log.config("branches: " + Arrays.toString(this.branches.toArray()));
  }

  private static void initDictionary() throws Exception {
    Scanner sc = new Scanner(new File(DICTIONARY_FILE));
    ArrayList<String> words = new ArrayList<String>();
    while (sc.hasNext()) {
      words.add(sc.next());
    }
    sc.close();
    dictionary = new String[words.size()];
    for (int i = 0; i < words.size(); i++) {
      dictionary[i] = words.get(i);
    }
    log.config("size of dictionary: " + dictionary.length);
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    log.info("called getDocIds()");
    List<DocId> rootDocId = Arrays.asList(new DocId(""));
    pusher.pushDocIds(rootDocId);
    log.info("returning from getDocIds()");
  }

  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    String uniqueId = id.getUniqueId();
    log.fine("called getDocId(" + uniqueId + ")");
    String contentType = "text/html";

    if ("".equals(uniqueId)) { // root doc
      String content = null;
      if (branches.size() == 1) {
        content = makeContentFileLinks(branches.get(0));
      } else {
        content = makeSubfolderLinks(branches.get(0), uniqueId);
      }
      Acl.Builder aclBuilder =
          new Acl.Builder()
              .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
              .setPermitUsers(Arrays.asList(
                  new UserPrincipal(produceUsername("userP1", uniqueId)),
                  new UserPrincipal(produceUsername("userP2", uniqueId))))
              .setDenyUsers(Arrays.asList(
                  new UserPrincipal(produceUsername("userD1", uniqueId)),
                  new UserPrincipal(produceUsername("userD2", uniqueId)),
                  new UserPrincipal(this.specialUser)));
      resp.setAcl(aclBuilder.build());
      resp.setContentType(contentType);
      resp.setCrawlOnce(true);
      OutputStream os = resp.getOutputStream();
      os.write(content.getBytes(encoding));
      log.fine("returning from getDocId(" + uniqueId + ")");
      return;
    }

    ensureValidId(uniqueId);

    // make index.html or make content
    String content = null;
    String parts[] = uniqueId.split("/", 0); // drop trailing empties
    DocId parentId = makeParentId(parts);

    Acl.Builder aclBuilder =
        new Acl.Builder()
            .setInheritFrom(parentId)
            .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
            .setPermitUsers(Arrays.asList(
                new UserPrincipal(produceUsername("userP1", uniqueId)),
                new UserPrincipal(produceUsername("userP2", uniqueId))))
            .setDenyUsers(Arrays.asList(
                new UserPrincipal(produceUsername("userD1", uniqueId)),
                new UserPrincipal(produceUsername("userD2", uniqueId)),
                new UserPrincipal(this.specialUser)));

    if (!uniqueId.endsWith("/")) {
      // is file; override some parts of builder and make content file
      aclBuilder.setInheritanceType(Acl.InheritanceType.LEAF_NODE);
      if (isPublicFile(uniqueId)) {
        aclBuilder = new Acl.Builder();
      } else if (isSpecialFile(uniqueId)) {
        aclBuilder
            .setPermitUsers(Arrays.asList(
                new UserPrincipal(this.specialUser)))
            .setDenyUsers(Arrays.asList(
                new UserPrincipal(produceUsername("userD1", uniqueId)),
                new UserPrincipal(produceUsername("userD2", uniqueId))));
      }
      content = makeContent();
      resp.setContentType("text/plain; charset=utf-8");
    } else {
      // is directory
      // number of parts with empties at end stripped
      if (parts.length >= this.depth || parts.length < 1) {
        throw new IllegalStateException("bad id: " + id);
      }
      if ((this.depth - 1) == parts.length) {
        content = makeContentFileLinks(this.branches.get(parts.length));
      } else {
        content = makeSubfolderLinks(this.branches.get(parts.length), uniqueId);
      }
      resp.setContentType(contentType);
    }

    resp.setAcl(aclBuilder.build());
    resp.setCrawlOnce(false);
    OutputStream os = resp.getOutputStream();
    os.write(content.getBytes(encoding));
    log.fine("returning from getDocId(" + uniqueId + ")");
  }

  private DocId makeParentId(String[] parts) {
    if (parts.length <= 1) {
      return new DocId("");
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length - 1; i++) {
      sb.append(parts[i]).append("/");
    }
    // keep last slash, because parent is always a directory
    return new DocId(sb.toString());
  }

  private String makeSubfolderLinks(int numOfFolders, String uniqueId) {
    // if uniqueId is 0x/0xx/0xxx/, then the names to be made here should be
    // 0xxxx, 1xxxx, 2xxxx ...
    int folderDepth = uniqueId.split("/", -1).length; // keep trailing empties
    StringBuilder sb = new StringBuilder();
    sb.append("<body>\n");
    for (int i = 0; i < numOfFolders; i++) {
      String filename = i + this.padByLength[folderDepth];
      sb.append("<a href=\"")
          .append(filename)
          .append("/")
          .append("\">")
          .append(filename)
          .append("</a></br>\n");
    }
    sb.append("</body>\n");
    return sb.toString();
  }

  private String makeContentFileLinks(int numOfFiles) {
    StringBuilder sb = new StringBuilder();
    sb.append("<body>\n");
    for (int i = 0; i < numOfFiles; i++) {
      int filename = i;
      sb.append("<a href=\"")
          .append(filename)
          .append("\">")
          .append(filename)
          .append("</a></br>\n");
    }
    sb.append("</body>\n");
    return sb.toString();
  }

  /* generates a text file of about 20K consisting of random dictionary words,
   * starting with the word "document" (so that public searches can easily find
   * all the documents). */
  private static String makeContent() {
    StringBuilder sb = new StringBuilder();
    sb.append("document");
    int lines = 0;
    int linelength = 8;
    while (lines < NUMBER_OF_LINES) {
      String next = getRandomWord();
      if (next.length() + linelength <= 80) {
        sb.append(" ").append(next);
        linelength += 1 + next.length();
      } else {
        sb.append("\n").append(next);
        linelength = next.length();
        lines++;
      }
    }
    return sb.toString();
  }

  private void ensureValidId(String id) {
    // make sure doc id makes sense; we know it is not root.
    // examples of valid ids:
    // "test/"
    // "test/15/"
    // "test/<some more folders>/
    // "test/15/10/<some more folders>/filename"

    // -1 means we want number of parts with empties at end kept
    String parts[] = id.split("/", -1);
    if (parts.length > this.depth || parts.length < 2) {
      throw new IllegalStateException("bad id: " + id);
    }
  }

  private String produceUsername(String prefix, String uniqueId) {
    return prefix + Math.abs(uniqueId.hashCode());
  }

  private boolean isSpecialFile(String docId) {
    int hashcode = docId.hashCode();
    int mod = this.totalNumberOfFiles / this.specialFileCount;
    if (mod == 0) {
      return true;
    }
    int result = hashcode % mod;
    if (result < 0) {
      result += mod;
    }
    return result == 1;
  }

  private boolean isPublicFile(String docId) {
    int hashcode = docId.hashCode();
    int mod = this.totalNumberOfFiles / this.publicFileCount;
    if (mod == 0) {
      return true;
    }
    int result = hashcode % mod;
    if (result < 0) {
      result += mod;
    }
    return result == 1;
  }

  /** Call default main for adaptors.
   *  @param args argv
   *
   *  @throws Exception when something goes wrong
   */
  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[args.length - 1].equals("-printExampleContent")) {
      initDictionary();
      String content = makeContent();
      System.out.println(content);
      System.exit(0);
    }
    AbstractAdaptor.main(new StresserAdaptor(), args);
  }
}
