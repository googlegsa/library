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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Creates groups and users and pushes some number of principals per group. */
public class GroupDefinitionsScaleTester extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(GroupDefinitionsScaleTester.class.getName());

  private int nusers;
  private int ngroups;
  private int nuserspergroup;
  private int ngroupspergroup;

  private String namespace;
  private String domain;

  private Random rander = new Random();

  @Override
  public void initConfig(Config config) {
    config.addKey("test.numusers", null);
    config.addKey("test.numgroups", null);
    config.addKey("test.userspergroup", null);
    config.addKey("test.grouppergroup", null);
    config.addKey("test.namespace", "duper-draper-scarlet-and-bond");
    config.addKey("test.domain", "duperduperdomain.net");
  }

  private static int toInt(String s) {
    return Integer.parseInt(s);
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    nusers = toInt(context.getConfig().getValue("test.numusers"));
    ngroups = toInt(context.getConfig().getValue("test.numgroups"));
    nuserspergroup = toInt(context.getConfig().getValue("test.userspergroup"));
    ngroupspergroup = toInt(context.getConfig().getValue("test.grouppergroup"));
    namespace = context.getConfig().getValue("test.namespace");
    domain = context.getConfig().getValue("test.domain");
  }

  private UserPrincipal makeUser(int i) {
    return new UserPrincipal("user" + i + "@" + domain, namespace);
  }

  private GroupPrincipal makeGroup(int i) {
    return new GroupPrincipal("group" + i + "@" + domain, namespace);
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException,
      IOException {
    for (int i = 0; i < ngroups; i++) {
      List<Principal> members = new ArrayList<Principal>();
      for (int j = 0; j < ngroupspergroup; j++) {
        int chosen = rander.nextInt(ngroups);
        members.add(makeGroup(chosen));
      }
      for (int j = 0; j < nuserspergroup; j++) {
        int chosen = rander.nextInt(nusers);
        members.add(makeUser(chosen));
      }
      GroupPrincipal group = makeGroup(i);
      Map<GroupPrincipal, List<Principal>> groupdef
          = Collections.singletonMap(group, members);
      final boolean caseSensitive = false;
      pusher.pushGroupDefinitions(groupdef, caseSensitive);
      log.log(Level.INFO, "pushed group {0} with {1} members", new Object[] {
          group, members.size()});
    }  
  }

  @Override
  public void getDocContent(Request req, Response res) throws IOException {
    res.respondNotFound();
  }
 
  public static void main(String args[]) {
    AbstractAdaptor.main(new GroupDefinitionsScaleTester(), args);
  }
}
