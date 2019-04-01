/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.enterprise.adaptor.secmgr.json;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Static methods for some useful type adapters.
 */
public final class TypeAdapters {

  private static final String JSON_KEY_CLASS = "typeName";

  // Don't instantiate.
  private TypeAdapters() {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets a type adapter for an iterable type.  The returned adapter does lazy
   * deserialization.
   *
   * @return A type adapter for {@code Iterable<T>}.
   */
  public static Object iterable() {
    return ITERABLE;
  }

  private static final Object ITERABLE = new IterableTypeAdapter<Object>();

  private static final class IterableTypeAdapter<T>
      extends AbstractIterableTypeAdapter<T, Iterable<T>> {

    IterableTypeAdapter() {
      super(Iterable.class);
    }

    @Override
    public Iterable<T> deserialize(JsonElement element, Type type,
        final JsonDeserializationContext context) {
      if (element.isJsonNull()) {
        return null;
      }
      final JsonArray ja = element.getAsJsonArray();
      final Type elementType = GsonTypes.getParameterTypes(type, rawType)[0];
      return new Iterable<T>() {
        @Override
        public Iterator<T> iterator() {
          final Iterator<JsonElement> jsonIterator = ja.iterator();
          return new Iterator<T>() {

            @Override
            public boolean hasNext() {
              return jsonIterator.hasNext();
            }

            @Override
            public T next() {
              JsonElement je = jsonIterator.next();
              return (je == null || je.isJsonNull())
                  ? null
                  : context.<T>deserialize(je, elementType);
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException();
            }
          };
        }
      };
    }
  }

  private abstract static class AbstractIterableTypeAdapter<T, I extends Iterable<T>>
      implements JsonSerializer<I>, JsonDeserializer<I> {

    protected final Class<?> rawType;

    protected AbstractIterableTypeAdapter(Class<?> rawType) {
      Preconditions.checkNotNull(rawType);
      this.rawType = rawType;
    }

    @Override
    public JsonElement serialize(I iterable, Type typeOfI, JsonSerializationContext context) {
      if (iterable == null) {
        return JsonNull.INSTANCE;
      }
      JsonArray ja = new JsonArray();
      Type typeOfElement = GsonTypes.getParameterTypes(typeOfI, rawType)[0];
      for (T element : iterable) {
        ja.add((element == null)
            ? JsonNull.INSTANCE
            : context.serialize(element,
                (typeOfElement == Object.class)
                ? element.getClass()
                : typeOfElement));
      }
      return ja;
    }
  }

  /**
   * Gets a type adapter for an immutable list.
   *
   * @return A type adapter for {@code ImmutableList<Object>}.
   */
  public static Object immutableList() {
    return IMMUTABLE_LIST;
  }

  /**
   * Gets a type adapter for an immutable set.
   *
   * @return A type adapter for {@code ImmutableSet<Object>}.
   */
  public static Object immutableSet() {
    return IMMUTABLE_SET;
  }

  /**
   * Gets a type adapter for an immutable multiset.
   *
   * @return A type adapter for {@code ImmutableMultiset<Object>}.
   */
  public static Object immutableMultiset() {
    return IMMUTABLE_MULTISET;
  }

  private static final Object IMMUTABLE_LIST = new ImmutableListTypeAdapter<Object>();
  private static final Object IMMUTABLE_SET = new ImmutableSetTypeAdapter<Object>();
  private static final Object IMMUTABLE_MULTISET = new ImmutableMultisetTypeAdapter<Object>();

  private static final class ImmutableListTypeAdapter<T>
      extends AbstractCollectionTypeAdapter<T, ImmutableList<T>> {

    ImmutableListTypeAdapter() {
      super(ImmutableList.class);
    }

    @Override
    protected ImmutableCollection.Builder<T> getBuilder() {
      return ImmutableList.builder();
    }
  }

  private static final class ImmutableSetTypeAdapter<T>
      extends AbstractCollectionTypeAdapter<T, ImmutableSet<T>> {

    ImmutableSetTypeAdapter() {
      super(ImmutableSet.class);
    }

    @Override
    protected ImmutableCollection.Builder<T> getBuilder() {
      return ImmutableSet.builder();
    }
  }

  private static final class ImmutableMultisetTypeAdapter<T>
      extends AbstractCollectionTypeAdapter<T, ImmutableMultiset<T>> {

    ImmutableMultisetTypeAdapter() {
      super(ImmutableMultiset.class);
    }

    @Override
    protected ImmutableCollection.Builder<T> getBuilder() {
      return ImmutableMultiset.builder();
    }
  }

  private abstract static class AbstractCollectionTypeAdapter<E, C extends Collection<E>>
      extends AbstractIterableTypeAdapter<E, C> {

    protected AbstractCollectionTypeAdapter(Class<?> rawType) {
      super(rawType);
    }

    protected abstract ImmutableCollection.Builder<E> getBuilder();

    @Override
    public C deserialize(JsonElement src, Type typeOfSrc, JsonDeserializationContext context) {
      if (src.isJsonNull()) {
        return null;
      }
      JsonArray ja = src.getAsJsonArray();
      Type typeOfElement = GsonTypes.getParameterTypes(typeOfSrc, rawType)[0];
      ImmutableCollection.Builder<E> builder = getBuilder();
      for (JsonElement je : ja) {
        builder.add((je == null || je.isJsonNull())
            ? null
            : context.<E>deserialize(je, typeOfElement));
      }
      @SuppressWarnings("unchecked")
      C result = (C) builder.build();
      return result;
    }
  }

  /**
   * Gets a type adapter for an immutable map.
   *
   * @return A type adapter for {@code ImmutableMap<?, ?>}.
   */
  public static Object immutableMap() {
    return IMMUTABLE_MAP;
  }

  private static final Object IMMUTABLE_MAP = new ImmutableMapTypeAdapter<Object, Object>();

  private static final class ImmutableMapTypeAdapter<K, V>
      implements JsonSerializer<ImmutableMap<K, V>>, JsonDeserializer<ImmutableMap<K, V>> {

    @Override
    public JsonElement serialize(ImmutableMap<K, V> map, Type typeOfMap,
        JsonSerializationContext context) {
      Type[] pts = GsonTypes.getParameterTypes(typeOfMap, ImmutableMap.class);
      Type typeOfKey = pts[0];
      Type typeOfValue = pts[1];
      JsonArray ja = new JsonArray();
      for (Map.Entry<K, V> entry : map.entrySet()) {
        JsonArray je = new JsonArray();
        je.add(context.serialize(entry.getKey(), typeOfKey));
        je.add(context.serialize(entry.getValue(), typeOfValue));
        ja.add(je);
      }
      return ja;
    }

    @Override
    public ImmutableMap<K, V> deserialize(JsonElement src, Type typeOfSrc,
        JsonDeserializationContext context) {
      Type[] pts = GsonTypes.getParameterTypes(typeOfSrc, ImmutableMap.class);
      Type typeOfKey = pts[0];
      Type typeOfValue = pts[1];
      JsonArray ja = src.getAsJsonArray();
      ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();
      for (int i = 0; i < ja.size(); i++) {
        JsonArray je = ja.get(i).getAsJsonArray();
        K key = context.<K>deserialize(je.get(0), typeOfKey);
        V value = context.<V>deserialize(je.get(1), typeOfValue);
        builder.put(key, value);
      }
      return builder.build();
    }
  }

  /**
   * Gets a type adapter for a given class that dispatches on a given set of
   * subclasses.
   *
   * @param <T> A given class type.
   * @param classes Some subclasses of {@code <T>}.
   * @return A dispatching type adapter for {@code <T>}.
   */
  public static <T> Object dispatch(Iterable<Class<? extends T>> classes) {
    return new DispatchTypeAdapter<T>(classes);
  }

  private static final class DispatchTypeAdapter<T>
      implements JsonSerializer<T>, JsonDeserializer<T> {

    final ImmutableList<Class<? extends T>> classes;

    DispatchTypeAdapter(Iterable<Class<? extends T>> classes) {
      this.classes = ImmutableList.copyOf(classes);
    }

    @Override
    public JsonElement serialize(T instance, Type typeOfT, JsonSerializationContext context) {
      for (Class<? extends T> subClass : classes) {
        if (subClass.isInstance(instance)) {
          JsonObject jo = context.serialize(instance, subClass).getAsJsonObject();
          jo.addProperty(JSON_KEY_CLASS, subClass.getSimpleName());
          return jo;
        }
      }
      throw new JsonParseException("Unknown subclass: " + instance);
    }

    @Override
    public T deserialize(JsonElement je, Type typeOfT, JsonDeserializationContext context) {
      JsonObject jo = je.getAsJsonObject();
      if (!jo.has(JSON_KEY_CLASS)) {
        throw new JsonParseException("Missing " + JSON_KEY_CLASS + " binding: " + jo);
      }
      String typeName = jo.getAsJsonPrimitive(JSON_KEY_CLASS).getAsString();
      for (Class<? extends T> subClass : classes) {
        if (subClass.getSimpleName().equals(typeName)) {
          return context.<T>deserialize(jo, subClass);
        }
      }
      throw new JsonParseException("Unknown subclass: " + typeName);
    }
  }
}
