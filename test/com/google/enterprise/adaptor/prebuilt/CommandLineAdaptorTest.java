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
import static java.util.Map.Entry;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.AuthzStatus;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocRequest;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.UserPrincipal;
import com.google.enterprise.adaptor.prebuilt.StreamingCommand.InputSource;
import com.google.enterprise.adaptor.prebuilt.StreamingCommand.OutputSink;
import com.google.enterprise.adaptor.testing.RecordingResponse;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tests for {@link CommandLineAdaptor}.
 */
public class CommandLineAdaptorTest {
  private final Charset charset = Charset.forName("US-ASCII");

  private static class CommandLineAdaptorTestMock extends CommandLineAdaptor {

    static final List<DocId> original = Arrays.asList(new DocId[] {
        new DocId("1001"),
        new DocId("1002"),
        new DocId("1003"),
    });

    @Override
    public int executeLister(String[] command, InputSource stdin, final OutputSink stdout,
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

    private static final Map<String, String> ID_TO_CONTENT;
    private static final Map<String, String> ID_TO_MIME_TYPE;
    private static final Map<String, Date> ID_TO_LAST_MODIFIED;
    private static final Map<String, Date> ID_TO_LAST_CRAWLED;
    private static final Map<String, Metadata> ID_TO_METADATA;
    private static final Map<String, Map<String, String>> ID_TO_PARAMS;

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

      Map<String, Map<String, String>> idToParams
          = new HashMap<String, Map<String, String>>();
      Map<String, String> params = new HashMap<String, String>();
      params.put("DoNotSkipDocument", "true");
      params.put("LastAccessDate", "2000-10-08T14:56:00Z");
      idToParams.put("1002", Collections.unmodifiableMap(params));
      idToParams.put("1003",
          Collections.unmodifiableMap(new HashMap<String, String>()));

      ID_TO_PARAMS = Collections.unmodifiableMap(idToParams);
    }

    @Override
    public int executeRetriever(String[] command, InputSource stdin, final OutputSink stdout,
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
      Map<String, String> params = ID_TO_PARAMS.get(docId);

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
      if (params != null) {
        for (Map.Entry<String, String> item : params.entrySet()) {
          result.append("param-name=").append(item.getKey()).append("\n");
          result.append("param-value=").append(item.getValue()).append("\n");
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

    private static final Map<String, String> ID_TO_AUTHZ_STATUS;

    static {
      Map<String, String> idToAuthzStatus = new HashMap<String, String>();
      idToAuthzStatus.put("1001", "PERMIT");
      idToAuthzStatus.put("1002", "DENY");
      idToAuthzStatus.put("1003", "INDETERMINATE");
      ID_TO_AUTHZ_STATUS = Collections.unmodifiableMap(idToAuthzStatus);
    }

    @Override
    public Command.Result executeAuthorizer(String[] command, byte[] stdin)
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

      StringBuffer result = new StringBuffer();
      result.append("GSA Adaptor Data Version 1 [\n]\n");
      result.append("id=1001").append("\n");
      result.append("authz-status=PERMIT").append("\n");
      result.append("id=1002").append("\n");
      result.append("authz-status=DENY").append("\n");
      result.append("id=1003").append("\n");
      result.append("authz-status=INDETERMINATE").append("\n");
      byte[] stdout = result.toString().getBytes();
      return new Command.Result(0, stdout, new byte[0]);
    }
  }

  @Test
  public void testListerAndRetriever() throws Exception {
    CommandLineAdaptor adaptor = new CommandLineAdaptorTestMock();

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
    assertEquals(CommandLineAdaptorTestMock.original, idList);

    // Test authorizer
    final UserPrincipal user = new UserPrincipal("user1");
    final String password = "password1";
    final Set<GroupPrincipal> groups = new TreeSet<GroupPrincipal>();
    groups.add(new GroupPrincipal("group1"));
    groups.add(new GroupPrincipal("group2"));
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
      Request request = new DocRequest(docId);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      RecordingResponse response = new RecordingResponse(baos);

      adaptor.getDocContent(request, response);

      boolean notModified = !CommandLineAdaptorTestMock.ID_TO_LAST_MODIFIED.get(docId.getUniqueId())
          .after(CommandLineAdaptorTestMock.ID_TO_LAST_CRAWLED.get(docId.getUniqueId()));

      assertEquals(notModified,
          response.getState() == RecordingResponse.State.NOT_MODIFIED);

      if (!notModified) {
        assertEquals(CommandLineAdaptorTestMock.ID_TO_MIME_TYPE.get(docId.getUniqueId()),
            response.getContentType());

        assertEquals(CommandLineAdaptorTestMock.ID_TO_METADATA.get(docId.getUniqueId()),
            response.getMetadata());

        assertEquals(CommandLineAdaptorTestMock.ID_TO_PARAMS.get(docId.getUniqueId()),
            response.getParams());

        byte[] expected = CommandLineAdaptorTestMock.ID_TO_CONTENT.get(
            docId.getUniqueId()).getBytes();
        byte[] actual = baos.toByteArray();
        String expectedString = new String(expected);
        String actualString = new String(actual);
        assertArrayEquals(
            CommandLineAdaptorTestMock.ID_TO_CONTENT.get(docId.getUniqueId()).getBytes(),
            baos.toByteArray());
      }
    }
  }
}
