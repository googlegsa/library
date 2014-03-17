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

import org.opensaml.common.SAMLObject;
import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.binding.decoding.BaseSAML2MessageDecoder;
import org.opensaml.ws.message.MessageContext;
import org.opensaml.ws.message.decoder.MessageDecodingException;
import org.opensaml.ws.soap.soap11.Envelope;
import org.opensaml.ws.soap.soap11.Header;
import org.opensaml.ws.transport.http.HTTPInTransport;
import org.opensaml.xml.AttributeExtensibleXMLObject;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.parse.ParserPool;
import org.opensaml.xml.util.DatatypeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

/**
 * SAML 2.0 SOAP 1.1 over HTTP MultiContext binding decoder.
 * Based on OpenSaml's HTTPSOAP11Decoder
 */
public class HTTPSOAP11MultiContextDecoder extends BaseSAML2MessageDecoder {

  /** Class logger. */
  private static final Logger log = Logger.getLogger(HTTPSOAP11MultiContextDecoder.class.getName());

  /** QNames of understood SOAP headers. */
  private final List<QName> understoodHeaders = new ArrayList<QName>();

  /** QName of SOAP mustUnderstand header attribute. */
  private final QName soapMustUnderstand = new QName(SAMLConstants.SOAP11ENV_NS, "mustUnderstand");

  private Envelope soapMessage;
  private List<XMLObject> soapBodyChildren;
  private int thisChild;

  /** Constructor. */
  public HTTPSOAP11MultiContextDecoder() {
    super();
  }

  /**
   * Constructor.
   *
   * @param pool parser pool used to deserialize messages
   */
  public HTTPSOAP11MultiContextDecoder(ParserPool pool) {
    super(pool);
  }

  public String getBindingURI() {
    return SAMLConstants.SAML2_SOAP11_BINDING_URI;
  }

  @Override
  protected boolean isIntendedDestinationEndpointURIRequired(
      @SuppressWarnings("rawtypes") SAMLMessageContext samlMsgCtx) {
    return false;
  }

  /**
   * Gets the SOAP header names that are understood by the application.
   *
   * @return SOAP header names that are understood by the application
   */
  public List<QName> getUnderstoodHeaders() {
    return understoodHeaders;
  }

  /**
   * Sets the SOAP header names that are understood by the application.
   *
   * @param headerNames SOAP header names that are understood by the application
   */
  public void setUnderstoodHeaders(List<QName> headerNames) {
    understoodHeaders.clear();
    if (headerNames != null) {
      understoodHeaders.addAll(headerNames);
    }
  }

  @Override
  protected void doDecode(MessageContext messageContext) throws MessageDecodingException {
    if (!(messageContext instanceof SAMLMessageContext<?, ?, ?>)) {
      throw new MessageDecodingException(
          "Invalid message context type, this decoder only support SAMLMessageContext");
    }
    @SuppressWarnings("unchecked")
    SAMLMessageContext<SAMLObject, SAMLObject, SAMLObject> samlMsgCtx =
        (SAMLMessageContext<SAMLObject, SAMLObject, SAMLObject>) messageContext;

    samlMsgCtx.setInboundMessage(soapMessage);
    if (soapMessage == null) {
      start(samlMsgCtx);
    }

    if (soapBodyChildren.size() < 1) {
      throw new MessageDecodingException(
          "Unexpected number of children in the SOAP body, " + soapBodyChildren.size()
          + ". Unable to extract SAML message");
    }

    if (thisChild >= soapBodyChildren.size()) {
      // indicates to the caller that there are no more messages to decode
      // this should be caught and recovered from
      throw new IndexOutOfBoundsException();
    }

    XMLObject incomingMessage = soapBodyChildren.get(thisChild);
    thisChild++;

    if (!(incomingMessage instanceof SAMLObject)) {
      throw new MessageDecodingException(
          "Unexpected SOAP body content.  Expected a SAML request but recieved "
              + incomingMessage.getElementQName());
    }
    SAMLObject samlMessage = (SAMLObject) incomingMessage;

    log.log(Level.FINE,
        "Decoded SOAP messaged which included SAML message of type {}",
        samlMessage.getElementQName());
    samlMsgCtx.setInboundSAMLMessage(samlMessage);

    populateMessageContext(samlMsgCtx);
  }

  /**
   * Checks that all SOAP headers that require understanding are in the understood header
   * list.
   *
   * @param headers SOAP headers to check
   *
   * @throws MessageDecodingException thrown if a SOAP header requires
   *         understanding but is not understood by the decoder
   */
  protected void checkUnderstoodSOAPHeaders(List<XMLObject> headers)
      throws MessageDecodingException {
    if (headers == null || headers.isEmpty()) {
      return;
    }

    AttributeExtensibleXMLObject attribExtensObject;
    for (XMLObject header : headers) {
      if (header instanceof AttributeExtensibleXMLObject) {
        attribExtensObject = (AttributeExtensibleXMLObject) header;
        if (DatatypeHelper.safeEquals("1", attribExtensObject.getUnknownAttributes().get(
            soapMustUnderstand))) {
          if (!understoodHeaders.contains(header.getElementQName())) {
            throw new MessageDecodingException(
                "SOAP decoder encountered a  header, "
                + header.getElementQName()
                + ", that requires undestanding, "
                + "however this decoder does not understand that header");
          }
        }
      }
    }
  }

  private void start(SAMLMessageContext<SAMLObject, SAMLObject, SAMLObject> samlMsgCtx)
      throws MessageDecodingException {
    if (!(samlMsgCtx.getInboundMessageTransport() instanceof HTTPInTransport)) {
      throw new MessageDecodingException(
          "Invalid inbound message transport type, this decoder only support HTTPInTransport");
    }
    HTTPInTransport inTransport = (HTTPInTransport) samlMsgCtx.getInboundMessageTransport();

    if (!inTransport.getHTTPMethod().equalsIgnoreCase("POST")) {
      throw new MessageDecodingException(
          "This message deocoder only supports the HTTP POST method");
    }

    log.fine("Unmarshalling SOAP message");
    soapMessage = Envelope.class.cast(unmarshallMessage(inTransport.getIncomingStream()));

    Header messageHeader = soapMessage.getHeader();
    if (messageHeader != null) {
      checkUnderstoodSOAPHeaders(soapMessage.getHeader().getUnknownXMLObjects());
    }
    soapBodyChildren = soapMessage.getBody().getUnknownXMLObjects();
    thisChild = 0;
  }
}
