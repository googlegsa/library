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

import org.opensaml.common.SAMLObject;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

/**
 * A SAML extension added to AuthzDecisionQuery messages by the secmgr.
 */
@ParametersAreNonnullByDefault
public interface SecmgrCredential extends SAMLObject {
  public static final QName DEFAULT_ELEMENT_NAME
      = new QName(OpenSamlUtil.GOOGLE_NS_URI, "SecmgrCredential", OpenSamlUtil.GOOGLE_NS_PREFIX);
  public static final QName NAME_ATTRIB_NAME
      = new QName(XMLConstants.NULL_NS_URI, "name", XMLConstants.DEFAULT_NS_PREFIX);
  public static final QName NAMESPACE_ATTRIB_NAME
      = new QName(XMLConstants.NULL_NS_URI, "namespace", XMLConstants.DEFAULT_NS_PREFIX);
  public static final QName DOMAIN_ATTRIB_NAME
      = new QName(XMLConstants.NULL_NS_URI, "domain", XMLConstants.DEFAULT_NS_PREFIX);
  public static final QName PASSWORD_ATTRIB_NAME
      = new QName(XMLConstants.NULL_NS_URI, "password", XMLConstants.DEFAULT_NS_PREFIX);

  @Nonnull
  public String getName();

  @Nonnull
  public String getNamespace();

  @Nonnull
  public String getPassword();

  @Nullable
  public String getDomain();

  public List<Group> getGroups();

  public void setName(String name);

  public void setNamespace(String namespace);

  public void setDomain(@Nullable String domain);

  public void setPassword(@Nullable String password);

  public void setGroups(List<Group> groups);
}
