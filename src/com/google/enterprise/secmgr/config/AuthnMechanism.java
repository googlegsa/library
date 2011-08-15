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

package com.google.enterprise.secmgr.config;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.enterprise.secmgr.json.TypeAdapters;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.concurrent.ThreadSafe;

/**
 * The configuration of an authentication mechanism.  This is the base class for
 * all mechanisms; individual mechanisms inherit from here.
 */
@ThreadSafe
public abstract class AuthnMechanism {
  private static final int CONFIG_NAME_MAX_LENGTH = 200;
  private static final Pattern CONFIG_NAME_PATTERN
      = Pattern.compile("^[a-zA-Z0-9_][a-zA-Z0-9_-]*$");

  private final String name;
  private final AuthnAuthority authority;

  protected AuthnMechanism() {
    name = null;
    authority = AuthnAuthority.make();
  }

  protected AuthnMechanism(String name) {
    // TODO(cph): eliminate name == null once all callers supply real names.
    // Right now the only callers with name == null are unit tests.
    this.name = name;
    if (name == null) {
      authority = AuthnAuthority.make();
    } else {
      Preconditions.checkArgument(isValidConfigName(name), "Invalid mechanism name: %s", name);
      authority = AuthnAuthority.make(name);
    }
  }

  /**
   * Is a given string a valid name for a mechanism or credential group?  A
   * valid name is between 1 and 200 characters and consists only of ASCII
   * alphanumerics, the underscore character, and the hyphen character.  The
   * first character may not be a hyphen.
   *
   * @param name The string to test for validity.
   * @return True only if the string is valid.
   */
  public static boolean isValidConfigName(String name) {
    return name != null
        && !name.isEmpty()
        && name.length() <= CONFIG_NAME_MAX_LENGTH
        && CONFIG_NAME_PATTERN.matcher(name).matches();
  }

  /**
   * Check a string argument that's allowed to be null.  Disallows empty
   * strings.  May return a modified string; e.g. may run .trim() on the
   * argument.
   *
   * @param string The string to check.
   * @return A checked string that is possibly different from the argument.
   * @throws IllegalArgumentException if the input string is empty.
   */
  protected static String checkStringOrNull(String string) {
    Preconditions.checkArgument(string == null || !string.isEmpty());
    return string;
  }

  /**
   * Check a string argument.  Disallows null and empty strings.  May return a
   * modified string; e.g. may run .trim() on the argument.
   *
   * @param string The string to check.
   * @return A checked string that is possibly different from the argument.
   * @throws IllegalArgumentException if the input string is null or empty.
   */
  protected static String checkString(String string) {
    Preconditions.checkArgument(string != null && !string.isEmpty());
    return string;
  }

  /**
   * Check a URL string argument.  Disallows null and non-well-formed strings.
   * May return a modified string; e.g. may run .trim() on the argument.
   *
   * @param string The URL string to check.
   * @return A checked URL string that is possibly different from the argument.
   * @throws IllegalArgumentException if the input string is null or not well formed.
   */
  protected static String checkUrlString(String string) {
    try {
      return (new URL(checkString(string))).toString();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Check a trust-duration argument.
   *
   * @param trustDuration The trust-duration argument to check.
   * @return A checked value.
   * @throws IllegalArgumentException if the argument is invalid.
   */
  protected static long checkTrustDuration(long trustDuration) {
    Preconditions.checkArgument(trustDuration >= 0);
    return trustDuration;
  }

  /**
   * Get the type name of the mechanism.
   *
   * @return The type name, never null or empty.
   */
  public abstract String getTypeName();

  /**
   * @return An immutable list of the transformations this mechanism implements.
   */
  public abstract List<CredentialTransform> getCredentialTransforms();

  /**
   * Can this mechanism use ULF credentials?
   */
  public boolean canUseUlfCredentials() {
    return Iterables.any(getCredentialTransforms(),
        new Predicate<CredentialTransform>() {
          public boolean apply(CredentialTransform credentialTransform) {
            CredentialTypeSet inputs = credentialTransform.getInputs();
            return !inputs.getAreVerified()
                && inputs.getElements().containsAll(
                    CredentialTypeSet.PRINCIPAL_AND_PASSWORD.getElements());
          }
        });
  }

  /**
   * Return a copy of the current mechanism, with a given name.
   *
   * @param name The new name.
   * @return A copy of the current mechanism with the new name.
   */
  public abstract AuthnMechanism copyWithNewName(String name);

  /**
   * @return This mechanism's name.
   */
  public String getName() {
    return name;
  }

  /**
   * @return This mechanism's authority.
   */
  public AuthnAuthority getAuthority() {
    return authority;
  }

  public static Function<AuthnMechanism, AuthnAuthority> getAuthorityFunction() {
    return AUTHORITY_FUNCTION;
  }

  private static final Function<AuthnMechanism, AuthnAuthority> AUTHORITY_FUNCTION
      = new Function<AuthnMechanism, AuthnAuthority>() {
          @Override
          public AuthnAuthority apply(AuthnMechanism mechanism) {
            return mechanism.getAuthority();
          }
        };

  /**
   * A sample URL to GET for credential verification.
   *
   * @return The mechanism's sample URL as a string, or null if none.
   */
  public String getSampleUrl() {
    throw new UnsupportedOperationException();
  }

  /**
   * For how many milliseconds does this mechanism trust newly-verified
   * credentials?
   *
   * @return The number of milliseconds, always a non-negative number.
   */
  public long getTrustDuration() {
    throw new UnsupportedOperationException();
  }

  protected boolean equals(AuthnMechanism other) {
    return getName() == null
        || other.getName() == null
        || getName().equalsIgnoreCase(other.getName());
  }

  protected int hashCode(Object... objects) {
    Object[] copy = new Object[objects.length + 1];
    copy[0] = (getName() != null)
        ? getName().toLowerCase(Locale.US)
        : null;
    for (int i = 0; i < objects.length; i += 1) {
      copy[i + 1] = objects[i];
    }
    return Objects.hashCode(copy);
  }

  @Override
  public String toString() {
    return ConfigSingleton.getGson().toJson(this);
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(AuthnMechanism.class,
        TypeAdapters.dispatch(
            ImmutableList.of(
                AuthnMechBasic.class,
                AuthnMechClient.class,
                AuthnMechConnector.class,
                AuthnMechForm.class,
                AuthnMechKerberos.class,
                AuthnMechLdap.class,
                AuthnMechNtlm.class,
                AuthnMechSaml.class,
                AuthnMechSampleUrl.class)));
  }

  /** A base class for type proxies of sub-classes. */
  protected abstract static class MechanismProxy<T extends AuthnMechanism>
      implements TypeProxy<T> {
    public String name;

    protected MechanismProxy() {
    }

    protected MechanismProxy(AuthnMechanism mechanism) {
      name = mechanism.getName();
    }
  }
}
