// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib.tests;

import adaptorlib.MetaTaggerTransform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;

public class MetaTaggerTransformTest {
  private static String TEST_DIR = "src/adaptorlib/tests/";
  private static Logger LOG = Logger.getLogger(MetaTaggerTransform.class.getName());

  public static void main(String[] args) {
    new MetaTaggerTransformTest().run();
  }

  private void run() {
    LOG.log(Level.INFO, "Running MetaTaggerTransform tests");
    testNoPattern();
    testNoInput();
    testSimple();
    testNoHead();
    testDuplicatePatternsInPatternFile();
    LOG.log(Level.INFO, "MetaTaggerTransform tests finished.");
  }

  private void testNoInput() {
    LOG.log(Level.INFO, "Running testNoInput...");
    MetaTaggerTransform transform = new MetaTaggerTransform();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    String testString = "";
    try {
      contentIn.write(testString.getBytes());
      transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    }
    catch(Exception e) {
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

  private void testNoPattern() {
    LOG.log(Level.INFO, "Running testNoPattern...");
    MetaTaggerTransform transform = new MetaTaggerTransform();
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    String testString = "Here is some input";
    try {
      contentIn.write(testString.getBytes());
      transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    }
    catch(Exception e) {
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

  private void testSimple() {
    LOG.log(Level.INFO, "Running testSimple...");
    MetaTaggerTransform transform = new MetaTaggerTransform(TEST_DIR + "testPattern1.txt");
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    File inputFile = new File(TEST_DIR + "testPattern1.html");
    byte[] content = new byte[(int)inputFile.length()];
    File goldenFile = new File(TEST_DIR + "testPattern1.html.golden");
    byte[] goldenContent = new byte[(int)goldenFile.length()];
    try {
      new FileInputStream(inputFile).read(content);
      contentIn.write(content);
      new FileInputStream(goldenFile).read(goldenContent);
      transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    }
    catch(Exception e) {
      LOG.log(Level.WARNING, e.toString());
      assert false;
    }

    assert contentOut.toString().equals(new String(goldenContent));
    assert metadataIn.size() == 0;
    assert metadataOut.size() == 0;
    assert params.get("key1").equals("value1");
    assert params.keySet().size() == 1;
  }

  private void testNoHead() {
    LOG.log(Level.INFO, "Running testNoHead...");
    MetaTaggerTransform transform = new MetaTaggerTransform(TEST_DIR + "testPattern1.txt");
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    File inputFile = new File(TEST_DIR + "testPattern2.txt");
    byte[] content = new byte[(int)inputFile.length()];
    File goldenFile = new File(TEST_DIR + "testPattern2.txt");  // same as input
    byte[] goldenContent = new byte[(int)goldenFile.length()];
    try {
      new FileInputStream(inputFile).read(content);
      contentIn.write(content);
      new FileInputStream(goldenFile).read(goldenContent);
      transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    }
    catch(Exception e) {
      LOG.log(Level.WARNING, e.toString());
      assert false;
    }

    assert contentOut.toString().equals(new String(goldenContent));
    assert metadataIn.size() == 0;
    assert metadataOut.size() == 0;
    assert params.get("key1").equals("value1");
    assert params.keySet().size() == 1;
  }

  private void testDuplicatePatternsInPatternFile() {
    LOG.log(Level.INFO, "Running testDuplicatePatternsInPatternFile...");
    MetaTaggerTransform transform = new MetaTaggerTransform(TEST_DIR + "testPatternDup.txt");
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("key1", "value1");

    File inputFile = new File(TEST_DIR + "testPattern1.html");
    byte[] content = new byte[(int)inputFile.length()];
    File goldenFile = new File(TEST_DIR + "testPatternDup.html.golden");
    byte[] goldenContent = new byte[(int)goldenFile.length()];
    try {
      new FileInputStream(inputFile).read(content);
      contentIn.write(content);
      new FileInputStream(goldenFile).read(goldenContent);
      transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    }
    catch(Exception e) {
      LOG.log(Level.WARNING, e.toString());
      assert false;
    }

    assert contentOut.toString().equals(new String(goldenContent));
    assert metadataIn.size() == 0;
    assert metadataOut.size() == 0;
    assert params.get("key1").equals("value1");
    assert params.keySet().size() == 1;
  }
}
