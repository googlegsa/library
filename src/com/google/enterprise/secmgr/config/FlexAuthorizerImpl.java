// Copyright 2010 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A basic implementation of FlexAuthorizer.  This provides the primary logic
 * for everything except serialization.  The routing table is stored as a list
 * and the priority order is the ordering in the list.  Each row has a unique
 * identifier.  The rule table is stored as a map and the key is the display
 * name of the row.
 *
 * @author meghna@google.com (Meghna Dhar)
 */
public final class FlexAuthorizerImpl implements FlexAuthorizer {

  private static final String ROOT_URL_PATTERN = "/";

  private final Map<String, FlexAuthzRule> ruleTable;
  private final List<FlexAuthzRoutingTableEntry> routingTable;

  @Inject
  private FlexAuthorizerImpl() {
    ruleTable = Maps.newHashMap();
    routingTable = Lists.newArrayList();
  }

  @VisibleForTesting
  FlexAuthorizerImpl(Map<String, FlexAuthzRule> ruleTable,
      List<FlexAuthzRoutingTableEntry> routingTable) {
    this.ruleTable = ruleTable;
    this.routingTable = routingTable;
  }

  @Override
  public void addToRoutingTable(int order, FlexAuthzRoutingTableEntry entry) {
    checkPriorityOrder(order, routingTable.size());
    checkForMatchingRule(entry.getAuthzRule());
    routingTable.add(order, entry);
  }

  @Override
  public void addToRoutingTable(FlexAuthzRoutingTableEntry entry) {
    checkForMatchingRule(entry.getAuthzRule());
    routingTable.add(entry);
  }

  @Override
  public void addToRulesTable(FlexAuthzRule rule) {
    String name = rule.getRowDisplayName();
    Preconditions.checkArgument(ruleTable.get(name) == null,
        "Rule already exists: %s", name);
    ruleTable.put(name, rule);
  }

  @Override
  public void clearTables() {
    routingTable.clear();
    ruleTable.clear();
  }

  @Override
  public void deleteFromRoutingTable(UUID uuid) {
    Preconditions.checkNotNull(uuid);
    int index = getRoutingPriorityOrder(uuid);
    Preconditions.checkArgument(index >= 0, "Entry not found in routing table: %s", uuid);
    routingTable.remove(index);
  }

  @Override
  public void deleteFromRulesTable(String name) {
    Preconditions.checkNotNull(name);
    FlexAuthzRule rule = ruleTable.get(name);
    Preconditions.checkArgument(rule != null, "Rule isn't in table: %s", name);
    for (FlexAuthzRoutingTableEntry entry : routingTable) {
      Preconditions.checkState(entry.getAuthzRule() != rule,
          "Rule '%s' is referred to by one or more routing-table entries: %s",
          name, entry.getUniqueRowId().toString());
    }
    ruleTable.remove(name);
  }

  @Override
  public List<FlexAuthzRoutingTableEntry> getAllRoutingTable() {
    return ImmutableList.copyOf(routingTable);
  }

  @Override
  public List<FlexAuthzRule> getAllRulesTable() {
    return ImmutableList.copyOf(ruleTable.values());
  }

  @Override
  public int getRoutingTableSize() {
    return routingTable.size();
  }

  @Override
  public FlexAuthzRoutingTableEntry getFromRoutingTable(UUID uuid) {
    Preconditions.checkNotNull(uuid);
    for (FlexAuthzRoutingTableEntry entry : routingTable) {
      if (uuid.equals(entry.getUniqueRowId())) {
        return entry;
      }
    }
    return null;
  }

  @Override
  public FlexAuthzRule getFromRulesTable(String name) {
    Preconditions.checkNotNull(name);
    return ruleTable.get(name);
  }

  @Override
  public void updateRoutingTable(int order, FlexAuthzRoutingTableEntry entry) {
    checkPriorityOrder(order, routingTable.size() - 1);
    checkForMatchingRule(entry.getAuthzRule());
    UUID uuid = entry.getUniqueRowId();
    int oldIndex = getRoutingPriorityOrder(uuid);
    Preconditions.checkArgument(oldIndex >= 0, "Entry not found in routing table: %s", uuid);
    // oldIndex is valid in table prior to change; order is where we want the
    // entry to be after the change.  So do the delete first, then the insert.
    routingTable.remove(oldIndex);
    routingTable.add(order, entry);
  }

  @Override
  public void updateRulesTable(FlexAuthzRule rule) {
    String name = rule.getRowDisplayName();
    FlexAuthzRule oldRule = ruleTable.get(name);
    Preconditions.checkArgument(oldRule != null, "Rule not in table: %s", name);
    ruleTable.put(name, rule);
    // Now update the routing table, replacing the old rule with the new.
    for (int i = 0; i < routingTable.size(); i += 1) {
      FlexAuthzRoutingTableEntry entry = routingTable.get(i);
      if (entry.getAuthzRule() == oldRule) {
        routingTable.set(i,
            new FlexAuthzRoutingTableEntry(
                entry.getUrlPattern(),
                rule,
                entry.getUniqueRowId()));
      }
    }
  }

