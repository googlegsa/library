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

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

/**
 * Configuration parameter names, along with their types and default values.
 */
@Immutable
public enum ParamName {
  ACL_GROUPS_FILENAME(String.class, "../../../../conf/acls/acl_groups.enterprise"),
  ACL_URLS_FILENAME(String.class, "../../../../conf/acls/acl_urls.enterprise"),
  CERTIFICATE_AUTHORITIES_FILENAME(String.class, "../../../../conf/certs/cacerts.jks"),
  CONNECTOR_MANAGER_URLS(StringSet.class, StringSet.make()),
  DENY_RULES_FILENAME(String.class, "../../../../conf/deny_rules.enterprise"),
  GLOBAL_BATCH_REQUEST_TIMEOUT(Float.class, Float.valueOf(2.5f)),
  GLOBAL_SINGLE_REQUEST_TIMEOUT(Float.class, Float.valueOf(5.0f)),
  SAML_METADATA_FILENAME(String.class, "../../../../conf/saml-metadata.xml"),
  SERVER_CERTIFICATE_FILENAME(String.class, "../../../../conf/certs/server.jks"),
  // whether to check the server certificate during serving time
  CHECK_SERVER_CERTIFICATE(Boolean.class, Boolean.valueOf(true)),
  SIGNING_CERTIFICATE_FILENAME(String.class, "/etc/google/certs/server.crt"),
  SIGNING_KEY_FILENAME(String.class, "/etc/google/certs/server.key"),
  STUNNEL_PORT(Integer.class, Integer.valueOf(7843)),

  // deprecated params, for backward compatability when reading config
  AUTHZ_CONFIG_FILENAME(String.class, "../../../../conf/FlexAuthz.xml");

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
    if (this.valueClass == StringSet.class) {
      return valueClass.cast(StringSet.valueOf(value));
    }
    throw new IllegalStateException("Unknown value class: " + valueClass.getName());
  }
}
