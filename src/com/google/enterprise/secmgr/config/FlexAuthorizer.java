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

import java.util.List;
import java.util.UUID;

/**
 * Interface for Flexible Authorization.
 *
 * This class provides accessors and mutators for the Authz Routing Table and
 * the Authz Rules Table.  Each entry in the routing table is uniquely defined
 * by a UUID.  Each rule in the rule table is uniquely defined by its display
 * name.
 *
 * @author meghna@google.com (Meghna Dhar)
 */
public interface FlexAuthorizer {

  /**
   * Add an entry to the routing table.
   *
   * @param order The priority order for the entry; the entry will
   *     be stored at that position, and all subsequent entries have their order
   *     increased by one.
   * @param entry The entry to be added.
   * @throws IllegalArgumentException if the new entry refers to a rule that's
   *     not in the rule table, or if the order is less than zero or greater
   *     than the number of entries in the routing table.
   */
  public void addToRoutingTable(int order, FlexAuthzRoutingTableEntry entry);

  /**
   * Add an entry at the end of the routing table.
   *
   * @param entry The entry to be added.
   * @throws IllegalArgumentException if the new entry refers to a rule that's
   *     not in the rule table.
   */
  public void addToRoutingTable(FlexAuthzRoutingTableEntry entry);

  /**
   * Delete an entry from the routing table.
   *
   * @param uuid The UUID of the entry to delete.
   * @throws IllegalArgumentException if there's no entry in the table with that UUID.
   */
  public void deleteFromRoutingTable(UUID uuid);

  /**
   * Update an entry in the routing table.
   *
   * @param order The priority order for the entry; the entry will
   *     be stored at that position, and all subsequent entries have their order
   *     increased by one.
   * @param entry The new entry; must have the same UUID as an existing entry.
   * @throws IllegalArgumentException if the entry refers to a rule that's not
   *     in the rule table, or if the entry's UUID doesn't match an existing
   *     entry.
   */
  public void updateRoutingTable(int order, FlexAuthzRoutingTableEntry entry);

  /**
   * Given a UUID, get the corresponding entry from the routing table.
   *
   * @param uuid The UUID to search for.
   * @return The corresponding entry, or null if there's none.
   */
  public FlexAuthzRoutingTableEntry getFromRoutingTable(UUID uuid);

  /**
   * Given a UUID, get the priority order of the corresponding entry in the routing table.
   *
   * @param uuid The UUID to search for.
   * @return The priority order of the corresponding entry, or -1 if there's none.
   */
  public int getRoutingPriorityOrder(UUID uuid);

  /**
   * Get all the entries in the routing table, in priority order.
   *
   * @return An immutable list of the routing-table entries.
   */
  public List<FlexAuthzRoutingTableEntry> getAllRoutingTable();

  /**
   * @return The number of entries in the routing table.
   */
  public int getRoutingTableSize();

  /**
   * Add a new rule to the rule table.
   *
   * @param entry The rule to be added.
   * @throws IllegalArgumentException if there's already a rule of that name in the table.
   */
  public void addToRulesTable(FlexAuthzRule entry);

  /**
   * Delete a rule from the rule table.
   *
   * @param displayName The name of the rule to delete.
   * @throws IllegalStateException if the rule is referred to by a routing-table
   *     entry, or if there's no rule of that name in the rule table.
   */
  public void deleteFromRulesTable(String displayName);

  /**
   * Update a rule in the rule table.  Also updates all the routing-table
   * entries that refer to the rule being replaced.
   *
   * @param entry The replacement rule.
   * @throws IllegalArgumentException if there's no rule of that name in the table.
   */
  public void updateRulesTable(FlexAuthzRule entry);

  /**
   * Given a name, get the matching rule from the rule table.
   *
   * @param displayName The name to search for.
   * @return The rule with that name, or null if there's none.
   */
  public FlexAuthzRule getFromRulesTable(String displayName);

  /**
   * Get all the rules in the rule table.
   *
   * @return An immutable list of the rules in the table.
   */
  public List<FlexAuthzRule> getAllRulesTable();

  /**
   * Clear the tables.
   */
  public void clearTables();
}
