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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import org.opensaml.common.impl.AbstractSAMLObject;
import org.opensaml.xml.XMLObject;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/** An implementation for {@link Group}. */
@ParametersAreNonnullByDefault
final class GroupImpl extends AbstractSAMLObject implements Group {
  private String name;
  private String namespace;
  private String domain;

  GroupImpl(String nsUri, String localName, String nsPrefix) {
    super(nsUri, localName, nsPrefix);
  }

  @Override
  public String getName() {
    Preconditions.checkState(name != null, "Name must be non-null");
    return name;
  }

  @Override
  public String getNamespace() {
    Preconditions.checkState(namespace != null, "Namespace must be non-null");
    return namespace;
  }

  @Override
  public String getDomain() {
    return domain;
  }

  @Override
  public void setName(String name) {
    Preconditions.checkState(name != null, "Name must be non-null");
    this.name = name;
  }

  @Override
  public void setNamespace(String namespace) {
    Preconditions.checkState(namespace != null, "Namespace must be non-null");
    this.namespace = namespace;
  }

  @Override
  public void setDomain(String domain) {
    this.domain = domain;
  }

  @Override
  public List<XMLObject> getOrderedChildren() {
    // This object has no child elements.
    return ImmutableList.of();
  }
}
