// Copyright 2013 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpExchange;

import org.apache.commons.lang.StringEscapeUtils;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

/**
 * Test cases for {@link SamlIdentityProvider}.
 */
public class SamlIdentityProviderTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SamlMetadata metadata = new SamlMetadata(
      "bruteforce.mtv.corp.google.com", 5678,
      "entyo36.hot.corp.google.com",
      "http://google.com/enterprise/gsa/security-manager",
      "http://google.com/enterprise/gsa/adaptor");
  private UserPrincipal user = new UserPrincipal("user1");
  private Set<GroupPrincipal> groups = GroupPrincipal.makeSet(Arrays.asList(
      "group1", "group2"));
  private AuthnAuthority adaptor = new AuthnAuthorityImpl();
  private SamlIdentityProvider identityProvider = new SamlIdentityProvider(
      adaptor, metadata, null, 30000);
  private MockHttpContext httpContext = new MockHttpsContext(null, "/samlip");

  @BeforeClass
  public static void initSaml() {
    GsaCommunicationHandler.bootstrapOpenSaml();
  }

  @Test
  public void testNullAdaptor() {
    thrown.expect(NullPointerException.class);
    new SamlIdentityProvider(null, metadata, null, 30000);
  }

  @Test
  public void testNullMetadata() {
    thrown.expect(NullPointerException.class);
    new SamlIdentityProvider(adaptor, null, null, 30000);
  }

  @Test
  public void testNegativeExpiration() {
    thrown.expect(IllegalArgumentException.class);
    new SamlIdentityProvider(adaptor, metadata, null, -5);
  }

  @Test
  public void testZeroExpiration() {
    thrown.expect(IllegalArgumentException.class);
    new SamlIdentityProvider(adaptor, metadata, null, 0);
  }

  @Test
  public void testNormalFlow() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("GET", "/samlip?"
        + "SAMLRequest=fZJdb5swFIbv%2Byss3wPGGSGxAlW2rh9Su0UN3cVuJsc5IZbApj4GNf"
          + "%2B%2BBJIq26Te2dbx8x6fx4vrt7oiHTjU1mQ0DhklYJTdalNm9KW4DWb0Or9aoKwr"
          + "3ohl6%2FfmGV5bQE%2BWiOB8f%2B%2BbNdjW4NbgOq3g5fkxo3vvGxRRBMYf7GQa7q"
          + "0PlXVNWFpbVtCv6whBtU77Q1BLI0tw0TFFnqnqRKXkpk%2FTRvqhxTN441oPO%2BsU"
          + "hLXv%2FmWLZJrOBp5uKHm4yegfmG%2BTlCXzSTqFhM3TL0pylc53G57GkM52fRmu%2"
          + "BnTdQUZ3skI4nmALDwa9ND6jnMWTgMUBTwrGRMwFY%2BF0xn9TsnLWW2Wrr9qMk2ud"
          + "EVaiRmFkDSi8Euvl06PgIRObsQjFfVGsgtXPdTEAOr0F96Ovzujd8A7y3XhwjdMIZH"
          + "0aFXkaR0XJr7MzfnTWWzQoRkufpzenVmk%2BShXDG90l4XPAhyCaH130Ki6UwkfLUY"
          + "nyP8OL6DIzP23%2F%2Flf5Ow%3D%3D", httpContext);
    ex.getRequestHeaders().set("Host", "bruteforce.mtv.corp.google.com:5678");
    identityProvider.getSingleSignOnHandler().handle(ex);
    assertEquals(200, ex.getResponseCode());
    String response = StringEscapeUtils.unescapeHtml(new String(ex.getResponseBytes(), "UTF-8"));
    assertTrue(response.contains("action=\"https://entyo36.hot.corp.google.com/"
        + "security-manager/samlassertionconsumer\""));
    assertTrue(response.contains("SAMLResponse"));
    Matcher m = Pattern.compile(" name=\"SAMLResponse\" value=\"([^\"]*)\"")
        .matcher(response);
    assertTrue(m.find());
    String samlResponse = new String(
        DatatypeConverter.parseBase64Binary(m.group(1)), "UTF-8");
    assertTrue(samlResponse.contains("https://entyo36.hot.corp.google.com/"
        + "security-manager/samlassertionconsumer"));
    assertTrue(samlResponse.contains("user1"));
    assertTrue(
        samlResponse.contains(
            "<saml2:AttributeStatement><saml2:Attribute Name=\"member-of\">"
                + "<saml2:AttributeValue xmlns=\"http://www.w3.org/2001/XMLSchema-instance\">group1"
                + "</saml2:AttributeValue>"
                + "<saml2:AttributeValue xmlns=\"http://www.w3.org/2001/XMLSchema-instance\">group2"
                + "</saml2:AttributeValue>"
                + "</saml2:Attribute></saml2:AttributeStatement>"));
  }

  @Test
  public void testWrongMethod() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("POST", "/samlip",
        httpContext);
    identityProvider.getSingleSignOnHandler().handle(ex);
    assertEquals(405, ex.getResponseCode());
  }

  @Test
  public void testWrongPath() throws Exception {
    MockHttpExchange ex = new MockHttpExchange("GET", "/samlipMORE",
        httpContext);
    identityProvider.getSingleSignOnHandler().handle(ex);
    assertEquals(404, ex.getResponseCode());
  }

  private class AuthnAuthorityImpl implements AuthnAuthority {
    @Override
    public void authenticateUser(HttpExchange ex, Callback callback)
        throws IOException {
      callback.userAuthenticated(ex, new AuthnIdentityImpl());
    }
  }

  private class AuthnIdentityImpl implements AuthnIdentity {
    @Override
    public UserPrincipal getUser() {
      return user;
    }

    @Override
    public Set<GroupPrincipal> getGroups() {
      return groups;
    }

    @Override
    public String getPassword() {
      return null;
    }
  }
}
