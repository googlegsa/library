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

import static com.google.enterprise.adaptor.MetadataTransform.TransmissionDecision;

import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transform causing exclusion of certain Documents, based on a date
 * in that document's Metadata or Param properties. Documents whose
 * associated date is before the configured date will not be indexed.
 * <p>
 * Configuration properties:
 * <p>
 * {@code key} The name of the metadata or param property whose date value
 * determines whether the document will be skipped or not.
 * <p>
 * {@code format} The {@link DateFormat} used to parse the document's
 * date values. If no {@code format} is specified, a ISO8601 format
 * ("yyyy-MM-dd") is used. If format is "millis", then the dates
 * are parsed as if they were milliseconds since the epoch
 * (January 1, 1970, 00:00:00 GMT). Otherwise, a lenient
 * {@link SimpleDateFormat} with the format pattern will be used.
 * <p>
 * {@code date} The cut-off for the date value. Document's whose date
 * value is before the configued {@code date} will not be indexed.
 * The configured {@code date} must be parsable by the configured date
 * {@code format}, unless {@code format} is "millis", in which
 * case the date must be specified in ISO8601 format. Only one of
 * {@code date} or {@code days} configuration may be specified.
 * <p>
 * {@code days} The cut-off for the date value. Document's whose date
 * value is more than {@code days} before present will not be indexed.
 * This can be used to have a rolling window of indexed documents. Those
 * whose date have expired will be removed from the index.  Only one of
 * {@code date} or {@code days} configuration may be specified.
 * <p>
 * {@code keyset} The location of the date value to consider. The
 * {@code keyset} may be set to {@code metadata} or to {@code params}
 * to restrict the search to metadata or params, respectively.
 *  If {@code keyset} is not specified, it defaults to {@code metadata}.
 * <p>
 * Example 1: skip documents that have not been accessed for more than 3 years:
 * <pre><code>
 * metadata.transform.pipeline=dateFilter
 * metadata.transform.pipeline.dateFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.DateFilter.create
 * metadata.transform.pipeline.dateFilter.key=Last_Access_Date
 * metadata.transform.pipeline.dateFilter.days=1095
 * </code></pre>
 * <p>
 * Example 2: skip pre-Y2K client records that used old-style US date formats:
 * <pre><code>
 * metadata.transform.pipeline=dateFilter
 * metadata.transform.pipeline.dateFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.DateFilter.create
 * metadata.transform.pipeline.dateFilter.key=Last_Visit_Date
 * metadata.transform.pipeline.dateFilter.format=MM/dd/YY
 * metadata.transform.pipeline.dateFilter.date=01/01/00
 * </code></pre>
 * <p>
 * Example 3: skip documents that have not been modified since 2010:
 * <pre><code>
 * metadata.transform.pipeline=dateFilter
 * metadata.transform.pipeline.dateFilter.factoryMethod=com.google.enterprise.adaptor.prebuilt.DateFilter.create
 * metadata.transform.pipeline.dateFilter.keyset=params
 * metadata.transform.pipeline.dateFilter.key=Last-Modified-Millis-UTC
 * metadata.transform.pipeline.dateFilter.format=millis
 * metadata.transform.pipeline.dateFilter.date=2010-01-01
 * </code></pre>
 */
public class DateFilter implements MetadataTransform {
  private static final Logger log
      = Logger.getLogger(DateFilter.class.getName());

  private static final String ISO_8601_FORMAT = "yyyy-MM-dd";

  // TODO (bmj): remove this and use the one in MetadataTransforms.
  public enum Keyset {
    METADATA("metadata"),
    PARAMS("params");

    private final String name;

    private Keyset(String name) {
      this.name = name;
    }

    public static Keyset from(String val) {
      return (val == null) ? METADATA : Keyset.valueOf(val.toUpperCase());
    }

    @Override
    public String toString() {
      return name;
    }
  };

  /** The name of the key (either Metadata key or params key) to match. */
  private final String key;

