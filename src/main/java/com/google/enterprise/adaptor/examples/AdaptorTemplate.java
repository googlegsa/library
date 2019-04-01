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
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Demonstrates what code is necessary for putting public
 * content onto a GSA.  The key operations are:
 * <ol><li> providing document ids
 *   <li> providing document bytes given a document id</ol>
 */
public class AdaptorTemplate extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(AdaptorTemplate.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  /** Gives list of document ids that you'd like on the GSA. */
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    /* Replace this mock data with code that lists your repository. */
    mockDocIds.add(new DocId("1001"));
    mockDocIds.add(new DocId("1002"));
    pusher.pushDocIds(mockDocIds);
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    String str;
    if ("1001".equals(id.getUniqueId())) {
      str = "Document 1001 says hello and apple orange";
    } else if ("1002".equals(id.getUniqueId())) {
      str = "Document 1002 says hello and banana strawberry";
    } else {
      resp.respondNotFound();
      return;
    }
    resp.setContentType("text/plain; charset=utf-8");
    OutputStream os = resp.getOutputStream();
    os.write(str.getBytes(encoding));
  }

  /** Call default main for adaptors. 
   *  @param args argv
   */
  public static void main(String[] args) {
    AbstractAdaptor.main(new AdaptorTemplate(), args);
  }
}
