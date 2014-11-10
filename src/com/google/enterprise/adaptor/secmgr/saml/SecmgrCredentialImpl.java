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

import org.opensaml.common.impl.AbstractSAMLObject;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.util.XMLObjectChildrenList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/** An implementation for {@link SecmgrCredential}. */
@ParametersAreNonnullByDefault
final class SecmgrCredentialImpl extends AbstractSAMLObject implements SecmgrCredential {
  private String name;
  private String namespace;
  private String domain;
  private String password;
  private final XMLObjectChildrenList<Group> groups;

  SecmgrCredentialImpl(String nsUri, String localName, String nsPrefix) {
    super(nsUri, localName, nsPrefix);
    groups = new XMLObjectChildrenList<Group>(this);
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
  public String getPassword() {
    return password;
  }

  @Override
  public List<Group> getGroups() {
    return groups;
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
  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public void setGroups(List<Group> groups) {
    this.groups.clear();
    for (Group g : groups) {
      g.detach();
      this.groups.add(g);
    }
  }

  @Override
  public List<XMLObject> getOrderedChildren() {
    ArrayList<XMLObject> children = new ArrayList<XMLObject>();
    children.addAll(groups);
    return Collections.unmodifiableList(children);
  }
}
