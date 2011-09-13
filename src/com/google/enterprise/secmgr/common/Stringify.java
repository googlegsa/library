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

package com.google.enterprise.secmgr.common;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import java.util.Arrays;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Static methods for converting objects to strings.  This is useful both for
 * logging and for assertion failure messages in tests.
 *
 * @author Chris Hanson
 */
@ParametersAreNonnullByDefault
public final class Stringify {

  /**
   * Generates a display string for a given object.  Reformats strings in Java
   * syntax, to eliminate ambiguities.  This is useful when generating
   * expectation strings.
   *
   * @param object The object to generate a string for.
   * @return A string representation of the object.
   */
  @CheckReturnValue
  @Nonnull
  public static String object(@Nullable Object object) {
    if (object == null) {
      return "null";
    }
    if (object instanceof String) {
      String string = (String) object;
      StringBuilder builder = new StringBuilder();
      builder.append('"');
      for (int i = 0; i < string.length(); i += 1) {
        char c = string.charAt(i);
        writeChar(c, '"', builder);
      }
      builder.append('"');
      return builder.toString();
    } else if (object instanceof Character) {
      char c = (Character) object;
      StringBuilder builder = new StringBuilder();
      builder.append("'");
      writeChar(c, '\'', builder);
      builder.append("'");
      return builder.toString();
    }
    return object.toString();
  }

  private static void writeChar(char c, char quote, StringBuilder builder) {
    switch (c) {
      case '\b':
        builder.append("\\b");
        break;
      case '\t':
        builder.append("\\t");
        break;
      case '\n':
        builder.append("\\n");
        break;
      case '\f':
        builder.append("\\f");
        break;
      case '\r':
        builder.append("\\r");
        break;
      case '\\':
        builder.append("\\\\");
        break;
      default:
        if (c == quote) {
          builder.append('\\');
          builder.append(c);
        } else if (c < ' ') {
          builder.append(String.format("\\u%04x", (int) c));
        } else {
          builder.append(c);
        }
        break;
    }
  }

  /**
   * A function that maps an object to a string.
   * @see #object
   */
  @Nonnull
  public static final Function<Object, String> OBJECT_FUNCTION =
      new Function<Object, String>() {
        @Override
        public String apply(Object o) {
          return object(o);
        }
      };

  /**
   * Generates a display string for a method invocation.
   *
   * @param methodName The name of the method being invoked.
   * @param operands The objects being passed as arguments to the method.
   * @return A string representation of the invocation.
   */
  @CheckReturnValue
  @Nonnull
  public static String invocation(String methodName, Iterable<?> operands) {
    Preconditions.checkNotNull(methodName);
    return methodName + "(" + objects(operands) + ")";
  }

  /**
   * Generates a display string for a method invocation.
   *
   * @param methodName The name of the method being invoked.
   * @param operands The objects being passed as arguments to the method.
   * @return A string representation of the invocation.
   */
  @CheckReturnValue
  @Nonnull
  public static String invocation(String methodName, Object... operands) {
    return invocation(methodName, Arrays.asList(operands));
  }

  /**
   * Generates a display string for a sequence of objects.
   *
   * @param elements A sequence of objects.
   * @return A string representation of the given sequence.
   */
  @CheckReturnValue
  @Nonnull
  public static String objects(Iterable<?> elements) {
    return Joiner.on(", ").join(Iterables.transform(elements, OBJECT_FUNCTION));
  }

  /**
   * Generates a display string for a sequence of objects.
   *
   * @param elements A sequence of objects.
   * @return A string representation of the given sequence.
   */
  @CheckReturnValue
  @Nonnull
  public static String objects(Object... elements) {
    return objects(Arrays.asList(elements));
  }
}
