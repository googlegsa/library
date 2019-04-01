// Copyright 2013 Google Inc. All Rights Reserved.
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
package com.google.enterprise.adaptor.experimental;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;

/** Tests for {@link Sim}. */
public class SimTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final String NL = "\r\n";

  private static final String METADATA_XML
      = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" + NL
      + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" \"\">" + NL
      + "<gsafeed>" + NL
      + "<!--GSA EasyConnector-->" + NL
      + "<header>" + NL
      + "<datasource>adaptor_moses-mtv-corp-google-com_15678</datasource>" + NL
      + "<feedtype>metadata-and-url</feedtype>" + NL
      + "</header>" + NL
      + "<group>" + NL
      + "<record mimetype=\"text/plain\" url=\"http://urlA\"/>" + NL
      + "<record mimetype=\"text/plain\" url=\"http://urlB\"/>" + NL
      + "<record mimetype=\"text/plain\" url=\"http://urlC\"/>" + NL
      + "</group>" + NL
      + "</gsafeed>" + NL;

  private static final String POST_BODY = "--<<" + NL
      + "Content-Disposition: form-data; name=\"datasource\"" + NL
      + "Content-Type: text/plain" + NL
      + "" + NL
      + "adaptor_moses-mtv-corp-google-com_15678" + NL
      + "--<<" + NL
      + "Content-Disposition: form-data; name=\"feedtype\"" + NL
      + "Content-Type: text/plain" + NL
      + "" + NL
      + "metadata-and-url" + NL
      + "--<<" + NL
      + "Content-Disposition: form-data; name=\"data\"" + NL
      + "Content-Type: text/xml" + NL
      + "" + NL
      + METADATA_XML
      + "" + NL
      + "--<<--";

  @Test
  public void testExtractingFeedFromMultipartPost() throws Exception {
    byte postBytes[] = POST_BODY.getBytes(Sim.UTF8);
    InputStream is = new ByteArrayInputStream(postBytes);
    String found = Sim.extractFeedFromMultipartPost(is,
        postBytes.length, "multipart/form-data; boundary=<<");
    assertTrue(METADATA_XML.equals(found));
  }

  @Test
  public void testExtractingUrls() throws Exception {
    Set<URL> found = Sim.extractUrls(METADATA_XML);
    assertEquals(3, found.size());
    assertTrue(found.contains(new URL("http://urlA")));
    assertTrue(found.contains(new URL("http://urlB")));
    assertTrue(found.contains(new URL("http://urlC")));
  }

  @Test
  public void testPercentDecoderSingle() {
    assertEquals("" + ((char) (10)), Sim.percentDecode("%0A"));
  }

  @Test
  public void testPercentDecoderAllBytes() {
    String encoded256 =
        "%00%01%02%03%04%05%06%07%08%09%0A%0B%0C%0D%0E%0F%10%11%12%13%14%15%16"
        + "%17%18%19%1A%1B%1C%1D%1E%1F%20%21%22%23%24%25%26%27%28%29%2A%2B%2C-."
        + "%2F0123456789%3A%3B%3C%3D%3E%3F%40ABCDEFGHIJKLMNOPQRSTUVWXYZ%5B%5C"
        + "%5D%5E_%60abcdefghijklmnopqrstuvwxyz%7B%7C%7D~%7F%C2%80%C2%81%C2%82"
        + "%C2%83%C2%84%C2%85%C2%86%C2%87%C2%88%C2%89%C2%8A%C2%8B%C2%8C%C2%8D"
        + "%C2%8E%C2%8F%C2%90%C2%91%C2%92%C2%93%C2%94%C2%95%C2%96%C2%97%C2%98"
        + "%C2%99%C2%9A%C2%9B%C2%9C%C2%9D%C2%9E%C2%9F%C2%A0%C2%A1%C2%A2%C2%A3"
        + "%C2%A4%C2%A5%C2%A6%C2%A7%C2%A8%C2%A9%C2%AA%C2%AB%C2%AC%C2%AD%C2%AE"
        + "%C2%AF%C2%B0%C2%B1%C2%B2%C2%B3%C2%B4%C2%B5%C2%B6%C2%B7%C2%B8%C2%B9"
        + "%C2%BA%C2%BB%C2%BC%C2%BD%C2%BE%C2%BF%C3%80%C3%81%C3%82%C3%83%C3%84"
        + "%C3%85%C3%86%C3%87%C3%88%C3%89%C3%8A%C3%8B%C3%8C%C3%8D%C3%8E%C3%8F"
        + "%C3%90%C3%91%C3%92%C3%93%C3%94%C3%95%C3%96%C3%97%C3%98%C3%99%C3%9A"
        + "%C3%9B%C3%9C%C3%9D%C3%9E%C3%9F%C3%A0%C3%A1%C3%A2%C3%A3%C3%A4%C3%A5"
        + "%C3%A6%C3%A7%C3%A8%C3%A9%C3%AA%C3%AB%C3%AC%C3%AD%C3%AE%C3%AF%C3%B0"
        + "%C3%B1%C3%B2%C3%B3%C3%B4%C3%B5%C3%B6%C3%B7%C3%B8%C3%B9%C3%BA%C3%BB"
        + "%C3%BC%C3%BD%C3%BE%C3%BF";
    StringBuilder decoded = new StringBuilder();
    for (char c = 0; c < 256; c++) {
      decoded.append(c);
    }
    assertEquals(decoded.toString(), Sim.percentDecode(encoded256));
  }

  @Test
  public void testPercentDecoderUnicode() {
    String decoded = Sim.percentDecode("AaZz09-_.~"
        + "%60%3D%2F%3F%2B%27%3B%5C%2F%22%21%40%23%24%25%5E%26"
        + "%2A%28%29%5B%5D%7B%7D%C3%AB%01");
    assertEquals("AaZz09-_.~`=/?+';\\/\"!@#$%^&*()[]{}ë\u0001", decoded);
  }
}
