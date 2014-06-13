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

package com.google.enterprise.adaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Test cases for {@link Config}.
 */
public class ConfigTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MockFile configFile = new MockFile("non-existent-file");
  private Config config = new ModifiedConfig();

  @Test
  public void testNoInputLoad() {
    // Requires gsa.hostname to be set
    thrown.expect(InvalidConfigurationException.class);
    config.validate();
  }

  @Test
  public void testAddDuplicateKeyWithValue() {
    config.addKey("somekey", "value");
    thrown.expect(IllegalStateException.class);
    config.addKey("somekey", null);
  }

  @Test
  public void testAddDuplicateKeyWithoutValue() {
    config.addKey("somekey", null);
    thrown.expect(IllegalStateException.class);
    config.addKey("somekey", "value");
  }

  @Test
  public void testGetValue() {
    config.addKey("somekey", "default", new Config.ValueComputer() {
          public String compute(String rawValue) {
            assertEquals("default", rawValue);
            return "computed";
          }
        });
    assertEquals("default", config.getRawValue("somekey"));
    assertEquals("computed", config.getValue("somekey"));
  }

  @Test
  public void testConfigModificationDetection() throws Exception {
    configFile.setFileContents("adaptor.fullListingSchedule=1\n");
    config.setValue("gsa.hostname", "notreal");
    config.load(configFile);
    assertEquals("notreal", config.getGsaHostname());
    assertEquals("1", config.getAdaptorFullListingSchedule());
    assertEquals(configFile, config.getConfigFile());

    final List<ConfigModificationEvent> events
        = new LinkedList<ConfigModificationEvent>();
    ConfigModificationListener listener = new ConfigModificationListener() {
      @Override
      public void configModified(ConfigModificationEvent ev) {
        events.add(ev);
      }
    };
    configFile.setFileContents("adaptor.fullListingSchedule=2\n");
    config.addConfigModificationListener(listener);
    config.ensureLatestConfigLoaded();
    assertEquals("1", config.getAdaptorFullListingSchedule());
    assertEquals(0, events.size());

    configFile.setLastModified(configFile.lastModified() + 1);
    config.ensureLatestConfigLoaded();
    assertEquals("2", config.getAdaptorFullListingSchedule());
    assertEquals("notreal", config.getGsaHostname());
    assertEquals(1, events.size());
    assertEquals(1, events.get(0).getModifiedKeys().size());
    assertTrue(events.get(0).getModifiedKeys()
               .contains("adaptor.fullListingSchedule"));
    events.clear();

    // Change nothing.
    configFile.setLastModified(configFile.lastModified() + 1);
    config.ensureLatestConfigLoaded();
    assertEquals(0, events.size());
    assertEquals("2", config.getAdaptorFullListingSchedule());
    assertEquals("notreal", config.getGsaHostname());

    config.removeConfigModificationListener(listener);
    configFile.setFileContents("adaptor.fullListingSchedule=3\n");
    configFile.setLastModified(configFile.lastModified() + 1);
    config.ensureLatestConfigLoaded();
    assertEquals(0, events.size());
    assertEquals("3", config.getAdaptorFullListingSchedule());
    assertEquals("notreal", config.getGsaHostname());
  }

  public void testAdminHostname() throws Exception {
    configFile.setFileContents(
        "gsa.hostname=feedhost\n" + "gsa.admin.hostname=admin\n");
    config.load(configFile);
    assertEquals("feedhost", config.getGsaHostname());
    assertEquals("admin", config.getGsaAdminHostname());
  }

  public void testNoAdminHostname() throws Exception {
    configFile.setFileContents("gsa.hostname=feedhost\n");
    config.load(configFile);
    assertEquals(config.getGsaHostname(), config.getGsaAdminHostname());
  }

  // TODO(ejona): Enable test once config allows gsa.hostname changes.
  /* **DISABLED** @Test*/
  public void testConfigModifiedInvalid() throws Exception {
    configFile.setFileContents("gsa.hostname=notreal\n");
    config.load(configFile);
    assertEquals("notreal", config.getGsaHostname());

    // Missing gsa.hostname.
    configFile.setFileContents("");
    configFile.setLastModified(configFile.lastModified() + 1);
    boolean threwException = false;
    try {
      config.ensureLatestConfigLoaded();
    } catch (IllegalStateException e) {
      threwException = true;
    }
    assertTrue(threwException);
    assertEquals("notreal", config.getGsaHostname());
  }

  @Test
  public void testGetTransformPipelineSpecValid() throws Exception {
    final List<Map<String, String>> golden
        = new ArrayList<Map<String, String>>();
    {
      Map<String, String> map;

      map = new HashMap<String, String>();
      map.put("name", "trans1");
      map.put("key1", "value1");
      golden.add(map);

      map = new HashMap<String, String>();
      map.put("name", "trans2");
      map.put("key2", "value2");
      map.put("key3", "value3");
      golden.add(map);

      map = new HashMap<String, String>();
      map.put("name", "trans3");
      golden.add(map);
    }
    configFile.setFileContents(
        "transform.pipeline = trans1,  trans2  ,trans3\n"
        + "transform.pipeline.trans1.key1=value1\n"
        + "transform.pipeline.trans2.key2=value2\n"
        + "transform.pipeline.trans2.key3=value3\n"
    );
    config.setValue("gsa.hostname", "notreal");
    config.load(configFile);
    assertEquals(golden, config.getTransformPipelineSpec());
  }

  @Test
  public void testGetTransformPipelineSpecEmpty() throws Exception {
    configFile.setFileContents("transform.pipeline=\n");
    config.load(configFile);
    assertEquals(Collections.emptyList(), config.getTransformPipelineSpec());
  }

  @Test
  public void testGetTransformPipelineSpecInValid() throws Exception {
    configFile.setFileContents("transform.pipeline=name1, ,name3\n");
    config.setValue("gsa.hostname", "notreal");
    config.load(configFile);
    thrown.expect(RuntimeException.class);
    config.getTransformPipelineSpec();
  }

  private static class ModifiedConfig extends Config {
    @Override
    protected Reader createReader(File file) throws IOException {
      return ((MockFile) file).createReader();
    }
  }
}
