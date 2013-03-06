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

package com.google.enterprise.adaptor.prebuilt;

import static com.google.enterprise.adaptor.TestHelper.getDocIds;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import static java.util.Map.Entry;

import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.Adaptor;
import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.AuthzStatus;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.UserPrincipal;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link CommandLineAdaptor}.
 */
public class CommandLineAdaptorTest {
  private final Charset charset = Charset.forName("US-ASCII");

  private static class MockListerCommand extends StreamingCommand {

    static final List<DocId> original = Arrays.asList(new DocId[] {
        new DocId("1001"),
        new DocId("1002"),
        new DocId("1003"),
    });

    @Override
    public int exec(String[] command, File workingDir, InputSource stdin, final OutputSink stdout,
        OutputSink stderr) throws IOException, InterruptedException {
      assertEquals(command[0], "./lister_cmd.sh");
      assertEquals(command[1], "lister_arg1");

      final StringBuilder result = new StringBuilder();
      result.append("GSA Adaptor Data Version 1 [\n]\n");
      for (DocId docId : original) {
        result.append("id=").append(docId.getUniqueId()).append("\n");
      }
      Thread out = new Thread() {
        @Override
        public void run() {
          try {
            stdout.sink(new ByteArrayInputStream(result.toString().getBytes()));
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        }
      };

      out.start();
      out.join();

      return 0;
    }
  }

  private static class MockRetrieverCommand extends StreamingCommand {

    private static final Map<String, String> ID_TO_CONTENT;
    private static final Map<String, String> ID_TO_MIME_TYPE;
    private static final Map<String, Date> ID_TO_LAST_MODIFIED;
    private static final Map<String, Date> ID_TO_LAST_CRAWLED;
    private static final Map<String, Metadata> ID_TO_METADATA;

    static {
      Map<String, String> idToContent = new HashMap<String, String>();
      idToContent.put("1002", "Content of document 1002");
      idToContent.put("1003", "Content of document 1003");
      ID_TO_CONTENT = Collections.unmodifiableMap(idToContent);

      Map<String, String> idToMimeType = new HashMap<String, String>();
      idToMimeType.put("1002", "application/pdf");
      idToMimeType.put("1003", "text/plain");
      ID_TO_MIME_TYPE = Collections.unmodifiableMap(idToMimeType);


      Map<String, Date> idToLastModified = new HashMap<String, Date>();
      idToLastModified.put("1001", new Date(50));
      idToLastModified.put("1002", new Date(100));
      idToLastModified.put("1003", new Date(8000));
      ID_TO_LAST_MODIFIED = Collections.unmodifiableMap(idToLastModified);

      Map<String, Date> idToLastCrawled = new HashMap<String, Date>();
      idToLastCrawled.put("1001", new Date(100));
      idToLastCrawled.put("1002", new Date(99));
      idToLastCrawled.put("1003", new Date(5000));
      ID_TO_LAST_CRAWLED = Collections.unmodifiableMap(idToLastCrawled);

      Metadata id1002Metadata = new Metadata();
      id1002Metadata.add("metaname-1002a", "metavalue-1002a");
      id1002Metadata.add("metaname-1002b", "metavalue-1002b");
      Metadata id1003Metadata = new Metadata();
      id1003Metadata.add("metaname-1003", "metavalue-1003");

      Map<String, Metadata> idToMetadata = new HashMap<String, Metadata>();
      idToMetadata.put("1002", id1002Metadata.unmodifiableView());
      idToMetadata.put("1003", id1003Metadata.unmodifiableView());

      ID_TO_METADATA = Collections.unmodifiableMap(idToMetadata);
    }

    @Override
    public int exec(String[] command, File workingDir, InputSource stdin, final OutputSink stdout,
                    OutputSink stderr) throws IOException, InterruptedException {
      assertEquals(command[0], "./retriever_cmd.sh");
      assertEquals(command[1], "retriever_arg1");
      assertEquals(command[2], "retriever_arg2");

      String docId = command[3];
      String content = ID_TO_CONTENT.get(docId);
      Metadata metadata = ID_TO_METADATA.get(docId);
      Date lastModified = ID_TO_LAST_MODIFIED.get(docId);
      Date lastCrawled = ID_TO_LAST_CRAWLED.get(docId);
      String mimeType = ID_TO_MIME_TYPE.get(docId);

      final StringBuffer result = new StringBuffer();
      result.append("GSA Adaptor Data Version 1 [\n]\n");
      result.append("id=").append(docId).append("\n");
      if (lastCrawled.after(lastModified)) {
        result.append("up-to-date").append("\n");
      }
      if (mimeType != null) {
        result.append("mime-type=").append(mimeType).append("\n");
      }
      if (metadata != null) {
        for (Map.Entry<String, String> item : metadata) {
          result.append("meta-name=").append(item.getKey()).append("\n");
          result.append("meta-value=").append(item.getValue()).append("\n");
        }
      }
      if (content != null) {
        result.append("content").append("\n");
        result.append(content);
      }
      Thread out = new Thread() {
        @Override
        public void run() {
          try {
            stdout.sink(new ByteArrayInputStream(result.toString().getBytes()));
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        }
      };

      out.start();
      out.join();

      return 0;
    }
  }

