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

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.PollingIncrementalLister;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Demonstrates what code is necessary for putting public content onto a GSA.
 * The key operations are:
 * <ol>
 * <li>providing document ids
 * <li>providing document bytes given a document id
 * </ol>
 */
public class HelloWorldConnector extends AbstractAdaptor implements
    PollingIncrementalLister {

    private static final Logger log = 
        Logger.getLogger(HelloWorldConnector.class.getName());
    int requestCount = 1, requestCount2 = 1;

    @Override
    public void init(AdaptorContext context) throws Exception {
      context.setPollingIncrementalLister(this);
      HelloWorldAuthenticator authenticator = 
          new HelloWorldAuthenticator(context);
      context.setAuthnAuthority(authenticator);
      context.setAuthzAuthority(authenticator);
      context.createHttpContext("/google-response", 
          new ResponseHandler(context));
    }

    /** Full crawl **/
    @Override
    public void getDocIds(DocIdPusher pusher) throws InterruptedException {
      ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
      // push docids
      mockDocIds.add(new DocId(""));
      pusher.pushDocIds(mockDocIds);
      // push records
      DocIdPusher.Record record = new DocIdPusher.Record.Builder(new DocId(
          "1009")).setCrawlImmediately(true).setLastModified(new Date())
          .build();
      pusher.pushRecords(Collections.singleton(record));
      // push named resources
      HashMap<DocId, Acl> aclParent = new HashMap<DocId, Acl>();
      ArrayList<Principal> permits = new ArrayList<Principal>();
      permits.add(new UserPrincipal("user1", "Default"));
      aclParent.put(new DocId("fakeID"), new Acl.Builder()
        .setEverythingCaseInsensitive().setPermits(permits)
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .build());
      pusher.pushNamedResources(aclParent);
      //push groups
      // pusher.pushGroupDefinitions(defs, caseSensitive, handler)
    }

    /** Gives the bytes of a document referenced with id. */
    @Override
    public void getDocContent(Request req, Response resp) throws IOException {  
      DocId id = req.getDocId();
      log.info("DocId '" + id.getUniqueId() + "'");

      // Hard-coded list of our doc id's
      if ("".equals(id.getUniqueId())) {
        // this is a the root folder, write some URLs
        Writer writer = new OutputStreamWriter(resp.getOutputStream());
        writer.write("<!DOCTYPE html>\n<html><body>");
        writer.write("<br></br>");
        writer.write("<a href=\"1001\">doc_not_changed</a>");
        writer.write("<br></b r>");
        writer.write("<a href=\"1002\">doc_changed</a>");
        writer.write("<br></br>");
        writer.write("<a href=\"1003\">doc_deleted</a>");
        writer.write("<br></br>");
        writer.write("<a href=\"1004\">doc_with_meta</a>");
        writer.write("<br></br>");
        writer.write("<a href=\"1005\">doc_with_ACL</a>");
        writer.write("<br></br>");
        writer.write("<a href=\"1006\">doc_with_ACL_Inheritance</a>");
        writer.write("<br></br>");
        writer.write("<a href=\"1007\">doc_with_Fragment</a>");
        writer.write("<br></br>");
        writer.write("<a href=\"1008\">doc_with_Fragment</a>");
        writer.write("<br></br>");
        writer.write("</body></html>");
        writer.close();
      } else if ("1001".equals(id.getUniqueId())) {
        // Example with If-Modified-Since
        req.hasChangedSinceLastAccess(new Date());
        if (req.getLastAccessTime() != null) { 
          // GSA is asking whether anything changed
          log.info("asked for last access");
          resp.respondNotModified();
        } else {
          log.info("No asked for last access");
          resp.setLastModified(new Date());
          Writer writer = new OutputStreamWriter(resp.getOutputStream());
          writer.write("Menu 1001 says latte");
          writer.close();
        }
      } else if ("1002".equals(id.getUniqueId())) {
        // Very basic doc
        Writer writer = new OutputStreamWriter(resp.getOutputStream());
        writer.write("Menu 1002 says cappuccino");
        writer.close();
      } else if ("1003".equals(id.getUniqueId())) {
        // Alternate between doc and a 404 response
        if (requestCount2 == 1) {
          Writer writer = new OutputStreamWriter(resp.getOutputStream());
          writer.write("Menu 1003 says machiato");
          writer.close();
          requestCount2 = 0;
        } else {
          resp.respondNotFound();
          requestCount2 = 1;
        }
      } else if ("1004".equals(id.getUniqueId())) {
        // doc with metdata & different display URL
        try {
          resp.addMetadata("flavor", "vanilla");
          resp.addMetadata("flavor", "hazel nuts");
          resp.addMetadata("taste", "strawberry");
          resp.setDisplayUrl(new URI("http://fake.com/a"));
          Writer writer = new OutputStreamWriter(resp.getOutputStream());
          writer.write("Menu 1004 says espresso");
          writer.close();
        } catch (Exception e) {
          log.info(e.getMessage());
        }
      } else if ("1005".equals(id.getUniqueId())) {
        // doc with ACLs
        try {
          ArrayList<Principal> permits = new ArrayList<Principal>();
          permits.add(new UserPrincipal("user1", "Default"));
          permits.add(new UserPrincipal("eric", "Default"));
          permits.add(new GroupPrincipal("group1", "Default"));
          ArrayList<Principal> denies = new ArrayList<Principal>();
          denies.add(new UserPrincipal("user2", "Default"));
          denies.add(new GroupPrincipal("group2", "Default"));
          
          resp.setAcl(new Acl.Builder()
              .setEverythingCaseInsensitive()
              .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
              .setPermits(permits).setDenies(denies).build());
          
          Writer writer = new OutputStreamWriter(resp.getOutputStream());
          writer.write("Menu 1005 says americano");
          writer.close();
        } catch (Exception e) {
          log.info(e.getMessage());
        }
      } else if ("1006".equals(id.getUniqueId())) {
        // Inherit ACLs from 1005
        try {
          ArrayList<Principal> permits = new ArrayList<Principal>();
          permits.add(new GroupPrincipal("group3", "Default"));
          ArrayList<Principal> denies = new ArrayList<Principal>();
          denies.add(new GroupPrincipal("group3", "Default"));
          
          resp.setAcl(new Acl.Builder()
              .setEverythingCaseInsensitive()
              .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
              .setInheritFrom(new DocId("1005")).setPermits(permits)
              .setDenies(denies).build());
          
          Writer writer = new OutputStreamWriter(resp.getOutputStream());
          writer.write("Menu 1006 says misto");
          writer.close();
        } catch (Exception e) {
          log.info(e.getMessage());
        }
      } else if ("1007".equals(id.getUniqueId())) {
        // Inherit ACLs from 1005 & 1006
        try {
          ArrayList<Principal> permits = new ArrayList<Principal>();
          permits.add(new GroupPrincipal("group5", "Default"));
          
          resp.putNamedResource("Whatever", new Acl.Builder()
              .setEverythingCaseInsensitive()
              .setInheritFrom(new DocId("1006"))
              .setPermits(permits)
              .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
              .build());
          
          ArrayList<Principal> permits2 = new ArrayList<Principal>();
          permits2.add(new GroupPrincipal("group4", "Default"));
          ArrayList<Principal> denies = new ArrayList<Principal>();
          denies.add(new GroupPrincipal("group4", "Default"));
          
          resp.setAcl(new Acl.Builder()
              .setEverythingCaseInsensitive()
              .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
              .setInheritFrom(new DocId("1005")).setPermits(permits2)
              .setDenies(denies).build());
          
          Writer writer = new OutputStreamWriter(resp.getOutputStream());
          writer.write("Menu 1006 says frappuccino");
          writer.close();
        } catch (Exception e) {
          log.info(e.getMessage());
        }
      } else if ("1008".equals(id.getUniqueId())) {
        // Inherit ACLs from 1007
        try {
          ArrayList<Principal> denies = new ArrayList<Principal>();
          denies.add(new GroupPrincipal("group5", "Default"));
          
          resp.setAcl(new Acl.Builder()
              .setEverythingCaseInsensitive()
              .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
              .setInheritFrom(new DocId("1007"), "Whatever")
              .setDenies(denies).build());
          Writer writer = new OutputStreamWriter(resp.getOutputStream());
          writer.write("Menu 1008 says coffee");
          writer.close();
        } catch (Exception e) {
          log.info(e.getMessage());
        }
      } else if ("1009".equals(id.getUniqueId())) {
        // Late Binding (security handled by connector)
        try {
          resp.setSecure(true);
          Writer writer = new OutputStreamWriter(resp.getOutputStream());
          writer.write("Menu 1009 says espresso");
          writer.close();
        } catch (Exception e) {
          log.info(e.getMessage());
        }
      } else {
        resp.respondNotFound();        
      }
    }
    
    @Override
    public void getModifiedDocIds(DocIdPusher pusher) throws IOException, 
        InterruptedException {
      ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
      mockDocIds.add(new DocId("1002"));
      pusher.pushDocIds(mockDocIds);    
    }  
    
    /** Call default main for adaptors. */
    public static void main(String[] args) {
      AbstractAdaptor.main(new HelloWorldConnector(), args);
    }  
}   
