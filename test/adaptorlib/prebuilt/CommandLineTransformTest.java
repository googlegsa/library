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

import adaptorlib.TestHelper;
import adaptorlib.TransformException;
import adaptorlib.TransformPipeline;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link CommandLineTransform}.
 */
public class CommandLineTransformTest {
  @Test
  public void testSed() throws IOException, TransformException {
    TestHelper.assumeOsIsNotWindows();

    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    // The newline causes the test to work with both BSD and GNU sed.
    String testStr = "testing\n";
    contentIn.write(testStr.getBytes());
    params.put("key1", "value1");

    CommandLineTransform cmd = new CommandLineTransform("regex replace");
    cmd.transformCommand("sed s/i/1/");
    cmd.commandAcceptsParameters(false);
    pipeline.add(cmd);
    pipeline.transform(contentIn, new ByteArrayOutputStream(),
                       contentOut, new ByteArrayOutputStream(), params);

    assertEquals(testStr, contentIn.toString());
    assertEquals(testStr.replace("i", "1"), contentOut.toString());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }
}
