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

import static org.junit.Assume.*;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for tests.
 */
public class TestHelper {
  // Prevent instantiation
  private TestHelper() {}

  private static boolean isRunningOnWindows() {
    String osName = System.getProperty("os.name");
    boolean isWindows = osName.toLowerCase().startsWith("windows");
    return isWindows;
  }

  public static void assumeOsIsNotWindows() {
    assumeTrue(!isRunningOnWindows());
  }

  public static void assumeOsIsWindows() {
    assumeTrue(isRunningOnWindows());
  }

  public static List<DocId> getDocIds(Adaptor adaptor, Map<String, String> configEntries)
      throws Exception {
    final AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    final Config config = new Config();
    adaptor.initConfig(config);
    for (Map.Entry<String, String> entry : configEntries.entrySet()) {
      config.setValue(entry.getKey(), entry.getValue());
    }
    adaptor.init(new WrapperAdaptor.WrapperAdaptorContext(null) {
      @Override
      public DocIdPusher getDocIdPusher() {
        return pusher;
      }

      @Override
      public Config getConfig() {
        return config;
      }
    });
    adaptor.getDocIds(pusher);
    return pusher.getDocIds();
  }

  public static List<DocId> getDocIds(Adaptor adaptor)
      throws Exception {
    return getDocIds(adaptor, Collections.<String, String>emptyMap());
  }

  public static byte[] getDocContent(Adaptor adaptor, DocId docId) throws IOException,
      InterruptedException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    WrapperAdaptor.GetContentsResponse resp
        = new WrapperAdaptor.GetContentsResponse(baos);
    adaptor.getDocContent(new WrapperAdaptor.GetContentsRequest(docId), resp);
    if (resp.isNotFound()) {
      throw new FileNotFoundException("Could not find " + docId);
    }
    return baos.toByteArray();
  }


}
