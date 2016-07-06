// Copyright 2016 Google Inc. All Rights Reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.enterprise.adaptor.GsaFeedFileArchiver;
import com.google.enterprise.adaptor.InvalidConfigurationException;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.SimpleGsaFeedFileMaker;
import com.google.enterprise.adaptor.GsaFeedFileSender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Feed-making and feed-sending program (that isn't an Adaptor).
 */
class SendToGsa {

  private static Logger log
      = Logger.getLogger(SendToGsa.class.getName());

  private Config config;

  private static final Pattern DATASOURCE_FORMAT
      = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_-]*");

  // complete set of command-line flags that do not take a value
  private static final Set<String> ZERO_VALUE_FLAGS =
      Collections.unmodifiableSet(Sets.newHashSet(
          "aclcaseinsensitive", "aclpublic", "crawlimmediately", "crawlonce",
          "dontsend", "filesofurls", "lock", "noarchive", "nofollow",
          "secure"));

  // set of command-line flags that take a value that are additive (they may
  // be called more than once, and each call appends to previous values)
  private static final Set<String> MULTI_VALUE_FLAGS =
      Collections.unmodifiableSet(Sets.newHashSet(
          "aclallowusers", "aclallowgroups", "acldenyusers", "acldenygroups",
          "suffixtoignore"));

  // set of command-line flags that take a value that are not additive (they
  // store a single value).  Repeated calls with the same value are allowed
  // (although a warning is given), but repeated calls with differing values
  // give an error.
  private static final Set<String> SINGLE_VALUE_FLAGS =
      Collections.unmodifiableSet(Sets.newHashSet(
          "aclnamespace", "datasource", "feeddirectory", "feedtype",
          "gsa", "lastmodified", "mimetype"));

  // set of command-line flags that are handled specially (by parsing the
  // contents of the filename passed in as its value).
  private static final Set<String> ONE_VALUE_FLAGS_POST_PROCESSING_REQUIRED =
      Collections.unmodifiableSet(Sets.newHashSet("script"));

  // complete set of command-line flags that take a value
  private static final Set<String> ONE_VALUE_FLAGS;
  static {
    Set<String> allOneValueFlags = new HashSet<String>();
    allOneValueFlags.addAll(MULTI_VALUE_FLAGS);
    allOneValueFlags.addAll(SINGLE_VALUE_FLAGS);
    allOneValueFlags.addAll(ONE_VALUE_FLAGS_POST_PROCESSING_REQUIRED);
    ONE_VALUE_FLAGS = Collections.unmodifiableSet(allOneValueFlags);
  }


  /**
   * Parse (and validate) the command-line values.
   *
   * @return validated configuration.
   * @throws InvalidConfigurationException when command-line flags are invalid
   */
  public void parseArgs(String[] args) {
    config = new Config();
    for (int i = 0; i < args.length; i++) {
      String flag = args[i];
      if (flag == null) {  // let parseFlag handle the error reporting
        config.parseFlag(null, null);
        continue;
      }
      if (flag.startsWith("-")) {
        i += config.parseFlag(flag, i < args.length - 1 ? args[i + 1] : null);
      } else {
        config.parseFilename(flag);
      }
    }
    config.validate();
  }

  /**
   * Creates the feed file as specified in the config, but does not push it.
   */
  @VisibleForTesting
  String createFeedFile() {
    Config c = config;
    final GsaFeedFileArchiver saver = new GsaFeedFileArchiver(c.feeddirectory);
    final SimpleGsaFeedFileMaker maker;
    if ("web".equals(c.feedtype)) {
      SimpleGsaFeedFileMaker.MetadataAndUrl metaMaker
          = new SimpleGsaFeedFileMaker.MetadataAndUrl();
      metaMaker.setCrawlImmediately(c.crawlimmediately);
      metaMaker.setCrawlOnce(c.crawlonce);
      maker = metaMaker;
    } else {
      maker = new SimpleGsaFeedFileMaker.Content(c.feedtype);
    }

    if (c.aclpublic) {
      maker.setPublicAcl();
    } else {
      maker.setAclProperties(c.aclcaseinsensitive, c.aclnamespace,
          Arrays.asList(c.allowusers.split(",", 0)),
          Arrays.asList(c.allowgroups.split(",", 0)),
          Arrays.asList(c.denyusers.split(",", 0)),
          Arrays.asList(c.denygroups.split(",", 0)));
    }
    maker.setDataSource(c.datasource);
    maker.setLastModified(c.lastmodified);
    maker.setLock(c.lock);
    maker.setMimetype(c.mimetype);
    // TODO(myk): add calls to setNoArchive / setNoFollow, when FeederGate
    // supports them.
    for (String fname : config.filenames) {
      try {
        maker.addFile(new File(fname));
      } catch (IOException e) {
        log.log(Level.WARNING, "Unable to add file '" + fname + "' - ignored",
            e);
      }
    }
    String feed = maker.toXmlString();
    saver.saveFeed("send2gsa", feed);
    return feed;
  }

  /**
   * Pushes the created feed (unless the config indicates that it should not
   * be pushed).
   */
  @VisibleForTesting
  void pushFeedFile(String xmlDoc) throws IOException {
    if (config.dontsend) {
      log.info("Not pushing feed file; see directory " + config.feeddirectory
          + " for feed file.");
      return;
    }
    GsaFeedFileSender sender = new GsaFeedFileSender(config.gsa, config.secure,
        Charset.forName("UTF-8"));
    if ("web".equals(config.feedtype)) {
      sender.sendMetadataAndUrl(config.datasource, xmlDoc,
          false /* useCompression */);
    } else if ("incremental".equals(config.feedtype)) {
      sender.sendIncremental(config.datasource, xmlDoc,
          false /* useCompression */);
    } else if ("full".equals(config.feedtype)) {
      sender.sendFull(config.datasource, xmlDoc, false /* useCompression */);
    } else {
      throw new AssertionError("feedType must be set to 'full', 'incremental'"
          + ", or 'web', not '" + config.feedtype + "'");
    }
  }

  /**
   * Returns a copy of the config (so that the original is not tampered with).
   */
  @VisibleForTesting
  Config getConfig() {
    return new Config(config);
  }

  /** SendToGsa main method.  Creates and optionally sends a Feed to the GSA.
   *  @param args flags and values that control the feed.
   */
  public static void main(String[] args) throws IOException {
    SendToGsa instance = new SendToGsa();
    instance.parseArgs(args);
    String xml = instance.createFeedFile();
    instance.pushFeedFile(xml);
  }

  /**
   * non-Adaptor Config class that parses all command line flags.
   */
  static class Config { // TODO(myk): investigate using something
        // along the lines of https://commons.apache.org/proper/commons-cli/
    /* stores configuration data as we parse flags/values */
    private final Map<String, String> flags;
    private final Collection<String> filenames;
    private final Collection<String> errors;

    // variables available after validation is complete.  (A few are set during
    // the validation process.)
    private boolean aclcaseinsensitive;
    private String aclnamespace;
    private boolean aclpublic;
    private String allowgroups;
    private String allowusers;
    private boolean crawlimmediately;
    private boolean crawlonce;
    private String datasource;
    private String denygroups;
    private String denyusers;
    private boolean dontsend;
    private String feeddirectory;
    private String feedtype;
    private String gsa;
    private Date lastmodified;
    private String mimetype;
    private boolean lock;
    private boolean noarchive;
    private boolean nofollow;
    private boolean secure;

    public Config() {
      flags = new TreeMap<String, String>();
      filenames = new ArrayList<String>();
      errors = new ArrayList<String>();
    }

    /**
     * Returns a copy of the config.  The original config may be modified after
     * this routine is invoked.
     */
    public Config(Config existingConfig) {
      flags = new TreeMap<String, String>(existingConfig.flags);
      filenames = new ArrayList<String>(existingConfig.filenames);
      errors = new ArrayList<String>(existingConfig.errors);
    }

    /**
     * Parse (and validate) a single flag.  Returns 0 if the flag
     * doesn't require any values (meaning that the next command-line flag
     * follows the current one).  Returns 1 if the flag does require a value
     * (which was passed in to this method).  Modifies the current configuration
     * to store the flag/value.
     *
     * @return number of "additional" command-line values consumed.
     */
    int parseFlag(String flag, String value) {
      if (null == flag) {
        errors.add("Encountered null flag");
        return 0;
      }
      flag = flag.toLowerCase();
      // skip over leading hyphens, extract flag (leave alone if hyphens only)
      for (int j = 0; j < flag.length(); j++) {
        if (flag.charAt(j) != '-') {
          flag = flag.substring(j);
          break;
        }
      }

      if (ZERO_VALUE_FLAGS.contains(flag)) {
        handleZeroValueFlag(flag);
        return 0;
      }
      if (ONE_VALUE_FLAGS.contains(flag)) {
        handleOneValueFlag(flag, value);
        return 1;
      }
      // keep track of unrecognized flags, to report them (all) at validation.
      errors.add("Encountered unrecognized flag \"" + flag + "\"");
      return 0;
    }

    private void handleZeroValueFlag(String flag) {
      // check to see if this flag has previously been set.  Warn if so.
      if (flags.get(flag) != null) {
        log.config("Ignoring duplicate flag " + flag);
        return;
      }
      flags.put(flag, "true");
      return;
    }

    private void handleOneValueFlag(String flag, String values) {
      String existing = flags.get(flag);  // TODO(myk): make "existing" a list
      if (MULTI_VALUE_FLAGS.contains(flag)) {
        // allow the single value to be a comma-separated list
        if (null == values || "".equals(values)) {
          errors.add("Flag \"" + flag + "\" must be followed with one or more "
              + "values");
          return;
        }
        for (String value : values.split(",", 0)) {
          value = value.trim();
          if (null == existing || "".equals(existing)) {
            existing = value;
          } else {
            // append any new values to what we previously have seen
            existing += "," + value;
          }
        }
        flags.put(flag, existing);
      } else if (SINGLE_VALUE_FLAGS.contains(flag)) {
        if (flags.get(flag) == null) {
          flags.put(flag, values);
        } else {
          if (flags.get(flag).equals(values)) {
            log.config("Ignoring duplicate flag " + flag);
            return;
          } else {
            // mark the flag as having been seen with conflicting values
            errors.add("The flag \"" + flag + "\" was already set to value \""
                + flags.get(flag) + "\".  It cannot be overridden to \""
                + values + "\"");
          }
        }
      } else if (ONE_VALUE_FLAGS_POST_PROCESSING_REQUIRED.contains(flag)) {
        parseFlagFile(values);
      } else {
        errors.add("Encountered unexpected flag \"" + flag + "\"");
      }
    }

    /**
     * Read flags/values from the specified file.
     */
    protected void parseFlagFile(String filename) {
      try {
        File file = new File(filename);
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          line = line.trim();
          if (!line.startsWith("#")) { // lines starting with "#" are comments
            String[] words = line.split(" ", 0);
            String flag = null;
            String value = null;
            if (words.length > 0) {
              flag = words[0].toLowerCase();
            }
            if (words.length > 1) {
              value = words[1];
            }
            if (words.length > 2) {
              if ('#' != words[2].charAt(0)) {
                errors.add("Only flags and values allowed in \"script\" files. "
                    + "Too many words found: " + line + " (line ignored)");
                flag = "";
              }
            }
            if (flag == null || "".equals(flag.trim())) { // ignore blank lines
              continue;
            }
            if (ONE_VALUE_FLAGS_POST_PROCESSING_REQUIRED.contains(flag)) {
              errors.add("A script file may not contain a \"script\" flag");
            } else {
              parseFlag(flag, value);
            }
          }
        }
        fileReader.close();
        // TODO(myk): consider handling fileReader.close() extra carefully
      } catch (IOException e) {
        PrintWriter pw = new PrintWriter(new StringWriter());
        e.printStackTrace(pw);
        errors.add("Unexpected Exception in the " + filename + " script: "
            + pw.toString());
      }
    }

    /**
     * Add the file (or directory) to the list of files to feed.  If it's a
     * directory, recursively crawl it.
     */
    protected void parseFilename(String filename) {
      if (null == filename || "".equals(filename)) {
        log.warning("Empty filename specified - ignored.");
        return;
      }
      File f = new File(filename);
      if (!f.exists()) {
        log.warning("File \"" + filename + "\" does not exist - ignored.");
        return;
      }
      RecursiveFileIterator files = new RecursiveFileIterator(f);
      while (files.hasNext()) {
        String fn = files.next().getPath();
        filenames.add(fn);
      }
    }

    /**
     * Validate the configuration.  Prints out warnings (but continues), unless
     * something is so wrong that it requires an abort.
     */
    @VisibleForTesting
    void validate() {
      // first, check ACLs.  Either "aclpublic" or at least one of the
      // "acl{allow,deny}{users,groups}" options must have been set.
      aclpublic = (null != flags.get("aclpublic"));
      boolean aclSet = ((null != flags.get("aclallowusers"))
          || (null != flags.get("aclallowgroups"))
          || (null != flags.get("acldenyusers"))
          || (null != flags.get("acldenygroups")));
      if (aclpublic && aclSet) {
        errors.add("aclPublic flag may not be set together with any of the "
            + "aclAllowUsers/aclAllowGroups/aclDenyUsers/aclDenyGroups flags");
      }
      if (!aclpublic && !aclSet) {
        errors.add("either aclPublic flag or at least one of the "
            + "aclAllowUsers/aclAllowGroups/aclDenyUsers/aclDenyGroups flags "
            + "must be set");
      }

      feedtype = flags.get("feedtype");
      // certain flags imply feedtype web
      crawlimmediately = (null != flags.get("crawlimmediately"));
      crawlonce = (null != flags.get("crawlonce"));
      boolean filesofurls = (null != flags.get("filesofurls"));
      if (crawlimmediately || crawlonce || filesofurls) {
        if (null == feedtype) {
          flags.put("feedtype", "web");
          feedtype = "web";
        }
        if (!"web".equals(feedtype)) {
          errors.add("at least one of the crawlImmediately/crawlOnce/"
              + "filesOfUrls flags were set (all of which imply feedtype web), "
              + "but feedtype set to " + feedtype);
        }
      }

      // otherwise, default to incremental feed
      if (null == feedtype) {
        flags.put("feedtype", "incremental");
        feedtype = "incremental";
      }

      // make sure feedtype is set to something valid
      if (!(("full".equals(feedtype)) || ("incremental".equals(feedtype))
          || ("web".equals(feedtype)))) {
        errors.add("feedType must be set to 'full', 'incremental', or 'web', "
            + "not '" + feedtype + "'");
      }

      // exclude any files that end with an excluded suffix.
      if (null != flags.get("suffixtoignore")) {
        String[] ignoredSuffices = flags.get("suffixtoignore").split(",", 0);
        Collection<String> filesToIgnore = new ArrayList<String>();
        for (String filename : filenames) {
          for (String suffix : ignoredSuffices) {
            if (filename.endsWith(suffix)) {
              log.config("Ignoring " + filename + ", since it ends with '"
                  + suffix + "'.");
              filesToIgnore.add(filename);
              break;
            }
          }
        }
        for (String filename : filesToIgnore) {
          filenames.remove(filename);
        }
      }

      // remove any duplicates from our list of filenames
      Collection<String> uniqueNames = new HashSet<String>();
      Collection<String> filesToIgnore = new ArrayList<String>();
      for (String filename : filenames) {
        if (uniqueNames.contains(filename)) {
          log.config("Ignoring duplicate file " + filename);
          filesToIgnore.add(filename);
        }
        uniqueNames.add(filename);
      }
      for (String filename : filesToIgnore) {
        filenames.remove(filename);
      }

      if (filenames.size() == 0) {
        errors.add("No content specified.  send2gsa must be invoked with a non-"
           + "empty list of files");
      }

      gsa = flags.get("gsa");
      dontsend = (null != flags.get("dontsend"));

      if (!dontsend && null == gsa) {
        errors.add("You must either specify the 'gsa' or the 'dontSend' flag");
      }

      if (null == flags.get("lastmodified")) {
        lastmodified = null;
      } else {
        lastmodified = parseLastModifiedTime(flags.get("lastmodified"));
      }

      datasource = flags.get("datasource");
      if (null == datasource) {
        if ("web".equals(feedtype)) {
          datasource = feedtype;
        } else {
          datasource = "send2gsa";
        }
      }
      if (!DATASOURCE_FORMAT.matcher(datasource).matches()) {
        errors.add("Data source contains illegal characters: " + datasource
            + " (it must start with a letter then consist of only letters, "
            + "numbers, underscores, and dashes.)");
      }

      if (!errors.isEmpty()) {
        for (String error : errors) {
          log.severe(error);
        }
        throw new InvalidConfigurationException("Encountered " + errors.size()
            + " error(s) in configuration: " + errors);
      }

      // provide some defaults
      mimetype = flags.get("mimetype");
      if (null == mimetype) {
        mimetype = "text/plain";  // FeederGate requires a value be specified
      }

      // validation successful; set remaining config variables for easy access
      aclcaseinsensitive = (null != flags.get("aclcaseinsensitive"));
      lock = (null != flags.get("lock"));
      noarchive = (null != flags.get("noarchive"));
      nofollow = (null != flags.get("nofollow"));
      secure = (null != flags.get("secure"));

      allowgroups = flags.get("aclallowgroups");
      if (null == allowgroups) {
        allowgroups = "";
      }

      allowusers = flags.get("aclallowusers");
      if (null == allowusers) {
        allowusers = "";
      }

      denygroups = flags.get("acldenygroups");
      if (null == denygroups) {
        denygroups = "";
      }

      denyusers = flags.get("acldenyusers");
      if (null == denyusers) {
        denyusers = "";
      }

      aclnamespace = flags.get("aclnamespace");
      if (null == aclnamespace) {
        aclnamespace = Principal.DEFAULT_NAMESPACE;
      }

      feeddirectory = flags.get("feeddirectory");
      if (null == feeddirectory) {
        File tempdir;
        try {
          tempdir = Files.createTempDir();
          feeddirectory = tempdir.getAbsolutePath();
        } catch (IllegalStateException e) {
          throw new RuntimeException("Could not create temporary directory", e);
        }
      }
    }

    /**
     * Tries a few different @{code SimpleDateFormat}s to parse the
     * user-specified time, returning as soon as one can parse the input.  If
     * none can match, an error message is added.
     * @param time user-specified time (for lastModified values)
     * @return Date that matches time, if parsing successful.
     */
    @VisibleForTesting
    Date parseLastModifiedTime(String time) {
      // first one on the list is rfc822 format; rest are simple variants on
      // Month/day/year or year-month-day with optional time specifiers.
      List<String> formats = Arrays.asList("EEE, dd MMM yyyy HH:mm:ss Z",
          "yyyy-MM-dd'T'HH:mm:ssZ", /* ISO 6001 with suffix e.g. -0700 */
          "yyyy-MM-dd'T'HH:mm:ssXXX", /* ISO 6001 with suffix e.g. -07:00 */
          "yyyy-MM-dd HH:mm:ssZ", "yyyy-MM-dd HH:mm:ssXXX",
          "yyyy-MM-dd HH:mm:ss Z", "yyyy-MM-dd HH:mm:ss XXX",
          "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd",
          "M/d/y H:m:s Z", "M/d/y H:m:s XXX", "M/d/y H:m:s",
          "M/d/y H:m Z", "M/d/y H:m XXX", "M/d/y H:m",
          "M/d/y Z", "M/d/y XXX", "M/d/y");
      for (String format : formats) {
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.ENGLISH);
        try {
          return sdf.parse(time);
        } catch (ParseException pe) {
          // just drop the exception on the floor
        }
      }
      errors.add("Could not parse lastModified time of '" + time + "'");
      return null;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Config(");
      boolean first = true;
      for (Map.Entry<String, String> me : flags.entrySet()) {
        if (first) {
          first = false;
        } else {
          sb.append(",");
        }
        sb.append(me.getKey());
        sb.append("=");
        sb.append(me.getValue());
      }
      sb.append(",files to feed: " + filenames + ")");
      return sb.toString();
    }
  }
}
