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

import com.google.common.base.Objects;
import com.google.common.labs.matcher.ParsedUrlPattern;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Implementation of the Flexible Authorization Routing Table Row.
 *
 * Each row in the routing table contains
 * Url Pattern - a string which specifies either a single document via a
 * specific URL or a set of documents that share the same pattern,
 * pointer to the Authz Rule and a unique row identifier.
 *
 * @author meghna@google.com (Meghna Dhar)
 *
 */
public class FlexAuthzRoutingTableEntry {
  private final String urlPattern;
  private final FlexAuthzRule authzRule;
  private final UUID uniqueRowId;

  public static boolean isValidPattern(String patternStr) {
    if (patternStr.isEmpty()) {
      return false;
    }
    try {
      ParsedUrlPattern p = new ParsedUrlPattern(patternStr);
    } catch (IllegalArgumentException e) {
      return false;
    }

    String regexpProtocols[] = new String[] { "regexp:", "regexpIgnoreCase:", "regexpCase:" };
    for (String regexpProtocol : regexpProtocols) {
      if (patternStr.startsWith(regexpProtocol)) {
        try {
          Pattern.compile(patternStr.substring(regexpProtocol.length()));
        } catch (PatternSyntaxException e) {
          return false;
        }
      }
    }

    // TODO(???): Introduce addtional validation as ParsedUrlPattern is lax.
    /* GSA rules on valid URL patterns:
       http://code.google.com/apis/searchappliance/documentation/50/help_gsa/z01apprules.html */

    return true;
  }

  public FlexAuthzRoutingTableEntry(String urlPattern, FlexAuthzRule authzRule) {
    this(urlPattern, authzRule, UUID.randomUUID());
  }

  public FlexAuthzRoutingTableEntry(String urlPattern, FlexAuthzRule authzRule,
      UUID uuid) {
    if (!isValidPattern(urlPattern)) {
      throw new IllegalArgumentException("invalid pattern: " + urlPattern);
    }
    this.urlPattern = urlPattern;
    this.authzRule = authzRule;
    this.uniqueRowId = uuid;
  }

  public String getUrlPattern() {
    return urlPattern;
  }

  public FlexAuthzRule getAuthzRule() {
    return authzRule;
  }

  public UUID getUniqueRowId(){
    return uniqueRowId;
  }

  @Override
  public String toString() {
    return ConfigSingleton.getGson().toJson(this);
  }

  /**
   * only intended to be used for testing
   * Since each time the default config is loaded, there will be a new
   * uniqueRowId generated, we do not include the uniqueRowId in the
   * equals method
   */
  @Override
  public synchronized boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof FlexAuthzRoutingTableEntry)) { return false; }
    FlexAuthzRoutingTableEntry other = (FlexAuthzRoutingTableEntry) object;
    return Objects.equal(getUrlPattern(), other.getUrlPattern())
        && Objects.equal(getAuthzRule(), other.getAuthzRule());
  }

  @Override
  public synchronized int hashCode() {
      return Objects.hashCode(getUrlPattern(), getAuthzRule(), getUniqueRowId());
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(FlexAuthzRoutingTableEntry.class,
        ProxyTypeAdapter.make(FlexAuthzRoutingTableEntry.class, LocalProxy.class));
  }

  private static final class LocalProxy implements TypeProxy<FlexAuthzRoutingTableEntry> {
    String urlPattern;
    FlexAuthzRule authzRule;
    UUID uniqueRowId;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(FlexAuthzRoutingTableEntry entry) {
      urlPattern = entry.getUrlPattern();
      authzRule = entry.getAuthzRule();
      uniqueRowId = entry.getUniqueRowId();
    }

    @Override
    public FlexAuthzRoutingTableEntry build() {
      return new FlexAuthzRoutingTableEntry(urlPattern, authzRule, uniqueRowId);
    }
  }
}
