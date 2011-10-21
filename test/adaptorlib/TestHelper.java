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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

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

  public static List<DocId> getDocIds(Adaptor adaptor) throws IOException,
      InterruptedException {
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    adaptor.setDocIdPusher(pusher);
    adaptor.getDocIds(pusher);
    return pusher.getDocIds();
  }

  public static byte[] getDocContent(Adaptor adaptor, DocId docId) throws IOException,
      InterruptedException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    adaptor.getDocContent(new WrapperAdaptor.GetContentsRequest(docId),
                          new WrapperAdaptor.GetContentsResponse(baos));
    return baos.toByteArray();
  }


}
