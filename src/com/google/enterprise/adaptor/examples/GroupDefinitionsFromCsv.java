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

package com.google.enterprise.adaptor.examples;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Reads memberships from CSV, forms groups, and sends to to GSA. */
public class GroupDefinitionsFromCsv extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(GroupDefinitionsFromCsv.class.getName());
  private static Charset encoding = Charset.forName("UTF-8");

  private File csvFile;
  private String domain;

  @Override
  public void initConfig(Config config) {
    config.addKey("csv.filename", null);
    config.addKey("csv.domain", null);
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    String fname = context.getConfig().getValue("csv.filename");
    csvFile = new File(fname);
    if (!csvFile.exists() || !csvFile.isFile()) {
      throw new IllegalStateException("cannot find file: " + fname);
    }
    domain =  context.getConfig().getValue("csv.domain");
  }
  
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    try {
      getDocIdsHelper(pusher);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void getDocIdsHelper(DocIdPusher pusher) throws InterruptedException,
      IOException {
    BufferedReader br = new BufferedReader(new FileReader(csvFile));
    String line = br.readLine();
    if (null == line) {
      throw new IllegalStateException("no content in csv file");
    }
    if (!"GROUPID,ENTITYID".equalsIgnoreCase(line)) {
      throw new IllegalStateException("invalid csv header line"); 
    }

    Map<String, List<String>> groupdefs
        = new TreeMap<String, List<String>>();
    while ((line = br.readLine()) != null) {
      String parts[] = line.split(",", -1); 
      if (2 != parts.length) {
        throw new IllegalStateException("invalid csv line: " + line);
      }
      String groupid = parts[0];
      if (!groupdefs.containsKey(groupid)) {
        groupdefs.put(groupid, new LinkedList<String>());
      }
      if (!"".equals(parts[1])) {
        String entity = parts[1];
        groupdefs.get(groupid).add(entity);
      }
    }

    boolean caseSensitive = true;
    pusher.pushGroupDefinitions(convert(groupdefs), caseSensitive);
  }

  private String makeName(String id) {
    return "" + id + "@" + domain;
  }

  private Map<GroupPrincipal, List<Principal>> convert(
      Map<String, List<String>> src) {
    Map<GroupPrincipal, List<Principal>> dest
        = new TreeMap<GroupPrincipal, List<Principal>>();
    for (Map.Entry<String, List<String>> e : src.entrySet()) {
      int nentities = e.getValue().size();
      List<Principal> entities = new ArrayList<Principal>(nentities);
      for (int i = 0; i < nentities; i++) {
        String id = e.getValue().get(i);
        String name = makeName(id);
        boolean entityIsGroup = src.containsKey(id);
        if (entityIsGroup) {
          entities.add(new GroupPrincipal(name));
        } else {
          entities.add(new UserPrincipal(name));
        }
      }
      dest.put(new GroupPrincipal(makeName(e.getKey())), entities);
    }
    return dest;
  }

  @Override
  public void getDocContent(Request req, Response res) throws IOException {
    res.respondNotFound();
  }
 
  public static void main(String args[]) {
    AbstractAdaptor.main(new GroupDefinitionsFromCsv(), args);
  }
}
