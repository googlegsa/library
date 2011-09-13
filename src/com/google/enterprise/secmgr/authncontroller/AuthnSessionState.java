// Copyright 2011 Google Inc.
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

package com.google.enterprise.secmgr.authncontroller;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.enterprise.secmgr.common.Chain;
import com.google.enterprise.secmgr.common.CookieStore;
import com.google.enterprise.secmgr.common.GCookie;
import com.google.enterprise.secmgr.common.SecurityManagerUtil;
import com.google.enterprise.secmgr.config.AuthnAuthority;
import com.google.enterprise.secmgr.config.AuthnMechanism;
import com.google.enterprise.secmgr.config.CredentialGroup;
import com.google.enterprise.secmgr.identity.AbstractCredential;
import com.google.enterprise.secmgr.identity.Credential;
import com.google.enterprise.secmgr.identity.Verification;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A representation of the state stored in an authentication session.  This is
 * not the AuthnState that's used to keep track of the what the controller is
 * doing; this is the credentials gathered by the controller.
 */
@Immutable
@ParametersAreNonnullByDefault
public final class AuthnSessionState {

  /**
   * The possible instruction operations supported here.
   */
  public static enum Operation {
    ADD_COOKIE(GCookie.class),
    ADD_CREDENTIAL(Credential.class),
    ADD_VERIFICATION(Verification.class),
    REMOVE_COOKIE(GCookie.class),
    REMOVE_CREDENTIAL(Credential.class),
    REMOVE_VERIFICATION(Verification.class);

    @Nonnull private final Class<?> operandClass;

    private Operation(Class<?> operandClass) {
      Preconditions.checkNotNull(operandClass);
      this.operandClass = operandClass;
    }

    @CheckReturnValue
    @Nonnull
    public Class<?> getOperandClass() {
      return operandClass;
    }
  }

  private static final AuthnSessionState EMPTY = new AuthnSessionState(Chain.<Instruction>empty());

  @Nonnull private final Chain<Instruction> instructions;

  private AuthnSessionState(Chain<Instruction> instructions) {
    Preconditions.checkNotNull(instructions);
    this.instructions = instructions;
  }

  /**
   * Gets an empty session state.
   */
  @CheckReturnValue
  @Nonnull
  public static AuthnSessionState empty() {
    return EMPTY;
  }

  /**
   * Gets a new session state with only the given verification.
   *
   * @param authority The authority that performed the verification process.
   * @param verification The verification that the state will hold.
   * @return A new state with that verification.
   */
  @CheckReturnValue
  @Nonnull
  public static AuthnSessionState of(AuthnAuthority authority, Verification verification) {
    return empty().addVerification(authority, verification);
  }

  @VisibleForTesting
  static AuthnSessionState of(Iterable<Instruction> instructions) {
    return new AuthnSessionState(Chain.copyOf(instructions));
  }

  @VisibleForTesting
  List<Instruction> getInstructions() {
    return instructions.toList();
  }

  /**
   * Is this state object empty?
   *
   * @return True only if there are no elements in this state.
   */
  @CheckReturnValue
  public boolean isEmpty() {
    return instructions.isEmpty();
  }

