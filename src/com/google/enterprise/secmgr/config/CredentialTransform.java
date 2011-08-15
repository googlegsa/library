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

/**
 * A model of a particular credential transform implemented by a mechanism.
 * For example, a typical transform accepts some credentials as inputs and
 * verifies them, returning mutually-verified credentials.
 */
public final class CredentialTransform {
  private final CredentialTypeSet inputs;
  private final CredentialTypeSet outputs;

  private CredentialTransform(CredentialTypeSet inputs, CredentialTypeSet outputs) {
    this.inputs = inputs;
    this.outputs = outputs;
  }

  /**
   * Make a credential transform.
   *
   * @param inputs The inputs to the transform.
   * @param outputs The outputs from the transform.
   * @return A transform with the given inputs and outputs.
   */
  public static CredentialTransform make(CredentialTypeSet inputs, CredentialTypeSet outputs) {
    return new CredentialTransform(inputs, outputs);
  }

  /**
   * @return The inputs to this transform.
   */
  public CredentialTypeSet getInputs() {
    return inputs;
  }

  /**
   * @return The outputs from this transform.
   */
  public CredentialTypeSet getOutputs() {
    return outputs;
  }
}
