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
import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.EnumSet;

import javax.annotation.concurrent.Immutable;

/**
 * An generalized configuration-parameters implementation.  This can store any
 * named parameter, of any type.
 *
 * @see ParamName
 */
@Immutable
public final class ConfigParams {

  private final ImmutableMap<ParamName, Object> map;

  private ConfigParams(ImmutableMap<ParamName, Object> map) {
    this.map = map;
  }

  /**
   * Gets the set of keys.
   *
   * @return The keys.
   */
  public static EnumSet<ParamName> keySet() {
    return EnumSet.allOf(ParamName.class);
  }

  /**
   * Gets the value of a parameter.
   *
   * @param key The name of the parameter to retrieve.
   * @return The value of the parameter.
   */
  public Object get(ParamName key) {
    return map.get(key);
  }

  /**
   * Gets the value of a parameter, casting it to a given type.
   *
   * @param key The name of the parameter to retrieve.
   * @param valueClass The class to cast the value to.
   * @return The value of the parameter.
   * @throws IllegalArgumentException if <code>valueClass</code> isn't
   *     assignable from the parameter's value.
   */
  public <T> T get(ParamName key, Class<T> valueClass) {
    Preconditions.checkArgument(valueClass.isAssignableFrom(key.getValueClass()));
    return valueClass.cast(get(key));
  }

  /**
   * Gets a default set of configuration parameters.
   *
   * @return The default set.
   */
  public static ConfigParams makeDefault() {
    return builder().build();
  }

  /**
   * Gets a new configuration-parameters builder.
   *
   * @return A new builder.
   */
  public static Builder builder() {
    return new Builder(null);
  }

  /**
   * Gets a new configuration-parameters builder initialized with defaults.
   *
   * @param defaultValues A set of configuration parameters to use as defaults.
   * @return A new builder.
   */
  public static Builder builder(ConfigParams defaultValues) {
    Preconditions.checkNotNull(defaultValues);
    return new Builder(defaultValues);
  }

  /**
   * A builder factory for configuration parameters.
   */
  public static final class Builder {
    private final ConfigParams defaultValues;
    private final ImmutableMap.Builder<ParamName, Object> mapBuilder;
    private final EnumSet<ParamName> unseen;

    private Builder(ConfigParams defaultValues) {
      this.defaultValues = defaultValues;
      mapBuilder = ImmutableMap.builder();
      unseen = EnumSet.allOf(ParamName.class);
    }

    /**
     * Set the value of a parameter.
     *
     * @param key The parameter's name.
     * @param value The parameter's value.
     * @return This builder, for programming convenience.
     * @throws IllegalArgumentException if the value isn't valid for this parameter.
     */
    public Builder put(ParamName key, Object value) {
      Preconditions.checkArgument(key.isValidValue(value));
      mapBuilder.put(key, value);
      unseen.remove(key);
      return this;
    }

    /**
     * @return The completed parameters object.
     */
    public ConfigParams build() {
      for (ParamName key : unseen) {
        mapBuilder.put(key,
            (defaultValues == null)
            ? key.getDefaultValue()
            : defaultValues.get(key));
      }
      return new ConfigParams(mapBuilder.build());
    }
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof ConfigParams)) { return false; }
    ConfigParams other = (ConfigParams) object;
    for (ParamName key : keySet()) {
      if (!get(key).equals(other.get(key))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    EnumSet<ParamName> keys = keySet();
    Object[] values = new Object[keys.size()];
    int i = 0;
    for (ParamName key : keys) {
      values[i++] = get(key);
    }
    return Objects.hashCode(values);
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(ConfigParams.class, new LocalTypeAdapter());
  }

  static final class LocalTypeAdapter
      implements JsonSerializer<ConfigParams>, JsonDeserializer<ConfigParams> {
    LocalTypeAdapter() {
    }

    @Override
    public JsonElement serialize(ConfigParams params, Type type, JsonSerializationContext context) {
      JsonObject jo = new JsonObject();
      for (ParamName key : ConfigParams.keySet()) {
        jo.add(key.toString(), context.serialize(params.get(key), key.getValueClass()));
      }
      return jo;
    }

    @Override
    public ConfigParams deserialize(JsonElement src, Type type,
        JsonDeserializationContext context) {
      JsonObject jo = src.getAsJsonObject();
      ConfigParams.Builder builder = ConfigParams.builder();
      for (ParamName key : ConfigParams.keySet()) {
        JsonElement je = jo.get(key.toString());
        if (je != null) {
          builder.put(key, context.deserialize(je, key.getValueClass()));
        }
      }
      return builder.build();
    }
  }
}
