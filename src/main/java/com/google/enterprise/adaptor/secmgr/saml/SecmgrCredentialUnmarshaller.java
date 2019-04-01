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

import com.google.enterprise.adaptor.secmgr.common.XmlUtil;

import org.opensaml.common.impl.AbstractSAMLObjectUnmarshaller;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.UnmarshallingException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/** An unmarshaller (XML to object converter) for {@link SecmgrCredential}. */
final class SecmgrCredentialUnmarshaller extends AbstractSAMLObjectUnmarshaller {

  @Override
  public XMLObject unmarshall(Element domElement)
      throws UnmarshallingException {
    SecmgrCredential object = (SecmgrCredential) super.unmarshall(domElement);
    try {
      object.getName();
      object.getNamespace();
      object.getDomain();
      object.getPassword();
      object.getGroups();
    } catch (IllegalStateException e) {
      throw new UnmarshallingException(e);
    }
    return object;
  }

  @Override
  protected void processAttribute(XMLObject xmlObject, Attr attribute)
      throws UnmarshallingException {
    SecmgrCredential object = (SecmgrCredential) xmlObject;
    if (XmlUtil.attributeHasQname(attribute, SecmgrCredential.NAME_ATTRIB_NAME)) {
      try {
        object.setName(attribute.getValue());
      } catch (IllegalArgumentException e) {
        throw new UnmarshallingException(e);
      }
    } else if (XmlUtil.attributeHasQname(attribute,
          SecmgrCredential.NAMESPACE_ATTRIB_NAME)) {
      try {
        object.setNamespace(attribute.getValue());
      } catch (IllegalArgumentException e) {
        throw new UnmarshallingException(e);
      }
    } else if (XmlUtil.attributeHasQname(attribute, SecmgrCredential.DOMAIN_ATTRIB_NAME)) {
      try {
        object.setDomain(attribute.getValue());
      } catch (IllegalArgumentException e) {
        throw new UnmarshallingException(e);
      }
    } else if (XmlUtil.attributeHasQname(attribute, SecmgrCredential.PASSWORD_ATTRIB_NAME)) {
      try {
        object.setPassword(attribute.getValue());
      } catch (IllegalArgumentException e) {
        throw new UnmarshallingException(e);
      }
    }
  }

  @Override
  protected void processChildElement(XMLObject parentObject, XMLObject childObject)
      throws UnmarshallingException {
    SecmgrCredential credential = (SecmgrCredential) parentObject;
    if (childObject instanceof Group) {
      credential.getGroups().add((Group) childObject);
    } else {
      super.processChildElement(parentObject, childObject);
    }
  }
}
