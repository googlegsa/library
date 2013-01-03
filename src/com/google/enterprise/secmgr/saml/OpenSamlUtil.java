// Copyright 2008 Google Inc.
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

import static org.opensaml.common.xml.SAMLConstants.SAML20P_NS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
//import com.google.enterprise.secmgr.authncontroller.AuthnSession;
import com.google.enterprise.secmgr.common.FileUtil;
import com.google.enterprise.secmgr.common.HttpUtil;
import com.google.enterprise.secmgr.config.ConfigSingleton;

//import org.jdom.Namespace;
import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.IdentifierGenerator;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.SAMLObjectBuilder;
import org.opensaml.common.SAMLVersion;
import org.opensaml.common.binding.BasicEndpointSelector;
import org.opensaml.common.binding.BasicSAMLMessageContext;
import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.binding.artifact.BasicSAMLArtifactMap;
import org.opensaml.common.binding.artifact.SAMLArtifactMap;
import org.opensaml.common.binding.artifact.SAMLArtifactMap.SAMLArtifactMapEntry;
import org.opensaml.common.binding.security.SAMLProtocolMessageXMLSignatureSecurityPolicyRule;
import org.opensaml.common.impl.SecureRandomIdentifierGenerator;
import org.opensaml.saml2.binding.security.SAML2HTTPRedirectDeflateSignatureRule;
import org.opensaml.saml2.core.Action;
import org.opensaml.saml2.core.Artifact;
import org.opensaml.saml2.core.ArtifactResolve;
import org.opensaml.saml2.core.ArtifactResponse;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.AudienceRestriction;
import org.opensaml.saml2.core.AuthnContext;
import org.opensaml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.saml2.core.AuthzDecisionQuery;
import org.opensaml.saml2.core.AuthzDecisionStatement;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.DecisionTypeEnumeration;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.RequestAbstractType;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.Statement;
import org.opensaml.saml2.core.Status;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.core.StatusMessage;
import org.opensaml.saml2.core.StatusResponseType;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml2.metadata.RoleDescriptor;
import org.opensaml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml2.metadata.provider.FilesystemMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.saml2.metadata.provider.ObservableMetadataProvider;
import org.opensaml.security.MetadataCredentialResolver;
import org.opensaml.util.storage.MapBasedStorageService;
import org.opensaml.ws.message.MessageContext;
import org.opensaml.ws.message.decoder.MessageDecoder;
import org.opensaml.ws.message.decoder.MessageDecodingException;
import org.opensaml.ws.message.encoder.MessageEncoder;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.ws.security.SecurityPolicy;
import org.opensaml.ws.security.SecurityPolicyResolver;
import org.opensaml.ws.security.SecurityPolicyRule;
import org.opensaml.ws.security.provider.BasicSecurityPolicy;
import org.opensaml.ws.security.provider.StaticSecurityPolicyResolver;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.security.CriteriaSet;
import org.opensaml.xml.security.SecurityException;
import org.opensaml.xml.security.SecurityHelper;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.security.credential.CredentialResolver;
import org.opensaml.xml.security.credential.UsageType;
import org.opensaml.xml.security.keyinfo.BasicProviderKeyInfoCredentialResolver;
import org.opensaml.xml.security.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xml.security.keyinfo.KeyInfoCriteria;
import org.opensaml.xml.security.keyinfo.KeyInfoProvider;
import org.opensaml.xml.security.keyinfo.provider.DSAKeyValueProvider;
import org.opensaml.xml.security.keyinfo.provider.InlineX509DataProvider;
import org.opensaml.xml.security.keyinfo.provider.RSAKeyValueProvider;
import org.opensaml.xml.security.trust.TrustEngine;
import org.opensaml.xml.security.x509.X509Credential;
import org.opensaml.xml.signature.KeyInfo;
import org.opensaml.xml.signature.SignatureTrustEngine;
import org.opensaml.xml.signature.impl.ExplicitKeySignatureTrustEngine;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.servlet.http.HttpServletRequest;
import javax.xml.namespace.QName;

/**
 * A collection of utilities to support OpenSAML programming.  The majority of the
 * definitions here are static factory methods for SAML objects.
 * <p>
 * <strong>Notes on security policies and credential resolution:</strong>
 * <p>
 * OpenSAML has a very complicated mechanism for dealing with credentials and trust, of
 * which we use only a small part.  Here are the basic components:
 *
 * <dl>
 * <dt>{@link Credential}
 * <dd>Some information that can be used for signing or encryption.
 *
 * <dt>{@link CredentialResolver}
 * <dd>Selects one or more credentials based on a set of criteria.  We currently use only
 * {@link MetadataCredentialResolver}, which gets credentials from metadata using criteria
 * like entity ID and role.
 *
 * <dt>{@link KeyInfoCredentialResolver}
 * <dd>Extracts one or more credentials from a {@link KeyInfo} element; it's allowed to
 * choose between different credentials based on internal criteria.  We currently use a
 * KeyInfoCredentialResolver that selects only X.509 certificate credentials.
 *
 * <dt>{@link TrustEngine}
 * <dd>Evaluates the trustworthiness and validity of a given object against some given
 * criteria.  It is used as an element of some policy rules.
 *
 * <dt>{@link SecurityPolicy}
 * <dd>A collection of policy rules, evaluated against a message context, that determines
 * if a message is well-formed, valid, and otherwise okay to process.
 *
 * <dt>{@link SecurityPolicyRule}
 * <dd>A component of a security policy, also evaluated against a message context.
 *
 * <dt>{@link SecurityPolicyResolver}
 * <dd>Uses a given set of criteria to select a security policy.  We currently use only a
 * static policy resolver, which always returns the same policy.
 * </dl>
 *
 * <p>
 * The programmer simply attaches a security-policy resolver to the SAML message context
 * and OpenSAML will automatically enforce the security policy as appropriate.
 */
