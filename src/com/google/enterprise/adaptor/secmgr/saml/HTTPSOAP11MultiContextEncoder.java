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

import org.opensaml.Configuration;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.binding.encoding.BaseSAML2MessageEncoder;
import org.opensaml.ws.message.MessageContext;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.ws.soap.common.SOAPObjectBuilder;
import org.opensaml.ws.soap.soap11.Body;
import org.opensaml.ws.soap.soap11.Envelope;
import org.opensaml.ws.transport.http.HTTPOutTransport;
import org.opensaml.ws.transport.http.HTTPTransportUtils;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.opensaml.xml.util.XMLHelper;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.logging.Logger;

/**
 * SAML 2.0 SOAP 1.1 over HTTP MultiContext binding encoder.
 * Based on OpenSaml's HTTPSOAP11Encoder
 */
public class HTTPSOAP11MultiContextEncoder extends BaseSAML2MessageEncoder {

  private Envelope envelope;
  private Body body;
  private HTTPOutTransport outTransport;

  /** Class logger. */
  private static final Logger log = Logger.getLogger(HTTPSOAP11MultiContextEncoder.class.getName());

  /** Constructor. */
  public HTTPSOAP11MultiContextEncoder() {
    super();
  }

  public String getBindingURI() {
    return SAMLConstants.SAML2_SOAP11_BINDING_URI;
  }

  public boolean providesMessageConfidentiality(MessageContext messageContext) {
    if (messageContext.getOutboundMessageTransport().isConfidential()) {
      return true;
    }

    return false;
  }

  public boolean providesMessageIntegrity(MessageContext messageContext) {
    if (messageContext.getOutboundMessageTransport().isIntegrityProtected()) {
      return true;
    }

    return false;
  }

  @Override
  protected void doEncode(MessageContext messageContext) throws MessageEncodingException {
    if (!(messageContext instanceof SAMLMessageContext<?, ?, ?>)) {
      throw new MessageEncodingException(
          "Invalid message context type, this encoder only supports SAMLMessageContext");
    }
    @SuppressWarnings("unchecked")
    SAMLMessageContext<SAMLObject, SAMLObject, SAMLObject> samlMsgCtx =
        (SAMLMessageContext<SAMLObject, SAMLObject, SAMLObject>) messageContext;

    if (!(messageContext.getOutboundMessageTransport() instanceof HTTPOutTransport)) {
      throw new MessageEncodingException(
          "Invalid outbound message transport type, this encoder only supports HTTPOutTransport");
    }
    outTransport = (HTTPOutTransport) messageContext.getOutboundMessageTransport();

    if (envelope == null) {
      buildSOAPMessage();
    }

    SAMLObject samlMessage = samlMsgCtx.getOutboundSAMLMessage();
    if (samlMessage == null) {
      throw new MessageEncodingException("No outbound SAML message contained in message context");
    }

    signMessage(samlMsgCtx);
    samlMsgCtx.setOutboundMessage(envelope);

    log.fine("Adding SAML message to the SOAP message's body");

    body.getUnknownXMLObjects().add(samlMessage);
  }

  public void finish() throws MessageEncodingException {
    Element envelopeElem = marshallMessage(envelope);
    try {
      HTTPTransportUtils.addNoCacheHeaders(outTransport);
      HTTPTransportUtils.setUTF8Encoding(outTransport);
      HTTPTransportUtils.setContentType(outTransport, "text/xml");
      outTransport.setHeader("SOAPAction", "http://www.oasis-open.org/committees/security");
      Writer out = new OutputStreamWriter(outTransport.getOutgoingStream(), "UTF-8");
      XMLHelper.writeNode(envelopeElem, out);
      out.flush();
    } catch (UnsupportedEncodingException e) {
      throw new MessageEncodingException("JVM does not support required UTF-8 encoding");
    } catch (IOException e) {
      throw new MessageEncodingException("Unable to write message content to outbound stream", e);
    }
  }

  /**
   * Builds the SOAP message to be encoded.
   */
  protected void buildSOAPMessage() {
    log.fine("Building SOAP message");
    XMLObjectBuilderFactory builderFactory = Configuration.getBuilderFactory();

    @SuppressWarnings("unchecked")
    SOAPObjectBuilder<Envelope> envBuilder =
        (SOAPObjectBuilder<Envelope>) builderFactory.getBuilder(Envelope.DEFAULT_ELEMENT_NAME);
    envelope = envBuilder.buildObject();

    @SuppressWarnings("unchecked")
    SOAPObjectBuilder<Body> bodyBuilder =
        (SOAPObjectBuilder<Body>) builderFactory.getBuilder(Body.DEFAULT_ELEMENT_NAME);
    body = bodyBuilder.buildObject();

    envelope.setBody(body);
  }
}
