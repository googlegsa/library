// Copyright 2009 Google Inc.
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

package com.google.enterprise.secmgr.saml;

import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.xml.AbstractExtensibleXMLObject;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

// This class should be part of OpenSAML but is missing from there.
public class AttributeValueImpl
    extends AbstractExtensibleXMLObject
    implements AttributeValue {

  private static final QName xsiType =
      new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
  private static final String xsString = "xs:string";

  private String value;

  public AttributeValueImpl() {
    super(SAMLConstants.SAML20_NS,
        DEFAULT_ELEMENT_LOCAL_NAME,
          SAMLConstants.SAML20_PREFIX);
  }

  public AttributeValueImpl(String nsUri, String localName, String nsPrefix) {
    super(nsUri, localName, nsPrefix);
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = prepareForAssignment(this.value, value);
    this.getUnknownAttributes().put(xsiType, xsString);
  }
}
