// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;

import static org.junit.Assert.*;
import org.junit.Test;

import adaptorlib.TransformPipeline;
import adaptorlib.DocumentTransform;
import adaptorlib.CommandLineTransform;
import adaptorlib.TransformException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;

public class TransformPipelineTest {
  private static Logger LOG = Logger.getLogger(TransformPipelineTest.class.getName());

  @Test
  public void testNoOp() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    pipeline.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(0, contentIn.size());
    assertEquals(0, contentOut.size());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testNoOpWithInput() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    String testString = "Here is some input";
    contentIn.write(testString.getBytes());
    pipeline.transform(contentIn, metadataIn, contentOut, metadataOut, params);

    assertEquals(testString, contentIn.toString());
    assertEquals(testString, contentOut.toString());
    assertEquals(0, metadataIn.size());
    assertEquals(0, metadataOut.size());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testSed() throws IOException, TransformException {
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    String testStr = "testing";
    contentIn.write(testStr.getBytes());
    params.put("key1", "value1");

    CommandLineTransform cmd = new CommandLineTransform("regex replace");
    cmd.transformCommand("/bin/sed s/i/1/");
    cmd.commandAcceptsParameters(false);
    pipeline.add(cmd);
    pipeline.transform(contentIn, new ByteArrayOutputStream(),
                       contentOut, new ByteArrayOutputStream(), params);

    assertEquals("testing", contentIn.toString());
    assertEquals("test1ng", contentOut.toString());
    assertEquals("value1", params.get("key1"));
    assertEquals(1, params.keySet().size());
  }

  @Test
  public void testModifyParams() throws IOException, TransformException {
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    TransformPipeline pipeline = new TransformPipeline();
    pipeline.add(new DocumentTransform("Param Transform") {
        @Override
        public void transform(ByteArrayOutputStream cIn, ByteArrayOutputStream mIn,
                              OutputStream cOut, OutputStream mOut,
                              Map<String, String> p) throws TransformException, IOException {
          p.put("newKey", "newValue");
        }
      });
    pipeline.transform(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                       new ByteArrayOutputStream(), new ByteArrayOutputStream(), params);

    assertEquals("value1", params.get("key1"));
    assertEquals("newValue", params.get("newKey"));
    assertEquals(2, params.keySet().size());
  }
}
