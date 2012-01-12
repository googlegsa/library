// Copyright 2010 Google Inc.
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

package com.google.enterprise.secmgr.config;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.secmgr.json.TypeAdapters;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.Iterator;

import javax.annotation.concurrent.Immutable;

/**
 * Configuration parameter names, along with their types and default values.
 */
@Immutable
public enum ParamName {
  ACL_GROUPS_FILENAME(String.class, "../../../../conf/acls/acl_groups.enterprise"),
  ACL_URLS_FILENAME(String.class, "../../../../conf/acls/acl_urls.enterprise"),
  CERTIFICATE_AUTHORITIES_FILENAME(String.class, "../../../../conf/certs/cacerts.jks"),
  // whether to check the server certificate during serving time
  CHECK_SERVER_CERTIFICATE(Boolean.class, Boolean.valueOf(true)),
  CONNECTOR_MANAGER_INFO(ConnMgrInfo.class,
      ConnMgrInfo.make(ImmutableSet.<ConnMgrInfo.Entry>of())),
  DENY_RULES_FILENAME(String.class, "../../../../conf/deny_rules.enterprise"),
  GLOBAL_BATCH_REQUEST_TIMEOUT(Float.class, Float.valueOf(2.5f)),
  GLOBAL_SINGLE_REQUEST_TIMEOUT(Float.class, Float.valueOf(5.0f)),
  LATE_BINDING_ACL(Boolean.class, Boolean.valueOf(false)),
  SAML_METADATA_FILENAME(String.class, "../../../../conf/saml-metadata.xml"),
  SERVER_CERTIFICATE_FILENAME(String.class, "../../../../conf/certs/server.jks"),
  SIGNING_CERTIFICATE_FILENAME(String.class, "/etc/google/certs/server.crt"),
  SIGNING_KEY_FILENAME(String.class, "/etc/google/certs/server.key"),
  SLOW_HOST_EMBARGO_PERIOD(Integer.class, Integer.valueOf(600)),
  SLOW_HOST_NUMBER_OF_TIMEOUTS(Integer.class, Integer.valueOf(100)),
  SLOW_HOST_SAMPLE_PERIOD(Integer.class, Integer.valueOf(300)),
  SLOW_HOST_TRACKER_ENABLED(Boolean.class, Boolean.valueOf(false)),
  SLOW_HOST_TRACKER_SIZE(Integer.class, Integer.valueOf(100)),
  STUNNEL_PORT(Integer.class, Integer.valueOf(7843)),

  // deprecated params, for backward compatability when reading config
  AUTHZ_CONFIG_FILENAME(String.class, "../../../../conf/FlexAuthz.xml"),
  CONNECTOR_MANAGER_URLS(StringSet.class, new StringSet(ImmutableSet.<String>of()));

  private final Class<?> valueClass;
  private final Object defaultValue;

  private ParamName(Class<?> valueClass, Object defaultValue) {
    Preconditions.checkNotNull(valueClass);
    Preconditions.checkArgument(valueClass.isInstance(defaultValue));
    this.valueClass = valueClass;
    this.defaultValue = defaultValue;
  }

  /**
   * @return The value class for this parameter.
   */
  public Class<?> getValueClass() {
    return valueClass;
  }

  /**
   * @return The default value for this parameter.
   */
  public Object getDefaultValue() {
    return defaultValue;
  }

  /**
   * Is a given object a valid value for this key?
   *
   * @param value The object to check.
   * @return True only if it would be a valid value.
   */
  public boolean isValidValue(Object value) {
    return valueClass.isInstance(value);
  }

  /**
   * Given a string representing a value for this key, converts it to an
   * object.
   *
   * @param value The string to convert.
   * @param valueClass The class of object to convert it to.
   * @return The converted value.
   * @throws IllegalArgumentException if the string can't be converted, or if
   *     the value class is inappropriate for this parameter.
   */
  public <T> T stringToValue(String value, Class<T> valueClass) {
    Preconditions.checkNotNull(value);
    Preconditions.checkArgument(valueClass.isAssignableFrom(this.valueClass));
    if (this.valueClass == String.class) {
      return valueClass.cast(value);
    }
    try {
      if (this.valueClass == Integer.class) {
        return valueClass.cast(Integer.valueOf(value));
      }
      if (this.valueClass == Float.class) {
        return valueClass.cast(Float.valueOf(value));
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(e);
    }
    if (this.valueClass == ConnMgrInfo.class) {
      return valueClass.cast(ConnMgrInfo.valueOf(value));
    }
    if (this.valueClass == StringSet.class) {
      return valueClass.cast(StringSet.valueOf(value));
    }
    throw new IllegalStateException("Unknown value class: " + valueClass.getName());
  }

  private static final class StringSet implements Iterable<String> {

    final ImmutableSet<String> contents;

    StringSet(ImmutableSet<String> contents) {
      this.contents = ImmutableSet.copyOf(contents);
    }

    @Override
    public Iterator<String> iterator() {
      return contents.iterator();
    }

    /**
     * Decodes a string-encoded string set.
     *
     * @param string The encoded string set.
     * @return The decoded string set.
     */
    public static StringSet valueOf(String string) {
      return ConfigSingleton.getGson().fromJson(string, StringSet.class);
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) { return true; }
      if (!(object instanceof StringSet)) { return false; }
      StringSet other = (StringSet) object;
      return Objects.equal(contents, other.contents);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(contents);
    }

    @Override
    public String toString() {
      return ConfigSingleton.getGson().toJson(this);
    }
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(new TypeToken<ImmutableSet<String>>() {}.getType(),
        TypeAdapters.immutableSet());
  }
}
