// Copyright 2014 Google Inc. All Rights Reserved.
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
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Result of running this adaptor: 
 *   1. CHILD_OVERRIDES ACLs with inheritance chain of depth 10 
 *   2. 500K docs, say 20KB in size 
 *   3. There is a special user who has access to roughly 100 files and will be 
 *      denied access to all other files. The 100 files are uniformly 
 *      distributed around all leaves.
 *   4. For all the files/directories other than the 100 files which the 
 *      special user has access to, they will have 2 random permitted users and
 *      2 unique denied users. The users' names are uniquely made up, e.g., 
 *      user1, user123. Note that the unique users get changed with every 
 *      recrawl.
 * 
 */
public class AclPopulator extends AbstractAdaptor {
  private static final Logger log 
      = Logger.getLogger(AclPopulator.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  private final String specialUser = "specialUser";
  private final int specialFileCount = 100;
  /*
   * 2000 means roughly 20KB. Each file has 2000 lines and each line contains
   * one 9-digit number and a newline. So the file is roughly 20KB.
   */
  private final int numberOfTenByteLines = 2000;

  private List<Integer> branches;
  private int aclChainLength;
  private int depth; // since root (/) also has ACL,
                     // so the folder depth is aclChainLength - 1

  private int userCount;
  private int totalNumberOfFiles;

  // pre-computed format string to be used in making up url
  private String padByLength[];

  @Override
  public void init(AdaptorContext context) throws Exception {
    // 2 * 2 * 2 * 5 * 5 * 5 * 5 * 10 * 10 = 500,000
    branches = Arrays.asList(2, 2, 2, 5, 5, 5, 5, 10, 10);
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
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {}

  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    String uniqueId = id.getUniqueId();

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

      resp.setContentType("text/html");
      resp.setCrawlOnce(true);
      OutputStream os = resp.getOutputStream();
      os.write(content.getBytes(encoding));
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
      if (isSpecialFile(uniqueId)) {
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
      resp.setContentType("text/html");
    }

    resp.setAcl(aclBuilder.build());
    resp.setCrawlOnce(true);
    OutputStream os = resp.getOutputStream();
    os.write(content.getBytes(encoding));
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

  private String makeContent() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < this.numberOfTenByteLines; ++i) {
      // make it %09d so that we a number takes roughly 10 bytes, including the
      // trailing \n
      sb.append(String.format("%09d", i)).append("\n");
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

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new AclPopulator(), args);
  }
}