@Immutable
public final class OpenSamlUtil {
  private static final Logger LOGGER = Logger.getLogger(OpenSamlUtil.class.getName());

  /**
   * The human-readable name of the (GSA) service provider.
   */
  public static final String GOOGLE_PROVIDER_NAME = "Google Search Appliance";

  /**
   * The human-readable name of the (Security Manager) service provider.
   */
  public static final String SM_PROVIDER_NAME = "Google Security Manager";

  /**
   * The SAML "bearer" method, normally used in SubjectConfirmation.
   */
  public static final String BEARER_METHOD = "urn:oasis:names:tc:SAML:2.0:cm:bearer";

  public static final String SAML_EXTENSIONS_TAG = "Extensions";

  public static final String GSA_SESSION_ID_TAG = "GsaSessionId";

  public static final String GSA_FLAG_FAST_AUTHZ_TAG = "EnableFastAuthz";
  public static final String GSA_FAST_AUTHZ_FAST = "FAST";
  public static final String GSA_FAST_AUTHZ_ALL = "ALL";

  public static final String GOOGLE_NS_URI = "http://www.google.com/";
  public static final String GOOGLE_NS_PREFIX = "google:";
  public static final String GOOGLE_NS_PREFIX_NOCOLON = "google";
  /*public static final Namespace GOOGLE_NS =
      Namespace.getNamespace(GOOGLE_NS_PREFIX_NOCOLON, GOOGLE_NS_URI);*/

  static {
    try {
      DefaultBootstrap.bootstrap();
    } catch (ConfigurationException e) {
      throw new IllegalStateException(e);
    }

    // This is required in order to patch around missing code in OpenSAML.
    Configuration.registerObjectProvider(
        AttributeValue.DEFAULT_ELEMENT_NAME,
        new AttributeValueBuilder(),
        new AttributeValueMarshaller(),
        new AttributeValueUnmarshaller());
  }

  private static final XMLObjectBuilderFactory objectBuilderFactory =
      Configuration.getBuilderFactory();

  // TODO(cph): @SuppressWarnings is needed because objectBuilderFactory.getBuilder() returns a
  // supertype of the actual type.
  @SuppressWarnings("unchecked")
  private static <T extends SAMLObject> SAMLObjectBuilder<T> makeSamlObjectBuilder(QName name) {
    return (SAMLObjectBuilder<T>) objectBuilderFactory.getBuilder(name);
  }

