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

package com.google.enterprise.adaptor;

import com.google.enterprise.adaptor.secmgr.saml.Group;
import com.google.enterprise.adaptor.secmgr.saml.HTTPSOAP11MultiContextDecoder;
import com.google.enterprise.adaptor.secmgr.saml.HTTPSOAP11MultiContextEncoder;
import com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil;
import com.google.enterprise.adaptor.secmgr.saml.SecmgrCredential;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.joda.time.DateTime;
import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.saml2.common.Extensions;
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
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.security.SecurityException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler for responding to late-binding, SAML batch authorization requests
 * from the GSA.
 */
class SamlBatchAuthzHandler implements HttpHandler {
  private static final Logger log
      = Logger.getLogger(SamlBatchAuthzHandler.class.getName());

  private final AuthzAuthority authzAuthority;
  private final SamlMetadata metadata;
  private DocIdDecoder docIdDecoder;

  public SamlBatchAuthzHandler(AuthzAuthority authzAuthority,
      DocIdDecoder docIdDecoder, SamlMetadata samlMetadata) {
    this.authzAuthority = authzAuthority;
    this.docIdDecoder = docIdDecoder;
    this.metadata = samlMetadata;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    if (!"POST".equals(ex.getRequestMethod())) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
          Translation.HTTP_BAD_METHOD);
      return;
    }
    if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
          Translation.HTTP_NOT_FOUND);
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
        log.log(Level.INFO, "Error decoding message", e);
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
            Translation.HTTP_BAD_REQUEST_ERROR_DECODING);
        return;
      } catch (SecurityException e) {
        log.log(Level.WARNING, "Security error while decoding message", e);
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
            Translation.HTTP_BAD_REQUEST_SECURITY_ERROR);
        return;
      } catch (IndexOutOfBoundsException e) {
        // Normal indication that there are no more messages to decode.
        break;
      }
      queries.add(context.getInboundSAMLMessage());
    }

    // Figure out if the user is authorized.
    List<Response> responses;
    try {
      responses = processQueries(queries, HttpExchanges.getRequestUri(ex));
    } catch (TranslationIllegalArgumentException e) {
      log.log(Level.INFO, "Error processing queries", e);
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_REQUEST,
          e.getTranslation());
      return;
    }

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
                                        URI requestUri) {
    DateTime now = new DateTime();
    // Convert URIs into DocIds, but maintain a mapping of the relationship to
    // later determine the relationship of query to response.
    Map<AuthzDecisionQuery, DocId> docIds
        = new HashMap<AuthzDecisionQuery, DocId>(queries.size() * 2);
    String userIdentifier = null;
    AuthnIdentity identityFromSecmgrCred = null; // First one found is captured
    for (AuthzDecisionQuery query : queries) {
      String resource = query.getResource();
      if (resource == null) {
        throw new TranslationIllegalArgumentException(
            Translation.AUTHZ_BAD_QUERY_NO_RESOURCE);
      }
      if (query.getSubject() == null
          || query.getSubject().getNameID() == null) {
        throw new TranslationIllegalArgumentException(
            Translation.AUTHZ_BAD_QUERY_NO_SUBJECT);
      }
      String subject = query.getSubject().getNameID().getValue();
      if (subject == null) {
        throw new TranslationIllegalArgumentException(
            Translation.AUTHZ_BAD_QUERY_NO_SUBJECT);
      }
      if (userIdentifier != null) {
        if (!userIdentifier.equals(subject)) {
          throw new TranslationIllegalArgumentException(
              Translation.AUTHZ_BAD_QUERY_NOT_SAME_USER);
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
      if (identityFromSecmgrCred == null) {
        identityFromSecmgrCred = extractCredInfo(query);
      }
    }

    // Ask the Adaptor if the user is allowed.
    AuthnIdentity identity = null;
    if (identityFromSecmgrCred == null) {
      identity =
          new AuthnIdentityImpl.Builder(new UserPrincipal(userIdentifier))
              .build();
    } else {
      if (!userIdentifier
          .equals(identityFromSecmgrCred.getUser().parse().plainName)) {
        throw new TranslationIllegalArgumentException(
            Translation.AUTHZ_BAD_QUERY_NOT_SAME_USER);
      }
      identity = identityFromSecmgrCred;
    }
    log.info(identity.toString());
    docIds = Collections.unmodifiableMap(docIds);
    Map<DocId, AuthzStatus> statuses;
    try {
      statuses = authzAuthority.isUserAuthorized(identity, docIds.values());
    } catch (Exception e) {
      log.log(Level.WARNING, "Exception while satisfying Authn query", e);
      statuses = null;
    }
    if (statuses == null) {
      statuses = Collections.emptyMap();
    }

    // For each query, build a SAML response based on Adaptor's response.
    List<Response> result = new ArrayList<Response>(queries.size());
    for (AuthzDecisionQuery query : queries) {
      AuthzStatus status;
      DocId docId = docIds.get(query);
      if (docId == null) {
        // URL doesn't belong to adaptor
        status = AuthzStatus.INDETERMINATE;
      } else {
        status = statuses.get(docId);
        // INDETERMINATE means that the document doesn't exist, so the GSA must
        // have an old copy of some file. It isn't safe to do anything but DENY.

        // null means that the adaptor threw an exception or is buggy. The only
        // safe thing to do is DENY.
        if (status == null || status == AuthzStatus.INDETERMINATE) {
          status = AuthzStatus.DENY;
        }
      }
      result.add(createResponse(query, status, now));
    }
    return result;
  }
  
  private static AuthnIdentity extractCredInfo(AuthzDecisionQuery query) {
    AuthnIdentity identity = null;
    Extensions extensions = query.getExtensions();
    if (extensions != null) {
      List<XMLObject> objs = extensions.getOrderedChildren();
      for (XMLObject obj : objs) {
        if (obj instanceof SecmgrCredential) {
          SecmgrCredential cred = (SecmgrCredential) obj;
          String name = cred.getName();
          String domain = cred.getDomain();
          String userIdentity = name;
          if (domain != null && !"".equals(domain.trim())) {
            // TODO: change to use domain + "\\" + name
            userIdentity = name + "@" + domain;
          }
          Set<GroupPrincipal> groups = new TreeSet<GroupPrincipal>();
          for (Group g : cred.getGroups()) {
            String groupIdentity = g.getName();
            if (g.getDomain() != null && !"".equals(g.getDomain().trim())) {
              // TODO: change to use domain + "\\" + name
              groupIdentity = g.getName() + "@" + g.getDomain();
            }
            groups.add(new GroupPrincipal(groupIdentity, g.getNamespace()));
          }
          identity =
              new AuthnIdentityImpl.Builder(new UserPrincipal(userIdentity,
                  cred.getNamespace())).setPassword(cred.getPassword())
                  .setGroups(groups).build();
          break; // use the first SecmgrCredential
        }
      }
    }
    return identity;
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

  private static class TranslationIllegalArgumentException
      extends IllegalArgumentException {
    private final Translation translation;

    public TranslationIllegalArgumentException(Translation translation) {
      super(translation.toString());
      this.translation = translation;
    }

    public Translation getTranslation() {
      return translation;
    }
  }
}