  /**
   * Unit testing relies on this; it's not used in production.
   */
  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof AuthnSessionState)) { return false; }
    AuthnSessionState other = (AuthnSessionState) object;
    return Objects.equal(instructions, other.instructions);
  }

  /**
   * Unit testing relies on this; it's not used in production.
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(instructions);
  }

  /**
   * Unit testing uses this for failure reporting; it's not used in production.
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (Instruction instruction : getInstructions()) {
      builder.append("  ");
      builder.append(instruction);
      builder.append("\n");
    }
    return builder.toString();
  }

  /**
   * Appends some state to this state.
   *
   * @param delta The state to append
   * @return A new state with that state appended.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState add(AuthnSessionState delta) {
    return new AuthnSessionState(instructions.addAll(delta.getInstructions()));
  }

  /**
   * Gets a session state that represents the delta between this state and a
   * given one.  For a given ancestor state {@code A}, it is always true that
   * {@code this.equals(A.add(this.getDelta(A)))}.
   *
   * @param ancestor The ancestor state to use as a reference.
   * @return A new state with instructions that have been added since the
   *     ancestor.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState getDelta(AuthnSessionState ancestor) {
    return AuthnSessionState.of(instructions.toList(ancestor.instructions));
  }

  @CheckReturnValue
  private AuthnSessionState addInstruction(Instruction instruction) {
    Preconditions.checkNotNull(instruction);
    return new AuthnSessionState(instructions.add(instruction));
  }

  /**
   * Adds a cookie to this state.
   *
   * @param authority The authority from which the cookie was received.
   * @param cookie The cookie to be added.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState addCookie(AuthnAuthority authority, GCookie cookie) {
    return addInstruction(Instruction.make(Operation.ADD_COOKIE, authority, cookie));
  }

  /**
   * Adds some cookies to this state.
   *
   * @param authority The authority from which the cookie was received.
   * @param cookies The cookies to be added.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState addCookies(AuthnAuthority authority, Iterable<GCookie> cookies) {
    AuthnSessionState state = this;
    for (GCookie cookie : cookies) {
      state = state.addCookie(authority, cookie);
    }
    return state;
  }

  /**
   * Removes a cookie from this state.
   *
   * @param authority The authority from which the cookie was originally received.
   * @param cookie The cookie to be removed.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState removeCookie(AuthnAuthority authority, GCookie cookie) {
    return addInstruction(Instruction.make(Operation.REMOVE_COOKIE, authority, cookie));
  }

  /**
   * Removes some cookies from this state.
   *
   * @param authority The authority from which the cookie was received.
   * @param cookies The cookies to be removed.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState removeCookies(AuthnAuthority authority, Iterable<GCookie> cookies) {
    AuthnSessionState state = this;
    for (GCookie cookie : cookies) {
      state = state.removeCookie(authority, cookie);
    }
    return state;
  }

  /**
   * Adds a credential to this state.
   *
   * @param authority The authority from which the credential was received.
   * @param credential The credential to be added.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState addCredential(AuthnAuthority authority, Credential credential) {
    return addInstruction(Instruction.make(Operation.ADD_CREDENTIAL, authority, credential));
  }

  /**
   * Adds some credentials to this state.
   *
   * @param authority The authority from which the cookie was received.
   * @param credentials The credentials to be added.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState addCredentials(AuthnAuthority authority,
      Iterable<Credential> credentials) {
    AuthnSessionState state = this;
    for (Credential credential : credentials) {
      state = state.addCredential(authority, credential);
    }
    return state;
  }

  /**
   * Removes a credential from this state.
   *
   * @param authority The authority from which the credential was originally received.
   * @param credential The credential to be removed.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState removeCredential(AuthnAuthority authority,
      Credential credential) {
    return addInstruction(Instruction.make(Operation.REMOVE_CREDENTIAL, authority, credential));
  }

  /**
   * Adds a verification to this state.
   *
   * @param authority The authority that performed the verification process.
   * @param verification The verification to be added.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState addVerification(AuthnAuthority authority, Verification verification) {
    return addInstruction(Instruction.make(Operation.ADD_VERIFICATION, authority, verification));
  }

  /**
   * Adds some verifications to this state.
   *
   * @param authority The authority that performed the verification process.
   * @param verifications The verifications to be added.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState addVerifications(AuthnAuthority authority,
      Iterable<Verification> verifications) {
    AuthnSessionState state = this;
    for (Verification verification : verifications) {
      state = state.addVerification(authority, verification);
    }
    return state;
  }

  /**
   * Removes a verification from this state.
   *
   * @param authority The authority that performed the verification process.
   * @param verification The verification to be removed.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState removeVerification(AuthnAuthority authority, Verification verification) {
    return addInstruction(Instruction.make(Operation.REMOVE_VERIFICATION, authority, verification));
  }

  /**
   * Removes some verifications from this state.
   *
   * @param authority The authority that performed the verification process.
   * @param verifications The verifications to be removed.
   */
  @CheckReturnValue
  @Nonnull
  public AuthnSessionState removeVerifications(AuthnAuthority authority,
      Iterable<Verification> verifications) {
    AuthnSessionState state = this;
    for (Verification verification : verifications) {
      state = state.removeVerification(authority, verification);
    }
    return state;
  }

  /**
   * Computes a summary of this state's contents.
   *
   * @param credentialGroups The credential groups associated with this session.
   * @return An immutable summary of the state's contents.
   */
  @CheckReturnValue
  @Nonnull
  public Summary computeSummary(Iterable<CredentialGroup> credentialGroups) {
    return evolveSummary(new Evolver(credentialGroups));
  }

  /**
   * Computes a summary by extending a given summary with this state's contents.
   *
   * @param summary The summary to be extended.
   * @return An immutable extended summary.
   */
  @CheckReturnValue
  @Nonnull
  public Summary evolveSummary(Summary summary) {
    return evolveSummary(summary.evolve());
  }

  private Summary evolveSummary(Evolver evolver) {
    for (Instruction instruction : getInstructions()) {
      AuthnAuthority authority = instruction.getAuthority();
      Object operand = instruction.getOperand();
      switch (instruction.getOperation()) {
        case ADD_COOKIE:
          evolver.addCookie(authority, (GCookie) operand);
          break;
        case REMOVE_COOKIE:
          evolver.removeCookie(authority, (GCookie) operand);
          break;
        case ADD_CREDENTIAL:
          evolver.addCredential(authority, (Credential) operand);
          break;
        case REMOVE_CREDENTIAL:
          evolver.removeCredential(authority, (Credential) operand);
          break;
        case ADD_VERIFICATION:
          evolver.addVerification(authority, (Verification) operand);
          break;
        case REMOVE_VERIFICATION:
          evolver.removeVerification(authority, (Verification) operand);
          break;
        default:
          throw new IllegalStateException("Unknown instruction operation: "
              + instruction.getOperation());
      }
    }
    return evolver.getSummary();
  }

  @NotThreadSafe
  @ParametersAreNonnullByDefault
  private static final class Evolver {
    @Nonnull final ImmutableList<CredentialGroup> credentialGroups;
    @Nonnull final Map<AuthnAuthority, CookieStore> cookiesMap;
    @Nonnull final SetMultimap<AuthnAuthority, Credential> credentialsMap;
    @Nonnull final Map<AuthnAuthority, Verification> verificationsMap;

    Evolver(Iterable<CredentialGroup> credentialGroups) {
      this.credentialGroups = ImmutableList.copyOf(credentialGroups);
      cookiesMap = Maps.<AuthnAuthority, CookieStore>newHashMap();
      credentialsMap = HashMultimap.<AuthnAuthority, Credential>create();
      verificationsMap = Maps.<AuthnAuthority, Verification>newHashMap();
    }

    Evolver(ImmutableList<CredentialGroup> credentialGroups,
        ImmutableMap<AuthnAuthority, ImmutableSet<GCookie>> cookiesMap,
        ImmutableSetMultimap<AuthnAuthority, Credential> credentialsMap,
        ImmutableMap<AuthnAuthority, Verification> verificationsMap) {
      this.credentialGroups = credentialGroups;
      this.cookiesMap = thawCookiesMap(cookiesMap);
      this.credentialsMap = HashMultimap.create(credentialsMap);
      this.verificationsMap = Maps.newHashMap(verificationsMap);
    }

    static Map<AuthnAuthority, CookieStore> thawCookiesMap(
        ImmutableMap<AuthnAuthority, ImmutableSet<GCookie>> cookiesMap) {
      Map<AuthnAuthority, CookieStore> result = Maps.newHashMap();
      for (Map.Entry<AuthnAuthority, ImmutableSet<GCookie>> entry : cookiesMap.entrySet()) {
        CookieStore cookies = GCookie.makeStore();
        cookies.addAll(entry.getValue());
        result.put(entry.getKey(), cookies);
      }
      return result;
    }

    Summary getSummary() {
      return new Summary(credentialGroups, freezeCookiesMap(),
          ImmutableSetMultimap.copyOf(credentialsMap), ImmutableMap.copyOf(verificationsMap));
    }

    ImmutableMap<AuthnAuthority, ImmutableSet<GCookie>> freezeCookiesMap() {
      ImmutableMap.Builder<AuthnAuthority, ImmutableSet<GCookie>> builder = ImmutableMap.builder();
      for (Map.Entry<AuthnAuthority, CookieStore> entry : cookiesMap.entrySet()) {
        builder.put(entry.getKey(), ImmutableSet.copyOf(entry.getValue()));
      }
      return builder.build();
    }

    void addCookie(AuthnAuthority authority, GCookie cookie) {
      getCookies(authority).add(cookie);
    }

    void removeCookie(AuthnAuthority authority, GCookie cookie) {
      getCookies(authority).remove(cookie);
    }

    CookieStore getCookies(AuthnAuthority authority) {
      CookieStore cookies = cookiesMap.get(authority);
      if (cookies == null) {
        cookies = GCookie.makeStore();
        cookiesMap.put(authority, cookies);
      }
      return cookies;
    }

    void addCredential(AuthnAuthority authority, Credential credential) {
      AuthnAuthority cgAuthority = getCredGroupAuthority(authority);
      if (!credentialsMap.containsEntry(cgAuthority, credential)) {
        Iterable<Credential> removed
            = SecurityManagerUtil.removeInPlace(credentialsMap.get(cgAuthority),
                AbstractCredential.getTypePredicate(credential.getClass()));
        for (AuthnAuthority mechAuthority : getMechAuthorities(cgAuthority)) {
          Verification verification = verificationsMap.get(mechAuthority);
          if (verification != null && verification.containsAnyCredential(removed)) {
            verificationsMap.remove(mechAuthority);
          }
        }
        credentialsMap.put(cgAuthority, credential);
      }
    }

    void removeCredential(AuthnAuthority authority, Credential credential) {
      AuthnAuthority cgAuthority = getCredGroupAuthority(authority);
      if (credentialsMap.containsEntry(cgAuthority, credential)) {
        for (AuthnAuthority mechAuthority : getMechAuthorities(cgAuthority)) {
          Verification verification = verificationsMap.get(mechAuthority);
          if (verification != null && verification.containsCredential(credential)) {
            verificationsMap.remove(mechAuthority);
          }
        }
        credentialsMap.remove(cgAuthority, credential);
      }
    }

    void addVerification(AuthnAuthority authority, Verification verification) {
      verificationsMap.put(authority, verification);
      for (Credential credential : verification.getCredentials()) {
        addCredential(authority, credential);
      }
    }

    void removeVerification(AuthnAuthority authority, Verification verification) {
      if (verification.equals(verificationsMap.get(authority))) {
        verificationsMap.remove(authority);
      }
    }

    AuthnAuthority getCredGroupAuthority(AuthnAuthority authority) {
      for (CredentialGroup credentialGroup : credentialGroups) {
        for (AuthnMechanism mechanism : credentialGroup.getMechanisms()) {
          if (authority.equals(mechanism.getAuthority())) {
            return credentialGroup.getAuthority();
          }
        }
      }
      return authority;
    }

    Iterable<AuthnAuthority> getMechAuthorities(AuthnAuthority authority) {
      for (CredentialGroup credentialGroup : credentialGroups) {
        if (authority.equals(credentialGroup.getAuthority())) {
          return Iterables.transform(credentialGroup.getMechanisms(),
              AuthnMechanism.getAuthorityFunction());
        }
      }
      return ImmutableList.of(authority);
    }
  }

  /**
   * The result of {@link #computeSummary}, this structure contains a summary of
   * the session state as computed by executing the session-state's
   * instructions.
   */
  @Immutable
  @ParametersAreNonnullByDefault
  public static final class Summary {
    @Nonnull private final ImmutableList<CredentialGroup> credentialGroups;
    @Nonnull private final ImmutableMap<AuthnAuthority, ImmutableSet<GCookie>> cookiesMap;
    @Nonnull private final ImmutableSetMultimap<AuthnAuthority, Credential> credentialsMap;
    @Nonnull private final ImmutableMap<AuthnAuthority, Verification> verificationsMap;

    private Summary(ImmutableList<CredentialGroup> credentialGroups,
        ImmutableMap<AuthnAuthority, ImmutableSet<GCookie>> cookiesMap,
        ImmutableSetMultimap<AuthnAuthority, Credential> credentialsMap,
        ImmutableMap<AuthnAuthority, Verification> verificationsMap) {
      this.credentialGroups = credentialGroups;
      this.cookiesMap = cookiesMap;
      this.credentialsMap = credentialsMap;
      this.verificationsMap = verificationsMap;
    }

    @CheckReturnValue
    @Nonnull
    private Evolver evolve() {
      return new Evolver(credentialGroups, cookiesMap, credentialsMap, verificationsMap);
    }

    /**
     * Gets the credential groups that were used to generate this summary.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableList<CredentialGroup> getCredentialGroups() {
      return credentialGroups;
    }

    /**
     * Gets all the cookies in this summary.
     *
     * @return An immutable map of the cookies for each authority.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableMap<AuthnAuthority, ImmutableSet<GCookie>> getCookiesMap() {
      return cookiesMap;
    }

    /**
     * Gets all the credentials in this summary.
     *
     * @return An immutable map of the credentials for each authority.
     */
    public ImmutableSetMultimap<AuthnAuthority, Credential> getCredentialsMap() {
      return credentialsMap;
    }

    /**
     * Gets all the verifications in this summary.
     *
     * @return An immutable map of the verifications for each authority.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableMap<AuthnAuthority, Verification> getVerificationsMap() {
      return verificationsMap;
    }

    /**
     * Gets the cookies in this summary for some specified authorities.
     *
     * @param predicate A predicate specifying the authorities to get cookies for.
     * @return An immutable set of the cookies for the matching authorities.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableSet<GCookie> getCookies(Predicate<AuthnAuthority> predicate) {
      ImmutableSet.Builder<GCookie> builder = ImmutableSet.builder();
      for (Map.Entry<AuthnAuthority, ImmutableSet<GCookie>> entry : cookiesMap.entrySet()) {
        if (predicate.apply(entry.getKey())) {
          builder.addAll(entry.getValue());
        }
      }
      return builder.build();
    }

    /**
     * Gets the unexpired cookies in this summary for some specified authorities.
     *
     * @param predicate A predicate specifying the authorities to get cookies for.
     * @param timeStamp A reference time to use for expiring cookies.
     * @return An immutable set of the unexpired cookies for the matching authorities.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableSet<GCookie> getCookies(Predicate<AuthnAuthority> predicate, long timeStamp) {
      ImmutableSet.Builder<GCookie> builder = ImmutableSet.builder();
      for (Map.Entry<AuthnAuthority, ImmutableSet<GCookie>> entry : cookiesMap.entrySet()) {
        if (predicate.apply(entry.getKey())) {
          for (GCookie cookie : entry.getValue()) {
            if (!cookie.isExpired(timeStamp)) {
              builder.add(cookie);
            }
          }
        }
      }
      return builder.build();
    }

    /**
     * Gets the credentials in this summary for some specified authorities.
     *
     * @param predicate A predicate specifying the authorities to get credentials for.
     * @return An immutable set of the credentials for the matching authorities.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableSet<Credential> getCredentials(Predicate<AuthnAuthority> predicate) {
      ImmutableSet.Builder<Credential> builder = ImmutableSet.builder();
      for (AuthnAuthority authority : credentialsMap.keySet()) {
        if (predicate.apply(authority)) {
          builder.addAll(credentialsMap.get(authority));
        }
      }
      return builder.build();
    }

    /**
     * Gets the verifications in this summary for some specified authorities.
     *
     * @param predicate A predicate specifying the authorities to get verifications for.
     * @return An immutable set of the verifications for the matching authorities.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableSet<Verification> getVerifications(Predicate<AuthnAuthority> predicate) {
      ImmutableSet.Builder<Verification> builder = ImmutableSet.builder();
      for (Map.Entry<AuthnAuthority, Verification> entry : verificationsMap.entrySet()) {
        if (predicate.apply(entry.getKey())) {
          builder.add(entry.getValue());
        }
      }
      return builder.build();
    }

    /**
     * Gets the unexpired verifications in this summary for some specified authorities.
     *
     * @param predicate A predicate specifying the authorities to get verifications for.
     * @param timeStamp A reference time to use for expiring verifications.
     * @return An immutable set of the unexpired verifications for the matching authorities.
     */
    @CheckReturnValue
    @Nonnull
    public ImmutableSet<Verification> getVerifications(Predicate<AuthnAuthority> predicate,
        long timeStamp) {
      ImmutableSet.Builder<Verification> builder = ImmutableSet.builder();
      for (Map.Entry<AuthnAuthority, Verification> entry : verificationsMap.entrySet()) {
        if (predicate.apply(entry.getKey())) {
          Verification verification = entry.getValue();
          if (!verification.hasExpired(timeStamp)) {
            builder.add(verification);
          }
        }
      }
      return builder.build();
    }
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(AuthnSessionState.class,
        ProxyTypeAdapter.make(AuthnSessionState.class, LocalProxy.class));
    builder.registerTypeAdapter(Instruction.class,
        new Instruction.LocalTypeAdapter());
  }

  private static final class LocalProxy implements TypeProxy<AuthnSessionState> {
    List<Instruction> instructions;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(AuthnSessionState state) {
      instructions = state.getInstructions();
    }

    @Override
    public AuthnSessionState build() {
      return AuthnSessionState.of(instructions);
    }
  }

  /**
   * An instruction, consisting of an operation, an authority, and an operand.
   */
  @Immutable
  @ParametersAreNonnullByDefault
  public static final class Instruction {
    @Nonnull private final Operation operation;
    @Nonnull private final AuthnAuthority authority;
    @Nonnull private final Object operand;

    private Instruction(Operation operation, AuthnAuthority authority, Object operand) {
      this.operation = operation;
      this.authority = authority;
      this.operand = operand;
    }

    @CheckReturnValue
    @Nonnull
    public static Instruction make(Operation operation, AuthnAuthority authority, Object operand) {
      Preconditions.checkNotNull(authority);
      Preconditions.checkArgument(operation.getOperandClass().isInstance(operand));
      return new Instruction(operation, authority, operand);
    }

    @CheckReturnValue
    @Nonnull
    public Operation getOperation() {
      return operation;
    }

    @CheckReturnValue
    @Nonnull
    public AuthnAuthority getAuthority() {
      return authority;
    }

    @CheckReturnValue
    @Nonnull
    public Object getOperand() {
      return operand;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) { return true; }
      if (!(object instanceof Instruction)) { return false; }
      Instruction other = (Instruction) object;
      return Objects.equal(getOperation(), other.getOperation())
          && Objects.equal(getAuthority(), other.getAuthority())
          && Objects.equal(getOperand(), other.getOperand());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(getOperation(), getAuthority(), getOperand());
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      switch (operation) {
        case ADD_COOKIE:
        case ADD_CREDENTIAL:
        case ADD_VERIFICATION:
          builder.append("add to ");
          break;
        case REMOVE_COOKIE:
        case REMOVE_CREDENTIAL:
        case REMOVE_VERIFICATION:
          builder.append("remove from ");
          break;
        default:
          throw new IllegalStateException("Unknown operation: " + operation);
      }
      builder.append(authority);
      builder.append(": ");
      builder.append(operand);
      return builder.toString();
    }

    private static final class LocalTypeAdapter
        implements JsonSerializer<Instruction>, JsonDeserializer<Instruction> {
      static final String KEY_OPERATION = "operation";
      static final String KEY_AUTHORITY = "authority";
      static final String KEY_OPERAND = "operand";

      LocalTypeAdapter() {
      }

      @Override
      public JsonElement serialize(Instruction instruction, Type type,
          JsonSerializationContext context) {
        JsonObject jo = new JsonObject();
        Operation operation = instruction.getOperation();
        jo.addProperty(KEY_OPERATION, operation.toString());
        jo.add(KEY_AUTHORITY, context.serialize(instruction.getAuthority(), AuthnAuthority.class));
        jo.add(KEY_OPERAND,
            context.serialize(instruction.getOperand(), operation.getOperandClass()));
        return jo;
      }

      @Override
      public Instruction deserialize(JsonElement src, Type type,
          JsonDeserializationContext context) {
        JsonObject jo = src.getAsJsonObject();
        Operation operation = Operation.valueOf(jo.getAsJsonPrimitive(KEY_OPERATION).getAsString());
        AuthnAuthority authority = context.deserialize(jo.get(KEY_AUTHORITY), AuthnAuthority.class);
        return Instruction.make(operation, authority,
            context.deserialize(jo.get(KEY_OPERAND), operation.getOperandClass()));
      }
    }
  }
}
