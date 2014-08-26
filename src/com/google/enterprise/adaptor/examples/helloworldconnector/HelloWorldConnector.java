package com.google.enterprise.adaptor.examples.helloworldconnector;

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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Demonstrates what code is necessary for putting content onto a GSA.
 * The key operations are:
 * <ol>
 * <li>providing document ids either by Lister or Graph Traversal
 * <li>providing document bytes and metadata given a document id
 * </ol>
 */
public class HelloWorldConnector extends AbstractAdaptor implements
    PollingIncrementalLister {

  private static final Logger log =
      Logger.getLogger(HelloWorldConnector.class.getName());
  private boolean provideBodyOfDoc1003 = true;

  @Override
  public void init(AdaptorContext context) throws Exception {
    context.setPollingIncrementalLister(this);
    HelloWorldAuthenticator authenticator =
        new HelloWorldAuthenticator(context);
    context.setAuthnAuthority(authenticator);
    context.setAuthzAuthority(authenticator);
    context.createHttpContext("/google-response", authenticator);
  }

  /**
   * This example shows how to use both the Lister & Graph Traversal.
   * The root document ("") is a virtual doc which will contain a list of
   * links to other docs when returned by the Retriever.
   * If you aren't using Graph Traversal, all docids would be pushed in
   * here, like 1001 and 1002 are.
   */
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    log.entering("HelloWorldConnector", "getDocIds");
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    // push docids
    mockDocIds.add(new DocId(""));
    mockDocIds.add(new DocId("1001"));
    mockDocIds.add(new DocId("1002"));
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
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    log.entering("HelloWorldConnector", "getDocContent");
    DocId id = req.getDocId();
    log.info("DocId '" + id.getUniqueId() + "'");

    // Hard-coded list of our doc ids
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
      // Set lastModifiedDate to 10 minutes ago
      Date lastModifiedDate = new Date(System.currentTimeMillis() - 600000);
      if (req.hasChangedSinceLastAccess(lastModifiedDate)) {
        if (req.getLastAccessTime() == null) {
          log.info("Requested docid 1001 with No If-Modified-Since");
        } else {
          log.info("Requested docid 1001 with If-Modified-Since < 10 minutes");
        }
        resp.setLastModified(new Date());
        Writer writer = new OutputStreamWriter(resp.getOutputStream());
        writer.write("Menu 1001 says latte");
        writer.close();
      } else {
        log.info("Docid 1001 Not Modified");
        resp.respondNotModified();
      }
    } else if ("1002".equals(id.getUniqueId())) {
      // Very basic doc
      Writer writer = new OutputStreamWriter(resp.getOutputStream());
      writer.write("Menu 1002 says cappuccino");
      writer.close();
    } else if ("1003".equals(id.getUniqueId())) {
      // Alternate between doc and a 404 response
      if (provideBodyOfDoc1003) {
        Writer writer = new OutputStreamWriter(resp.getOutputStream());
        writer.write("Menu 1003 says machiato");
        writer.close();
      } else {
        resp.respondNotFound();
      }
      provideBodyOfDoc1003 = !provideBodyOfDoc1003;
    } else if ("1004".equals(id.getUniqueId())) {
      // doc with metdata & different display URL
      resp.addMetadata("flavor", "vanilla");
      resp.addMetadata("flavor", "hazel nuts");
      resp.addMetadata("taste", "strawberry");

      try {
        resp.setDisplayUrl(new URI("http://fake.com/a"));
      } catch (URISyntaxException e) {
        log.info(e.getMessage());
      }
      Writer writer = new OutputStreamWriter(resp.getOutputStream());
      writer.write("Menu 1004 says espresso");
      writer.close();
    } else if ("1005".equals(id.getUniqueId())) {
      // doc with ACLs
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
    } else if ("1006".equals(id.getUniqueId())) {
      // Inherit ACLs from 1005
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
    } else if ("1007".equals(id.getUniqueId())) {
      // Inherit ACLs from 1005 & 1006
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
      writer.write("Menu 1007 says frappuccino");
      writer.close();
    } else if ("1008".equals(id.getUniqueId())) {
      // Inherit ACLs from 1007
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
    } else if ("1009".equals(id.getUniqueId())) {
      // Late Binding (security handled by connector)
      resp.setSecure(true);
      Writer writer = new OutputStreamWriter(resp.getOutputStream());
      writer.write("Menu 1009 says espresso");
      writer.close();
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
