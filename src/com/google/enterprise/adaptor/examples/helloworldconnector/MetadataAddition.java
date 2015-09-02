package com.google.enterprise.adaptor.examples.helloworldconnector;

// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.google.enterprise.adaptor.MetadataTransform;
import com.google.enterprise.adaptor.Metadata;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example transform which will add values to metadata key "taste" if the 
 * document already has existing metdata key "taste"
 * <p>
 * A simple transformation can be added to the adaptor-config.properties file.
 * This example combines "Mango" and "peach" with any existing "taste"
 * metadata.  If the document does not have a meta taste key, no values will
 * be added.
 * <p>
 * <code>
 * metadata.transform.pipeline=step1<br>
 * metadata.transform.pipeline.step1.taste=Mango,peach<br>
 * metadata.transform.pipeline.step1.factoryMethod=com.google.enterprise.adaptor.examples.HelloWorldConnector.MetadataAddition.load<br>
 * </code>
 * <p>
 */

public class MetadataAddition implements MetadataTransform {
  private static final Logger log = Logger.getLogger(MetadataAddition.class
      .getName());
  private static final String META_TASTE = "taste";
  private Set<String> valuesToAdd = null;

  private MetadataAddition(String values) {
    if (null == values) {
      throw new NullPointerException();
    }
    String valueArray[] = values.split(",", 0);
    valuesToAdd = new HashSet<String>(Arrays.asList(valueArray));
  }

  /**
   * Called as <code>transfordm.pipeline.<stepX>.factoryMethod for this
   * transformation pipline as specified in adaptor-config.properties.
   * <p>
   * This method simply returns a new object with the additional
   * metadata as specified as values for step1.taste
   */
  public static MetadataAddition load(Map<String, String> cfg) {
    return new MetadataAddition(cfg.get(META_TASTE));
  }

  /**
   * Here we check to see if the current doc contains a "taste" key
   * and if so, add the additional values from the config file
   */
  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    Set<String> values = metadata.getAllValues(META_TASTE);
    if (values.isEmpty()) {
      log.log(Level.INFO, "no metadata {0}. Skipping", META_TASTE);
    } else {
      log.log(Level.INFO,
              "adding values {1} for existing metadata {0}  ",
              new Object[] { META_TASTE, valuesToAdd });
      metadata.set(META_TASTE, combine(values, valuesToAdd));
    }
  }

  private Set<String> combine(Set<String> s1, Set<String> s2) {
    Set<String> combined = new HashSet<String>(s1);
    combined.addAll(s2);
    return combined;
  }
}
