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

package com.google.enterprise.adaptor.prebuilt;

import com.google.enterprise.adaptor.DocumentTransform;
import com.google.enterprise.adaptor.Metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common transforms that you would expect to have available.
 */
public class PrebuiltTransforms {
  private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]+");

  private static final Logger log
      = Logger.getLogger(PrebuiltTransforms.class.getName());

  // Prevent instantiation.
  private PrebuiltTransforms() {}

  /**
   * Returns a transform that copies metadata values from one key to another.
   * The {@code "overwrite"} key can be set to {@code "true"} to cause the
   * destination to be replaced; otherwise the destination key is supplemented.
   *
   * <p>Copies are defined by pairs of {@code "X.from"} and {@code "X.to"}
   * configuration entries (where {@code X} is an integer). The value for each
   * is a metadata key. Copies are applied in the increasing order of the
   * integers.
   *
   * <p>Example configuration:
   * <pre><code>overwrite=false
   *3.from=colour
   *3.to=color
   *5.from=author
   *5.to=contributors</code></pre>
   */
  public static DocumentTransform copyMetadata(Map<String, String> config) {
    boolean overwrite = Boolean.parseBoolean(config.get("overwrite"));
    List<KeyPairing> copies = parseCopies(config);
    if (copies.isEmpty()) {
      log.warning("No entries listed to be copied");
    }
    return new CopyTransform(copies, overwrite, false);
  }

  /**
   * Returns a transform that moves metadata values from one key to another.
   * This method returns a transform that behaves identically to {@link
   * #copyMetadata}, except that the source keys are removed. If the source key
   * has no metadata values then the destination is left as-is.
   */
  public static DocumentTransform moveMetadata(Map<String, String> config) {
    boolean overwrite = Boolean.parseBoolean(config.get("overwrite"));
    List<KeyPairing> copies = parseCopies(config);
    if (copies.isEmpty()) {
      log.warning("No entries listed to be moved");
    }
    return new CopyTransform(copies, overwrite, true);
  }

  /**
   * Pairs of keys, with a src-key-name and destination-key-name.
   * The sequence is in order the copies/moves should happen.
   */
  private static List<KeyPairing> parseCopies(Map<String, String> config) {
    Map<Integer, Map<String, String>> allSubs = parseOrderedMaps(config);
    List<KeyPairing> copies = new ArrayList<KeyPairing>(allSubs.size());
    for (Map.Entry<Integer, Map<String, String>> instruction
        : allSubs.entrySet()) {
      String from = instruction.getValue().get("from");
      String to = instruction.getValue().get("to");
      if (from == null || to == null) {
        log.log(Level.FINE, "Ignoring int {0}. Missing .from or .to",
            instruction.getKey());
        continue;
      }
      if (from.equals(to)) {
        log.log(Level.WARNING, "removing no-op: {0}", from);
        continue;
      }
      KeyPairing kp = new KeyPairing(from, to);
      copies.add(kp);
      log.log(Level.FINE, "Found config to rename {0} to {1}",
          new Object[] {from, to});
    }
    return copies;
  }

  /**
   * Splits configurations that start with number and period into
   * their own maps.
   * <p>
   * For example we get all configuration items that are like "12.BLAH"
   * (some integer followed by a dot and more text). The inner map's keys
   * are the text strings that follow the dot (so "BLAH" would be a key in 
   * this case).
   */
  private static Map<Integer, Map<String, String>>
      parseOrderedMaps(Map<String, String> config) {
    Map<Integer, Map<String, String>> numberedConfigs
        = new TreeMap<Integer, Map<String, String>>();
    for (Map.Entry<String, String> me : config.entrySet()) {
      String[] parts = me.getKey().split("\\.", 2);
      if (parts.length != 2) {
        log.log(Level.FINER,
            "Not a copy definition. Does not contain a dot: {0}", me.getKey());
        continue;
      }
      if (!INTEGER_PATTERN.matcher(parts[0]).matches()) {
        log.log(Level.FINER,
            "Not a copy definition. Does not start with an integer: {0}",
            me.getKey());
        continue;
      }
      int i = Integer.parseInt(parts[0]);
      Map<String, String> values = numberedConfigs.get(i);
      if (values == null) {
        values = new HashMap<String, String>();
        numberedConfigs.put(i, values);
      }
      values.put(parts[1], me.getValue());
    }
    return numberedConfigs;
  }

  private static class CopyTransform implements DocumentTransform {
    private final List<KeyPairing> copies;
    private final boolean overwrite;
    private final boolean move;

    private CopyTransform(List<KeyPairing> copies, boolean overwrite,
        boolean move) {
      this.copies = Collections.unmodifiableList(
          new ArrayList<KeyPairing>(copies));
      this.overwrite = overwrite;
      this.move = move;
    }

    @Override
    public void transform(Metadata metadata, Map<String, String> params) {
      for (KeyPairing kp : copies) {
        Set<String> values = metadata.getAllValues(kp.src);
        if (values.isEmpty()) {
          log.log(Level.FINE, "No values for {0}. Skipping", kp.src);
          continue;
        }
        log.log(Level.FINE, "Copying values from {0} to {1}: {2}",
            new Object[] {kp.src, kp.dest, values});
        Set<String> destValues = metadata.getAllValues(kp.dest);
        if (!overwrite && !destValues.isEmpty()) {
          values = new HashSet<String>(values);
          log.log(Level.FINER, "Preexisting values for {0}. Combining: {1}",
              new Object[] {kp.dest, destValues});
          values.addAll(destValues);
        }
        metadata.set(kp.dest, values);
        if (move) {
          log.log(Level.FINER, "Deleting source {0}", kp.src);
          metadata.set(kp.src, Collections.<String>emptySet());
        }
      }
    }

    @Override
    public String toString() {
      return "CopyTransform(copies=" + copies + ",overwrite=" + overwrite
          + ",move=" + move + ")";
    }
  }

  /** Contains source and destination metadata key. */
  private static class KeyPairing {
    private final String src;
    private final String dest;

    KeyPairing(String from, String to) {
      if (null == from || null == to) {
        throw new NullPointerException();
      } 
      src = from;
      dest = to;
    }

    public String toString() {
      return "KeyPairing(from=" + src + ",to=" + dest + ")";
    }
  }

  /**
   * Returns a transform that deletes metadata keys. The keys to be deleted are
   * defined by {@code "keyX"} configuration entries (where {@code X} is an
   * integer).
   *
   * <p>Example configuration:
   * <pre><code>key2=sensitive
   *key4=unhelpful</code></pre>
   */
  public static DocumentTransform deleteMetadata(Map<String, String> config) {
    Set<String> keys = new HashSet<String>(parseList(config, "key"));
    if (keys.isEmpty()) {
      log.warning("No entries listed to delete");
    }
    return new DeleteTransform(keys);
  }

  private static List<String> parseList(Map<String, String> config,
      String prefix) {
    List<String> keys = new LinkedList<String>();
    for (Map.Entry<String, String> me : config.entrySet()) {
      if (!me.getKey().startsWith(prefix)) {
        continue;
      }
      String number = me.getKey().substring(prefix.length());
      if (!INTEGER_PATTERN.matcher(number).matches()) {
        log.log(Level.FINE, "Ignoring {0}. Number does not follow .{1}",
            new Object[] {me.getKey(), prefix});
        continue;
      }
      keys.add(me.getValue());
    }
    return keys;
  }

  private static class DeleteTransform implements DocumentTransform {
    private final List<String> keys;

    public DeleteTransform(Collection<String> keys) {
      this.keys = Collections.unmodifiableList(new ArrayList<String>(keys));
    }

    @Override
    public void transform(Metadata metadata, Map<String, String> params) {
      for (String key : keys) {
        metadata.set(key, Collections.<String>emptySet());
      }
    }

    @Override
    public String toString() {
      return "DeleteTransform(keys=" + keys + ")";
    }
  }

  /**
   * Returns a transform that preforms string replacements within metadata
   * values. The keys to have replacements done on their values are defined by
   * {@code "keyX"} configuration entries (where {@code X} is an
   * integer). The {@code "overwrite"} configuration key can be set to {@code
   * "false"} to cause the original string to be left intact; otherwise the
   * original string is replaced.
   *
   * <p>The needle to be found is configured via a {@code "string"} or {@code
   * "pattern"} configuration key. {@code string}'s value is treated as a
   * literal string to be found whereas {@code pattern}'s value is treated as
   * a regular expression. The replacement is defined by {@code "replacement"}
   * and is interpreted as a literal string if {@code "string"} was provided
   * and a regular expression replacement if {@code "pattern"} was provided.
   *
   * <p>Example configuration:
   * <pre><code>overwrite=false
   *key1=favorite
   *key5=least favorite
   *pattern=(Java|C|Perl)
   *replacement=$1 (but it should be x86 assembler)</code</pre>
   */
  public static DocumentTransform replaceMetadata(Map<String, String> config) {
    boolean overwrite = true;
    String overwriteString = config.get("overwrite");
    if (overwriteString != null) {
      overwrite = Boolean.parseBoolean(overwriteString);
    }
    String string = config.get("string");
    String pattern = config.get("pattern");
    String replacement = config.get("replacement");
    if (replacement == null) {
      throw new IllegalArgumentException("Missing replacement");
    }
    Pattern toMatch;
    String replacementPattern;
    if (string != null) {
      if (pattern != null) {
        throw new IllegalArgumentException(
            "Using both string and pattern is not permitted");
      }
      toMatch = Pattern.compile(Pattern.quote(string));
      replacementPattern = Matcher.quoteReplacement(replacement);
    } else if (pattern != null) {
      toMatch = Pattern.compile(pattern);
      replacementPattern = replacement;
    } else {
      throw new IllegalArgumentException(
          "Neither string or pattern is defined");
    }

    Set<String> keys = new HashSet<String>(parseList(config, "key"));
    if (keys.isEmpty()) {
      log.warning("No entries listed to replace");
    }
    return new ReplaceTransform(keys, toMatch, replacementPattern, overwrite);
  }

  private static class ReplaceTransform implements DocumentTransform {
    private final List<String> keys;
    private final Pattern toMatch;
    private final String replacement;
    private final boolean overwrite;

    public ReplaceTransform(Collection<String> keys, Pattern toMatch,
        String replacement, boolean overwrite) {
      this.keys = Collections.unmodifiableList(new ArrayList<String>(keys));
      this.toMatch = toMatch;
      this.replacement = replacement;
      this.overwrite = overwrite;
    }

    @Override
    public void transform(Metadata metadata, Map<String, String> params) {
      for (String key : keys) {
        Set<String> original = metadata.getAllValues(key);
        if (original.isEmpty()) {
          log.log(Level.FINE, "No values for {0}. Skipping", key);
          continue;
        }
        log.log(Level.FINE, "Replacing values that match {0} with {1}: {2}",
            new Object[] {toMatch, replacement, original});
        Set<String> values = new HashSet<String>(original);
        for (String value : original) {
          String newValue = toMatch.matcher(value).replaceAll(replacement);
          if (overwrite) {
            values.remove(value);
          }
          values.add(newValue);
        }
        log.log(Level.FINE, "After replacing: {0}", values);
        metadata.set(key, values);
      }
    }

    @Override
    public String toString() {
      return "ReplaceTransform(keys=" + keys + ",toMatch=" + toMatch
          + ",replacement=" + replacement + ",overwrite=" + overwrite + ")";
    }
  }
}
