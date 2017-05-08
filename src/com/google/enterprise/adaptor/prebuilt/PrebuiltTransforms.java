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

import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.MetadataTransform;

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
import java.util.TreeSet;
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
  }

  // Prevent instantiation.
  private PrebuiltTransforms() {}

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

  /**
   * Returns a transform that copies metadata or param values from one key to
   * another. The {@code "overwrite"} key can be set to {@code "true"} to cause
   * the destination to be replaced. Otherwise if the destination is a metadata
   * key, its values is are supplemented. If the destination is a param key,
   * {@code "overwrite"} is ignored and its value is always replaced.
   *
   * <p>Copies are defined by pairs of {@code "X.from"} and {@code "X.to"}
   * configuration entries (where {@code X} is an integer). The value for each
   * is a metadata or param  key. Copies are applied in the increasing order of
   * the integers. The config keys {@code "X.from.keyset"} and
   * {@code "X.to.keyset"} may be set to {@code metadata} or to {@code params}
   * to restrict the source and destination to only {@code Metadata} or
   * {@code params}, respectively.  Most keys/values of interest will normally
   * be specified in the document's {@code Metadata}, but some key/values of
   * interest (e.g. Content-Type, DocId) exist in the document's {@code params}.
   *
   * <p>Example configuration:
   * <pre><code>
     overwrite=false
     1.from=colour
     1.to=color
     2.from=author
     2.to=contributors
     </code></pre>
   *
   * @param config transform configuration
   * @return transform
   */
  public static MetadataTransform copy(Map<String, String> config) {
    boolean overwrite
        = Boolean.parseBoolean(getTrimmedValue(config, "overwrite"));
    List<KeyPairing> copies = parseCopies(config);
    if (copies.isEmpty()) {
      log.warning("No entries listed to be copied");
    }
    return new CopyTransform(copies, overwrite, false);
  }

  @Deprecated
  public static MetadataTransform copyMetadata(Map<String, String> config) {
    return copy(config);
  }

  /**
   * Returns a transform that moves metadata or param values from one key to
   * another. This method returns a transform that behaves identically to
   * {@link #copy}, except that the source keys are removed. If the source key
   * has no values then the destination is left as-is.
   *
   * @param config transform configuration
   * @return transform
   */
  public static MetadataTransform move(Map<String, String> config) {
    boolean overwrite
        = Boolean.parseBoolean(getTrimmedValue(config, "overwrite"));
    List<KeyPairing> copies = parseCopies(config);
    if (copies.isEmpty()) {
      log.warning("No entries listed to be moved");
    }
    return new CopyTransform(copies, overwrite, true);
  }

  @Deprecated
  public static MetadataTransform moveMetadata(Map<String, String> config) {
    return move(config);
  }

  /**
   * Pairs of {@code Keys}, with a src-key-name and destination-key-name
   * and their cooresponding Keyset.
   * The sequence is in order the copies/moves should happen.
   */
  private static List<KeyPairing> parseCopies(Map<String, String> config) {
    Map<Integer, Map<String, String>> allSubs = parseOrderedMaps(config);
    List<KeyPairing> copies = new ArrayList<KeyPairing>(allSubs.size());
    for (Map.Entry<Integer, Map<String, String>> instruction
        : allSubs.entrySet()) {
      String from = getTrimmedValue(instruction.getValue(), "from");
      String to = getTrimmedValue(instruction.getValue(), "to");
      if (from == null || to == null) {
        log.log(Level.FINE, "Ignoring int {0}. Missing .from or .to",
            instruction.getKey());
        continue;
      }
      Key fromKey = new Key(from,
          getTrimmedValue(instruction.getValue(), "from.keyset"));
      Key toKey = new Key(to,
          getTrimmedValue(instruction.getValue(), "to.keyset"));
      KeyPairing keyPairing = new KeyPairing(fromKey, toKey);
      if (fromKey.equals(toKey)) {
        log.log(Level.WARNING, "removing no-op: {0}", keyPairing);
        continue;
      }
      copies.add(keyPairing);
      log.log(Level.FINE, "Found config to copy {0} to {1}",
          new Object[] {fromKey, toKey});
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

  private static class CopyTransform implements MetadataTransform {
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
        Set<String> values = new TreeSet<String>();
        switch (kp.src.keyset) {
          case METADATA:
            values.addAll(metadata.getAllValues(kp.src.key));
            break;
          case PARAMS:
            if (params.get(kp.src.key) != null) {
              values.add(params.get(kp.src.key));
            }
            break;
        }
        if (values.isEmpty()) {
          log.log(Level.FINE, "No values for {0}. Skipping", kp.src);
          continue;
        }
        log.log(Level.FINE, "Copying values from {0} to {1}: {2}",
            new Object[] {kp.src, kp.dest, values});
        switch (kp.dest.keyset) {
          case METADATA:
            Set<String> destValues = metadata.getAllValues(kp.dest.key);
            if (!overwrite && !destValues.isEmpty()) {
              log.log(Level.FINER, "Preexisting values for {0}. Combining: {1}",
                  new Object[] {kp.dest, destValues});
            values.addAll(destValues);
            }
            metadata.set(kp.dest.key, values);
            break;
          case PARAMS:
            String value = values.iterator().next();
            if (values.size() > 1) {
              log.log(Level.FINER,
                  "Multiple values for {0}. Using first value of {1} for {2}",
                  new Object[] { kp.src, value, kp.dest });
            }
            params.put(kp.dest.key, value);
            break;
        }
        if (move) {
          log.log(Level.FINER, "Deleting source {0}", kp.src);
          switch (kp.src.keyset) {
            case METADATA:
              metadata.set(kp.src.key, Collections.<String>emptySet());
              break;
            case PARAMS:
              params.remove(kp.src.key);
              break;
          }
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
    private final Key src;
    private final Key dest;

    KeyPairing(Key from, Key to) {
      if (null == from || null == to) {
        throw new NullPointerException();
      } 
      src = from;
      dest = to;
    }

    @Override
    public String toString() {
      return "(from=" + src + ",to=" + dest + ")";
    }
  }

  /** Describes a key in a keyset. */
  private static class Key {
    private final String key;
    private final Keyset keyset;

    Key(String key, String keyset) {
      this(key, Keyset.from(keyset));
    }

    Key(String key, Keyset keyset) {
      if (key == null || keyset == null) {
        throw new NullPointerException();
      }
      this.key = key;
      this.keyset = keyset;
    }

    @Override
    public boolean equals(Object o) {
      if (null == o || !getClass().equals(o.getClass())) {
        return false;
      }
      Key k = (Key) o;
      return this.key.equals(k.key) && this.keyset == k.keyset;
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      return "(key=" + key + ",keyset=" + keyset + ")";
    }
  }

  /**
   * Returns a transform that deletes metadata or param keys. The keys to be
   * deleted are defined by {@code "keyX"} configuration entries (where
   * {@code X} is an integer). The config keys {@code "keysetX"} may be set
   * to {@code metadata} or to {@code params} to restrict the {@code keyX}
   * to only {@code Metadata} or {@code params}, respectively.
   *
   * <p>Example configuration:
   * <pre><code>key2=sensitive
   *key4=unhelpful</code></pre>
   *
   * @param config transform configuration
   * @return transform
   */
  public static MetadataTransform delete(Map<String, String> config) {
    List<Key> keys = keyList(config);
    if (keys.isEmpty()) {
      log.warning("No entries listed to delete");
    }
    return new DeleteTransform(keys);
  }

  @Deprecated
  public static MetadataTransform deleteMetadata(Map<String, String> config) {
    return delete(config);
  }

  private static List<Key> keyList(Map<String, String> config) {
    String prefix = "key";
    List<Key> keys = new LinkedList<Key>();
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
      if (me.getValue().trim().length() == 0) {
        log.log(Level.FINE, "Ignoring {0}. No key name specified", me);
        continue;
      }
      keys.add(new Key(me.getValue().trim(),
                       getTrimmedValue(config, "keyset" + number)));
    }
    return Collections.unmodifiableList(keys);
  }

  private static class DeleteTransform implements MetadataTransform {
    private final List<Key> keys;

    public DeleteTransform(List<Key> keys) {
      this.keys = keys;
    }

    @Override
    public void transform(Metadata metadata, Map<String, String> params) {
      for (Key key : keys) {
        switch (key.keyset) {
          case METADATA:
            metadata.set(key.key, Collections.<String>emptySet());
            break;
          case PARAMS:
            params.remove(key.key);
            break;
        }
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
   *replacement=$1 (but it should be x86 assembler)</code></pre>
   *
   * @param config transform configuration
   * @return transform
   */
  public static MetadataTransform replace(Map<String, String> config) {
    boolean overwrite = true;
    String overwriteString = getTrimmedValue(config, "overwrite");
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

    List<Key> keys = keyList(config);
    if (keys.isEmpty()) {
      log.warning("No entries listed to replace");
    }
    return new ReplaceTransform(keys, toMatch, replacementPattern, overwrite);
  }

  @Deprecated
  public static MetadataTransform replaceMetadata(Map<String, String> config) {
    return replace(config);
  }
  
  private static class ReplaceTransform implements MetadataTransform {
    private final List<Key> keys;
    private final Pattern toMatch;
    private final String replacement;
    private final boolean overwrite;

    public ReplaceTransform(List<Key> keys, Pattern toMatch,
        String replacement, boolean overwrite) {
      this.keys = keys;
      this.toMatch = toMatch;
      this.replacement = replacement;
      this.overwrite = overwrite;
    }

    @Override
    public void transform(Metadata metadata, Map<String, String> params) {
      for (Key key : keys) {
        switch (key.keyset) {
          case METADATA:
            replaceInMetadata(key.key, metadata);
            break;
          case PARAMS:
            replaceInParams(key.key, params);
            break;
        }
      }
    }

    private void replaceInMetadata(String key, Metadata metadata) {
      Set<String> original = metadata.getAllValues(key);
      if (original.isEmpty()) {
        log.log(Level.FINE, "No metadata values for {0}. Skipping", key);
        return;
      }
      log.log(Level.FINE,
         "Replacing {1} with {2} in metadata values of {0}: {3}",
          new Object[] {key, toMatch, replacement, original});
      Set<String> values = new HashSet<String>(original);
      for (String value : original) {
        String newValue = toMatch.matcher(value).replaceAll(replacement);
        if (overwrite) {
          values.remove(value);
        }
        values.add(newValue);
      }
      metadata.set(key, values);
      log.log(Level.FINE, "After replacing metadata values for {0}: {1}",
          new Object [] {key, values});
    }

    private void replaceInParams(String key, Map<String, String> params) {
      String original = getTrimmedValue(params, key);
      if (original == null) {
        log.log(Level.FINE, "No param value for {0}. Skipping", key);
        return;
      }
      log.log(Level.FINE,
          "Replacing {1} with {2} in param value of {0}: {3}",
          new Object[] {key, toMatch, replacement, original});
      String newValue = toMatch.matcher(original).replaceAll(replacement);
      params.put(key, newValue);
      log.log(Level.FINE, "After replacing param value for {0}: {1}",
          new Object [] {key, newValue});
    }

    @Override
    public String toString() {
      return "ReplaceTransform(keys=" + keys + ",toMatch=" + toMatch
          + ",replacement=" + replacement + ",overwrite=" + overwrite + ")";
    }
  }
}
