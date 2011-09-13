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
import com.google.enterprise.secmgr.config.CredentialTypeName;
import com.google.enterprise.secmgr.json.ProxyTypeAdapter;
import com.google.enterprise.secmgr.json.TypeProxy;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * A credential consisting of a password.
 */
@Immutable
@ParametersAreNonnullByDefault
public final class CredPassword extends AbstractCredential {

  @Nonnull private final String text;

  private CredPassword(String text) {
    Preconditions.checkNotNull(text);
    this.text = text;
  }

  /**
   * Gets a password credential for a given password text.
   *
   * @param text The text of the password.
   * @return A corresponding password credential.
   */
  @Nonnull
  public static CredPassword make(String text) {
    return new CredPassword(text);
  }

  @Override
  public boolean isPublic() {
    return false;
  }

  @Override
  public CredentialTypeName getTypeName() {
    return CredentialTypeName.PASSWORD;
  }

  @Override
  public boolean isVerifiable() {
    return !text.isEmpty();
  }

  /**
   * Gets the user's password.
   *
   * @return The password text.
   */
  @Nonnull
  public String getText() {
    return text;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof CredPassword)) { return false; }
    CredPassword credential = (CredPassword) object;
    return Objects.equal(text, credential.getText());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(text);
  }

  @Override
  public String toString() {
    return "{password}";
  }

  static void registerTypeAdapters(GsonBuilder builder) {
    builder.registerTypeAdapter(CredPassword.class,
        ProxyTypeAdapter.make(CredPassword.class, LocalProxy.class));
  }

  private static final class LocalProxy implements TypeProxy<CredPassword> {
    @SerializedName("password") String text;

    @SuppressWarnings("unused")
    LocalProxy() {
    }

    @SuppressWarnings("unused")
    LocalProxy(CredPassword password) {
      text = password.getText();
    }

    @Override
    public CredPassword build() {
      return make(text);
    }
  }
}
