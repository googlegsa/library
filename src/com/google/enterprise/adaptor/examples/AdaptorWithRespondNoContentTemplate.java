// Copyright 2015 Google Inc. All Rights Reserved.
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
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.AuthzAuthority;
import com.google.enterprise.adaptor.AuthzStatus;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Demonstrates what code is necessary for responding NO_CONTENT (204) to GSA.
 * The key operations are:
 * <ol><li> providing document ids
 *   <li> providing document bytes and ACLs given a document id
 *   <li> responding with NO_CONTENT (204) when document content is same and 
 *        metadata or ACLs are updated.
 * </ol>
 */
public class AdaptorWithRespondNoContentTemplate extends AbstractAdaptor
    implements AuthzAuthority{
  private static final Logger log
      = Logger.getLogger(AdaptorWithRespondNoContentTemplate.class.getName());
  private final Charset encoding = Charset.forName("UTF-8");
  private final Repository contentRepository;

  public AdaptorWithRespondNoContentTemplate() {
    this.contentRepository = new Repository();
  }
  @Override
  public void init(AdaptorContext context) {
    context.setAuthzAuthority(this);
  }

  @Override
  public void getDocContent(Request request, Response response)
      throws IOException, InterruptedException {
    Repository.RepositoryDocument document =
        contentRepository.getRepositoryDocument(
            request.getDocId().getUniqueId());
    if (document == null) {
      response.respondNotFound();
      return;
    }
    // Update metadata.
    for (String meta : document.getMetadata().keySet()) {
      response.addMetadata(meta, document.getMetadata().get(meta));
    }
    // Construct ACL.
    List<UserPrincipal> permitUsers = new ArrayList<UserPrincipal>();
    for (String user : document.getPermitUsers()) {
      permitUsers.add(new UserPrincipal(user));
    }
    List<GroupPrincipal> permitGroups = new ArrayList<GroupPrincipal>();
    for (String group : document.getPermitGroups()) {
      permitGroups.add(new GroupPrincipal(group));
    }
    response.setAcl(new Acl.Builder().setPermitUsers(permitUsers)
        .setPermitGroups(permitGroups).build());

    // Set last modified date
    response.setLastModified(document.getLastModified());

    // Check if document is modified
    if (!request.hasChangedSinceLastAccess(document.getLastModified())) {
      log.log(Level.FINE, "Responding 204 since document has not been "
          + "modified since last access time.");
      response.respondNoContent();
      return;
    }
    OutputStream os = response.getOutputStream();
    os.write(document.getContent().getBytes(encoding));
  }

  @Override
  public void getDocIds(DocIdPusher pusher)
      throws IOException, InterruptedException {
    ArrayList<DocId> mockDocIds = new ArrayList<DocId>();
    for (String docId : contentRepository.getAllDocumentIds()) {
      mockDocIds.add(new DocId(docId));
    }
    pusher.pushDocIds(mockDocIds);
  }

  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity userIdentity,
      Collection<DocId> ids) throws IOException {
    Map<DocId, AuthzStatus> result =
        new HashMap<DocId, AuthzStatus>(ids.size() * 2);
    for (DocId docId : ids) {
      result.put(docId, AuthzStatus.PERMIT);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Dummy repository which provides documents with content, ACLs, metadata
   * and randomly updates last modified timestamp.
   */

  private static class Repository {
    private final Random random = new Random();
    private final Map<String, RepositoryDocument> repository;    
    
    public Repository() {
      repository = new HashMap<String, RepositoryDocument>();
      repository.put("1001", new RepositoryDocument("1001"));
      repository.put("1002", new RepositoryDocument("1002"));
    }

    public List<String> getAllDocumentIds() {
      List<String> docIds = new ArrayList<String>();
      docIds.add("1001");
      docIds.add("1002");
      return Collections.unmodifiableList(docIds);
    }

    public RepositoryDocument getRepositoryDocument(String docId) {
      if (!repository.containsKey(docId)) {
        return null;
      }
      // To mimic content repository behavior, repository will return
      // new version of document randomly.
      boolean sendNewVersion = random.nextBoolean();
      RepositoryDocument document = repository.get(docId);
      if (sendNewVersion) {
        log.log(Level.FINE, "Generating new version of document {0}", docId);
        document.incrementVersion();
      }
      return document;      
    }

    static class RepositoryDocument {
      final private String id;     
      final private Map<String, String> metadata;      
      final private List<String> permitUsers;
      final private List<String> permitGroups;
      private Date lastModified;
      private int version;     

      public RepositoryDocument(String id) {
        this.id = id;
        metadata = new HashMap<String, String>();
        metadata.put("Title", id);
        metadata.put("property1", id + " value 1");
        metadata.put("property2", id + " value 2");
        this.permitUsers =  Collections.singletonList("user2");
        this.permitGroups =  Collections.singletonList("group2");       
        this.lastModified = new Date();       
        this.version = 1;
      }

      public String getContent() {
        return String.format(
            "Version %d - ocument %s says Hi! @ %s", version, id, lastModified);
      }

      public Map<String, String> getMetadata() {
        return metadata;
      }

      public Date getLastModified() {
        return lastModified;
      }

      public List<String> getPermitUsers() {
        return permitUsers;
      }

      public List<String> getPermitGroups() {
        return permitGroups;
      }
      
      public synchronized void incrementVersion() {
        version++;
        lastModified = new Date();        
      }
    }
  }

   /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new AdaptorWithRespondNoContentTemplate(), args);
  }

}
