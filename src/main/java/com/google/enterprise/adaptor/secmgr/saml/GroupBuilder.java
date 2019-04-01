/*
 * Copyright 2014 Google Inc.
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
package com.google.enterprise.adaptor.secmgr.saml;

import org.opensaml.common.impl.AbstractSAMLObjectBuilder;

/** A factory for {@link Group}. */
final class GroupBuilder extends AbstractSAMLObjectBuilder<Group> {
  @Override
  public Group buildObject() {
    return buildObject(
        Group.DEFAULT_ELEMENT_NAME.getNamespaceURI(),
        Group.DEFAULT_ELEMENT_NAME.getLocalPart(),
        Group.DEFAULT_ELEMENT_NAME.getPrefix());
  }

  @Override
  public Group buildObject(String nsUri, String localName, String nsPrefix) {
    return new GroupImpl(nsUri, localName, nsPrefix);
  }
}
