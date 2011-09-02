// Copyright 2011 Google Inc.
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

package adaptorlib.examples;

import adaptorlib.TransformException;

import static org.junit.Assert.*;
import org.junit.Test;

import mx.bigdata.jcalais.CalaisClient;
import mx.bigdata.jcalais.CalaisConfig;
import mx.bigdata.jcalais.CalaisObject;
import mx.bigdata.jcalais.CalaisResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link CalaisNERTransform}.
 */
public class CalaisNERTransformTest {

  private static class MockCalaisObject implements CalaisObject {
    private String type, name;
    MockCalaisObject(String t, String n) {
      this.type = t;
      this.name = n;
    }

    public String getField(String field) {
      if ("_type".equals(field)) {
        return type;
      } else if ("name".equals(field)) {
        return name;
      } else {
        throw new IllegalArgumentException(); 
      }
    }

    public Iterable getList(String field) {
      throw new UnsupportedOperationException();
    }
  }

  private static class MockCalaisResponse implements CalaisResponse {
      
    public CalaisObject getMeta() {
      throw new UnsupportedOperationException();
    }

    public CalaisObject getInfo() {
      throw new UnsupportedOperationException();
    }
 
    public Iterable<CalaisObject> getTopics() {
      throw new UnsupportedOperationException();
    }

    public Iterable<CalaisObject> getEntities() {
      List<CalaisObject> ents = new ArrayList<CalaisObject>();
      ents.add(new MockCalaisObject("Person", "Charles Taylor"));
      ents.add(new MockCalaisObject("Position", "President"));
      ents.add(new MockCalaisObject("Person", "Naomi Campbell"));
      ents.add(new MockCalaisObject("Country", "Sierra Leone"));
      ents.add(new MockCalaisObject("NaturalFeature", "Sierra Leone"));
      ents.add(new MockCalaisObject("Country", "Liberia"));
      return ents;
    }

    public Iterable<CalaisObject> getSocialTags() {
      throw new UnsupportedOperationException();
    }
  
    public Iterable<CalaisObject> getRelations() {
      throw new UnsupportedOperationException();
    }
  }

  private static class MockCalaisClient implements CalaisClient {
    public CalaisResponse analyze(URL url) {
      throw new UnsupportedOperationException();
    }
  
    public CalaisResponse analyze(URL url, CalaisConfig config){
      throw new UnsupportedOperationException();
    }

    public CalaisResponse analyze(Readable readable) {
      throw new UnsupportedOperationException();
    }

    public CalaisResponse analyze(Readable readable, CalaisConfig config){
      throw new UnsupportedOperationException();
    }

    public CalaisResponse analyze(String content) {
      throw new UnsupportedOperationException();
    }

    public CalaisResponse analyze(String content, CalaisConfig config) {
      return new MockCalaisResponse();
    }
  }

  private static class Factory implements CalaisNERTransform.CalaisClientFactory {
    public CalaisClient makeClient(String apiKey) {
      return new MockCalaisClient();
    }
  }

  // Note: These tests expect specific entities to be detected by OpenCalais.
  // Long term, we should mock out the webservice so we're not flaky.

  @Test
  public void testRestrictedSet() throws IOException, TransformException {
    CalaisNERTransform transform = new CalaisNERTransform(new Factory());
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("OpenCalaisApiKey", "4ydv87zawg7tf29jzex22d9u");
    params.put("UseCalaisEntity:Person", "True");
    params.put("UseCalaisEntity:Position", "True");
    // Test that "Country" is implicitly "True" when All isn't specified.
    params.put("UseCalaisEntity:NaturalFeature", "False");

    String testInput = "<HTML><HEAD></HEAD><BODY>"
        + "Prosecutors at the trial of former Liberian President Charles Taylor"
        + " hope the testimony of supermodel Naomi Campbell "
        + " will link Taylor to the trade in illegal conflict diamonds, "
        + " which they say he used to fund a bloody civil war in Sierra Leone."
        + "</BODY></HTML>";
    String golden = "<HTML><HEAD>\n"
        + "<meta name=\"Person\" content=\"Charles Taylor\" />\n"
        + "<meta name=\"Position\" content=\"President\" />\n"
        + "<meta name=\"Person\" content=\"Naomi Campbell\" />\n"
        + "</HEAD><BODY>Prosecutors at the trial of former Liberian President Charles Taylor"
        + " hope the testimony of supermodel Naomi Campbell "
        + " will link Taylor to the trade in illegal conflict diamonds, "
        + " which they say he used to fund a bloody civil war in Sierra Leone."
        + "</BODY></HTML>";
    contentIn.write(testInput.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    assertEquals(golden, contentOut.toString());
  }

  @Test
  public void testAllEntities() throws IOException, TransformException {
    CalaisNERTransform transform = new CalaisNERTransform(new Factory());
    ByteArrayOutputStream contentIn = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataIn = new ByteArrayOutputStream();
    ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
    ByteArrayOutputStream metadataOut = new ByteArrayOutputStream();
    Map<String, String> params = new HashMap<String, String>();
    params.put("OpenCalaisApiKey", "4ydv87zawg7tf29jzex22d9u");
    params.put("UseCalaisEntity:All", "True");

    String testInput = "<HTML><HEAD></HEAD><BODY>"
        + "Prosecutors at the trial of former Liberian President Charles Taylor"
        + " hope the testimony of supermodel Naomi Campbell "
        + " will link Taylor to the trade in illegal conflict diamonds, "
        + " which they say he used to fund a bloody civil war in Sierra Leone."
        + "</BODY></HTML>";
    String golden = "<HTML><HEAD>\n"
        + "<meta name=\"Person\" content=\"Charles Taylor\" />\n"
        + "<meta name=\"Position\" content=\"President\" />\n"
        + "<meta name=\"Person\" content=\"Naomi Campbell\" />\n"
        + "<meta name=\"Country\" content=\"Sierra Leone\" />\n"
        + "<meta name=\"NaturalFeature\" content=\"Sierra Leone\" />\n"
        + "<meta name=\"Country\" content=\"Liberia\" />\n"
        + "</HEAD><BODY>Prosecutors at the trial of former Liberian President Charles Taylor"
        + " hope the testimony of supermodel Naomi Campbell "
        + " will link Taylor to the trade in illegal conflict diamonds, "
        + " which they say he used to fund a bloody civil war in Sierra Leone."
        + "</BODY></HTML>";
    contentIn.write(testInput.getBytes());
    transform.transform(contentIn, metadataIn, contentOut, metadataOut, params);
    assertEquals(golden, contentOut.toString());
  }
}
