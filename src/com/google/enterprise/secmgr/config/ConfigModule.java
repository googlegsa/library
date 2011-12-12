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

import com.google.gson.GsonBuilder;
/*import com.google.inject.AbstractModule;
import com.google.inject.name.Names;*/

/**
 * Guice configuration for this package.
 */
public final class ConfigModule /*extends AbstractModule*/ {

  private final String configFilename;

  public ConfigModule(String configFilename) {
    this.configFilename = configFilename;
  }

/*  @Override
  protected void configure() {
    bind(ConfigCodec.class).to(JsonConfig.class);
    bind(ConfigSingleton.class);
    bind(FlexAuthorizer.class).to(FlexAuthorizerImpl.class);
    bindConstant()
        .annotatedWith(Names.named("configFile"))
        .to(configFilename);
    requestStaticInjection(ConfigSingleton.class);
  }*/

  public static void registerTypeAdapters(GsonBuilder builder) {
    AuthnAuthority.registerTypeAdapters(builder);
    AuthnMechBasic.registerTypeAdapters(builder);
    AuthnMechClient.registerTypeAdapters(builder);
    AuthnMechConnector.registerTypeAdapters(builder);
    AuthnMechForm.registerTypeAdapters(builder);
    AuthnMechKerberos.registerTypeAdapters(builder);
    AuthnMechLdap.registerTypeAdapters(builder);
    AuthnMechNtlm.registerTypeAdapters(builder);
    AuthnMechSaml.registerTypeAdapters(builder);
    AuthnMechSampleUrl.registerTypeAdapters(builder);
    AuthnMechanism.registerTypeAdapters(builder);
    ConfigParams.registerTypeAdapters(builder);
    ConnMgrInfo.registerTypeAdapters(builder);
    CredentialGroup.registerTypeAdapters(builder);
    FlexAuthorizerImpl.registerTypeAdapters(builder);
    FlexAuthzRoutingTableEntry.registerTypeAdapters(builder);
    FlexAuthzRule.registerTypeAdapters(builder);
    ParamName.registerTypeAdapters(builder);
    SecurityManagerConfig.registerTypeAdapters(builder);
  }
}
