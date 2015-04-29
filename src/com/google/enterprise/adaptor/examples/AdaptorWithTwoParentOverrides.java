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
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/** Example with depth three ACL chain. */
public class AdaptorWithTwoParentOverrides extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(AdaptorWithTwoParentOverrides.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  /** Gives list of document ids that you'd like on the GSA. */
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    mockDocIds.add(new DocId("website"));
    mockDocIds.add(new DocId("website/subsite"));
    mockDocIds.add(new DocId("website/subsite/file"));
    pusher.pushDocIds(mockDocIds);
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    if ("website".equals(id.getUniqueId())) {
      resp.setAcl(makeWebsiteAcl());
    } else if ("website/subsite".equals(id.getUniqueId())) {
      resp.setAcl(makeSubsiteAcl());
    } else if ("website/subsite/file".equals(id.getUniqueId())) {
      resp.setAcl(makeFileAcl());
    } else {
      resp.respondNotFound();
      return;
    }
    resp.setContentType("text/plain; charset=utf-8");
    OutputStream os = resp.getOutputStream();
    String str = "this file doc id is: " + id;
    os.write(str.getBytes(encoding));
  }
  
  private Acl makeWebsiteAcl() {
    List<UserPrincipal> deniedUsers = Arrays.asList(
        new UserPrincipal("vin")
    );
    return new Acl.Builder()
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setDenyUsers(deniedUsers)
        .setEverythingCaseInsensitive()
        .build();
  }
  
  private Acl makeSubsiteAcl() {
    List<UserPrincipal> permitUsers = Arrays.asList(
        new UserPrincipal("vin")
    );
    return new Acl.Builder()
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitUsers(permitUsers)
        .setInheritFrom(new DocId("website"))
        .setEverythingCaseInsensitive()
        .build();
  }
  
  private Acl makeFileAcl() {
    return new Acl.Builder()
        .setInheritFrom(new DocId("website/subsite"))
        .setEverythingCaseInsensitive()
        .build();
  }

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new AdaptorWithTwoParentOverrides(), args);
  }
}
