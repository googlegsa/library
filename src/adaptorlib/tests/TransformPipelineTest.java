// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib.tests;

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

  public static void main(String[] args) {
    new TransformPipelineTest().run();
  }

  private void run() {
    LOG.log(Level.INFO, "Running TransformPipeline tests");
    testNoOp();
    testNoOpWithInput();
    testSed();
    testModifyParams();
    LOG.log(Level.INFO, "TransformPipeline tests finished.");
  }

  private void testNoOp() {
    LOG.log(Level.INFO, "Running TestNoOp...");
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    try {
      pipeline.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, e.toString());
      assert false;
    }

    assert contentIn.size() == 0;
    assert contentOut.size() == 0;
    assert metadataIn.size() == 0;
    assert metadataOut.size() == 0;
    assert params.get("key1").equals("value1");
    assert params.keySet().size() == 1;
  }

  private void testNoOpWithInput() {
    LOG.log(Level.INFO, "Running TestNoOpWithInput...");
    TransformPipeline pipeline = new TransformPipeline();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");
    String testString = "Here is some input";
    try {
      contentIn.write(testString.getBytes());
      pipeline.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, e.toString());
      assert false;
    }

    assert contentIn.toString().equals(testString);
    assert contentOut.toString().equals(testString);
    assert metadataIn.size() == 0;
    assert metadataOut.size() == 0;
    assert params.get("key1").equals("value1");
    assert params.keySet().size() == 1;
  }

  private void testSed() {
    LOG.log(Level.INFO, "Running TestSed...");
    try {
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

      assert contentIn.toString().equals("testing");
      assert contentOut.toString().equals("test1ng");
      assert params.get("key1").equals("value1");
      assert params.keySet().size() == 1;
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, e.toString());
      assert false;
    }
  }

  private void testModifyParams() {
    LOG.log(Level.INFO, "testModifyParams...");
    try {
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

      assert params.get("key1").equals("value1");
      assert params.get("newKey").equals("newValue");
      assert params.keySet().size() == 2;
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, e.toString());
      assert false;
    }
  }
}
