// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib.tests;

import adaptorlib.TransformPipeline;
import adaptorlib.DocumentTransform;
import adaptorlib.CommandLineTransform;
import adaptorlib.TransformException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.HashMap;

public class TransformPipelineTest {

  public static void main(String[] args) {
    new TransformPipelineTest().run();
  }

  private void run() {
    System.out.println("Running TransformPipeline tests");
    testNoOp();
    testNoOpWithInput();
    testSed();
    testModifyParams();
    System.out.println("TransformPipeline tests finished.");
  }

  public void testNoOp() {
    System.out.println("Running TestNoOp...");
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
      System.out.println(e.toString());
      assert false;
    }

    assert contentIn.size() == 0;
    assert contentOut.size() == 0;
    assert metadataIn.size() == 0;
    assert metadataOut.size() == 0;
    assert params.get("key1").equals("value1");
    assert params.keySet().size() == 1;
  }

  public void testNoOpWithInput() {
    System.out.println("Running TestNoOpWithInput...");
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
      System.out.println(e.toString());
      assert false;
    }

    assert contentIn.toString().equals(testString);
    assert contentOut.toString().equals(testString);
    assert metadataIn.size() == 0;
    assert metadataOut.size() == 0;
    assert params.get("key1").equals("value1");
    assert params.keySet().size() == 1;
  }

  public void testSed() {
    System.out.println("Running TestSed...");
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
      System.err.println(e.toString());
      assert false;
    }
  }

  public void testModifyParams() {
    System.out.println("testModifyParams...");
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
      System.err.println(e.toString());
      assert false;
    }
  }
}
