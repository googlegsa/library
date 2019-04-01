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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.internal.$Gson$Types;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

/**
 * A type adapter for a given type that uses a proxy class for serialization.
 * The proxy is a class that will be serialized in place of the type.  An
 * instance of the type is serialized by converting it to an instance of the
 * proxy class by means of the proxy's one-argument constructor and then
 * serializing the proxy.  An instance of the type is deserialized by first
 * deserializing a proxy instance, then using the proxy's
 * {@link TypeProxy#build} to convert it to an instance of the type.
 *
 * @param <T> The type that the proxy will represent.
 * @param <P> The type of the corresponding proxy class.
 */
public final class ProxyTypeAdapter<T, P extends TypeProxy<T>>
    implements JsonSerializer<T>, JsonDeserializer<T> {

  private final Class<P> proxyClass;
  private final Constructor<P> constructor;

  private ProxyTypeAdapter(Class<P> proxyClass, Constructor<P> constructor) {
    this.proxyClass = proxyClass;
    this.constructor = constructor;
  }

  /**
   * Gets a type adapter for a given type that uses a proxy class for
   * serialization.
   *
   * @param <S> A type to get an adapter for.
   * @param <O> A proxy type to use.
   * @param clazz The class corresponding to {@code <S>}.
   * @param proxyClass A proxy class for {@code <S>}, corresponding to {@code <O>}.
   * @return A proxying type adapter for {@code <S>}.
   */
  public static <S, O extends TypeProxy<S>> Object make(Class<S> clazz,
      Class<O> proxyClass) {
    return new ProxyTypeAdapter<S, O>(proxyClass, getConstructor(proxyClass, clazz));
  }

  /**
   * Gets a type adapter for a given type that uses a proxy class for
   * serialization.
   *
   * @param <S> A type to get an adapter for.
   * @param <O> A proxy type to use.
   * @param type The type corresponding to {@code <S>}.
   * @param proxyClass A proxy class for {@code <S>}, corresponding to {@code <O>}.
   * @return A proxying type adapter for {@code <S>}.
   */
  static <S, O extends TypeProxy<S>> Object make(Type type, Class<O> proxyClass) {
    return new ProxyTypeAdapter<S, O>(proxyClass,
        getConstructor(proxyClass, $Gson$Types.getRawType(type)));
  }

  @Override
  public JsonElement serialize(T instance, Type typeOfT, JsonSerializationContext context) {
    return context.serialize(invokeConstructor(constructor, instance), proxyClass);
  }

  @Override
  public T deserialize(JsonElement elt, Type typeOfT, JsonDeserializationContext context) {
    return context.<TypeProxy<T>>deserialize(elt, proxyClass).build();
  }

  private static <S> Constructor<S> getConstructor(Class<S> clazz, Class<?>... argClasses) {
    Constructor<S> constructor;
    try {
      constructor = clazz.getDeclaredConstructor(argClasses);
    } catch (SecurityException e) {
      throw new IllegalArgumentException("Exception while getting constructor: ", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Exception while getting constructor: ", e);
    }
    constructor.setAccessible(true);
    return constructor;
  }

  private static <S> S invokeConstructor(Constructor<S> constructor, Object... args) {
    try {
      return constructor.newInstance(args);
    } catch (InstantiationException e) {
      throw new IllegalArgumentException("Exception while instantiating class: ", e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException("Exception while instantiating class: ", e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Exception while instantiating class: ", e);
    }
  }
}
