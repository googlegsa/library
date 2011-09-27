// Copyright 2011 Google Inc. All Rights Reserved.
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

package adaptorlib;

import com.google.enterprise.secmgr.saml.HTTPSOAP11MultiContextDecoder;
import com.google.enterprise.secmgr.saml.HTTPSOAP11MultiContextEncoder;
import com.google.enterprise.secmgr.saml.OpenSamlUtil;

import com.sun.net.httpserver.HttpExchange;

import org.joda.time.DateTime;

import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.saml2.core.Action;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.AuthzDecisionQuery;
import org.opensaml.saml2.core.AuthzDecisionStatement;
import org.opensaml.saml2.core.DecisionTypeEnumeration;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.Status;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.core.Subject;
import org.opensaml.ws.message.decoder.MessageDecodingException;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.xml.security.SecurityException;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Handler for responding to late-binding, SAML batch authorization requests
 * from the GSA.
 */
class SamlBatchAuthzHandler extends AbstractHandler {
  private final String defaultHostname;
  private final Adaptor adaptor;
  private final SamlMetadata metadata;
  private DocIdDecoder docIdDecoder;

  public SamlBatchAuthzHandler(String defaultHostname, Charset defaultCharset,
                               Adaptor adaptor, DocIdDecoder docIdDecoder,
                               SamlMetadata samlMetadata) {
    super(defaultHostname, defaultCharset);
    this.defaultHostname = defaultHostname;
    this.adaptor = adaptor;
    this.docIdDecoder = docIdDecoder;
    this.metadata = samlMetadata;
  }

  @Override
  public void meteredHandle(HttpExchange ex) throws IOException {
    if (!"POST".equals(ex.getRequestMethod())) {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
          "Unsupported request method");
      return;
    }
    // Setup SAML context.
    SAMLMessageContext<AuthzDecisionQuery, Response, NameID> context
        = OpenSamlUtil.makeSamlMessageContext();
    context.setInboundMessageTransport(new HttpExchangeInTransportAdapter(ex));
    context.setOutboundMessageTransport(
        new HttpExchangeOutTransportAdapter(ex));

    // Decode request.
    HTTPSOAP11MultiContextDecoder decoder = new HTTPSOAP11MultiContextDecoder();
    List<AuthzDecisionQuery> queries = new ArrayList<AuthzDecisionQuery>();
    while (true) {
      try {
        decoder.decode(context);
      } catch (MessageDecodingException e) {
        throw new IOException(e);
      } catch (SecurityException e) {
        throw new IOException(e);
      } catch (IndexOutOfBoundsException e) {
        // Normal indication that there are no more messages to decode.
        break;
      }
      queries.add(context.getInboundSAMLMessage());
    }

    // Figure out if the user is authorized.
    List<Response> responses = processQueries(queries, getRequestUri(ex));

    // Encode response.
    HTTPSOAP11MultiContextEncoder encoder = new HTTPSOAP11MultiContextEncoder();
    for (Response resp : responses) {
      context.setOutboundSAMLMessage(resp);
      try {
        encoder.encode(context);
      } catch (MessageEncodingException e) {
        throw new IOException(e);
      }
    }
    try {
      encoder.finish();
    } catch (MessageEncodingException e) {
      throw new IOException(e);
    }
    ex.getResponseBody().flush();
    ex.getResponseBody().close();
    ex.close();
  }

  private List<Response> processQueries(List<AuthzDecisionQuery> queries,
                                        URI requestUri) throws IOException {
    DateTime now = new DateTime();
    // Convert URIs into DocIds, but maintain a mapping of the relationship to
    // later determine the relationship of query to response.
    Map<AuthzDecisionQuery, DocId> docIds
        = new HashMap<AuthzDecisionQuery, DocId>(queries.size() * 2);
    String userIdentifier = null;
    for (AuthzDecisionQuery query : queries) {
      String resource = query.getResource();
      String subject = query.getSubject().getNameID().getValue();
      if (userIdentifier != null) {
        if (!userIdentifier.equals(subject)) {
          throw new IllegalArgumentException(
              "All queries must be for the same subject");
        }
      } else {
        userIdentifier = subject;
      }
      URI uri = URI.create(resource);
      if (!requestUri.getScheme().equals(uri.getScheme())
          || !requestUri.getHost().equals(uri.getHost())
          || requestUri.getPort() != uri.getPort()) {
        // This is some unknown URI that is unrelated to the adaptor. Don't add
        // a DocId to the map. This will cause the later loop to use
        // INDETERMINATE.
      } else {
        docIds.put(query, docIdDecoder.decodeDocId(uri));
      }
    }

    // Ask the Adaptor if the user is allowed.
    docIds = Collections.unmodifiableMap(docIds);
    if (userIdentifier == null) {
      throw new IllegalArgumentException("Queries need a subject");
    }
    // TODO(ejona): figure out how to get groups
    Map<DocId, AuthzStatus> statuses = adaptor.isUserAuthorized(userIdentifier,
        Collections.<String>emptySet(), docIds.values());
    if (statuses == null) {
      statuses = Collections.emptyMap();
    }

    // For each query, build a SAML response based on Adaptor's response.
    List<Response> result = new ArrayList<Response>(queries.size());
    for (AuthzDecisionQuery query : queries) {
      AuthzStatus status = null;
      DocId docId = docIds.get(query);
      if (docId != null) {
        status = statuses.get(docId);
      }
      if (status == null) {
        status = AuthzStatus.INDETERMINATE;
      }
      result.add(createResponse(query, status, now));
    }
    return result;
  }

  private Response createResponse(AuthzDecisionQuery query,
                                  AuthzStatus authzStatus, DateTime time) {
    String issuer = metadata.getLocalEntity().getEntityID();
    // Assume the query was for GET.
    Action action
        = OpenSamlUtil.makeAction(Action.HTTP_GET_ACTION, Action.GHPP_NS_URI);
    AuthzDecisionStatement statement = OpenSamlUtil.makeAuthzDecisionStatement(
        query.getResource(), authzStatusMap(authzStatus), action);
    Subject subject = OpenSamlUtil.makeSubject(
        query.getSubject().getNameID().getValue());
    Assertion assertion = OpenSamlUtil.makeAssertion(
        issuer, time, subject, null, statement);
    Status status = OpenSamlUtil.makeStatus(StatusCode.SUCCESS_URI);
    return OpenSamlUtil.makeResponse(issuer, time, status, query, assertion);
  }

  private static DecisionTypeEnumeration authzStatusMap(AuthzStatus status) {
    switch (status) {
      case PERMIT:
        return DecisionTypeEnumeration.PERMIT;
      case DENY:
        return DecisionTypeEnumeration.DENY;
      case INDETERMINATE:
      default:
        return DecisionTypeEnumeration.INDETERMINATE;
    }
  }
}
