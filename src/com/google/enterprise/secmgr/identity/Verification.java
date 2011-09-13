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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.enterprise.secmgr.common.SecurityManagerUtil;
import com.google.enterprise.secmgr.common.Stringify;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeAdapters;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.joda.time.DateTimeUtils;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Arrays;
import java.util.Collection;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * The structure that holds results from credential verification.
 */
@Immutable
@ParametersAreNonnullByDefault
public final class Verification {

  /**
   * A value for a verification expiration time that means the verification
   * doesn't expire.
   */
  public static final long NEVER_EXPIRES = -1;

  /**
   * A value for a verification expiration time that means the verification
   * expires when the current servlet request is finished.
   */
  public static final long EXPIRES_AFTER_REQUEST = -2;

  /**
   * Used internally to limit values accepted when making a verification.
   */
  public static final long MINIMUM_EXPIRATION_VALUE = -2;

  @Nonnull private final VerificationStatus status;
  private final long expirationTime;
  @Nonnull private final ImmutableSet<Credential> credentials;

  private Verification(VerificationStatus status, long expirationTime,
      Iterable<? extends Credential> credentials) {
    Preconditions.checkNotNull(status);
    Preconditions.checkArgument(expirationTime >= MINIMUM_EXPIRATION_VALUE);
    Preconditions.checkNotNull(credentials);
    this.status = status;
    this.expirationTime = expirationTime;
    this.credentials = ImmutableSet.copyOf(credentials);
  }

  /**
   * Gets a verification object with VERIFIED status and given credentials.
   *
   * @param expirationTime The expiration time of this verification.
   * @param credentials The credentials that the status applies to.
   * @return A verification with the given components.
   */
  public static Verification verified(long expirationTime,
      Iterable<? extends Credential> credentials) {
    return new Verification(VerificationStatus.VERIFIED, expirationTime, credentials);
  }

  /**
   * Gets a verification object with VERIFIED status and given credentials.
   *
   * @param expirationTime The expiration time of this verification.
   * @param credentials The credentials that the status applies to.
   * @return A verification with the given components.
   */
  public static Verification verified(long expirationTime, Credential... credentials) {
    return verified(expirationTime, Arrays.asList(credentials));
  }

  /**
   * Gets a verification object with REFUTED status and given credentials.
   *
   * @param credentials The credentials that the status applies to.
   * @return A verification with the given components.
   */
  public static Verification refuted(Iterable<? extends Credential> credentials) {
    return new Verification(VerificationStatus.REFUTED, EXPIRES_AFTER_REQUEST, credentials);
  }

  /**
   * Gets a verification object with REFUTED status and no credentials.
   *
   * @return A verification with the given components.
   */
  public static Verification refuted() {
    return refuted(ImmutableSet.<Credential>of());
  }

  @VisibleForTesting
  public static Verification make(VerificationStatus status, long expirationTime,
      Credential... credentials) {
    return new Verification(status, expirationTime, Arrays.asList(credentials));
  }

  /**
   * Gets the status of this verification.
   *
   * @return The verification's status.
   */
  @Nonnull
  public VerificationStatus getStatus() {
    return status;
  }

  /**
   * Are the credentials valid?
   *
   * @return True if the credentials were verified and found valid.
   */
  public boolean isVerified() {
    return status == VerificationStatus.VERIFIED;
  }

  /**
   * Are the credentials invalid?
   *
   * @return True if the credentials were verified and found invalid.
   */
  public boolean isRefuted() {
    return status == VerificationStatus.REFUTED;
  }

  /**
   * Is the verification status indeterminate?
   *
   * @return True if the credentials were verified with indeterminate result.
   */
  public boolean isIndeterminate() {
    return status == VerificationStatus.INDETERMINATE;
  }

  /**
   * Gets the expiration time for this verification.  Positive is a time
   * comparable to {@link System#currentTimeMillis}, zero means "already
   * expired", negative means "never expires".
   *
   * @return The expiration time.
   */
  public long getExpirationTime() {
    return expirationTime;
  }

  /**
   * Has the verification expired based on the given time?
   *
   * @param now The reference time.
   * @return True if the verification has expired.
   */
  public boolean hasExpired(long now) {
    return expirationTime >= 0
        && !SecurityManagerUtil.isRemoteOnOrAfterTimeValid(expirationTime, now);
  }

  /**
   * Compares the expiration time of this verification with that of a given
   * verification.  The result is a positive number if the expiration time of
   * this verification is later than that of the other; a negative number if
   * this time is earlier than that of the other; or zero if they are equal.
   *
   * @param other The verification to compare to.
   * @return The comparison result.
   */
  @CheckReturnValue
  public int compareExpirationTimes(Verification other) {
    return compareExpirationTimes(getExpirationTime(), other.getExpirationTime());
  }