  /**
   * If {@code METADATA}, search the metadata for the specified key;
   * if {@code PARAMS},  search the params for the specified key;
   */
  private Keyset keyset = Keyset.METADATA;

  /** The DateFormat used to parse the date values. */
  private final String dateFormatString;
  private final ThreadLocal<DateFormat> dateFormat;

  /** The active DateValueFilter */
  private final DateValueFilter filter;

  private DateFilter(String key, Keyset keyset, String dateFormatStr,
      DateValueFilter filter) {
    this.key = key;
    this.keyset = keyset;
    this.dateFormatString = dateFormatStr;
    this.filter = filter;

    // DateFormat is not thread-safe, so each thread gets its own instance.
    this.dateFormat = new ThreadLocal<DateFormat>() {
      @Override
      protected DateFormat initialValue() {
        DateFormat format;
        if ("millis".equalsIgnoreCase(dateFormatString)) {
          format = new MillisecondDateFormat();
        } else {
          format = new SimpleDateFormat(dateFormatString);
          format.setLenient(true);
        }
        return format;
      }
    };
  }

  /**
   * Search (only) the {@code Metadata} for an instance of the key
   * containing a date value that would trigger this document to be
   * skipped.
   * Returns a date value that the filter would exclude, or null
   * if none match.
   */
  private String excludedByDateInMetadata(Metadata metadata) {
    for (String value : metadata.getAllValues(key)) {
      try {
        if (filter.excluded(dateFormat.get().parse(value))) {
          return value;
        }
      } catch (ParseException e) {
        log.log(Level.WARNING, "Date value " + value + " does not conform to "
            + "date format " + dateFormatString, e);
      }
    }
    return null;
  }

  /**
   * Search (only) the {@code params} for an instance of the key
   * containing a date value that would trigger this document to be
   * skipped.
   * Returns a date value that the filter would exclude, or null
   * if none match.
   */
  private String excludedByDateInParams(Map<String, String> params) {
    String value = params.get(key);
    if (value != null) {
      try {
        if (filter.excluded(dateFormat.get().parse(value))) {
          return value;
        }
      } catch (ParseException e) {
        log.log(Level.WARNING, "Date value " + value + " does not conform to "
            + "date format " + dateFormatString, e);
      }
    }
    return null;
  }

  /**
   * Conditionally adds a {@code Transmission-Decision} entry to the
   * {@code params Map}. The decision is based on settings of the
   * {@code key}, {@code pattern}, {@code decideOnMatch}, {@code decision},
   * and {@code keyset} configuration variables (as discussed above).
   */
  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    String excludedDate;

    switch (keyset) {
      case METADATA:
        excludedDate = excludedByDateInMetadata(metadata);
        break;
      case PARAMS:
        excludedDate = excludedByDateInParams(params);
        break;
      default:
        excludedDate = null;
    }

    String docId = params.get(MetadataTransform.KEY_DOC_ID);
    if (null == docId || docId.isEmpty()) {
      docId = "with no docId";
    }