  private static class MockAuthorizerCommand extends Command {

    private static final Map<String, String> ID_TO_AUTHZ_STATUS;

    static {
      Map<String, String> idToAuthzStatus = new HashMap<String, String>();
      idToAuthzStatus.put("1001", "PERMIT");
      idToAuthzStatus.put("1002", "DENY");
      idToAuthzStatus.put("1003", "INDETERMINATE");
      ID_TO_AUTHZ_STATUS = Collections.unmodifiableMap(idToAuthzStatus);
    }
    @Override
    public int exec(String[] command, File workingDir, byte[] stdin)
        throws UnsupportedEncodingException{
      assertEquals(command[0], "./authorizer_cmd.sh");
      String expectedText = "GSA Adaptor Data Version 1 [\n]\n"
          + "username=user1\n"
          + "password=password1\n"
          + "group=group1\n"
          + "group=group2\n"
          + "id=1001\n"
          + "id=1002\n"
          + "id=1003\n"
          + "id=1004\n";
        assertEquals(expectedText, new String(stdin, "UTF-8"));
      return 0;
    }

    @Override
    public byte[] getStdout() {
      StringBuffer result = new StringBuffer();
      result.append("GSA Adaptor Data Version 1 [\n]\n");
      result.append("id=1001").append("\n");
      result.append("authz-status=PERMIT").append("\n");
      result.append("id=1002").append("\n");
      result.append("authz-status=DENY").append("\n");
      result.append("id=1003").append("\n");
      result.append("authz-status=INDETERMINATE").append("\n");
     return result.toString().getBytes();
    }
  }

  private static class CommandLineAdaptorTestMock extends CommandLineAdaptor {
    @Override
    protected StreamingCommand newListerCommand() {
      return new MockListerCommand();
    }

    @Override
    protected StreamingCommand newRetrieverCommand() {
      return new MockRetrieverCommand();
    }

    @Override
    protected Command newAuthorizerCommand() {
      return new MockAuthorizerCommand();
    }
  }

  private static class ContentsRequestTestMock implements Request {
    private DocId docId;
    private Date lastCrawled;

    public ContentsRequestTestMock(DocId docId) {
      this.docId = docId;
    }

    @Override
    public boolean hasChangedSinceLastAccess(Date lastModified) {
      Date date = getLastAccessTime();
      if (date == null) {
        return true;
      }
      return date.before(lastModified);
    }

    @Override
    public Date getLastAccessTime() {
      return lastCrawled;
    }

    @Override
    public DocId getDocId() {
      return docId;
    }
  }

  private static class ContentsResponseTestMock implements Response {
    private OutputStream os;
    private String contentType;
    private Date lastModified;
    private Metadata metadata = new Metadata();
    private Acl acl;
    private boolean secure;
    private List<URI> anchorUris = new ArrayList<URI>();
    private List<String> anchorTexts = new ArrayList<String>();
    private boolean notModified;
    private boolean notFound;
    private boolean noIndex;
    private boolean noFollow;
    private boolean noArchive;

    public ContentsResponseTestMock(OutputStream os) {
      this.os = os;
      notModified = false;
    }

    @Override
    public void respondNotModified() {
      notModified = true;
    }

    @Override
    public void respondNotFound() {
      notFound = true;
    }

    @Override
    public OutputStream getOutputStream() {
      return os;
    }

    @Override
    public void setContentType(String contentType) {
      this.contentType = contentType;
    }

    @Override
    public void setLastModified(Date lastModified) {
      this.lastModified = lastModified;
    }

    @Override
    public void addMetadata(String key, String value) {
      this.metadata.add(key, value);
    }

    @Override
    public void setAcl(Acl acl) {
      this.acl = acl;
    }

    @Override
    public void setSecure(boolean secure) {
      this.secure = secure;
    }