  /**
   * Compares two expiration times.
   *
   * @param t1 The first expiration time.
   * @param t2 The second expiration time.
   * @return A negative number if {@code t1<t2}, a positive number if
   *     {@code t1>t2}, or zero if {@code t1==t2}.
   * @throws IllegalArgumentException if either of the arguments is not
   *     recognized as an expiration time.
   */
  @CheckReturnValue
  public static int compareExpirationTimes(long t1, long t2) {
    Preconditions.checkArgument(t1 >= MINIMUM_EXPIRATION_VALUE);
    Preconditions.checkArgument(t2 >= MINIMUM_EXPIRATION_VALUE);
    if (t1 == t2) { return 0; }
    if (t1 == NEVER_EXPIRES) { return 1; }
    if (t2 == NEVER_EXPIRES) { return -1; }
    if (t1 == 0) { return -1; }
    if (t2 == 0) { return 1; }
    if (t1 == EXPIRES_AFTER_REQUEST) { return -1; }
    if (t2 == EXPIRES_AFTER_REQUEST) { return 1; }
    return (t1 > t2) ? 1 : -1;
  }

  /**
   * Gets the minimum expiration time for some given verifications.  Ignores any
   * verifications that don't satisfy {@link #isVerified}.
   *
   * @param verifications Some verifications to test.
   * @return The earliest expiration time for those verifications.  Returns
   *     {@link #NEVER_EXPIRES} if there are no verifications.
   */
  @CheckReturnValue
  public static long minimumExpirationTime(Iterable<Verification> verifications) {
    long expirationTime = NEVER_EXPIRES;
    for (Verification verification : verifications) {
      if (verification.isVerified()
          && compareExpirationTimes(verification.getExpirationTime(), expirationTime) < 0) {
        expirationTime = verification.getExpirationTime();
      }
    }
    return expirationTime;
  }

  /**
   * Gets the maximum expiration time for some given verifications.  Ignores any
   * verifications that don't satisfy {@link #isVerified}.
   *
   * @param verifications Some verifications to test.
   * @return The latest expiration time for those verifications.  Returns
   *     {@code 0} if there are no verifications.
   */
  @CheckReturnValue
  public static long maximumExpirationTime(Iterable<Verification> verifications) {
    long expirationTime = 0;
    for (Verification verification : verifications) {
      if (verification.isVerified()
          && compareExpirationTimes(verification.getExpirationTime(), expirationTime) > 0) {
        expirationTime = verification.getExpirationTime();
      }
    }
    return expirationTime;
  }

  /**
   * Gets the credentials that were verified.
   *
   * @return An immutable set of the credentials that underwent verification.
   */
  @Nonnull
  public ImmutableSet<Credential> getCredentials() {
    return credentials;
  }

