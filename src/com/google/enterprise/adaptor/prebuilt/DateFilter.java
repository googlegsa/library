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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private final ThreadLocal<SimpleDateFormat> dateFormat;

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

    // SimpleDateFormat is not thread-safe, so each thread gets its own instance.
    this.dateFormat = new ThreadLocal<SimpleDateFormat>() {
      @Override
      protected SimpleDateFormat initialValue() {
        SimpleDateFormat format = new SimpleDateFormat(dateFormatString);
        format.setLenient(true);
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
      log.log(Level.INFO, "Not skipping document {0}, because {1} is in range.",
          new Object[] { docId, key });
    }
  }

  @Override
  public String toString() {
    return new StringBuilder("DateFilter(")
        .append(key).append(", ")
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

    String dateStr = getTrimmedValue(cfg, "date");
    String daysStr = getTrimmedValue(cfg, "days");
    if (dateStr != null) {
      if (daysStr != null) {
        throw new IllegalArgumentException("Only one of 'date' or 'days' "
            + " configuration may be specified.");
      }
      SimpleDateFormat dateFormat = new SimpleDateFormat(format);
      dateFormat.setLenient(true);
      try {
        filter = new AbsoluteDateValueFilter(dateFormat.parse(dateStr));
      } catch (ParseException e) {
        throw new IllegalArgumentException("date " + dateStr
            + " does not conform to date format " + format, e);
      }
    } else if (daysStr != null) {
      filter = new ExpiringDateValueFilter(Integer.parseInt(daysStr));
    } else {
      throw new IllegalArgumentException("Either 'date' or 'days' "
          + " configuration must be specified.");
    }

    corpora = Corpora.from(getTrimmedValue(cfg, "corpora"));
    log.config("corpora set to " + corpora);

    return new DateFilter(key, format, filter, corpora);
  }

  private static String getTrimmedValue(Map<String, String> cfg, String key) {
    String value = cfg.get(key);
    return (value == null) ? value : Strings.emptyToNull(value.trim());
  }

  private static interface DateValueFilter {
    public boolean excluded(Date date);
  }

  private static class AbsoluteDateValueFilter implements DateValueFilter {
    private final Date oldestAllowed;

    public AbsoluteDateValueFilter(Date oldestAllowed) {
      Preconditions.checkArgument(oldestAllowed.compareTo(new Date()) < 0,
          oldestAllowed.toString() + " is in the future.");
      this.oldestAllowed = oldestAllowed;
    }

    @Override
    public boolean excluded(Date date) {
      return date.compareTo(oldestAllowed) < 0;
    }
  }

  private static class ExpiringDateValueFilter implements DateValueFilter {
    private final long relativeMillis;

    public ExpiringDateValueFilter(int daysOld) {
      Preconditions.checkArgument(daysOld > 0, "The number of days old for "
          + "expired content must be greater than zero.");
      this.relativeMillis = TimeUnit.DAYS.toMillis(daysOld);
    }

    @Override
    public boolean excluded(Date date) {
      Date oldestAllowed
          = new Date(System.currentTimeMillis() - relativeMillis);
      return date.compareTo(oldestAllowed) < 0;
    }
  }
}
