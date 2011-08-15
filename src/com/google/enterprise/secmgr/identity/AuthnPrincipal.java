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

package com.google.enterprise.secmgr.identity;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.enterprise.secmgr.common.Stringify;
import com.google.enterprise.secmgr.config.CredentialTypeName;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * A credential that contains the user's name and optionally some domain info.
 *
 * A principal may be stored in an identity's credential set.  Doing so doesn't
 * imply that it's verified; that's true only if the identity has a verification
 * that explicitly includes the principal.
 *
 * @see Verification
 */
@Immutable
@ParametersAreNonnullByDefault
public final class AuthnPrincipal extends AbstractCredential
    implements java.security.Principal {

  @Nonnull private final String name;
  @Nullable private final String activeDirectoryDomain;

  private AuthnPrincipal(String name, @Nullable String activeDirectoryDomain) {
    super();
    Preconditions.checkNotNull(name);
    this.name = name;
    this.activeDirectoryDomain = activeDirectoryDomain;
  }

  /**
   * Makes a principal.
   *
   * @param name The principal's name; may not be null.
   * @param activeDirectoryDomain The ActiveDirectory domain name; may be null.
   * @return A principal with the given components.
   */
  @Nonnull
  public static AuthnPrincipal make(String name, @Nullable String activeDirectoryDomain) {
    return new AuthnPrincipal(name, activeDirectoryDomain);
  }

  /**
   * Makes a principal with no domain.
   *
   * @param name The username.
   * @return A principal with the given username and no domain.
   */
  @Nonnull
  public static AuthnPrincipal make(String name) {
    return new AuthnPrincipal(name, null);
  }

  /**
   * Gets the name associated with this identity.  Usually a "user name" or
   * "login name".
   *
   * @return The identity's name as a string.
   */
  @Nonnull
  public String getName() {
    return name;
  }

  /**
   * Gets the Active Directory Domain name associated with this identity.
   *
   * @return The domain name as a string.
   */
  @Nullable
  public String getActiveDirectoryDomain() {
    return activeDirectoryDomain;
  }

  @Override
  public boolean isPublic() {
    return true;
  }

  @Override
  public CredentialTypeName getTypeName() {
    return CredentialTypeName.PRINCIPAL;
  }

  @Override
  public boolean isVerifiable() {
    return !name.isEmpty();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof AuthnPrincipal)) { return false; }
    AuthnPrincipal principal = (AuthnPrincipal) object;
    return Objects.equal(name, principal.getName())
        && Objects.equal(activeDirectoryDomain, principal.getActiveDirectoryDomain());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, activeDirectoryDomain);
  }

  @Override
  public String toString() {
    return "{principal: " + Stringify.object(joinUsernameDomain(name, activeDirectoryDomain)) + "}";
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(AuthnPrincipal.class,
        ProxyTypeAdapter.make(AuthnPrincipal.class, LocalProxy.class));
  }

  private static final class LocalProxy implements TypeProxy<AuthnPrincipal> {
    String name;
    String activeDirectoryDomain;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(AuthnPrincipal principal) {
      name = principal.getName();
      activeDirectoryDomain = principal.getActiveDirectoryDomain();
    }

    @Override
    public AuthnPrincipal build() {
      return make(name, activeDirectoryDomain);
    }
  }

  /**
   * Parses a string into a principal.
   *
   * @param string The combined username/domain string.
   * @return A principal with the separated username and domain.
   * @see #parseUsernameDomain
   */
  @Nonnull
  public static AuthnPrincipal parse(String string) {
    String[] parsed = parseUsernameDomain(string);
    return make(parsed[0], parsed[1]);
  }

  /**
   * Parses a string into a username/domain pair.
   *
   * @param string The combined username/domain string.
   * @return The username and domain strings as an array.
   */
  @Nonnull
  public static String[] parseUsernameDomain(String string) {
    Preconditions.checkNotNull(string);
    int slash = string.indexOf("\\");
    if (slash == -1) {
      slash = string.indexOf("/");
    }
    if (slash >= 0) {
      return new String[] { string.substring(slash + 1), string.substring(0, slash) };
    }
    int atSign = string.indexOf("@");
    if (atSign >= 0) {
      return new String[] { string.substring(0, atSign), string.substring(atSign + 1) };
    }
    return new String[] { string, null };
  }

  /**
   * Joins a username and domain into a string.
   *
   * @param username The username.
   * @param domain The domain, or {@code null} if none.
   * @return The combined username/domain string.
   */
  @Nonnull
  public static String joinUsernameDomain(String username, @Nullable String domain) {
    Preconditions.checkNotNull(username);
    return (Strings.isNullOrEmpty(domain)) ? username : username + "@" + domain;
  }
}
