// Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor.prebuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Unit tests for {@link FilterMimetypes}. */
public class FilterMimetypesTest {
  private static Random rander = new Random();

  private static FilterMimetypes defaultFilter() {
    return FilterMimetypes.create(new HashMap<String, String>());
  }

  @Test
  public void testLeaveAsIs() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "text/rtf");
    transform.transform(new Metadata(), params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testDropAll() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "application/pgp-signature");
    transform.transform(new Metadata(), params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testDropContent() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "application/macbinary");
    transform.transform(new Metadata(), params);
    assertEquals("do-not-index-content", params.get("Transmission-Decision"));
  }

  @Test
  public void testUnknown() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "abrah/kah/debrrah");
    transform.transform(new Metadata(), params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testContentTypeWithCharset() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "text/richtext  ;  itsy-bitsy-characters");
    transform.transform(new Metadata(), params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testContentTypeWithMoreParams() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "  text/rtf  ;  param-uno   ; paribo");
    transform.transform(new Metadata(), params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testContentTypeWithCapitalLetters() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "  TeXt/RtF  ;  param-uno   ; paribo");
    transform.transform(new Metadata(), params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testLeaveAsIsByPrefix() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "text/that-is-textual");
    transform.transform(new Metadata(), params);
    assertEquals("as-is", params.get("Transmission-Decision"));
  }

  @Test
  public void testDropContentByPrefix() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    params.put("Content-Type", "world/in-an-oyster-shell");
    transform.transform(new Metadata(), params);
    assertEquals("do-not-index-content", params.get("Transmission-Decision"));
  }

  private static boolean wildcardmatch2(String glob, String str) {
    if (glob.isEmpty() && str.isEmpty()) {
      return true;
    }
    if (glob.isEmpty() && !str.isEmpty()) {
      return false;
    }
    if (glob.length() <= 0) {
      throw new AssertionError();
    }
    char head = glob.charAt(0);
    if ('*' == head && str.isEmpty()) {
      return wildcardmatch2(glob.substring(1), str);
    }
    if ('*' == head && !str.isEmpty()) {
      return wildcardmatch2(glob, str.substring(1))
          || wildcardmatch2(glob.substring(1), str);
    }
    if ('*' == head) {
      throw new AssertionError();
    }
    if (str.isEmpty()) {
      return false;
    }
    return head == str.charAt(0)
        && wildcardmatch2(glob.substring(1), str.substring(1));
  }

  private static String makeRandomString(String alphabet, int len) {
    char[] chars = alphabet.toCharArray();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < len; i++) {
      char c = chars[rander.nextInt(chars.length)];
      sb.append(c);
    }
    return "" + sb;
  }

  @Test
  public void testWildcardMatcherWithFuzz() {
    for (int globlen = 2; globlen < 10; globlen++) {
      for (int strlen = 2; strlen < 20; strlen++) {
        for (int fuzzcase = 0; fuzzcase < 10; fuzzcase++) {
          String glob = makeRandomString("ab*", globlen);
          String str = makeRandomString("ab", strlen);
          assertEquals("wildcard match difference on " + glob + " " + str,
              wildcardmatch2(glob, str),
              FilterMimetypes.wildcardmatch(glob, str));
        }
      }
    }
  }

  @Test
  public void testWildcardMatcherWithSpecialCharsWithFuzz() {
    for (int globlen = 2; globlen < 10; globlen++) {
      for (int strlen = 2; strlen < 20; strlen++) {
        for (int fuzzcase = 0; fuzzcase < 10; fuzzcase++) {
          String glob = makeRandomString("?.[]()ab*", globlen);
          String str = makeRandomString("?.[]()ab", strlen);
          assertEquals("wildcard match difference on " + glob + " " + str,
              wildcardmatch2(glob, str),
              FilterMimetypes.wildcardmatch(glob, str));
        }
      }
    }
  }

  @Test
  public void testEmptySupportedConfigLine() {
    FilterMimetypes f = FilterMimetypes.create(new HashMap<String, String>() {{
      put("supportedMimetypes", "");
    }});
    assertEquals(0, f.getSupportedMimetypes().size());
    assertTrue(0 < f.getUnsupportedMimetypes().size());
    assertTrue(0 < f.getExcludedMimetypes().size());
  }

  @Test
  public void testEmptyUnsupportedConfigLine() {
    FilterMimetypes f = FilterMimetypes.create(new HashMap<String, String>() {{
      put("unsupportedMimetypes", "");
    }});
    assertTrue(0 < f.getSupportedMimetypes().size());
    assertEquals(0, f.getUnsupportedMimetypes().size());
    assertTrue(0 < f.getExcludedMimetypes().size());
  }

  @Test
  public void testEmptyExcludedConfigLine() {
    FilterMimetypes f = FilterMimetypes.create(new HashMap<String, String>() {{
      put("excludedMimetypes", "");
    }});
    assertTrue(0 < f.getSupportedMimetypes().size());
    assertTrue(0 < f.getUnsupportedMimetypes().size());
    assertEquals(0, f.getExcludedMimetypes().size());
  }

  @Test
  public void testGlobAddons() {
    FilterMimetypes f = defaultFilter();
    int nsupported = f.getSupportedMimetypes().size();
    int nunsupported = f.getUnsupportedMimetypes().size();
    int nexcluded = f.getExcludedMimetypes().size();
    FilterMimetypes more = FilterMimetypes.create(
        new HashMap<String, String>() {{
            put("supportedMimetypesAddon", "E.T. phone home.");
            put("unsupportedMimetypesAddon", "We'll always have Paris.");
            put("excludedMimetypesAddon", "Frankly, my dear, I don't give");
        }}
    );
    assertEquals(nsupported + 3, more.getSupportedMimetypes().size());
    assertEquals(nunsupported + 4, more.getUnsupportedMimetypes().size());
    assertEquals(nexcluded + 6, more.getExcludedMimetypes().size());
  }

  @Test
  public void testExplicitExcludedOverridesSupported() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    /* supported has text/* and excluded has text/asp */
    params.put("Content-Type", "text/asp");
    transform.transform(new Metadata(), params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testExplicitExcludedOverridesUnsupported() {
    MetadataTransform transform = defaultFilter();
    Map<String, String> params = new HashMap<String, String>();
    /* unsupported has message/* and excluded has message/rfc822 */
    params.put("Content-Type", "message/rfc822");
    transform.transform(new Metadata(), params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }
}
