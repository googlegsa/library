// Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Unit tests for {@link WrapperAdaptor}. */
public class WrapperAdaptorTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testWrapperResponse_addParam_Response() {
    WrapperAdaptor.WrapperResponse wrapper =
        new WrapperAdaptor.WrapperResponse(newProxyInstance(Response.class));
    wrapper.setLock(false);
    thrown.expect(UnsupportedOperationException.class);
    wrapper.addParam("foo", "bar");
  }

  @Test
  public void testWrapperResponse_addParam_Response2() {
    WrapperAdaptor.WrapperResponse wrapper =
        new WrapperAdaptor.WrapperResponse(newProxyInstance(Response2.class));
    wrapper.setLock(false);
    wrapper.addParam("foo", "bar");
  }

  /** Gets a proxy for the given class that does nothing. */
  private <T> T newProxyInstance(Class<T> clazz) {
    return clazz.cast(
        Proxy.newProxyInstance(clazz.getClassLoader(),
            new Class<?>[] { clazz },
            new InvocationHandler() {
              public Object invoke(Object proxy, Method method, Object[] args) {
                // This does not work with primitive return types.
                return null;
              }
            }));
  }
}
