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

package com.google.enterprise.adaptor.secmgr.servlets;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.enterprise.adaptor.secmgr.authncontroller.ExportedState;
import com.google.enterprise.adaptor.secmgr.common.SecurityManagerUtil;
import com.google.enterprise.adaptor.secmgr.modules.SamlClient;
import com.google.enterprise.adaptor.secmgr.saml.OpenSamlUtil;
import com.google.enterprise.adaptor.secmgr.saml.SamlLogUtil;

import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;
import org.joda.time.DateTimeUtils;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.AudienceRestriction;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameIDType;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.xml.XMLObject;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

/**
 * A parser to disassemble and validate a SAML Response element.
 */
@Immutable
public final class ResponseParser {
  private static final Logger LOGGER = Logger.getLogger(ResponseParser.class.getName());
  private static final DateTimeComparator dtComparator = DateTimeComparator.getInstance();

  private final SamlClient client;
  private final String recipient;
  private final Response response;
  private final String sessionId;
  private final long now;
  private final Assertion assertion;

  private ResponseParser(SamlClient client, String recipient, Response response, String sessionId) {
    this.client = client;
    this.recipient = recipient;
    this.response = response;
    this.sessionId = sessionId;
    this.now = DateTimeUtils.currentTimeMillis();
    this.assertion = findSuitableAssertion();
  }

  public static ResponseParser make(SamlClient client, String recipient, Response response,
      String sessionId) {
    return new ResponseParser(client, recipient, response, sessionId);
  }

  /** Log messages as info. */
  private void inform(String... messages) {
    for (String message : messages) {
      LOGGER.info(SecurityManagerUtil.sessionLogMessage(sessionId, message));
    }
  }

  /** Log messages as warnings. */
  private void warn(String... messages) {
    for (String message : messages) {
      LOGGER.warning(SecurityManagerUtil.sessionLogMessage(sessionId, message));
    }
  }

  /**
   * If condition is false then log messages as warnings.
   * <p>
   * This method is used to modify a chain of &amp;&amp; boolean conditions.
   * For example:
   * <pre>
   *   return name != null
   *       && isValidName(name)
   *       && hasSession(name);
   * </pre>
   * becomes:
   * <pre>
   *   return warnIfFalse(name != null, "Name is null")
   *       && warnIfFalse(isValidName(name), "Invalid name: " + name)
   *       && warnIfFalse(hasSession(name), "Missing session for: " + name);
   * </pre>
   * <p>
   * @return value of condition
   */
  private boolean warnIfFalse(boolean condition, String ... messages) {
    if (!condition) {
      warn(messages);
    }
    return condition;
  }

