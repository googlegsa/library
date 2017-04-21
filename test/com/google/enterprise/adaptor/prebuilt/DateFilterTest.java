// Copyright 2017 Google Inc. All Rights Reserved.
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

import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Unit tests for {@link DateFilter}. */
public class DateFilterTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

  @Test
  public void testMillisecondDateFormat() throws Exception {
    DateFormat msDateFormat = new DateFilter.MillisecondDateFormat();
    Date now = new Date();
    Date msDate = msDateFormat.parse(Long.toString(now.getTime()));
    assertEquals(now, msDate);
    assertEquals(Long.toString(now.getTime()), msDateFormat.format(now));
  }

  // tests on create calls (various errors) and toString results
  @Test
  public void testCreate_NoKey() {
    thrown.expect(NullPointerException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
  }

  @Test
  public void testCreate_EmptyKey() {
    thrown.expect(NullPointerException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
  }

  @Test
  public void testCreate_InvalidFormat() {
    thrown.expect(IllegalArgumentException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("format", "bogus");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
  }

  @Test
  public void testCreate_DefaultFormat() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
    assertEquals("DateFilter(lastModified, yyyy-MM-dd, 365 days, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testCreate_EmptyFormatSameAsNull() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("format", "");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
    assertEquals("DateFilter(lastModified, yyyy-MM-dd, 365 days, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testMillisecondFormat() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("format", "millis");
    config.put("date", "2000-01-01");
    DateFilter transform = DateFilter.create(config);
    assertEquals("DateFilter(lastModified, millis, 2000-01-01, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testCreate_NoDateOrDays() {
    thrown.expect(IllegalArgumentException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    DateFilter transform = DateFilter.create(config);
  }

  @Test
  public void testCreate_BothDateAndDays() {
    thrown.expect(IllegalArgumentException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("date", "1977-06-18");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
  }

  @Test
  public void testCreate_DateDoesNotMatchFormat() {
    thrown.expect(IllegalArgumentException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("format", "yyyy-MM-dd");
    config.put("date", "6/18/1977");
    DateFilter transform = DateFilter.create(config);
  }

  @Test
  public void testCreate_DateInTheFuture() {
    thrown.expect(IllegalArgumentException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("date", "2525-06-18");
    DateFilter transform = DateFilter.create(config);
  }

  @Test
  public void testCreate_DaysInTheFuture() {
    thrown.expect(IllegalArgumentException.class);
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "-18");
    DateFilter transform = DateFilter.create(config);
  }

  @Test
  public void testToString_CustomFormat() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("format", "yyMMddHHmmssZ");
    config.put("days", "365");
    MetadataTransform transform = DateFilter.create(config);
    assertEquals("DateFilter(lastModified, yyMMddHHmmssZ, 365 days, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testToString_DateValueFilter() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("date", "1977-06-18");
    MetadataTransform transform = DateFilter.create(config);
    assertEquals("DateFilter(lastModified, yyyy-MM-dd, 1977-06-18, "
        + "metadata or params)", transform.toString());
  }

  @Test
  public void testToString_CorporaMetadata() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "metadata");
    MetadataTransform transform = DateFilter.create(config);
    assertEquals("DateFilter(lastModified, yyyy-MM-dd, 365 days, "
        + "metadata)", transform.toString());
  }

  @Test
  public void testToString_CorporaParams() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "params");
    MetadataTransform transform = DateFilter.create(config);
    assertEquals("DateFilter(lastModified, yyyy-MM-dd, 365 days, "
        + "params)", transform.toString());
  }

  @Test
  public void testCreate_CorporaBogus() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "bogus");
    MetadataTransform transform = DateFilter.create(config);
    assertEquals("DateFilter(lastModified, yyyy-MM-dd, 365 days, "
        + "metadata or params)", transform.toString());
  }


  // tests on transform behavior

  @Test
  public void testTransform_ForcedTransmissionDecision() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", "1977-06-18");
    params.put(MetadataTransform.KEY_FORCED_TRANSMISSION_DECISION, "as-is");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_DoNotSkipUndatedDocument() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_RelativeDoNotSkipNewDocument() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", dateFormat.format(new Date()));
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_RelativeSkipOldDocument() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", "1977-06-18");
    params.put(MetadataTransform.KEY_DOC_ID, "docId01");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_RelativeWindow() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "5");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", dateFormat.format(new Date(
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4))));
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));

    params.put("lastModified", dateFormat.format(new Date(
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6))));
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AbsoluteDoNotSkipNewDocument() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("date", "1977-06-18");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", dateFormat.format(new Date()));
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AbsoluteSkipOldDocument() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("date", "1977-06-18");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", "1969-07-20");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_AbsoluteWindow() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("date", "1977-06-18");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", "1977-06-18");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));

    params.put("lastModified", "1977-06-17");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_LenientDateParsing() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("format", "yyyy-MM-dd");
    config.put("days", "365");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", "1969-07-21T02:56:15Z");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_CustomDateParsing() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("format", "MM/dd/yyyy");
    config.put("days", "365");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", "07/21/1969");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_MillisecondDateParsing() throws Exception {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("format", "millis");
    config.put("days", "365");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", Long.toString(new Date().getTime()));
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
    params.put("lastModified",
               Long.toString(dateFormat.parse("1977-06-17").getTime()));
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_DoNotSkipUnparsableDate() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "params");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", "07/21/1969");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_DateInMetadata() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "metadata");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    Metadata metadata = new Metadata();
    metadata.add("lastModified", "1977-06-18");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_RecentDateInMetadata() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    config.put("corpora", "metadata");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    Metadata metadata = new Metadata();
    metadata.add("lastModified", dateFormat.format(new Date()));
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_MultipleDatesInMetadata() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "modifiedHistory");
    config.put("days", "365");
    config.put("corpora", "metadata");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    Metadata metadata = new Metadata();
    metadata.add("modifiedHistory", dateFormat.format(new Date()));
    metadata.add("modifiedHistory", "1977-06-18");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_MultipleDatesInMetadataOneBad() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "modifiedHistory");
    config.put("days", "365");
    config.put("corpora", "metadata");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    Metadata metadata = new Metadata();
    metadata.add("modifiedHistory", "07/21/1969");
    metadata.add("modifiedHistory", "1977-06-18");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_DateInMetadataOrParams1() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    Metadata metadata = new Metadata();
    metadata.add("lastModified", "1977-06-18");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_DateInMetadataOrParams2() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", "1977-06-18");
    Metadata metadata = new Metadata();
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_DateInMetadataAndParams() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", dateFormat.format(new Date()));
    Metadata metadata = new Metadata();
    metadata.add("lastModified", "1977-06-18");
    transform.transform(metadata, params);
    assertEquals("do-not-index", params.get("Transmission-Decision"));
  }

  @Test
  public void testTransform_RecentDateInMetadataAndParams() {
    Map<String, String> config = new HashMap<String, String>();
    config.put("key", "lastModified");
    config.put("days", "365");
    DateFilter transform = DateFilter.create(config);
    Map<String, String> params = new HashMap<String, String>();
    params.put("lastModified", dateFormat.format(new Date()));
    Metadata metadata = new Metadata();
    metadata.add("lastModified", dateFormat.format(new Date()));
    transform.transform(metadata, params);
    assertEquals(null, params.get("Transmission-Decision"));
  }
}
