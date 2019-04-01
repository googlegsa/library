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

package com.google.enterprise.adaptor;

/** A forwarding session that namespaces its keys. */
class NamespacedSession implements Session {
  private final Session session;
  private final String prefix;

  public NamespacedSession(Session session, String prefix) {
    if (session == null || prefix == null) {
      throw new NullPointerException();
    }
    this.session = session;
    this.prefix = prefix;
  }

  @Override
  public void setAttribute(String key, Object value) {
    key = mapKey(key);
    session.setAttribute(key, value);
  }

  @Override
  public Object getAttribute(String key) {
    key = mapKey(key);
    return session.getAttribute(key);
  }

  @Override
  public Object removeAttribute(String key) {
    key = mapKey(key);
    return session.removeAttribute(key);
  }

  private String mapKey(String key) {
    return prefix + key;
  }
}
