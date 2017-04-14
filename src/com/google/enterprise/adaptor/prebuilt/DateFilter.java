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

public class DateFilter implements MetadataTransform {
  private static final Logger log
      = Logger.getLogger(DateFilter.class.getName());

  private static final ThreadLocal<SimpleDateFormat> dateFormatter =
      new ThreadLocal<SimpleDateFormat>() {
          @Override
          protected SimpleDateFormat initialValue() {
              return new SimpleDateFormat("yyyy-MM-dd");
          }
      };

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

  /** The active FileTimeFilter */
  private final FileTimeFilter filter;

  /**
   * If {@code METADATA}, only search the metadata for the specified key;
   * if {@code PARAMS}, only search the params for the specified key;
   * if {@code METADATA_OR_PARAMS}, search both.
   */
  private Corpora corpora = Corpora.METADATA_OR_PARAMS;

  private DateFilter(String key, String dateFormatString, FileTimeFilter filter,
      Corpora corpora) {
    this.key = key;
    this.dateFormatString = dateFormatString;
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
   * containing a value that matches the {@code pattern}.  Returns {@code true}
   * if found, {@code false} if not.
   */
  private boolean foundInMetadata(Metadata metadata) {
    boolean found = false;
    for (String value : metadata.getAllValues(key)) {
      if (pattern.matcher(value).find()) {
        found = true;
        break;
      }
    }
    log.fine((found ? "Did" : "Did not") + " find matching pattern for key `"
        + key + "' in metadata.");
    return found;
  }

  /**
   * Search (only) the {@code params} for an instance of the key
   * containing a value that matches the {@code pattern}.  Returns {@code true}
   * if found, {@code false} if not.
   */
  private boolean foundInParams(Map<String, String> params) {
    boolean found = false;
    if (params.containsKey(key)) {
      found = pattern.matcher(params.get(key)).find();
    }
    log.fine((found ? "Did" : "Did not") + " find matching pattern for key `"
        + key + "' in params.");
    return found;
  }

  /**
   * Conditionally adds a {@code Transmission-Decision} entry to the
   * {@code params Map}. The decision is based on settings of the
   * {@code key}, {@code pattern}, {@code decideOnMatch}, {@code decision},
   * and {@code corpora} configuration variables (as discussed above).
   */
  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    boolean found;
    switch (corpora) {
      case METADATA:
        found = foundInMetadata(metadata);
        break;
      case PARAMS:
        found = foundInParams(params);
        break;
      case METADATA_OR_PARAMS:
      default:
        found = foundInMetadata(metadata) || foundInParams(params);
    }

    String docId = params.get(MetadataTransform.KEY_DOC_ID);
    if (Strings.isNullOrEmpty(docId)) {
      docId = "with no docId";
    }

      if (found) {
        log.log(Level.INFO, "Transmission decision of {0} for document {1}, "
            + "because we found a match in {2}",
            new Object[] { decision, docId, corpora });
        params.put(MetadataTransform.KEY_TRANSMISSION_DECISION,
            decision.toString());
      } else {
        log.log(Level.FINE, "No transmission decision for document {0}, "
            + "because we did not find a match in {1}",
            new Object[] { docId, corpora });
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
    FileTimeFilter filter;
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
      filter = new AbsoluteFileTimeFilter(dateFormat.parse(dateStr));
    } else if (daysStr != null) {
      filter = new ExpiringFileTimeFilter(Integer.parseInt(daysStr));
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

  private static interface FileTimeFilter {
    public boolean excluded(FileTime fileTime);
  }

  private static class AlwaysAllowFileTimeFilter implements FileTimeFilter {
    @Override
    public boolean excluded(FileTime fileTime) {
      return false;
    }
  }

  private static class AbsoluteFileTimeFilter implements FileTimeFilter {
    private final FileTime oldestAllowed;

    public AbsoluteFileTimeFilter(FileTime oldestAllowed) {
      Preconditions.checkArgument(oldestAllowed.compareTo(
          FileTime.fromMillis(System.currentTimeMillis())) < 0,
          oldestAllowed.toString().substring(0, 10)
          + " is in the future.");
      this.oldestAllowed = oldestAllowed;
    }

    @Override
    public boolean excluded(FileTime fileTime) {
      return fileTime.compareTo(oldestAllowed) < 0;
    }
  }

  private static class ExpiringFileTimeFilter implements FileTimeFilter {
    private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000L;
    private final long relativeMillis;

    public ExpiringFileTimeFilter(int daysOld) {
      Preconditions.checkArgument(daysOld > 0, "The number of days old for "
          + "expired content must be greater than zero.");
      this.relativeMillis = daysOld * MILLIS_PER_DAY;
    }

    @Override
    public boolean excluded(FileTime fileTime) {
      FileTime oldestAllowed =
          FileTime.fromMillis(System.currentTimeMillis() - relativeMillis);
      return fileTime.compareTo(oldestAllowed) < 0;
    }
  }

}