  private static final SAMLObjectBuilder<Action> actionBuilder =
      makeSamlObjectBuilder(Action.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Artifact> artifactBuilder =
      makeSamlObjectBuilder(Artifact.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<ArtifactResolve> artifactResolveBuilder =
      makeSamlObjectBuilder(ArtifactResolve.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<ArtifactResponse> artifactResponseBuilder =
      makeSamlObjectBuilder(ArtifactResponse.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Assertion> assertionBuilder =
      makeSamlObjectBuilder(Assertion.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AssertionConsumerService> assertionConsumerServiceBuilder =
      makeSamlObjectBuilder(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Attribute> attributeBuilder =
      makeSamlObjectBuilder(Attribute.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AttributeStatement> attributeStatementBuilder =
      makeSamlObjectBuilder(AttributeStatement.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AttributeValue> attributeValueBuilder =
      makeSamlObjectBuilder(AttributeValue.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Audience> audienceBuilder =
      makeSamlObjectBuilder(Audience.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AudienceRestriction> audienceRestrictionBuilder =
      makeSamlObjectBuilder(AudienceRestriction.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AuthnContext> authnContextBuilder =
      makeSamlObjectBuilder(AuthnContext.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AuthnContextClassRef> authnContextClassRefBuilder =
      makeSamlObjectBuilder(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AuthnRequest> authnRequestBuilder =
      makeSamlObjectBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AuthnStatement> authnStatementBuilder =
      makeSamlObjectBuilder(AuthnStatement.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AuthzDecisionQuery> authzDecisionQueryBuilder =
      makeSamlObjectBuilder(AuthzDecisionQuery.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<AuthzDecisionStatement> authzDecisionStatementBuilder =
      makeSamlObjectBuilder(AuthzDecisionStatement.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Conditions> conditionsBuilder =
      makeSamlObjectBuilder(Conditions.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Issuer> issuerBuilder =
      makeSamlObjectBuilder(Issuer.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<NameID> nameIDBuilder =
      makeSamlObjectBuilder(NameID.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Response> responseBuilder =
      makeSamlObjectBuilder(Response.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Status> statusBuilder =
      makeSamlObjectBuilder(Status.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<StatusCode> statusCodeBuilder =
      makeSamlObjectBuilder(StatusCode.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<StatusMessage> statusMessageBuilder =
      makeSamlObjectBuilder(StatusMessage.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<Subject> subjectBuilder =
      makeSamlObjectBuilder(Subject.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<SubjectConfirmation> subjectConfirmationBuilder =
      makeSamlObjectBuilder(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
  private static final SAMLObjectBuilder<SubjectConfirmationData> subjectConfirmationDataBuilder =
      makeSamlObjectBuilder(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);

  // Metadata builders

  private static final SAMLObjectBuilder<SingleSignOnService> singleSignOnServiceBuilder =
      makeSamlObjectBuilder(SingleSignOnService.DEFAULT_ELEMENT_NAME);

  // Identifier generator

  private static final IdentifierGenerator idGenerator;
  static {
    try {
      idGenerator = new SecureRandomIdentifierGenerator();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  // Non-instantiable class.
  private OpenSamlUtil() {
  }

  private static void initializeRequest(RequestAbstractType request, String issuer,
      DateTime issueInstant) {
    request.setID(generateIdentifier());
    request.setVersion(SAMLVersion.VERSION_20);
    request.setIssuer(makeIssuer(issuer));
    request.setIssueInstant(issueInstant);
  }

  private static void initializeResponse(StatusResponseType response, String issuer,
      DateTime issueInstant, Status status, String inResponseTo) {
    response.setID(generateIdentifier());
    response.setVersion(SAMLVersion.VERSION_20);
    response.setIssuer(makeIssuer(issuer));
    response.setIssueInstant(issueInstant);
    response.setStatus(status);
    response.setInResponseTo(inResponseTo);
  }

  /**
   * Static factory for SAML {@link Action} objects.
   *
   * @param name A URI identifying the represented action.
   * @param namespace A URI identifying the class of names being specified.
   * @return A new <code>Action</code> object.
   */
  public static Action makeAction(String name, String namespace) {
    Action action = actionBuilder.buildObject();
    action.setAction(name);
    action.setNamespace(namespace);
    return action;
  }

  /**
   * Static factory for SAML {@link Artifact} objects.
   *
   * @param value The artifact string.
   * @return A new <code>Artifact</code> object.
   */
  private static Artifact makeArtifact(String value) {
    Artifact element = artifactBuilder.buildObject();
    element.setArtifact(value);
    return element;
  }

  /**
   * Static factory for SAML {@link ArtifactResolve} objects.
   *
   * @param issuer The entity issuing this request.
   * @param issueInstant The time of issue for this statement.
   * @param value The artifact string to be resolved.
   * @return A new <code>ArtifactResolve</code> object.
   */
  public static ArtifactResolve makeArtifactResolve(String issuer, DateTime issueInstant,
      String value) {
    ArtifactResolve request = artifactResolveBuilder.buildObject();
    initializeRequest(request, issuer, issueInstant);
    request.setArtifact(makeArtifact(value));
    return request;
  }

  /**
   * Static factory for SAML {@link ArtifactResponse} objects.
   *
   * @param issuer The entity issuing this response.
   * @param issueInstant The time of issue for this statement.
   * @param status The <code>Status</code> object indicating the success of the resolution.
   * @param inResponseTo The message ID of the request this is a response to.
   * @param message The embedded message.
   * @return A new <code>ArtifactResponse</code> object.
   */
  public static ArtifactResponse makeArtifactResponse(String issuer, DateTime issueInstant,
      Status status, String inResponseTo, SAMLObject message) {
    ArtifactResponse response = artifactResponseBuilder.buildObject();
    initializeResponse(response, issuer, issueInstant, status, inResponseTo);
    if (message != null) {
      response.setMessage(message);
    }
    return response;
  }

  /**
   * Static factory for SAML {@link Assertion} objects.
   *
   * @param issuer The entity issuing this assertion.
   * @param issueInstant The time of issue for this statement.
   * @param subject The subject of the assertion.
   * @param conditions The conditions under which this assertion is valid.
   * @param statements Statements being made by this assertion.
   * @return A new <code>Assertion</code> object.
   */
  public static Assertion makeAssertion(String issuer, DateTime issueInstant, Subject subject,
      Conditions conditions, Statement... statements) {
    Assertion assertion = assertionBuilder.buildObject();
    assertion.setID(generateIdentifier());
    assertion.setVersion(SAMLVersion.VERSION_20);
    assertion.setIssuer(makeIssuer(issuer));
    assertion.setIssueInstant(issueInstant);
    assertion.setSubject(subject);
    if (conditions != null) {
      assertion.setConditions(conditions);
    }
    for (Statement statement : statements) {
      if (statement instanceof AuthnStatement) {
        assertion.getAuthnStatements().add((AuthnStatement) statement);
      } else if (statement instanceof AuthzDecisionStatement) {
        assertion.getAuthzDecisionStatements().add((AuthzDecisionStatement) statement);
      } else if (statement instanceof AttributeStatement) {
        assertion.getAttributeStatements().add((AttributeStatement) statement);
      } else {
        throw new IllegalArgumentException("Unknown statement type: " + statement);
      }
    }
    return assertion;
  }

  /**
   * Static factory for SAML {@link AssertionConsumerService} objects.
   *
   * @param location A URL for this service.
   * @param binding A SAML binding used to communicate with the service.
   * @return A new {@code AssertionConsumerService} object.
   */
  public static AssertionConsumerService makeAssertionConsumerService(String location,
      String binding) {
    AssertionConsumerService endpoint = assertionConsumerServiceBuilder.buildObject();
    endpoint.setLocation(location);
    endpoint.setBinding(binding);
    endpoint.setIndex(0);
    endpoint.setIsDefault(true);
    return endpoint;
  }

  /**
   * Static factory for SAML {@link Attribute} objects.
   *
   * @param name The attribute name.
   * @return A new <code>Attribute</code> object.
   */
  public static Attribute makeAttribute(String name) {
    Attribute attribute = attributeBuilder.buildObject();
    attribute.setName(name);
    return attribute;
  }

  /**
   * Static factory for SAML {@link AttributeStatement} objects.
   *
   * @param attributes The attributes to include in the statement.
   * @return A new <code>AttributeStatement</code> object.
   */
  public static AttributeStatement makeAttributeStatement(Attribute... attributes) {
    AttributeStatement statement = attributeStatementBuilder.buildObject();
    for (Attribute attribute : attributes) {
      if (attribute != null) {
        statement.getAttributes().add(attribute);
      }
    }
    return statement;
  }

  /**
   * Static factory for SAML {@link AttributeValue} objects.
   *
   * @return A new <code>AttributeValue</code> object.
   */
  public static AttributeValue makeAttributeValue(String value) {
    AttributeValue attrValue = attributeValueBuilder.buildObject();
    attrValue.setValue(value);
    return attrValue;
  }

  /**
   * Static factory for SAML {@link Audience} objects.
   *
   * @param uri The audience URI.
   * @return A new <code>Audience</code> object.
   */
  private static Audience makeAudience(String uri) {
    Audience audience = audienceBuilder.buildObject();
    audience.setAudienceURI(uri);
    return audience;
  }

  /**
   * Static factory for SAML {@link AudienceRestriction} objects.
   *
   * @param uris The audience URIs.
   * @return A new <code>AudienceRestriction</code> object.
   */
  public static AudienceRestriction makeAudienceRestriction(String... uris) {
    AudienceRestriction restriction = audienceRestrictionBuilder.buildObject();
    for (String uri : uris) {
      restriction.getAudiences().add(makeAudience(uri));
    }
    return restriction;
  }

  /**
   * Static factory for SAML {@link AuthnContext} objects.
   *
   * @param classRef An <code>AuthnContextClassRef</code> identifying an authentication context
   * class.
   * @return A new <code>AuthnContext</code> object.
   */
  private static AuthnContext makeAuthnContext(AuthnContextClassRef classRef) {
    AuthnContext context = authnContextBuilder.buildObject();
    context.setAuthnContextClassRef(classRef);
    return context;
  }

  /**
   * Static factory for SAML {@link AuthnContext} objects.
   *
   * A convenience method that wraps the given URI in an {@link AuthnContextClassRef} object.
   *
   * @param uri A URI identifying an authentication context class.
   * @return A new <code>AuthnContext</code> object.
   */
  private static AuthnContext makeAuthnContext(String uri) {
    return makeAuthnContext(makeAuthnContextClassRef(uri));
  }

  /**
   * Static factory for SAML {@link AuthnContextClassRef} objects.
   *
   * @param uri A URI identifying an authentication context class.
   * @return A new <code>AuthnContextClassRef</code> object.
   */
  private static AuthnContextClassRef makeAuthnContextClassRef(String uri) {
    AuthnContextClassRef classRef = authnContextClassRefBuilder.buildObject();
    classRef.setAuthnContextClassRef(uri);
    return classRef;
  }

  /**
   * Static factory for SAML {@link AuthnRequest} objects.
   *
   * @param issuer The entity issuing this request.
   * @param issueInstant The time of issue for this statement.
   * @return A new <code>AuthnRequest</code> object.
   */
  public static AuthnRequest makeAuthnRequest(String issuer, DateTime issueInstant) {
    AuthnRequest request = authnRequestBuilder.buildObject();
    initializeRequest(request, issuer, issueInstant);
    return request;
  }

  /**
   * Static factory for SAML {@link AuthnStatement} objects.
   *
   * @param issueInstant The time of issue for this statement.
   * @param uri A URI identifying an authentication context class.
   * @return A new <code>AuthnStatement</code> object.
   */
  public static AuthnStatement makeAuthnStatement(DateTime issueInstant, String uri) {
    AuthnStatement statement = authnStatementBuilder.buildObject();
    statement.setAuthnInstant(issueInstant);
    statement.setAuthnContext(makeAuthnContext(uri));
    return statement;
  }

  /**
   * Static factory for SAML {@link AuthzDecisionQuery} objects.
   *
   * @param issuer The entity issuing this query.
   * @param issueInstant The time of issue for this query.
   * @param subject The subject requesting access to a resource.
   * @param resource The resource for which access is being requested.
   * @param action The action on the resource for which access is being requested.
   * @return A new <code>AuthzDecisionQuery</code> object.
   */
  public static AuthzDecisionQuery makeAuthzDecisionQuery(String issuer, DateTime issueInstant,
      Subject subject, String resource, Action action) {
    AuthzDecisionQuery query = authzDecisionQueryBuilder.buildObject();
    initializeRequest(query, issuer, issueInstant);
    query.setSubject(subject);
    query.setResource(resource);
    query.getActions().add(action);
    return query;
  }

  /**
   * Static factory for SAML {@link AuthzDecisionStatement} objects.
   *
   * @param resource The resource referred to by this access decision.
   * @param decision The access decision made by the authorization service.
   * @param actions The actions authorized to perform on the stated resource.
   * @return A new <code>AuthzDecisionStatement</code> object.
   */
  public static AuthzDecisionStatement makeAuthzDecisionStatement(String resource,
      DecisionTypeEnumeration decision, Action... actions) {
    AuthzDecisionStatement statement = authzDecisionStatementBuilder.buildObject();
    statement.setResource(resource);
    statement.setDecision(decision);
    statement.getActions().addAll(Arrays.asList(actions));
    return statement;
  }

  /**
   * Static factory for SAML {@link Conditions} objects.
   *
   * @param notBefore Earliest time at which assertion is valid.
   * @param notOnOrAfter Latest time at which assertion is valid.
   * @param restriction The audience restriction that must be satisfied.
   * @return A new <code>Conditions</code> object.
   */
  public static Conditions makeConditions(DateTime notBefore, DateTime notOnOrAfter,
      AudienceRestriction restriction) {
    Conditions conditions = conditionsBuilder.buildObject();
    conditions.setNotBefore(notBefore);
    conditions.setNotOnOrAfter(notOnOrAfter);
    conditions.getAudienceRestrictions().add(restriction);
    return conditions;
  }

  /**
   * Static factory for SAML {@link Issuer} objects.
   *
   * @param name The issuer of a response object.  In the absence of a specific format, this is a
   *     URI identifying the issuer.
   * @return A new <code>Issuer</code> object.
   */
  private static Issuer makeIssuer(String name) {
    Issuer issuer = issuerBuilder.buildObject();
    issuer.setValue(name);
    return issuer;
  }

  /**
   * Static factory for SAML {@link NameID} objects.
   *
   * @param name The name represented by this object.
   * @return A new <code>NameID</code> object.
   */
  private static NameID makeNameId(String name) {
    NameID id = nameIDBuilder.buildObject();
    id.setValue(name);
    return id;
  }

  /**
   * Static factory for SAML {@link Response} objects.
   *
   * @param issuer The entity issuing this response.
   * @param issueInstant The time of issue for this statement.
   * @param status The <code>Status</code> object indicating the success of requested action.
   * @param request The request that this is a response to.
   * @param assertions The assertions carried by this response.
   * @return A new <code>Response</code> object.
   */
  public static Response makeResponse(String issuer, DateTime issueInstant,
      Status status, RequestAbstractType request, Assertion... assertions) {
    return makeResponse(issuer, issueInstant, status, request.getID(), assertions);
  }

  /**
   * Static factory for SAML {@link Response} objects.
   *
   * @param issuer The entity issuing this response.
   * @param issueInstant The time of issue for this statement.
   * @param status The <code>Status</code> object indicating the success of requested action.
   * @param inResponseTo The message ID of the request this is a response to.
   * @param assertions The assertions carried by this response.
   * @return A new <code>Response</code> object.
   */
  public static Response makeResponse(String issuer, DateTime issueInstant, Status status,
      String inResponseTo, Assertion... assertions) {
    Response response = responseBuilder.buildObject();
    initializeResponse(response, issuer, issueInstant, status, inResponseTo);
    response.getAssertions().addAll(Arrays.asList(assertions));
    return response;
  }

  /**
   * Static factory for SAML {@link Status} objects.
   *
   * @param value A URI specifying one of the standard SAML status codes.
   * @return A new <code>Status</code> object.
   */
  public static Status makeStatus(String value) {
    Status status = statusBuilder.buildObject();
    status.setStatusCode(makeStatusCode(value));
    return status;
  }

  /**
   * Static factory for SAML {@link Status} objects.
   *
   * A convenience method that generates a status object with a message.
   *
   * @param value A URI specifying one of the standard SAML status codes.
   * @param message A string describing the status.
   * @return A new <code>Status</code> object.
   */
  public static Status makeStatus(String value, String message) {
    Status status = makeStatus(value);
    status.setStatusMessage(makeStatusMessage(message));
    return status;
  }

  /**
   * Static factory for SAML {@link Status} objects.
   *
   * @param statusCode A status code indicating result status of a request.
   * @param statusMessage An optional message providing human-readable detail of the status.
   * @return A new {@link Status} object.
   */
  public static Status makeStatus(StatusCode statusCode, StatusMessage statusMessage) {
    Status status = statusBuilder.buildObject();
    status.setStatusCode(statusCode);
    if (statusMessage != null) {
      status.setStatusMessage(statusMessage);
    }
    return status;
  }

  /**
   * Static factory for a successful SAML status element.
   *
   * @return A successful {@link Status} element.
   */
  public static Status makeSuccessfulStatus() {
    return makeStatus(makeStatusCode(StatusCode.SUCCESS_URI), null);
  }

  /**
   * Static factory for SAML {@link StatusCode} objects.
   *
   * @param value A URI specifying one of the standard SAML status codes.
   * @return A new <code>StatusCode</code> object.
   */
  public static StatusCode makeStatusCode(String value) {
    StatusCode code = statusCodeBuilder.buildObject();
    code.setValue(value);
    return code;
  }

  /**
   * Static factory for SAML {@link StatusMessage} objects.
   *
   * @param value A status message string.
   * @return A new <code>StatusMessage</code> object.
   */
  public static StatusMessage makeStatusMessage(String value) {
    StatusMessage message = statusMessageBuilder.buildObject();
    message.setMessage(value);
    return message;
  }

  /**
   * Static factory for SAML {@link Subject} objects.
   *
   * @param name The name identifying the subject.
   * @param confirmations The confirmations for this subject.
   * @return A new <code>Subject</code> object.
   */
  public static Subject makeSubject(String name, SubjectConfirmation... confirmations) {
    Subject samlSubject = subjectBuilder.buildObject();
    samlSubject.setNameID(makeNameId(name));
    if (confirmations != null) {
      samlSubject.getSubjectConfirmations().addAll(Arrays.asList(confirmations));
    }
    return samlSubject;
  }

  /**
   * Static factory for SAML {@link SubjectConfirmation} objects.
   *
   * @param method The method used to confirm the subject.
   * @param data The data about the confirmation.
   * @return A new <code>SubjectConfirmation</code> object.
   */
  public static SubjectConfirmation makeSubjectConfirmation(String method,
      SubjectConfirmationData data) {
    SubjectConfirmation confirmation = subjectConfirmationBuilder.buildObject();
    confirmation.setMethod(method);
    confirmation.setSubjectConfirmationData(data);
    return confirmation;
  }

  /**
   * Static factory for SAML {@link SubjectConfirmationData} objects.
   *
   * @param recipient The entity ID of the intended recipient.
   * @param expirationTime The expiration time for this subject.
   * @param inResponseTo The message ID of the AuthnRequest this is a response to.
   * @return A new <code>SubjectConfirmationData</code> object.
   */
  public static SubjectConfirmationData makeSubjectConfirmationData(String recipient,
      DateTime expirationTime, String inResponseTo) {
    SubjectConfirmationData data = subjectConfirmationDataBuilder.buildObject();
    data.setRecipient(recipient);
    data.setNotOnOrAfter(expirationTime);
    data.setInResponseTo(inResponseTo);
    return data;
  }

  /*
   * Metadata descriptions.
   */

  /**
   * Static factory for SAML {@link SingleSignOnService} objects.
   *
   * @param binding The SAML binding implemented by this service.
   * @param location The URL that the service listens to.
   * @return A new <code>SingleSignOnService</code> object.
   */
  public static SingleSignOnService makeSingleSignOnService(String binding, String location) {
    SingleSignOnService service = singleSignOnServiceBuilder.buildObject();
    service.setBinding(binding);
    service.setLocation(location);
    return service;
  }

  /*
   * Endpoint selection
   */

  public static void initializeLocalEntity(
      SAMLMessageContext<? extends SAMLObject, ? extends SAMLObject, ? extends SAMLObject> context,
      EntityDescriptor entity, RoleDescriptor role) {
    initializeLocalEntity(context, entity);
    context.setLocalEntityRole(role.getElementQName());
    context.setLocalEntityRoleMetadata(role);
  }

  public static void initializeLocalEntity(
      SAMLMessageContext<? extends SAMLObject, ? extends SAMLObject, ? extends SAMLObject> context,
      EntityDescriptor entity) {
    context.setLocalEntityId(entity.getEntityID());
    context.setLocalEntityMetadata(entity);
    context.setOutboundMessageIssuer(entity.getEntityID());
  }

  public static void initializePeerEntity(
      SAMLMessageContext<? extends SAMLObject, ? extends SAMLObject, ? extends SAMLObject> context,
      EntityDescriptor entity, RoleDescriptor role, QName endpointType, String binding) {
    context.setPeerEntityId(entity.getEntityID());
    context.setPeerEntityMetadata(entity);
    context.setPeerEntityRole(role.getElementQName());
    context.setPeerEntityRoleMetadata(role);
    {
      BasicEndpointSelector selector = new BasicEndpointSelector();
      selector.setEntityMetadata(entity);
      selector.setEndpointType(endpointType);
      selector.setEntityRoleMetadata(role);
      selector.getSupportedIssuerBindings().add(binding);
      context.setPeerEntityEndpoint(selector.selectEndpoint());
    }
  }

  /*
   * Identifiers
   */

  /**
   * Generate a random identifier.
   *
   * @return A new identifier string.
   */
  public static String generateIdentifier() {
    return idGenerator.generateIdentifier();
  }

  /*
   * Context and codecs
   */

  /**
   * Static factory for OpenSAML message-context objects.
   *
   * @param <TI> The type of the request object.
   * @param <TO> The type of the response object.
   * @param <TN> The type of the name identifier used for subjects.
   * @return A new message-context object.
   */
  public static <TI extends SAMLObject, TO extends SAMLObject, TN extends SAMLObject>
        SAMLMessageContext<TI, TO, TN> makeSamlMessageContext() {
    SAMLMessageContext<TI, TO, TN> context = new BasicSAMLMessageContext<TI, TO, TN>();
    context.setInboundSAMLProtocol(SAML20P_NS);  // we only use SAML 2.0
    context.setOutboundSAMLProtocol(SAML20P_NS);
    return context;
  }

  /**
   * Run a message encoder.
   *
   * @param encoder The message encoder to run.
   * @param context The message context to pass to the encoder.
   * @throws IOException if unable to encode message.
   */
  public static void runEncoder(MessageEncoder encoder, MessageContext context)
      throws IOException {
    try {
      encoder.encode(context);
    } catch (MessageEncodingException e) {
      throw new IOException(e);
    }
  }

  /**
   * Run a message decoder.
   *
   * @param decoder The message decoder to run.
   * @param context The message context to pass to the decoder.
   * @throws IOException if unable to decode message.
   */
  public static void runDecoder(MessageDecoder decoder, MessageContext context)
      throws IOException {
    try {
      decoder.decode(context);
    } catch (MessageDecodingException e) {
      throw new IOException(e);
    } catch (SecurityException e) {
      throw new IOException(e);
    }
  }

  /**
   * Run a message decoder.
   *
   * @param decoder The message decoder to run.
   * @param context The message context to pass to the decoder.
   * @param senderDescription A phrase describing the sender of the message.
   * @return True only if the message was successfully decode.
   */
  /*
  // Commented out to prevent pulling in AuthnSession.
  public static boolean runDecoder(MessageDecoder decoder, MessageContext context,
      AuthnSession session, String senderDescription) {
    try {
      decoder.decode(context);
    } catch (MessageDecodingException e) {
      LOGGER.warning(session.logMessage("Unable to decode SAML message from " + senderDescription));
      return false;
    } catch (SecurityException e) {
      LOGGER.warning(session.logMessage("Unable to verify signature of SAML message from " +
              senderDescription));
      return false;
    }
    return true;
  }*/

  /**
   * Get a string for the current date/time, in the correct format for SAML.
   *
   * @return The corresponding string.
   */
  public static String samlDateString() {
    return samlDateString(new DateTime());
  }

  /**
   * Get a string for a given date/time, in the correct format for SAML.
   *
   * @param date The date/time to convert.
   * @return The corresponding string.
   */
  public static String samlDateString(DateTime date) {
    synchronized (samlDateStringLock) {
      return Configuration.getSAMLDateFormatter().print(date);
    }
  }

  private static final Object samlDateStringLock = new Object();

  /**
   * Get a SAML metadata provider that reads from a specified file.  The provider adjusts
   * its output as the file is changed.
   *
   * @param file The file containing the metadata.
   * @return A metadata provider for the given file.
   * @throws MetadataProviderException if there are problems reading the file.
   */
  public static ObservableMetadataProvider getMetadataFromFile(File file)
      throws MetadataProviderException {
    FilesystemMetadataProvider provider = new FilesystemMetadataProvider(file);
    provider.setParserPool(new BasicParserPool());
    // Causes null-pointer errors in OpenSAML code:
    //provider.setRequireValidMetadata(true);
    return provider;
  }

  /**
   * Make a static security-policy resolver that resolves to a given policy.
   *
   * @param policy The security policy to resolve to.
   * @return A security-policy resolver that resolves to the given policy.
   */
  public static SecurityPolicyResolver makeStaticSecurityPolicyResolver(SecurityPolicy policy) {
    return new StaticSecurityPolicyResolver(policy);
  }

  /**
   * Make a static security-policy resolver that resolves to a given policy.
   *
   * @param rules The policy rules that comprise the returned policy.
   * @return A security-policy resolver that resolves to the given policy.
   */
  public static SecurityPolicyResolver makeStaticSecurityPolicyResolver(
      SecurityPolicyRule... rules) {
    return makeStaticSecurityPolicyResolver(makeBasicSecurityPolicy(rules));
  }

  /**
   * Make a basic security policy, consisting of a set of rules that must all be satisfied.
   *
   * @param rules The rules comprising the policy.
   * @return A new security policy.
   */
  public static SecurityPolicy makeBasicSecurityPolicy(SecurityPolicyRule... rules) {
    BasicSecurityPolicy securityPolicy = new BasicSecurityPolicy();
    securityPolicy.getPolicyRules().addAll(Arrays.asList(rules));
    return securityPolicy;
  }

  /**
   * Get a signature policy rule for XML signatures, using metadata credentials.
   *
   * @param request An HTTP request to use for specializing the metadata.
   * @return An appropriate signature policy rule.
   * @throws IOException
   */
  public static synchronized SecurityPolicyRule getMetadataSignaturePolicyRule(
      HttpServletRequest request)
      throws IOException {
    return new SAMLProtocolMessageXMLSignatureSecurityPolicyRule(
        getMetadataSignatureTrustEngine(request));
  }

  /**
   * Get a signature policy rule for the redirect binding, using metadata credentials.
   *
   * @param request An HTTP request to use for specializing the metadata.
   * @return An appropriate signature policy rule.
   * @throws IOException
   */
  public static synchronized SecurityPolicyRule getMetadataSignaturePolicyRuleForRedirect(
      HttpServletRequest request)
      throws IOException {
    return new SAML2HTTPRedirectDeflateSignatureRule(
        getMetadataSignatureTrustEngine(request));
  }

  /**
   * Make a trust engine that checks signatures using credentials in metadata.
   *
   * @param request An HTTP request to use for specializing the metadata.
   * @return An appropriate signature trust engine.
   * @throws IOException
   */
  public static SignatureTrustEngine getMetadataSignatureTrustEngine(HttpServletRequest request)
      throws IOException {
    KeyInfoCredentialResolver keyInfoCredentialResolver = getStandardKeyInfoCredentialResolver();
    MetadataCredentialResolver credentialResolver =
        new MetadataCredentialResolver(getMetadata(request).getProvider());
    credentialResolver.setKeyInfoCredentialResolver(keyInfoCredentialResolver);
    return new ExplicitKeySignatureTrustEngine(credentialResolver, keyInfoCredentialResolver);
  }

  private static KeyInfoCredentialResolver standardKeyInfoCredentialResolver = null;

  /**
   * Make a KeyInfoCredentialResolver that knows about some basic credential types.
   *
   * @return A new KeyInfoCredentialResolver.
   */
  public static synchronized KeyInfoCredentialResolver getStandardKeyInfoCredentialResolver() {
    if (standardKeyInfoCredentialResolver == null) {
      List<KeyInfoProvider> providers = Lists.newArrayList();
      providers.add(new DSAKeyValueProvider());
      providers.add(new RSAKeyValueProvider());
      providers.add(new InlineX509DataProvider());
      standardKeyInfoCredentialResolver = new BasicProviderKeyInfoCredentialResolver(providers);
    }
    return standardKeyInfoCredentialResolver;
  }

  /**
   * Return the standard credentials from a given KeyInfo object.
   *
   * @param keyInfo The KeyInfo object to examine.
   * @return The standard credentials found in the object.
   * @throws SecurityException
   */
  public static Iterable<Credential> resolveStandardKeyInfoCredentials(KeyInfo keyInfo)
      throws SecurityException {
    return getStandardKeyInfoCredentialResolver()
        .resolve(new CriteriaSet(new KeyInfoCriteria(keyInfo)));
  }

  /**
   * Does a given peer's metadata support verification of signed messages?
   *
   * @param context The SAML message context containing the peer's metadata.
   * @return True if the role supports verification of signed messages.
   */
  public static boolean peerSupportsSignatureVerification(
      SAMLMessageContext<? extends SAMLObject, ? extends SAMLObject, ? extends SAMLObject>
      context) {
    for (KeyDescriptor keyDescriptor : context.getPeerEntityRoleMetadata().getKeyDescriptors()) {
      if (keyDescriptor.getUse() == UsageType.SIGNING) {
        try {
          if (resolveStandardKeyInfoCredentials(keyDescriptor.getKeyInfo()).iterator().hasNext()) {
            return true;
          }
        } catch (SecurityException e) {
          throw new IllegalStateException("Got SecurityException while extracting credentials:", e);
        }
      }
    }
    return false;
  }

  /**
   * Read a PEM-encoded X.509 certificate file and its associated private-key file and
   * return an {@link X509Credential} object.
   *
   * @param certFile The certificate file.
   * @param keyFile The private-key file.
   * @return The credential object, never null.
   * @throws IOException if there's some kind of error reading or converting the files.
   */
  public static X509Credential readX509Credential(File certFile, File keyFile)
      throws IOException {
    return SecurityHelper.getSimpleCredential(
        readX509CertificateFile(certFile),
        readPrivateKeyFile(keyFile));
  }

  /**
   * Read a PEM-encoded X.509 certificate file and return it as an {@link X509Certificate}
   * object.
   *
   * @param file The file to read.
   * @return The certificate object, never null.
   * @throws IOException if there's some kind of error reading or converting the file.
   */
  public static X509Certificate readX509CertificateFile(File file)
      throws IOException {
    String base64Cert = FileUtil.readPEMCertificateFile(file);
    try {
      return SecurityHelper.buildJavaX509Cert(base64Cert);
    } catch (CertificateException e) {
      throw new IOException(e);
    }
  }

  /**
   * Read a PEM-encoded private-key file and return it as a {@link PrivateKey} object.
   *
   * @param file The file to read.
   * @return The private-key object, never null.
   * @throws IOException if there's some kind of error reading or converting the file.
   */
  public static PrivateKey readPrivateKeyFile(File file)
      throws IOException {
    try {
      return SecurityHelper.decodePrivateKey(file, new char[0]);
    } catch (KeyException e) {
      throw new IOException(e);
    }
  }

  /**
   * Convert an OpenSAML object to a DOM object.
   *
   * @param xmlObject The OpenSAML object to convert.
   * @return The corresponding DOM object.
   * @throws MarshallingException if unable to convert object.
   */
  public static Element marshallXmlObject(XMLObject xmlObject) throws MarshallingException {
    return Configuration.getMarshallerFactory().getMarshaller(xmlObject).marshall(xmlObject);
  }

  /**
   * Convert a DOM object to an OpenSAML object.
   *
   * @param element A DOM object representing a SAML element.
   * @return The corresponding OpenSAML object.
   * @throws UnmarshallingException if unable to convert object.
   */
  public static XMLObject unmarshallXmlObject(Element element) throws UnmarshallingException {
    return Configuration.getUnmarshallerFactory().getUnmarshaller(element).unmarshall(element);
  }

  /**
   * Make a new artifact map.
   *
   * @param artifactLifetime The lifetime of the map's artifacts, in milliseconds.
   * @return A new artifact map.
   */
  public static SAMLArtifactMap makeArtifactMap(int artifactLifetime) {
    return new BasicSAMLArtifactMap(
        new BasicParserPool(),
        new MapBasedStorageService<String, SAMLArtifactMapEntry>(),
        artifactLifetime);
  }

  /**
   * Gets an entity descriptor with a given ID.
   *
   * @param id The ID of the entity to get.
   * @param request The servlet request to use for getting the metadata.
   * @return The entity's descriptor, or {@code null} if there's no entity with
   *     that ID.
   */
  public static EntityDescriptor getEntity(String id, HttpServletRequest request)
      throws IOException {
    return getMetadata(request).getEntity(id);
  }

  /**
   * Gets the entity descriptor for the security manager.
   *
   * @param request The servlet request to use for getting the metadata.
   * @return The security manager's entity descriptor.
   */
  public static EntityDescriptor getSmEntity(HttpServletRequest request)
      throws IOException {
    return getMetadata(request).getSmEntity();
  }

  /**
   * Gets the entity ID for the security manager.
   *
   * @param request The servlet request to use for getting the metadata.
   * @return The security manager's entity ID.
   */
  public static String getSmEntityId(HttpServletRequest request)
      throws IOException {
    return getMetadata(request).getSmEntityId();
  }

  /**
   * Gets the SAML metadata.
   *
   * @param request The servlet request to use for customizing the metadata.
   * @return The metadata.
   */
  public static Metadata getMetadata(HttpServletRequest request)
      throws IOException {
    return Metadata.getInstance(HttpUtil.getRequestUrl(request, false));
  }

  @VisibleForTesting
  public static Metadata getMetadata()
      throws IOException {
    return Metadata.getInstance("localhost");
  }

  /**
   * Gets the credential to use for signing outgoing messages.
   *
   * @return The credential, or null if unable to obtain.
   */
  public static Credential getSigningCredential()
      throws IOException {
    String certFileName = ConfigSingleton.getSigningCertificateFilename();
    String keyFileName = ConfigSingleton.getSigningKeyFilename();
    if (certFileName == null || keyFileName == null) {
      LOGGER.info("No signing certificate available for outbound requests");
      return null;
    }
    File certFile = FileUtil.getContextFile(certFileName);
    File keyFile = FileUtil.getContextFile(keyFileName);
    if (!certFile.canRead()) {
      LOGGER.warning("Unable to read signing certificate file: " + certFile);
      return null;
    }
    if (!keyFile.canRead()) {
      LOGGER.warning("Unable to read signing key file: " + keyFile);
      return null;
    }
    try {
      return OpenSamlUtil.readX509Credential(certFile, keyFile);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Error reading certificate file(s): ", e);
      return null;
    }
  }
}