  public int getRoutingPriorityOrder(UUID uuid) {
    Preconditions.checkNotNull(uuid);
    int index = 0;
    for (FlexAuthzRoutingTableEntry entry : routingTable) {
      if (entry.getUniqueRowId().equals(uuid)) {
        return index;
      }
      index++;
    }
    return -1;
  }

  private void checkPriorityOrder(int order, int limit) {
    Preconditions.checkArgument(order >= 0 && order <= limit,
        "Priority order not in valid range: %d", order);
  }

  private void checkForMatchingRule(FlexAuthzRule rule) {
    String ruleName = rule.getRowDisplayName();
    Preconditions.checkArgument(ruleTable.get(ruleName) == rule,
        "Rule in table doesn't match: %s", ruleName);
  }

  @Override
  public String toString() {
    return ConfigSingleton.getGson().toJson(this);
  }

  @Override
  public synchronized boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof FlexAuthorizerImpl)) { return false; }
    FlexAuthorizerImpl other = (FlexAuthorizerImpl) object;
    return Objects.equal(getAllRulesTable(), other.getAllRulesTable())
        && Objects.equal(getAllRoutingTable(), other.getAllRoutingTable());
  }

  @Override
  public synchronized int hashCode() {
    return Objects.hashCode(getAllRulesTable(), getAllRoutingTable());
  }

  public static FlexAuthorizer makeDefault() {
    return makeDefault(null, false);
  }

  public static FlexAuthorizer makeDefault(String authzServiceUrl, boolean samlUseBatchedRequests) {
    FlexAuthorizer flexAuthorizer = new FlexAuthorizerImpl();
    int index = 0;
    makeDefaultRule(flexAuthorizer, index++, ROOT_URL_PATTERN,
        AuthzMechanism.CACHE,
        FlexAuthzRule.EMPTY_AUTHN_ID);
    makeDefaultRule(flexAuthorizer, index++, ROOT_URL_PATTERN,
        AuthzMechanism.POLICY,
        FlexAuthzRule.LEGACY_AUTHN_ID);
    if (!Strings.isNullOrEmpty(authzServiceUrl)) {
      makeDefaultRule(flexAuthorizer, index++, ROOT_URL_PATTERN,
          AuthzMechanism.SAML,
          FlexAuthzRule.LEGACY_AUTHN_ID,
          ImmutableMap.of(
              FlexAuthzRule.ParamName.SAML_ENTITY_ID, FlexAuthzRule.LEGACY_SAML_ENTITY_ID,
              FlexAuthzRule.ParamName.SAML_USE_BATCHED_REQUESTS,
              Boolean.toString(samlUseBatchedRequests)));
    }
    makeDefaultRule(flexAuthorizer, index++, FlexAuthzRule.LEGACY_CONNECTOR_URL_PATTERN,
        AuthzMechanism.CONNECTOR,
        FlexAuthzRule.LEGACY_AUTHN_ID,
        ImmutableMap.of(
            FlexAuthzRule.ParamName.CONNECTOR_NAME, FlexAuthzRule.EMPTY_CONNECTOR_NAME));
    makeDefaultRule(flexAuthorizer, index++, ROOT_URL_PATTERN,
        AuthzMechanism.HEADREQUEST,
        FlexAuthzRule.LEGACY_AUTHN_ID);
    return flexAuthorizer;
  }

  private static void makeDefaultRule(FlexAuthorizer flexAuthorizer, int index, String urlPattern,
      AuthzMechanism authzMechType, String authnId,
      Map<FlexAuthzRule.ParamName, String> mechSpecificParams) {
    FlexAuthzRule rule
        = new FlexAuthzRule(authnId, authzMechType, mechSpecificParams, String.valueOf(index),
            FlexAuthzRule.NO_TIME_LIMIT);
    flexAuthorizer.addToRulesTable(rule);
    flexAuthorizer.addToRoutingTable(new FlexAuthzRoutingTableEntry(urlPattern, rule));
  }

  private static void makeDefaultRule(FlexAuthorizer flexAuthorizer, int index, String urlPattern,
      AuthzMechanism authzMechType, String authnId) {
    FlexAuthzRule rule
        = new FlexAuthzRule(authnId, authzMechType, String.valueOf(index),
            FlexAuthzRule.NO_TIME_LIMIT);
    flexAuthorizer.addToRulesTable(rule);
    flexAuthorizer.addToRoutingTable(new FlexAuthzRoutingTableEntry(urlPattern, rule));
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(FlexAuthorizer.class,
        ProxyTypeAdapter.make(FlexAuthorizer.class, LocalProxy.class));
  }

  private static final class LocalProxy implements TypeProxy<FlexAuthorizer> {
    List<FlexAuthzRoutingTableEntry> entries;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(FlexAuthorizer flexAuthorizer) {
      entries = flexAuthorizer.getAllRoutingTable();
    }

    @Override
    public FlexAuthorizer build() {
      Map<String, FlexAuthzRule> ruleTable = Maps.newHashMap();
      for (FlexAuthzRoutingTableEntry entry : entries) {
        FlexAuthzRule rule = entry.getAuthzRule();
        ruleTable.put(rule.getRowDisplayName(), rule);
      }
      return new FlexAuthorizerImpl(ruleTable, entries);
    }
  }
}
