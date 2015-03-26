package com.google.enterprise.adaptor.examples;

import com.google.common.base.Strings;
import com.google.enterprise.adaptor.DocumentTransform;
import com.google.enterprise.adaptor.Metadata;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Metadata Transform to split date field into 3 separate metadata fields
 * (month, day, year)
 *
 * Specify the name of the metadata field containing the date as dateField
 * and the format of the date as dateFormat.  Format is described here:
 * http://docs.oracle.com/javase/6/docs/api/java/text/SimpleDateFormat.html
 *
 * Properties would look something like this:
 * transform.pipeline=step1
 * transform.pipeline.step1.dateField=Creation Time
 * transform.pipeline.step1.dateFormat=yyyy-MM-dd
 * transform.pipeline.step1.factoryMethod=com.google.enterprise.adaptor.examples.DateSplitTransform.load
 */

public class DateSplitTransform implements DocumentTransform {
  private static final Logger log = Logger.getAnonymousLogger();
  private static final String META_DATEFIELD = "dateField";
  private static final String META_DATEFORMAT = "dateFormat";
  private String dateField;
  private String dateFormat = "yyyy-MM-dd";

  private DateSplitTransform(String fieldname, String format) {
    if (null == fieldname) {
      throw new NullPointerException("dateField property not specified");
    } else {
      log.log(Level.CONFIG, "Using dateField: {0}", fieldname);
      dateField = fieldname;
    }

    if (null == format) {
      log.log(Level.CONFIG, "using default dateFormat: {0}", dateFormat);
    } else {
      dateFormat = format;
    }
  }

  /**
   * Called as <code>transform.pipeline.<stepX>.factoryMethod for this
   * transformation pipline as specified in adaptor-config.properties.
   * <p>
   * This method simply returns a new object with
   * the additional metadata as specified as values for step1.taste
   */
  public static DateSplitTransform load(Map<String, String> cfg) {
    return new DateSplitTransform(
        cfg.get(META_DATEFIELD),
        cfg.get(META_DATEFORMAT));
  }

  /**
   * Here we check to see if the current doc contains a "dateField" key
   * and if so, add the additional values
   */
  @Override
  public void transform(Metadata metadata, Map<String, String> params) {
    // For this example assume we have one date value for this field
    // If there are multiple values and the first one is invalid, the
    // additional values will not get processed
    String dateValue = metadata.getOneValue(dateField);
    if (Strings.isNullOrEmpty(dateValue)) {
      log.log(Level.FINE, "no metadata {0}. Skipping", dateField);
    } else {
      try {
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);
        Date date = formatter.parse(dateValue);
        Calendar cal = Calendar.getInstance();

        cal.setTime(date);
        metadata.add("year",
            Integer.toString(cal.get(Calendar.YEAR)));
        metadata.add("month",
            Integer.toString(cal.get(Calendar.MONTH) + 1));
        metadata.add("day",
            Integer.toString(cal.get(Calendar.DAY_OF_MONTH)));
      } catch (ParseException e) {
        log.log(Level.FINE,
            "Unable to parse date, not adding year/month/day metadata", e);
      }
    }
  }
}
