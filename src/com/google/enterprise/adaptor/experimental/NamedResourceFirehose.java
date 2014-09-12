// Copyright 2014 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.enterprise.adaptor.AsyncDocIdPusher;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.DocIdPusher.Record;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.PollingIncrementalLister;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Result of running this adaptor:
 *   1. Generate and push a configurable number of named resources to the GSA
 *      at a configurable rate. "namedResFirehose.numOfNamedResEachCycle"
 *      named resources per "adaptor.incrementalPollPeriodSecs" seconds.
 *   2. The generated named resources are emulating a file system. And the
 *      pathes of directories and files match those of AclPopulator.
 *
 */
public class NamedResourceFirehose extends AbstractAdaptor
    implements PollingIncrementalLister {
  private static final Logger log
      = Logger.getLogger(NamedResourceFirehose.class.getName());

  private static final String EXTRA_ACL_SUFFIX;
  private static final Date POINT_IN_PAST;

  static {
    EXTRA_ACL_SUFFIX = "&ExtraACL";

    Calendar c = new GregorianCalendar();
    c.set(2012, 9, 9, 0, 0, 0);
    POINT_IN_PAST = c.getTime();
  }

  private AsyncDocIdPusher asyncPusher = null;

  // cycle is when the getModifiedDocIds is called by the adaptor library
  private int numOfNamedResEachCycle = -1;
  private int[] branches = null;
  // partsOfDocId is used in making up docIds. Suppose a docId generated in
  // this adaptor like "0x/1xx/2xxx", and then in this example,
  // partsOfDocId = ["0x", "/", "1xx", "/", "2xxx"].
  private String[] partsOfDocId = null;

  // pre-computed format string to be used in making up url. This is to avoid
  // GSA infinite space detection.
  private String padByLength[];

  // counter for the number of named resources we have pushed in this cycle
  private int numNamedResPushed = 0;

  private String urlPrefix;

  @Override
  public void initConfig(Config config) {
    config.addKey("namedResFirehose.numOfNamedResEachCycle", null);
    config.addKey("namedResFirehose.branches", null);
    config.addKey("docId.prefix", "");
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    context.setPollingIncrementalLister(this);
    this.asyncPusher = context.getAsyncDocIdPusher();
    Config config = context.getConfig();
    this.numOfNamedResEachCycle = Integer.parseInt(
        config.getValue("namedResFirehose.numOfNamedResEachCycle"));

    parseBranches(config.getValue("namedResFirehose.branches"));
    checkArgs();

    this.partsOfDocId = new String[this.branches.length * 2 - 1];
    for (int i = 0; i < this.partsOfDocId.length; ++i) {
      if (i % 2 == 1) {
        this.partsOfDocId[i] = "/"; // valid docId is like 0x/1xx/2xxx/3xxxx
      }
    }

    this.padByLength = new String[this.branches.length];
    this.padByLength[0] = "x";
    for (int i = 1; i < this.branches.length; ++i) {
      this.padByLength[i] = this.padByLength[i - 1] + "x";
    }

    this.urlPrefix = config.getValue("docId.prefix");
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
  }

  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
  }

  @Override
  public void getModifiedDocIds(DocIdPusher pusher)
      throws IOException, InterruptedException {
    // push root dir
    pushDocId("");

    // push named resource until enough, indicated by numOfNamedResEachCycle
    pushAllDocIdsRecursively(0);

    resetCounters();
  }

  private void resetCounters() {
    this.numNamedResPushed = 0;
  }

  private void pushAllDocIdsRecursively(int cursor) {
    for (int i = 0; i < this.branches[cursor]; ++i) {
      this.partsOfDocId[cursor * 2] = String.valueOf(i)
          + this.padByLength[cursor];
      if (cursor + 1 < this.branches.length) {
        if (this.numNamedResPushed < this.numOfNamedResEachCycle) {
          pushDocId(buildDocId((cursor + 1) * 2));
          pushAllDocIdsRecursively(cursor + 1);
        }
      } else {
        if (this.numNamedResPushed < this.numOfNamedResEachCycle) {
          pushDocId(buildDocId(this.partsOfDocId.length));
        }
      }
    }
  }

  private String buildDocId(int numOfPartsToInclude) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < numOfPartsToInclude; ++i) {
      sb.append(this.partsOfDocId[i]);
    }
    return sb.toString();
  }

  /*
   * If the docId is a file, we will push three items. One is a record of which
   * the url is like http://<adaptor>:<port>/doc/<docIdStr>. The second is an
   * Acl, of which the url will be that of the record. The third is an Acl, of
   * which the url will be http://<adaptor>:<port>/doc/<docIdStr>&amp;ExtraACL.
   * The second Acl will inherit from the third Acl. And the third Acl will
   * inherit from the Acl of the parent directory of the file.
   */
  private void pushDocId(String docIdStr) {
    docIdStr = this.urlPrefix + docIdStr;
    if (docIdStr.equals(this.urlPrefix)) {
      Acl.Builder aclBuilder = new Acl.Builder();
      aclBuilder
          .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
          .setPermitUsers(Arrays.asList(
              new UserPrincipal("administrator"),
              new UserPrincipal("user1")))
          .setPermitGroups(Arrays.asList(
              new GroupPrincipal("administrators"),
              new GroupPrincipal("everyone")));
      this.asyncPusher.pushNamedResource(new DocId(docIdStr),
          aclBuilder.build());
      this.numNamedResPushed += 1;
    } else {
      DocId parentId = makeParentId(docIdStr);

      if (docIdStr.endsWith("/")) {
        // is directory
        Acl.Builder aclBuilder = new Acl.Builder();
        aclBuilder
            .setInheritFrom(parentId)
            .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES);
        this.asyncPusher.pushNamedResource(new DocId(docIdStr),
            aclBuilder.build());
        this.numNamedResPushed += 1;
      } else {
        // is file
        // first, the extra acl
        DocId extrAcl = new DocId(docIdStr
            + NamedResourceFirehose.EXTRA_ACL_SUFFIX);
        Acl.Builder extrAclBuilder = new Acl.Builder();
        extrAclBuilder
            .setInheritFrom(parentId)
            .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES);
        this.asyncPusher.pushNamedResource(extrAcl, extrAclBuilder.build());
        this.numNamedResPushed += 1;

        // then, the file itself
        DocId file = new DocId(docIdStr);
        Record.Builder recordBuilder = new Record.Builder(file);
        recordBuilder.setLastModified(POINT_IN_PAST);
        this.asyncPusher.pushRecord(recordBuilder.build());
        this.numNamedResPushed += 1;

        // then, the acl for the file
        Acl.Builder aclBuilder = new Acl.Builder();
        aclBuilder.setInheritFrom(extrAcl);
        this.asyncPusher.pushNamedResource(file, aclBuilder.build());
        this.numNamedResPushed += 1;
      }
    }
  }

  private DocId makeParentId(String uniqueId) {
    String str[] = uniqueId.split("/", 0); // drop trailing empties
    if (str.length <= 1) {
      return new DocId("");
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < str.length - 1; ++i) {
      sb.append(str[i]).append("/");
    }
    return new DocId(sb.toString());
  }

  private String produceUsername(String prefix, String uniqueId) {
    return prefix + Math.abs(uniqueId.hashCode());
  }

  private void parseBranches(String configBranches)
      throws NumberFormatException {
    // sanity check
    configBranches = configBranches.replaceAll("\\s+", "");
    String str[] = configBranches.split(",", 0); // drop trailing empties
    if (str.length == 0) {
      return;
    }

    // populate this.branches
    this.branches = new int[str.length];
    for (int i = 0; i < str.length; ++i) {
      this.branches[i] = Integer.parseInt(str[i]);
    }
  }

  private void checkArgs() throws IllegalStateException {
    if (this.numOfNamedResEachCycle < 0) {
      throw new IllegalStateException("numOfNamedResEachCycle can not be "
          + "negative");
    }

    if (this.branches == null) {
      throw new IllegalStateException("branches is not populated");
    }

    int numOfFiles = 1; // multiplication base
    for (int i = 0; i < this.branches.length; ++i) {
      if (this.branches[i] <= 0) {
        throw new IllegalStateException("branch can not be negative or zero");
      }
      numOfFiles *= this.branches[i];
    }

    int numOfDirs = 1; // root dir
    int lastFanOut = 1;
    for (int i = 0; i < this.branches.length - 1; ++i) {
      lastFanOut *= branches[i];
      numOfDirs += lastFanOut;
    }

    int numOfNamedRes = numOfFiles + numOfDirs;
    if (numOfNamedRes < this.numOfNamedResEachCycle) {
      throw new IllegalStateException("branches can not provide enough named "
          + "resources");
    }

    printArgs();
  }

  private void printArgs() {
    log.log(Level.CONFIG,
        "numOfNamedResEachCycle : " + this.numOfNamedResEachCycle);
    log.log(Level.CONFIG, "branches : " + Arrays.toString(this.branches));
  }

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new NamedResourceFirehose(), args);
  }
}