  // to avoid having 3 try/catch loops in findSuitableAssertionHelper
  private Assertion findSuitableAssertion() {
    try {
      return findSuitableAssertionHelper();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "An error occurred but logger could not parse "
                               + "the SamlResponse object.", e);
      return null;
    }
  }

  // TODO(cph): This logic is inadequate, but more or less the same as what the
  // GSA does.  Instead of trying to find a single valid assertion, we should
  // analyze the assertions as a whole and combine their content.  The SAML spec
  // allows the IdP to include arbitrary numbers of assertions, and with each
  // assertion an arbitrary number of statements.  The spec explicitly states
  // that an assertion with multiple statements is completely equivalent to
  // multiple assertions, each with a single statement (provided the other parts
  // of the assertions match one another).  It's the responsibility of the
  // relying party (us) to make sense of the information in whatever form the
  // IdP sends it.
  private Assertion findSuitableAssertionHelper() throws IOException {
    if (response.getAssertions().isEmpty()) {
      warn(SamlLogUtil.xmlMessage(
        "Received no assertions in this response.", response));
    }

    for (Assertion assertion : response.getAssertions()) {
      if (isAssertionValid(assertion)) {
        return assertion;
      }
      warn(SamlLogUtil.xmlMessage(
          "Rejected assertion because it was invalid", assertion));
    }
    warn(SamlLogUtil.xmlMessage(
        "Could not find a valid assertion for this response", response));
    return null;
  }

  /**
   * Is the response element valid?
   */
  public boolean isResponseValid() {
    Issuer issuer = response.getIssuer();
    return issuer == null || isValidIssuer(issuer);
  }

  /**
   * Get the response status code.
   * Must satisfy {@link #isResponseValid} prior to calling.
   */
  public String getResponseStatus() {
    return response.getStatus().getStatusCode().getValue();
  }

  /**
   * Are the assertions contained in this response valid?
   * Meaningful only when response status is "success".
   */
  public boolean areAssertionsValid() {
    return assertion != null;
  }

  /**
   * Get the asserted subject.
   * Must satisfy {@link #areAssertionsValid} prior to calling.
   */
  public String getSubject() {
    return assertion.getSubject().getNameID().getValue();
  }

  /**
   * Get the expiration time for the subject verification.
   * Must satisfy {@link #areAssertionsValid} prior to calling.
   *
   * @return The expiration time, or null if there is none.
   */
  public DateTime getExpirationTime() {
    DateTime time1 = assertion.getConditions().getNotOnOrAfter();
    List<SubjectConfirmation> confirmations = assertion.getSubject().getSubjectConfirmations();
    if (confirmations.isEmpty()) {
      return time1;
    }
    DateTime time2 = Iterables.find(confirmations, bearerPredicate)
        .getSubjectConfirmationData()
        .getNotOnOrAfter();
    return (time1 == null || dtComparator.compare(time1, time2) > 0) ? time2 : time1;
  }

  /**
   * Gets an exported-state object.  This information is a security manager
   * extension.  Must satisfy {@link #areAssertionsValid} prior to calling.
   */
  public ExportedState getExportedState() {
    return getExportedState(assertion);
  }

  /**
   * Gets an exported-state object from a given assertion.  This information is
   * a security manager extension.
   *
   * @param assertion The assertion to get the identities from.
   * @return A exported-state object, or {@code null} if there's none.
   */
  @VisibleForTesting
  public static ExportedState getExportedState(Assertion assertion) {
    for (AttributeStatement attributeStatement : assertion.getAttributeStatements()) {
      for (Attribute attribute : attributeStatement.getAttributes()) {
        if (ExportedState.ATTRIBUTE_NAME.equals(attribute.getName())) {
          List<XMLObject> attributeValues = attribute.getAttributeValues();
          if (attributeValues.size() == 1) {
            XMLObject attributeValue = attributeValues.get(0);
            String textContent = attributeValue.getDOM().getTextContent();
            return ExportedState.fromJsonString(textContent);
          }
        }
      }
    }
    return null;
  }

  /**
   * Get any groups that are provided by the assertions.
   * This information is a security manager extension.
   * Must satisfy {@link #areAssertionsValid} prior to calling.
   */
  public ImmutableSet<String> getGroups() {
    return getGroups(assertion);
  }

  /**
   * Gets any groups that are provided by a given assertion.  This information
   * is a security manager extension.
   *
   * @param assertion An assertion to get the groups from.
   * @return An immutable set of the groups found; may be empty.
   */
  @VisibleForTesting
  public static ImmutableSet<String> getGroups(Assertion assertion) {
    ExportedState state = getExportedState(assertion);
    return (state != null)
        ? state.getPviCredentials().getGroupsNames()
        : ImmutableSet.<String>of();
  }

  // **************** Validation primitives ****************

  private boolean isAssertionValid(Assertion assertion) {
    String validityDescription
        = "is empty (of statements): " + assertion.getAuthnStatements().isEmpty()
        + ", assertion issuer: " +  assertion.getIssuer()
        + ", is valid issuer: " + isValidIssuer(assertion.getIssuer())
        + ", is valid subject: " + isValidSubject(assertion.getSubject())
        + ", are valid conditions: " + isValidConditions(assertion.getConditions());
    inform(validityDescription);

    return !assertion.getAuthnStatements().isEmpty()
        && (assertion.getIssuer() != null)
        && isValidIssuer(assertion.getIssuer())
        && isValidSubject(assertion.getSubject())
        && isValidConditions(assertion.getConditions());
  }

  private boolean isValidIssuer(Issuer issuer) {
    return warnIfFalse(issuer.getFormat() == null || NameIDType.ENTITY.equals(issuer.getFormat()),
                "Issuer contains a format: " + issuer.getFormat() +
                " but is not equal to expected format: " + NameIDType.ENTITY)
        && warnIfFalse(client.getPeerEntity().getEntityID().equals(issuer.getValue()),
                "Issuer value: " + issuer.getValue() + " is not equals to "
                + "expected value: " + client.getPeerEntity().getEntityID());
  }

  private boolean isValidSubject(Subject subject) {
    return warnIfFalse(subject != null, "Subject is null.")
        && warnIfFalse(hasValidId(subject), "Subject has an invalid ID.")
        && warnIfFalse(areValidSubjectConfirmations(subject.getSubjectConfirmations()),
                "Subject does not have valid confirmations.");
  }

  // This is a security manager requirement; it's not mandated by the spec.
  private boolean hasValidId(Subject subject) {
    return warnIfFalse(subject.getBaseID() == null, "Subject BaseID is not null.")
        && warnIfFalse(subject.getNameID() != null, "Subject NameID is null")
        && warnIfFalse(!Strings.isNullOrEmpty(subject.getNameID().getValue()),
                "Subject NameID string is null or empty.")
        && warnIfFalse(subject.getEncryptedID() == null, "Subject contains an EncryptedID.");
  }

  private boolean areValidSubjectConfirmations(List<SubjectConfirmation> confirmations) {
    if (confirmations.isEmpty()) {
      // This violates the SAML spec, but the GSA has historically ignored this
      // information, so we must allow it.
      warn("SAML assertion received without subject confirmation");
      return true;
    }
    Iterable<SubjectConfirmation> bearers = Iterables.filter(confirmations, bearerPredicate);
    return warnIfFalse(!Iterables.isEmpty(bearers), "SubjectConfirmations contains no bearers.")
        && warnIfFalse(Iterables.all(bearers, validBearerPredicate),
        "SubjectConfirmations were invalid.");
  }

  private Predicate<SubjectConfirmation> bearerPredicate =
      new Predicate<SubjectConfirmation>() {
        public boolean apply(SubjectConfirmation confirmation) {
          return OpenSamlUtil.BEARER_METHOD.equals(confirmation.getMethod());
        }
      };

  private Predicate<SubjectConfirmation> validBearerPredicate =
      new Predicate<SubjectConfirmation>() {
        public boolean apply(SubjectConfirmation bearer) {
          return isValidSubjectConfirmationData(bearer.getSubjectConfirmationData());
        }
      };

  private boolean isValidSubjectConfirmationData(SubjectConfirmationData data) {
    return warnIfFalse(recipient.equals(data.getRecipient()),
                "SubjectConfirmationData - recipient not equals : " + recipient +
                "but was instead: " + data.getRecipient())
        && warnIfFalse(isValidExpiration(data.getNotOnOrAfter()), "Invalid expiration.")
        && warnIfFalse(client.getRequestId().equals((data.getInResponseTo())),
                "Assertion inResponseTo: " + data.getInResponseTo() +
                  " was not equal to this client's RequestID: " + client.getRequestId());
  }

  private boolean isValidExpiration(DateTime expiration) {
    return warnIfFalse(expiration != null, "Assertion expiration was null.") &&
           warnIfFalse(SecurityManagerUtil.isRemoteOnOrAfterTimeValid(expiration.getMillis(), now),
                "The assertion's expiration time is invalid.",
                "Security Manager Current Time: " + new DateTime(now).toString(),
                "Assertion expiration:" + new DateTime(expiration.getMillis()).toString());
  }

  // TODO(cph): This code needs to handle <OneTimeUse> and <ProxyRestriction>
  // conditions.
  private boolean isValidConditions(Conditions conditions) {
    return warnIfFalse(conditions != null, "Assertion conditions was null.")
        && warnIfFalse(isValidConditionNotBefore(conditions.getNotBefore()),
                "ConditionNotBefore is invalid: " + conditions.getNotBefore())
        && warnIfFalse(isValidConditionNotOnOrAfter(conditions.getNotOnOrAfter()),
                "ConditionNotOnOrAfter is invalid: " + conditions.getNotOnOrAfter())
        && warnIfFalse(isValidConditionAudienceRestrictions(conditions.getAudienceRestrictions()),
                "ConditionAudienceRestrictions is invalid: " +
                conditions.getAudienceRestrictions());
  }

  private boolean isValidConditionNotBefore(DateTime notBefore) {
    return notBefore == null ||
        SecurityManagerUtil.isRemoteBeforeTimeValid(notBefore.getMillis(), now);
  }

  private boolean isValidConditionNotOnOrAfter(DateTime notOnOrAfter) {
    return notOnOrAfter == null ||
        SecurityManagerUtil.isRemoteOnOrAfterTimeValid(notOnOrAfter.getMillis(), now);
  }

  private boolean isValidConditionAudienceRestrictions(List<AudienceRestriction> restrictions) {
    String localEntityId = client.getLocalEntity().getEntityID();
    for (AudienceRestriction restriction : restrictions) {
      for (Audience audience : restriction.getAudiences()) {
        if (localEntityId.equals(audience.getAudienceURI())) {
          return true;
        }
      }
    }
    return false;
  }
}