    if (excludedDate != null) {
      log.log(Level.INFO, "Skipping document {0}, because {1}: {2} is too old.",
          new Object[] { docId, key, excludedDate });
      params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
          TransmissionDecision.DO_NOT_INDEX.toString());
    } else {
      log.log(Level.FINE, "Not skipping document {0}, because {1} is in range.",
          new Object[] { docId, key });
    }
  }

  @Override
  public String toString() {
    return new StringBuilder("DateFilter(")
        .append(key).append(", ")
        .append(keyset).append(", ")
        .append(dateFormatString).append(", ")
        .append(filter.toString()).append(")")
        .toString();
  }

  public static DateFilter create(Map<String, String> cfg) {
    String key;
    Keyset keyset;
    String format;
    DateValueFilter filter;

    key = getTrimmedValue(cfg, "key");
    if (key == null) {
      throw new NullPointerException("key may not be null or empty");
    }
    log.config("key = " + key);

    keyset = Keyset.from(getTrimmedValue(cfg, "keyset"));
    log.config("keyset set to " + keyset);

    format = getTrimmedValue(cfg, "format");
    if (format == null) {
      format = ISO_8601_FORMAT;
    }
    log.config("format = " + format);
    // If using MillisecondDateFormat, the cutoff date is specified as ISO8601,
    // otherwise the cutoff date is in the configured format.
    SimpleDateFormat dateFormat = new SimpleDateFormat(
        "millis".equalsIgnoreCase(format) ? ISO_8601_FORMAT : format);
    dateFormat.setLenient(true);

    String dateStr = getTrimmedValue(cfg, "date");
    String daysStr = getTrimmedValue(cfg, "days");
    if (dateStr != null) {
      if (daysStr != null) {
        throw new IllegalArgumentException("Only one of 'date' or 'days' "
            + " configuration may be specified.");
      }
      try {
        filter =
            new AbsoluteDateValueFilter(dateFormat.parse(dateStr), dateStr);
      } catch (ParseException e) {
        throw new IllegalArgumentException("date " + dateStr
            + " does not conform to date format " + format, e);
      }
      log.config("date = " + dateStr);
    } else if (daysStr != null) {
      filter = new ExpiringDateValueFilter(Integer.parseInt(daysStr));
      log.config("days = " + daysStr);
    } else {
      throw new IllegalArgumentException("Either 'date' or 'days' "
          + " configuration must be specified.");
    }
    log.log(Level.INFO, "Documents whose {0} date is earlier than {1} will be "
        + "skipped.", new Object[] { key, filter.toString() });

    return new DateFilter(key, keyset, format, filter);
  }

  private static String getTrimmedValue(Map<String, String> cfg, String key) {
    String value = cfg.get(key);
    if (value != null) {
      value = value.trim();
      if (value.length() > 0) {
        return value;
      }
    }
    return null;
  }

  /** A DateFormat that parses text of milliseconds since the epoch. */
  // @VisibleForTesting
  static class MillisecondDateFormat extends DateFormat {
    @Override
    public StringBuffer format(Date date, StringBuffer buf, FieldPosition pos) {
      buf.append(Long.toString(date.getTime()));
      return buf;
    }

    @Override
    public Date parse(String source, ParsePosition pos) {
      try {
        long millis = Long.parseLong(source);
        pos.setIndex(source.length());
        return new Date(millis);
      } catch (NumberFormatException e) {
        pos.setErrorIndex(pos.getIndex());
        return null;
      }
    }
  }

  private static interface DateValueFilter {
    public boolean excluded(Date date);
  }

  private static class AbsoluteDateValueFilter implements DateValueFilter {
    private final Date oldestAllowed;
    private final String dateStr;

    public AbsoluteDateValueFilter(Date oldestAllowed, String dateStr) {
      if (oldestAllowed.compareTo(new Date()) > 0) {
        throw new IllegalArgumentException(
          oldestAllowed.toString() + " is in the future.");
      }
      this.oldestAllowed = oldestAllowed;
      this.dateStr = dateStr;
    }

    @Override
    public boolean excluded(Date date) {
      return date.compareTo(oldestAllowed) < 0;
    }

    @Override
    public String toString() {
      return dateStr;
    }
  }

  private static class ExpiringDateValueFilter implements DateValueFilter {
    private final int daysOld;
    private final long relativeMillis;

    public ExpiringDateValueFilter(int daysOld) {
      if (daysOld <= 0) {
        throw new IllegalArgumentException("The number of days old for "
            + "expired content must be greater than zero.");
      }
      this.daysOld = daysOld;
      this.relativeMillis = TimeUnit.DAYS.toMillis(daysOld);
    }

    @Override
    public boolean excluded(Date date) {
      Date oldestAllowed
          = new Date(System.currentTimeMillis() - relativeMillis);
      return date.compareTo(oldestAllowed) < 0;
    }

    @Override
    public String toString() {
      return daysOld + " days";
   }
  }
}
