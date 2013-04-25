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
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.AuthzStatus;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;

/**
 * Demonstrates what code is necessary for putting restricted
 * content onto a GSA.  The key operations are:
 * <ol><li> providing document ids
 *   <li> providing document bytes and ACLs given a document id
 *   <li> restricting access to documents
 * </ol>
 */
public class AdaptorWithCrawlTimeMetadataTemplate extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(AdaptorWithCrawlTimeMetadataTemplate.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  /** Gives list of document ids that you'd like on the GSA. */
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    /* Replace this mock data with code that lists your repository. */
    mockDocIds.add(new DocId("7007"));
    mockDocIds.add(new DocId("7007-parent"));
    mockDocIds.add(new DocId("8008"));
    pusher.pushDocIds(mockDocIds);
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    String str;
    if ("7007".equals(id.getUniqueId())) {
      str = "Document 7007 is for surviving members of magnificent 7";
      // Add custom meta items.
      resp.addMetadata("my-special-key", "my-custom-value");
      resp.addMetadata("date", "not soon enough");
      // Add custom acl.
      resp.setAcl(makeAclFor7007());
      // Add other attributes.
      resp.setDisplayUrl(URI.create("https://www.google.com/"));
    } else if ("7007-parent".equals(id.getUniqueId())) {
      str = "I have a child named 7007";
      resp.setAcl(makeAclFor7007Parent());
      // Add custom meta items.
      resp.addMetadata("my-day", "parent's day");
    } else if ("8008".equals(id.getUniqueId())) {
      str = "Document 8008 says hello and banana strawberry";
      // Must add metadata before getting OutputStream
      resp.addMetadata("date", "never than late");
    } else {
      resp.respondNotFound();
      return;
    }
    resp.setContentType("text/plain; charset=utf-8");
    OutputStream os = resp.getOutputStream();
    os.write(str.getBytes(encoding));
  }

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new AdaptorWithCrawlTimeMetadataTemplate(), args);
  }
  
  private Acl makeAclFor7007() {
    List<UserPrincipal> users7007 = Arrays.asList(
        new UserPrincipal("chris@seven7.google.com"),
        new UserPrincipal("vin"), 
        new UserPrincipal("chico")
    );
    List<GroupPrincipal> groups7007 = Arrays.asList(
        new GroupPrincipal("magnificent@seven7.google.com"),
        new GroupPrincipal("cowboys@seven7.google.com")
    );
    List<UserPrincipal> deniedUsers7007 = Arrays.asList(
        new UserPrincipal("britt"),
        new UserPrincipal("harry@seven7.google.com"),
        new UserPrincipal("lee"),
        new UserPrincipal("bernardo@seven7.google.com"), 
        new UserPrincipal("calvera")
    );
    List<GroupPrincipal> deniedGroups7007 = Arrays.asList(
        new GroupPrincipal("dead"),
        new GroupPrincipal("samurai@seven7.google.com", "kurosawa"),
        new GroupPrincipal("dead", "kurosawa")
    );
    return new Acl.Builder()
        .setPermitUsers(users7007)
        .setDenyUsers(deniedUsers7007)
        .setPermitGroups(groups7007)
        .setDenyGroups(deniedGroups7007)
        .setInheritFrom(new DocId("7007-parent"))
        .setEverythingCaseInsensitive()
        .build();
  }

  private Acl makeAclFor7007Parent() {
    return new Acl.Builder()
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitUsers(Arrays.asList(new UserPrincipal("vin")))
        .setDenyUsers(Arrays.asList(new UserPrincipal("chico")))
        .build();
  }

  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity userIdentity,
      Collection<DocId> ids) throws IOException {
    Map<DocId, AuthzStatus> result
        = new HashMap<DocId, AuthzStatus>(ids.size() * 2);
    for (DocId id : ids) {
      String uid = id.getUniqueId();
      if ("7007".equals(uid)) {
        if (null == userIdentity) {
          // null for userIdentity means anonymous. To get non-null identity:
          // 1) Follow instructions for secure mode in src/overview.html
          // 2) Set config property "server.secure" to "true"
          // 3) Perform secure search on your GSA (triggers authentication)
          log.info("no authenticated user found");
          result.put(id, AuthzStatus.DENY); 
        } else {
          List<Acl> acl = Arrays.asList(
               makeAclFor7007Parent(), makeAclFor7007());
          result.put(id, Acl.isAuthorized(userIdentity, acl)); 
        }
      } else if ("7007-parent".equals(uid)) {
        if (null == userIdentity) {
          log.info("no authenticated user found");
          result.put(id, AuthzStatus.DENY); 
        } else {
          List<Acl> acl = Arrays.asList(makeAclFor7007Parent());
          result.put(id, Acl.isAuthorized(userIdentity, acl)); 
        }
      } else if ("8008".equals(id.getUniqueId())) {
        result.put(id, AuthzStatus.PERMIT); 
      } else {
        result.put(id, AuthzStatus.INDETERMINATE);
      }
    }
    return result;
  }
}