  /**
   * Does this verification contain a given credential?
   *
   * @param credential A credential to test for.
   * @return True if the given credential is contained.
   */
  public boolean containsCredential(Credential credential) {
    for (Credential v : credentials) {
      if (v.equals(credential)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Does this verification contain all of some given credentials?
   *
   * @param credentials Some credentials to test for.
   * @return True if all of the given credentials are contained.
   */
  public boolean containsAllCredentials(Iterable<? extends Credential> credentials) {
    for (Credential credential : credentials) {
      if (!containsCredential(credential)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Does this verification contain any of some given credentials?
   *
   * @param credentials Some credentials to test for.
   * @return True if any of the given credentials are contained.
   */
  public boolean containsAnyCredential(Iterable<? extends Credential> credentials) {
    for (Credential credential : credentials) {
      if (containsCredential(credential)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof Verification)) { return false; }
    Verification verification = (Verification) object;
    return Objects.equal(status, verification.getStatus())
        && Objects.equal(credentials, verification.getCredentials());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(status, credentials);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("{Verification: status=");
    builder.append(status);
    builder.append("; ");
    if (expirationTime == NEVER_EXPIRES) {
      builder.append("never expires");
    } else if (expirationTime == EXPIRES_AFTER_REQUEST) {
      builder.append("expires after request");
    } else {
      builder.append("expires at ");
      builder.append(ISO8601_FORMAT.print(expirationTime));
    }
    if (!credentials.isEmpty()) {
      builder.append("; credentials ");
      builder.append(Stringify.objects(credentials));
    }
    builder.append("}");
    return builder.toString();
  }

  private static final DateTimeFormatter ISO8601_FORMAT = ISODateTimeFormat.dateTime();

  /**
   * Gets a predicate that compares a verification's status to a given value.
   *
   * @param status A status to test for.
   * @return The corresponding predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> getStatusPredicate(VerificationStatus status) {
    return Predicates.compose(Predicates.equalTo(status), GET_STATUS_FUNCTION);
  }

  private static final Function<Verification, VerificationStatus> GET_STATUS_FUNCTION =
      new Function<Verification, VerificationStatus>() {
        @Override
        public VerificationStatus apply(Verification v) {
          return v.getStatus();
        }
      };

  /**
   * Gets a predicate that tests whether a given verification has status
   * {@link VerificationStatus#VERIFIED}.
   *
   * @return The predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> isVerifiedPredicate() {
    return IS_VERIFIED_PREDICATE;
  }

  private static final Predicate<Verification> IS_VERIFIED_PREDICATE
      = getStatusPredicate(VerificationStatus.VERIFIED);

  /**
   * Gets a predicate that tests whether a given verification has status
   * {@link VerificationStatus#REFUTED}.
   *
   * @return The predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> isRefutedPredicate() {
    return IS_REFUTED_PREDICATE;
  }

  private static final Predicate<Verification> IS_REFUTED_PREDICATE
      = getStatusPredicate(VerificationStatus.REFUTED);

  /**
   * Gets a predicate that tests whether a verification is expired
   *
   * @param time The reference time for the expiration.
   * @return The corresponding predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> getExpirationPredicate(final long time) {
    return new Predicate<Verification>() {
      @Override
      public boolean apply(Verification v) {
        return v.hasExpired(time);
      }
    };
  }

  /**
   * Gets a predicate that tests whether a verification should expire after the
   * current request.
   *
   * @return The predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> getRequestExpirationPredicate() {
    return REQUEST_EXPIRATION_PREDICATE;
  }

  private static final Function<Verification, Long> GET_EXPIRATION_TIME_FUNCTION =
      new Function<Verification, Long>() {
        @Override
        public Long apply(Verification v) {
          return v.getExpirationTime();
        }
      };

  private static final Predicate<Verification> REQUEST_EXPIRATION_PREDICATE =
      Predicates.compose(Predicates.equalTo(EXPIRES_AFTER_REQUEST), GET_EXPIRATION_TIME_FUNCTION);

  /**
   * Gets a predicate that invokes the {@link #containsCredential} method on a
   * given verification.
   *
   * @param credential The credential to test for.
   * @return The corresponding predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> getContainsCredentialPredicate(
      final Credential credential) {
    Preconditions.checkNotNull(credential);
    return new Predicate<Verification>() {
      @Override
      public boolean apply(Verification verification) {
        return verification.containsCredential(credential);
      }
    };
  }

  /**
   * Gets a predicate that invokes the {@link #containsAllCredentials} method on
   * a given verification.
   *
   * @param credentials The credentials to test for.
   * @return The corresponding predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> getContainsAllCredentialsPredicate(
      final Iterable<? extends Credential> credentials) {
    Preconditions.checkNotNull(credentials);
    return new Predicate<Verification>() {
      @Override
      public boolean apply(Verification verification) {
        return verification.containsAllCredentials(credentials);
      }
    };
  }

  /**
   * Gets a predicate that invokes the {@link #containsAllCredentials} method on
   * a given verification.
   *
   * @param credentials The credentials to test for.
   * @return The corresponding predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> getContainsAllCredentialsPredicate(
      Credential... credentials) {
    return getContainsAllCredentialsPredicate(Arrays.asList(credentials));
  }

  /**
   * Gets a predicate that invokes the {@link #containsAnyCredential} method on
   * a given verification.
   *
   * @param credentials The credentials to test for.
   * @return The corresponding predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> getContainsAnyCredentialPredicate(
      final Iterable<? extends Credential> credentials) {
    return new Predicate<Verification>() {
      public boolean apply(Verification v) {
        return v.containsAnyCredential(credentials);
      }
    };
  }

  /**
   * Gets a predicate that invokes the {@link #containsAnyCredential} method on
   * a given verification.
   *
   * @param credentials The credentials to test for.
   * @return The corresponding predicate.
   */
  @CheckReturnValue
  @Nonnull
  public static Predicate<Verification> getContainsAnyCredentialPredicate(
      Credential... credentials) {
    return getContainsAnyCredentialPredicate(Arrays.asList(credentials));
  }

  /**
   * Determines the verification status from a set of verifications.
   *
   * @param verifications The set of verifications to check.
   * @return The aggregate status of the verifications.
   */
  @CheckReturnValue
  @Nonnull
  public static VerificationStatus getStatus(Iterable<Verification> verifications) {
    VerificationStatus result = VerificationStatus.INDETERMINATE;
    for (Verification verification : verifications) {
      if (verification.isRefuted()) {
        return VerificationStatus.REFUTED;
      }
      if (verification.isVerified()) {
        result = VerificationStatus.VERIFIED;
      }
    }
    return result;
  }

  /**
   * Determines the joint verification status for some given credentials from a
   * set of verifications.
   *
   * @param verifications The set of verifications to check.
   * @param credentials The set of credentials to look for.
   * @return The joint verification status of the relevant verifications.
   */
  @CheckReturnValue
  @Nonnull
  public static VerificationStatus getStatus(Iterable<Verification> verifications,
      Iterable<? extends Credential> credentials) {
    return getStatus(
        Iterables.filter(verifications,
            getContainsAllCredentialsPredicate(credentials)));
  }

  /**
   * Determines whether one of a set of verifications has VERIFIED state.
   *
   * @param verifications The set of verifications to check.
   * @return True if there's at least one VERIFIED element in the set.
   */
  public static boolean isVerified(Iterable<Verification> verifications) {
    return getStatus(verifications) == VerificationStatus.VERIFIED;
  }

  /**
   * Determines whether one of a set of verifications has REFUTED state.
   *
   * @param verifications The set of verifications to check.
   * @return True if there's at least one REFUTED element in the set.
   */
  public static boolean isRefuted(Iterable<Verification> verifications) {
    return getStatus(verifications) == VerificationStatus.REFUTED;
  }

  /**
   * Determines whether none of a set of verifications has VERIFIED or REFUTED
   * state.
   *
   * @param verifications The set of verifications to check.
   * @return False if there's at least one VERIFIED or REFUTED element in the
   *     set.
   */
  public static boolean isIndeterminate(Iterable<Verification> verifications) {
    return getStatus(verifications) == VerificationStatus.INDETERMINATE;
  }

  /**
   * Determines whether one of a set of verifications has VERIFIED state and
   * contains all of the given credentials.
   *
   * @param verifications The set of verifications to check.
   * @param credentials The set of credentials to look for.
   * @return True if there's at least one VERIFIED element in the containing all
   *     of the credentials.
   */
  public static boolean isVerified(Iterable<Verification> verifications,
      Iterable<Credential> credentials) {
    return getStatus(verifications, credentials) == VerificationStatus.VERIFIED;
  }

  /**
   * Determines whether one of a set of verifications has REFUTED state and
   * contains all of the given credentials.
   *
   * @param verifications The set of verifications to check.
   * @param credentials The set of credentials to look for.
   * @return True if there's at least one REFUTED element in the containing all
   *     of the credentials.
   */
  public static boolean isRefuted(Iterable<Verification> verifications,
      Iterable<Credential> credentials) {
    return getStatus(verifications, credentials) == VerificationStatus.REFUTED;
  }

  /**
   * Gets the newest of a set of verifications; in other words, the verification
   * with the latest expiration time.
   *
   * @param verifications The verifications to test.
   * @return The newest of the given verifications, or {@code null} if the given
   *     set is empty.
   */
  @CheckReturnValue
  @Nonnull
  public static Verification newestOf(Iterable<Verification> verifications) {
    Verification result = null;
    for (Verification verification : verifications) {
      if (result == null || verification.compareExpirationTimes(result) > 0) {
        result = verification;
      }
    }
    return result;
  }

  /**
   * Removes any expired verifications from a given collection.
   *
   * @param verifications A collection of verifications to process.
   * @param timeStamp A reference time for determining expiration.
   */
  public static void expireVerifications(Collection<Verification> verifications,
      @Nonnegative long timeStamp) {
    Iterables.removeIf(verifications, getExpirationPredicate(timeStamp));
  }

  /**
   * Removes any expired verifications from a given collection.  Uses the
   * current time as a reference.
   *
   * @param verifications A collection of verifications to process.
   */
  public static void expireVerifications(Collection<Verification> verifications) {
    expireVerifications(verifications, DateTimeUtils.currentTimeMillis());
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(Verification.class,
        ProxyTypeAdapter.make(Verification.class, LocalProxy.class));
    builder.registerTypeAdapter(new TypeToken<ImmutableSet<Credential>>() {}.getType(),
        TypeAdapters.immutableSet());
  }

  private static final class LocalProxy implements TypeProxy<Verification> {
    VerificationStatus status;
    long expirationTime;
    ImmutableSet<Credential> credentials;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(Verification verification) {
      status = verification.getStatus();
      expirationTime = verification.getExpirationTime();
      credentials = verification.getCredentials();
    }

    @Override
    public Verification build() {
      return new Verification(status, expirationTime, credentials);
    }
  }
}
