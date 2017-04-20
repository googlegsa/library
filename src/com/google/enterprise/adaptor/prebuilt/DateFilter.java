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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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
 * date values. If no {@code format} is specified, a lenient ISO8601 format
 * ("yyyy-MM-dd") is used. If format is "millis", then the dates
 * are parsed as if they were milliseconds since the epoch
 * (January 1, 1970, 00:00:00 GMT). Otherwise, the format must parsable
 * by {@link SimpleDateFormat}.
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
 * {@code corpora} The location of the date value to consider. The
 * {@code corpora} may be set to {@code metadata} or to {@code params}
 * to restrict the search to only metadata or params, respectively,
 * or to {@code metadata or params} to search both.
 */
public class DateFilter implements MetadataTransform {
  private static final Logger log
      = Logger.getLogger(DateFilter.class.getName());

  private static final String ISO_8601_FORMAT = "yyyy-MM-dd";

  /**
   * Which collections of keys/values to search.  Metadata, params, or both.
   */
  private enum Corpora {
    METADATA("metadata"),
    PARAMS("params"),
    METADATA_OR_PARAMS("metadata or params");

    private final String name;

    private Corpora(String n) {
      name = n;
    }

    public static Corpora from(String val) {
      if ("metadata".equalsIgnoreCase(val)) {
        return Corpora.METADATA;
      }
      if ("params".equalsIgnoreCase(val)) {
        return Corpora.PARAMS;
      }
      return METADATA_OR_PARAMS;
    }

    public String toString() {
      return name;
    }
  };

  /** The name of the key (either Metadata key or params key) to match. */
  private final String key;

  /** The DateFormat used to parse the date values. */
  private final String dateFormatString;
  private final ThreadLocal<DateFormat> dateFormat;

  /** The active DateValueFilter */
  private final DateValueFilter filter;

  /**
   * If {@code METADATA}, only search the metadata for the specified key;
   * if {@code PARAMS}, only search the params for the specified key;
   * if {@code METADATA_OR_PARAMS}, search both.
   */
  private Corpora corpora = Corpora.METADATA_OR_PARAMS;

  private DateFilter(String key, String dateFormatStr, DateValueFilter filter,
      Corpora corpora) {
    this.key = key;
    this.dateFormatString = dateFormatStr;
    this.filter = filter;
    this.corpora = corpora;

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
   * and {@code corpora} configuration variables (as discussed above).
   */
  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    String excludedDate;

    switch (corpora) {
      case METADATA:
        excludedDate = excludedByDateInMetadata(metadata);
        break;
      case PARAMS:
        excludedDate = excludedByDateInParams(params);
        break;
      case METADATA_OR_PARAMS:
      default:
        excludedDate = excludedByDateInParams(params);
        if (excludedDate == null) {
          excludedDate = excludedByDateInMetadata(metadata);
        }
    }

    String docId = params.get(MetadataTransform.KEY_DOC_ID);
    if (Strings.isNullOrEmpty(docId)) {
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
        .append(dateFormatString).append(", ")
        .append(filter.toString()).append(", ")
        .append(corpora).append(")")
        .toString();
  }

  public static DateFilter create(Map<String, String> cfg) {
    String key;
    String format;
    DateValueFilter filter;
    Corpora corpora;

    key = getTrimmedValue(cfg, "key");
    if (key == null) {
      throw new NullPointerException("key may not be null or empty");
    }
    log.config("key = " + key);

    format = getTrimmedValue(cfg, "format");
    if (format == null) {
      format = ISO_8601_FORMAT;
    }
    log.config("format = " + format);
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

    corpora = Corpora.from(getTrimmedValue(cfg, "corpora"));
    log.config("corpora set to " + corpora);

    return new DateFilter(key, format, filter, corpora);
  }

  private static String getTrimmedValue(Map<String, String> cfg, String key) {
    String value = cfg.get(key);
    return (value == null) ? value : Strings.emptyToNull(value.trim());
  }

  /** A DateFormat that parses text of milliseconds since the epoch. */
  @VisibleForTesting
  static class MillisecondDateFormat extends DateFormat {
    @Override
    public StringBuffer format(Date date, StringBuffer buf, FieldPosition pos) {
      buf.append(Long.toString(date.getTime()));
      return buf;
    }

    @Override
    public Date parse(String source, ParsePosition pos) {
      try {
        Long millis = Long.parseLong(source);
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
      Preconditions.checkArgument(oldestAllowed.compareTo(new Date()) < 0,
          oldestAllowed.toString() + " is in the future.");
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
      Preconditions.checkArgument(daysOld > 0, "The number of days old for "
          + "expired content must be greater than zero.");
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
