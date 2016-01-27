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

// A slightly-modified version of AclPopulator:
// 20 million files (instead of 500,000)
// the files contain dictionary words (instead of numbers)
// 10,000 of the leaf files are marked public
// we call setCrawlOnce(false), so that documents get re-fetched (multiple times).

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Result of running this adaptor:
 *   1. CHILD_OVERRIDES ACLs with inheritance chain of depth 10
 *   2. 20M docs, each about 20KB in size
 *   3. There is a special user who has access to roughly 100 files and will be
 *      denied access to all other files. The 100 files are uniformly
 *      distributed around all leaves.
 *   4. For all the files/directories other than the 100 files which the
 *      special user has access to, 10001 in total will be marked as public.
 *      The rest will have 2 random permitted users and 2 unique denied users.
 *      The users' names are uniquely made up, e.g.,
 *      user1, user123. Note that the unique users get changed with every
 *      recrawl.
 *
 */
public class StresserAdaptor extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(StresserAdaptor.class.getName());
  private static Charset encoding = Charset.forName("UTF-8");

  private final String specialUser = "specialUser";
  private final int specialFileCount = 100;
  private final int publicFileCount = 10001;
  /*
   * 267 means roughly 20KB. Each file has 267 lines and each line contains
   * approximately 75 characters and a newline.  So the file is roughly 20KB.
   */
  private static final int NUMBER_OF_LINES = 267;
  private static final int NUMBER_OF_WORDS = 99171;
  private static final String DICTIONARY_FILE = "/usr/share/dict/words";
  private static final String[] dictionary = new String[NUMBER_OF_WORDS];
  private static final Random rand = new Random();

  private List<Integer> branches = null;
  private int aclChainLength;
  private int depth; // since root (/) also has ACL,
                     // so the folder depth is aclChainLength - 1

  private int userCount;
  private int totalNumberOfFiles;

  // pre-computed format string to be used in making up url
  private String padByLength[];

  private static void initDictionary() throws Exception {

    // prepare the dictionary
    Scanner sc = new Scanner(new File(DICTIONARY_FILE));
    int word = 0;
    while (sc.hasNext()) {
      dictionary[word++] = sc.next();
    }
    sc.close();
  }

  @Override
  public void initConfig(Config config) {
    config.addKey("adaptor.branches", null);
  }

  @Override
  public void init(AdaptorContext context) throws Exception {

    Config config = context.getConfig();

    parseBranches(config.getValue("adaptor.branches"));
    if (branches == null) {
      // example: 2 * 2 * 5 * 10 * 10 * 10 * 10 * 10 * 10 = 20M
      branches = Arrays.asList(2, 2, 5, 10, 10, 10, 10, 10, 10);
    }

    if (branches.size() <= 0) {
      // Here we throw AssertionError because if branches is not in a good
      // shape, this is really an error in code instead of a user input.
      throw new AssertionError("Bad folder structure. "
          + "The depth should be at least 1.");
    }

    aclChainLength = branches.size() + 1;
    depth = branches.size();
    userCount = 0;
    totalNumberOfFiles = 1;
    for (int i = 0; i < branches.size(); ++i) {
      totalNumberOfFiles *= branches.get(i);
    }

    padByLength = new String[branches.size() + 1];
    padByLength[0] = "";
    for (int i = 1; i <= branches.size(); ++i) {
      padByLength[i] = padByLength[i - 1] + "x";
    }

    initDictionary();
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    log.info("Calling getDocIds()...");
    List<DocId> rootDocId = Arrays.asList(new DocId(""));
    pusher.pushDocIds(rootDocId);
    log.info("returning from getDocIds().");
  }

  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    String uniqueId = id.getUniqueId();
    log.fine("Calling getDocId(" + uniqueId + ")...");
    String contentType = "text/html";

    if ("".equals(uniqueId)) {
      String content = null;
      if (branches.size() == 1) {
        content = makeFiles(branches.get(0));
      } else {
        content = makeSubfolders(branches.get(0), uniqueId);
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
      resp.setCrawlOnce(false);
      OutputStream os = resp.getOutputStream();
      os.write(content.getBytes(encoding));
      log.fine("returning from getDocId(" + uniqueId + ").");
      return;
    }

    ensureValidId(uniqueId);

    // make index.html or make content
    String content = null;
    String parts[] = uniqueId.split("/", 0); // drop trailing empties
    DocId parentId = makeParentId(parts); // is a dir; ends in "/" or is ""

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
      // is file
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
        throw new IllegalStateException("Bad id: " + id);
      }
      if ((this.depth - 1) == parts.length) {
        content = makeFiles(this.branches.get(parts.length));
      } else {
        content = makeSubfolders(this.branches.get(parts.length), uniqueId);
      }
      resp.setContentType(contentType);
    }

    resp.setAcl(aclBuilder.build());
    resp.setCrawlOnce(false);
    OutputStream os = resp.getOutputStream();
    os.write(content.getBytes(encoding));
    log.fine("returning from getDocId(" + uniqueId + ").");
  }

  private String makeSubfolders(int numOfFolders, String uniqueId) {
    // if uniqueId is 0x/0xx/0xxx/, then the filename to be made here should be
    // 0xxxx, 1xxxx, 2xxxx, etc.
    int folderDepth = uniqueId.split("/", -1).length; // keep trailing empties
    StringBuilder sb = new StringBuilder();
    sb.append("<body>\n");
    for (int i = 0; i < numOfFolders; ++i) {
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

  private String makeFiles(int numOfFiles) {
    StringBuilder sb = new StringBuilder();
    sb.append("<body>\n");
    for (int i = 0; i < numOfFiles; ++i) {
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

  private static String getRandomWord() {
    return dictionary[rand.nextInt(NUMBER_OF_WORDS)];
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

  private DocId makeParentId(String[] parts) {
    if (parts.length <= 1) {
      return new DocId("");
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length - 1; ++i) {
      sb.append(parts[i]).append("/");
    }
    // keep last slash, because parent is always a directory
    return new DocId(sb.toString());
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
      throw new IllegalStateException("Bad id: " + id);
    }
  }

  private String produceUsername(String prefix, String uniqueId) {
    return prefix + Math.abs(uniqueId.hashCode());
  }

  private boolean isSpecialFile(String docId) {
    int hashcode = docId.hashCode();
    int mod = this.totalNumberOfFiles / this.specialFileCount;
    int result = hashcode % mod;
    if (result < 0) {
      result += mod;
    }

    return result == 1;
  }

  private boolean isPublicFile(String docId) {
    int hashcode = docId.hashCode();
    int mod = this.totalNumberOfFiles / this.publicFileCount;
    int result = hashcode % mod;
    if (result < 0) {
      result += mod;
    }

    return result == 1;
  }

  // copied nearly directly from various routines in NamedResourceFirehose.java
  private void parseBranches(String configBranches)
      throws NumberFormatException, IllegalStateException {
    // sanity check
    configBranches = configBranches.replaceAll("\\s+", "");
    String str[] = configBranches.split(",", 0); // drop trailing empties
    if (str.length == 0) {
      return;
    }

    // populate this.branches
    this.branches = new ArrayList<Integer>(str.length);
    for (int i = 0; i < str.length; ++i) {
      this.branches.add(Integer.parseInt(str[i]));
      if (this.branches.get(i) <= 0) {
        throw new IllegalStateException("branch [" + i
            + "] can not be negative or zero");
      }
    }
    log.log(Level.INFO, "branches : "
        + Arrays.toString(this.branches.toArray()));
  }

  /** Call default main for adaptors.
   *  @param args argv
   */
  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[args.length - 1].equals("-makeContentOnly")) {
      initDictionary();
      String content = makeContent();
      System.out.println(content);
      System.exit(0);
    }
    AbstractAdaptor.main(new StresserAdaptor(), args);
  }
}
