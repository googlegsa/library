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

package com.google.enterprise.adaptor.secmgr.saml;

import com.google.enterprise.adaptor.secmgr.common.XmlUtil;

import org.opensaml.Configuration;
import org.opensaml.common.SAMLObject;
import org.opensaml.xml.io.MarshallingException;
import org.w3c.dom.Element;

import java.io.IOException;

/**
 * Utilities for logging SAML messages.
 */
public class SamlLogUtil {

  private SamlLogUtil() {
    // prevent instantiation
    throw new UnsupportedOperationException();
  }

  /**
   * Generate a log message containing a SAML object.  The returned string
   * should be passed to a logger; if there's an error converting the SAML
   * object to a string, the returned string will be an error message.  In
   * either case we log the result.
   *
   * @param message Some text that will be prefixed to the log entry.
   * @param so A SAML object that will be converted to a string and appended to
   *     the log entry.
   * @return A suitably formatted log message.
   * @throws IOException if unable to serialize XML string.
   */
  public static String xmlMessage(String message, SAMLObject so)
      throws IOException {
    Element element = null;
    try {
      element = Configuration.getMarshallerFactory().getMarshaller(so).marshall(so);
    } catch (MarshallingException e) {
      return message + ": MarshallingException while marshalling " + so.toString()
          + ": " + e.getMessage();
    }
    if (element == null) {
      return message + ": SAMLObject marshalls to null " + so.toString();
    }
    return message + ":\n" + XmlUtil.getInstance().buildXmlString(element.getOwnerDocument());
  }
}
