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
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Tool to populate GSA with lots of ACLs.
 */
public class AclPopulator extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(AclPopulator.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  private ThreadLocal<Random> rnd = new ThreadLocal<Random>() {
    public Random initialValue() {
      return new Random();
    }
  };

  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
  }

  private static final String TOP_LEVEL_DIRS[] = new String[] {
      "eng",
      "pm",
      "qa",
      "googlers",
      "test",
      "abcde",
      "enterprise",
      "ops",
      "cowboys",
      "cowgirls"
  };
  private static final String SPECIAL_PERMIT_DIR = "googlers/";

  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    String uniqueId = id.getUniqueId();

    if ("".equals(uniqueId)) {
      String content = makeTopLevelIndexFile();
      Acl.Builder aclBuilder = new Acl.Builder()
          .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
          .setPermitUsers(Arrays.asList(new UserPrincipal("vin")))
          .setDenyUsers(Arrays.asList(new UserPrincipal("joker")));
      resp.setAcl(aclBuilder.build());

      resp.setContentType("text/html");
      OutputStream os = resp.getOutputStream();
      os.write(content.getBytes(encoding));
      return;
    }

    ensureValidId(uniqueId);

    // make index.html or make content
    String content = null;
    String parts[] = uniqueId.split("/", 0); // drop trailing empties
    DocId parentId = makeParentId(parts); // ends with slash; parent is a dir

    Acl.Builder aclBuilder = new Acl.Builder()
        .setInheritFrom(parentId)
        .setInheritanceType(Acl.InheritanceType.CHILD_OVERRIDES)
        .setPermitUsers(Arrays.asList(new UserPrincipal("vin")))
        .setDenyUsers(Arrays.asList(new UserPrincipal("joker")));

    if (!uniqueId.endsWith("/")) {
      // is file
      if (uniqueId.contains(SPECIAL_PERMIT_DIR)) {
        aclBuilder.setPermitUsers(Arrays.asList(new UserPrincipal("siyu")));
      }
      content = makeContent();
      resp.setContentType("text/plain; charset=utf-8");
    } else {
      // is directory
      switch(parts.length) {  // number of parts with empties at end stripped
        case 1:
          content = makeSubfolders(3);
          break;
        case 2:
          content = makeSubfolders(2);
          break;
        case 3:
          content = makeFiles(4);
          break;
        default:
          throw new IllegalStateException("bad id: " + uniqueId);
      }
      resp.setContentType("text/html");
    }

    resp.setAcl(aclBuilder.build());
    OutputStream os = resp.getOutputStream();
    os.write(content.getBytes(encoding));
  }

  private String makeSubfolders(int numOfFolders) {
    StringBuilder sb = new StringBuilder();
    sb.append("<body>\n");
    for (int i = 0; i < numOfFolders; ++i) {
      int filename = i;
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
    int tenBillion = 1000 * 1000 * 1000;
    for (int i = 0; i < 5000; ++i) {
      sb.append(rnd.get().nextInt(tenBillion));
      sb.append("\n");
    }
    return sb.toString();
  }

  private String makeTopLevelIndexFile() {
    StringBuilder sb = new StringBuilder();
    sb.append("<body>\n");
    for (String topLevelDir : TOP_LEVEL_DIRS) {
      sb.append("<a href=\"")
          .append(topLevelDir)
          .append("/")
          .append("\">")
          .append(topLevelDir)
          .append("/")
          .append("</a></br>\n");
    }
    sb.append("</body>\n");
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

  private static void ensureValidId(String id) {
    // make sure doc id makes sense; we know it is not root.
    // examples of valid ids:
    //    "eng/"
    //    "test/15/"
    //    "test/15/10/"
    //    "test/15/10/filename"
    String parts[] = id.split("/", -1);
    switch(parts.length) {  // number of parts with empties at end kept
      case 2:
      case 3:
      case 4: break;
      default: throw new IllegalStateException("bad id: " + id);
    }
  }

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new AclPopulator(), args);
  }
}
