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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import adaptorlib.Adaptor;
import adaptorlib.DocId;
import static adaptorlib.TestHelper.getDocIds;
import static adaptorlib.TestHelper.getDocContent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

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
      for (DocId docId : original) {
        result.append(docId.getUniqueId()).append("\n");
      }
      return result.toString().getBytes();
    }
  }

  private static class MockRetrieverCommand extends Command {

    private byte[] result;
    @Override
    public int exec(String[] command, File workingDir, byte[] stdin) {
      result = idToContent(command[1]);
      return 0;
    }

    @Override
    public byte[] getStdout() {
      return result;
    }

    private static byte[] idToContent(String id) {
      String result = "Content of document " + id;
      return result.getBytes();
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

  @Test
  public void testListerAndRetriever() throws Exception {

    Adaptor adaptor = new CommandLineAdaptorTestMock();

    List<DocId> retrieved = getDocIds(adaptor);
    assertEquals(MockListerCommand.original, retrieved);

    for (DocId docId : retrieved) {
      assertArrayEquals(MockRetrieverCommand.idToContent(docId.getUniqueId()),
                        getDocContent(adaptor, docId));
    }
  }
}
