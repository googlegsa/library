// Copyright 2013 Google Inc. All Rights Reserved.
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
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;

/** Demonstrates sending group definitions to GSA. */
public class GroupDefinitionsWriter extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(GroupDefinitionsWriter.class.getName());
  private static Charset encoding = Charset.forName("UTF-8");

  private static UserPrincipal u(String name) {
    return new UserPrincipal(name);
  }

  private static Map<GroupPrincipal, List<Principal>> defineSomeGroups() {
     Map<GroupPrincipal, List<Principal>> groups
         = new TreeMap<GroupPrincipal, List<Principal>>();
     groups.put(new GroupPrincipal("coppers"), Arrays.<Principal>asList(
         u("JohnLuther"), u("GerryBoyle"), u("DirtyHarry")));
     groups.put(new GroupPrincipal("Q"), Arrays.<Principal>asList(
         u("OliviaDAbo"), u("JohnDeLancie")));
     groups.put(new GroupPrincipal("all"), Arrays.<Principal>asList(
         u("DaffyDuck"), u("BugsBunny"), new GroupPrincipal("coppers")));
     return groups;
  }
  
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    boolean caseSensitive = true;
    pusher.pushGroupDefinitions(defineSomeGroups(), caseSensitive);
  }

  @Override
  public void getDocContent(Request req, Response res) throws IOException {
    res.respondNotFound();
  }
 
  public static void main(String args[]) {
    AbstractAdaptor.main(new GroupDefinitionsWriter(), args);
  }
}