    @Override
    public void addAnchor(URI uri, String text) {
      anchorUris.add(uri);
      anchorTexts.add(text);
    }

    @Override
    public void setNoIndex(boolean noIndex) {
      this.noIndex = noIndex;
    }

    @Override
    public void setNoFollow(boolean noFollow) {
      this.noFollow = noFollow;
    }

    @Override
    public void setNoArchive(boolean noArchive) {
      this.noArchive = noArchive;
    }

    public String getContentType() {
      return contentType;
    }

    public Date getLastModified() {
      return lastModified;
    }

    /** Returns unmodifibale view of metadata. */
    Metadata getMetadata() {
      return metadata.unmodifiableView();
    }

    public Acl getAcl() {
      return acl;
    }

    public boolean getNotModified() {
      return notModified;
    }

    public boolean getNotFound() {
      return notFound;
    }

    public boolean isNoIndex() {
      return noIndex;
    }

    public boolean isNoFollow() {
      return noFollow;
    }

    public boolean isNoArchive() {
      return noArchive;
    }
  }

  @Test
  public void testListerAndRetriever() throws Exception {

    Adaptor adaptor = new CommandLineAdaptorTestMock();

    Map<String, String> config = new HashMap<String, String>();
    config.put("commandline.lister.cmd", "./lister_cmd.sh");
    config.put("commandline.lister.arg1", "lister_arg1");
    config.put("commandline.retriever.cmd", "./retriever_cmd.sh");
    config.put("commandline.retriever.arg1", "retriever_arg1");
    config.put("commandline.retriever.arg2", "retriever_arg2");
    config.put("commandline.authorizer.cmd", "./authorizer_cmd.sh");
    config.put("commandline.authorizer.arg1", "authorizer_arg1");
    config.put("commandline.authorizer.delimeter", "\n");

    // Test lister
    List<DocId> idList = getDocIds(adaptor, config);
    assertEquals(MockListerCommand.original, idList);

    // Test authorizer
    final UserPrincipal user = new UserPrincipal("user1");
    final String password = "password1";
    final Set<GroupPrincipal> groups = GroupPrincipal.makeSet(Arrays.asList("group1", "group2"));
    AuthnIdentity authnIdentity = new AuthnIdentity() {
      @Override
      public UserPrincipal getUser() {
        return user;
      }
      @Override
      public String getPassword() {
        return password;
      }
      @Override
      public Set<GroupPrincipal> getGroups() {
        return groups;
      }
    };

    Map<DocId, AuthzStatus> expectedAuthzResult = new HashMap<DocId, AuthzStatus>();
    expectedAuthzResult.put(new DocId("1001"), AuthzStatus.PERMIT);
    expectedAuthzResult.put(new DocId("1002"), AuthzStatus.DENY);
    expectedAuthzResult.put(new DocId("1003"), AuthzStatus.INDETERMINATE);

    final List<DocId> docIds = Arrays.asList(new DocId("1001"), new DocId("1002"),
        new DocId("1003"), new DocId("1004"));
    Map<DocId, AuthzStatus> authzResult = adaptor.isUserAuthorized(authnIdentity, docIds);
    assertEquals(expectedAuthzResult, authzResult);

    // Test retriever
    for (DocId docId : idList) {

      ContentsRequestTestMock request = new ContentsRequestTestMock(docId);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ContentsResponseTestMock response = new ContentsResponseTestMock(baos);

      adaptor.getDocContent(request, response);

      boolean notModified = !MockRetrieverCommand.ID_TO_LAST_MODIFIED.get(docId.getUniqueId())
          .after(MockRetrieverCommand.ID_TO_LAST_CRAWLED.get(docId.getUniqueId()));

      assertEquals(notModified, response.getNotModified());

      if (!notModified) {
      assertEquals(MockRetrieverCommand.ID_TO_MIME_TYPE.get(docId.getUniqueId()),
          response.getContentType());

      assertEquals(MockRetrieverCommand.ID_TO_METADATA.get(docId.getUniqueId()),
          response.getMetadata());

        byte[] expected = MockRetrieverCommand.ID_TO_CONTENT.get(docId.getUniqueId()).getBytes();
        byte[] actual = baos.toByteArray();
        String expectedString = new String(expected);
        String actualString = new String(actual);
      assertArrayEquals(MockRetrieverCommand.ID_TO_CONTENT.get(docId.getUniqueId()).getBytes(),
          baos.toByteArray());
      }
    }
  }
}
