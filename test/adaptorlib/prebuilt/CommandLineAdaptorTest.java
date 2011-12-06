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

package adaptorlib.prebuilt;

import static adaptorlib.TestHelper.getDocIds;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import adaptorlib.*;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.Charset;
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

  private static class MockListerCommand extends Command {

    static final List<DocId> original = Arrays.asList(new DocId[] {
        new DocId("1001"),
        new DocId("1002"),
        new DocId("1003"),
    });

    @Override
    public int exec(String[] command, File workingDir, byte[] stdin) {
      return 0;
    }

    @Override
    public byte[] getStdout() {
      StringBuilder result = new StringBuilder();
      result.append("GSA Adaptor Data Version 1 [\n]\n");
      for (DocId docId : original) {
        result.append("id=").append(docId.getUniqueId()).append("\n");
      }
      return result.toString().getBytes();
    }
  }

  private static class MockRetrieverCommand extends Command {

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

      Map<String, Metadata> idToMetadata = new HashMap<String, Metadata>();
      Set<MetaItem> metadataSet1002 = new HashSet<MetaItem>();
      metadataSet1002.add(MetaItem.raw("metaname-1002a", "metavalue-1002a"));
      metadataSet1002.add(MetaItem.raw("metaname-1002b", "metavalue-1002b"));
      idToMetadata.put("1002", new adaptorlib.Metadata(metadataSet1002));
      Set<MetaItem> metadataSet1003 = new HashSet<MetaItem>();
      metadataSet1003.add(MetaItem.raw("metaname-1003", "metavalue-1003"));
      idToMetadata.put("1003", new adaptorlib.Metadata(metadataSet1003));
      ID_TO_METADATA = Collections.unmodifiableMap(idToMetadata);
    }

    private String docId;
    private String content;
    private Metadata metadata;
    private Date lastModified;
    private Date lastCrawled;
    private String mimeType;

    @Override
    public int exec(String[] command, File workingDir, byte[] stdin) {
      docId = command[1];
      content = ID_TO_CONTENT.get(docId);
      metadata = ID_TO_METADATA.get(docId);
      lastModified = ID_TO_LAST_MODIFIED.get(docId);
      lastCrawled = ID_TO_LAST_CRAWLED.get(docId);
      mimeType = ID_TO_MIME_TYPE.get(docId);
      return 0;
    }

    @Override
    public byte[] getStdout() {
      StringBuffer result = new StringBuffer();
      result.append("GSA Adaptor Data Version 1 [\n]\n");
      result.append("id=").append(docId).append("\n");
      if (lastCrawled.after(lastModified)) {
        result.append("up-to-date").append("\n");
      }
      if (mimeType != null) {
        result.append("mime-type=").append(mimeType).append("\n");
      }
      if (metadata != null) {
        for (MetaItem item : metadata) {
          result.append("meta-name=").append(item.getName()).append("\n");
          result.append("meta-value=").append(item.getValue()).append("\n");
        }
      }
      if (content != null) {
        result.append("content").append("\n");
        result.append(content);
      }

      return result.toString().getBytes();
    }
  }

  private static class CommandLineAdaptorTestMock extends CommandLineAdaptor {
    @Override
    protected Command newListerCommand() {
      return new MockListerCommand();
    }

    @Override
    protected Command newRetrieverCommand() {
      return new MockRetrieverCommand();
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
    private Metadata metadata;
    private boolean notModified;
    private boolean notFound;

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
    public void setMetadata(Metadata m) {
      this.metadata = m;
    }

    public String getContentType() {
      return contentType;
    }

    public Metadata getMetadata() {
      return metadata;
    }

    public boolean getNotModified() {
      return notModified;
    }

    public boolean getNotFound() {
      return notFound;
    }
  }


  @Test
  public void testListerAndRetriever() throws Exception {

    Adaptor adaptor = new CommandLineAdaptorTestMock();

    List<DocId> idList = getDocIds(adaptor);
    assertEquals(MockListerCommand.original, idList);

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
