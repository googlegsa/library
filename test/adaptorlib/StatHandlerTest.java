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

package adaptorlib;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.Charset;

/**
 * Tests for {@link StatHandler}.
 */
public class StatHandlerTest {
  private StatHandler handler;
  private Charset charset = Charset.forName("UTF-8");

  public StatHandlerTest() {
    MockConfig config = new MockConfig();
    config.setKey("gsa.characterEncoding", "UTF-8");
    config.setKey("server.hostname", "localhost");
    handler = new StatHandler(config, new SnapshotMockJournal());
  }

  @Test
  public void testPost() throws Exception {
    MockHttpExchange ex = makeExchange("http", "POST", "/s", "/s");
    handler.handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testWrapPath() throws Exception {
    MockHttpExchange ex = makeExchange("http", "GET", "/sabc", "/s");
    handler.handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  @Test
  public void testStat() throws Exception {
    final String golden = "{\"config\":{"
          + "\"gsa.characterEncoding\":\"UTF-8\","
          + "\"server.hostname\":\"localhost\""
        + "},"
        + "\"log\":\"\","
        + "\"simpleStats\":{"
          + "\"numTotalDocIdsPushed\":0,"
          + "\"numTotalGsaRequests\":0,"
          + "\"numTotalNonGsaRequests\":0,"
          + "\"numUniqueDocIdsPushed\":0,"
          + "\"numUniqueGsaRequests\":0,"
          + "\"numUniqueNonGsaRequests\":0,"
          + "\"timeResolution\":1,"
          + "\"whenStarted\":0"
        + "},"
        + "\"stats\":["
          + "{"
            + "\"currentTime\":0,"
            + "\"snapshotDuration\":100,"
            + "\"statData\":["
              + "{"
                + "\"requestProcessingsCount\":0,"
                + "\"requestProcessingsDurationSum\":0,"
                + "\"requestProcessingsMaxDuration\":0,"
                + "\"requestProcessingsThroughput\":0,"
                + "\"requestResponsesCount\":0,"
                + "\"requestResponsesDurationSum\":0,"
                + "\"requestResponsesMaxDuration\":0,"
                + "\"requestResponsesThroughput\":0,"
                + "\"time\":-100"
              + "},{"
                + "\"requestProcessingsCount\":0,"
                + "\"requestProcessingsDurationSum\":0,"
                + "\"requestProcessingsMaxDuration\":0,"
                + "\"requestProcessingsThroughput\":0,"
                + "\"requestResponsesCount\":0,"
                + "\"requestResponsesDurationSum\":0,"
                + "\"requestResponsesMaxDuration\":0,"
                + "\"requestResponsesThroughput\":0,"
                + "\"time\":0"
              + "}"
            + "]"
          + "}"
        + "]}";
    MockHttpExchange ex = makeExchange("http", "GET", "/s", "/s");
    handler.handle(ex);
    assertEquals(200, ex.getResponseCode());
    assertEquals(golden, new String(ex.getResponseBytes(), charset));
  }

  private MockHttpExchange makeExchange(String protocol, String method,
        String path, String contextPath) throws Exception {
    return new MockHttpExchange(protocol, method, path,
                                new MockHttpContext(handler, contextPath));
  }

  private class SnapshotMockJournal extends MockJournal {
    SnapshotMockJournal() {
      super(new MockTimeProvider());
    }

    @Override
    JournalSnapshot getSnapshot() {
      return new JournalSnapshot(this, 0, new Stats[] {
        new Stats(2, 100, 0),
      });
    }
  }
}
