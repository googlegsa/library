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
import com.google.gson.internal.$Gson$Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * An extension of $Gson$Types to get generalized parameter types.  This has
 * been submitted to the Gson project; if accepted this file can be removed.
 */
final class GsonTypes {

  // Don't instantiate.
  GsonTypes() {
    throw new UnsupportedOperationException();
  }

  static Type[] getParameterTypes(Type context, Class<?> parameterizedRawType) {
    return getParameterTypes(context, $Gson$Types.getRawType(context), parameterizedRawType);
  }

  static Type[] getParameterTypes(Type context, Class<?> contextRawType,
      Class<?> parameterizedRawType) {
    Type resolvedType = getSupertype(context, contextRawType, parameterizedRawType);
    return ((ParameterizedType) resolvedType).getActualTypeArguments();
  }

  // Copied from $Gson$Types since it's not exposed there.
  private static Type getSupertype(Type context, Class<?> contextRawType, Class<?> supertype) {
    Preconditions.checkArgument(supertype.isAssignableFrom(contextRawType));
    return $Gson$Types.resolve(context, contextRawType,
        getGenericSupertype(context, contextRawType, supertype));
  }

  // Copied from $Gson$Types since it's not exposed there.
  private static Type getGenericSupertype(Type context, Class<?> rawType, Class<?> toResolve) {
    if (toResolve == rawType) {
      return context;
    }

    // we skip searching through interfaces if unknown is an interface
    if (toResolve.isInterface()) {
      Class<?>[] interfaces = rawType.getInterfaces();
      for (int i = 0, length = interfaces.length; i < length; i++) {
        if (interfaces[i] == toResolve) {
          return rawType.getGenericInterfaces()[i];
        } else if (toResolve.isAssignableFrom(interfaces[i])) {
          return getGenericSupertype(rawType.getGenericInterfaces()[i], interfaces[i], toResolve);
        }
      }
    }

    // check our supertypes
    if (!rawType.isInterface()) {
      while (rawType != Object.class) {
        Class<?> rawSupertype = rawType.getSuperclass();
        if (rawSupertype == toResolve) {
          return rawType.getGenericSuperclass();
        } else if (toResolve.isAssignableFrom(rawSupertype)) {
          return getGenericSupertype(rawType.getGenericSuperclass(), rawSupertype, toResolve);
        }
        rawType = rawSupertype;
      }
    }

    // we can't resolve this further
    return toResolve;
  }
}
