// Copyright 2011 Google Inc. All Rights Reserved.
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

package adaptorlib;

import com.google.enterprise.secmgr.http.HttpClientInterface;
import com.google.enterprise.secmgr.modules.SamlClient;

import com.sun.net.httpserver.HttpExchange;

import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.security.SecurityHelper;
import org.opensaml.xml.security.credential.Credential;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * A credentials gatherer that implements authentication by communicating with
 * the GSA's security manager via SAML. This class only sends the initial
 * request; the response is handled in {@link SamlAssertionConsumerHandler}.
 */
class AuthnHandler extends AbstractHandler {
  /** Manager that handles keeping track of users attempting to authenticate. */
  private final SessionManager<HttpExchange> sessionManager;
  /**
   * Http client implementation that {@code SamlClient} will use to send
   * requests directly to the GSA, for resolving SAML artifacts.
   */
  private final HttpClientInterface httpClient = new HttpClientAdapter();
  /** Credentials to use to sign messages. */
  private final Credential cred;
  private final SamlMetadata metadata;

  AuthnHandler(String fallbackHostname, Charset defaultEncoding,
               SessionManager<HttpExchange> sessionManager,
               String gsaHostname, String keyAlias, int localPort)
      throws IOException {
    super(fallbackHostname, defaultEncoding);
    this.sessionManager = sessionManager;

    cred = getCredential(keyAlias);

    try {
      DefaultBootstrap.bootstrap();
    } catch (ConfigurationException ex) {
      throw new RuntimeException(ex);
    }
    this.metadata = new SamlMetadata(fallbackHostname, localPort, gsaHostname);
  }

  @Override
  public void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if (!"GET".equals(requestMethod) && !"HEAD".equals(requestMethod)) {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
          "Unsupported request method");
      return;
    }

    Session session = sessionManager.getSession(ex);
    AuthnState authnState = (AuthnState) session.getAttribute(
        AuthnState.SESSION_ATTR_NAME);
    if (authnState == null) {
      authnState = new AuthnState();
      session.setAttribute(AuthnState.SESSION_ATTR_NAME, authnState);
    }
    SamlClient client =
        new SamlClient(
            metadata.getLocalEntity(),
            metadata.getPeerEntity(),
            "GSA Adaptor",
            cred,
            httpClient);
    authnState.startAttempt(client, getRequestUri(ex));
    client.sendAuthnRequest(
        new adaptorlib.HttpExchangeOutTransportAdapter(ex, true));
  }

  /**
   * Create a {@code Credential} usable by OpenSAML by accessing the default
   * keystore. The key should have the same password as the keystore.
   */
  private static Credential getCredential(String alias) throws IOException {
    String keystoreFile = System.getProperty("javax.net.ssl.keyStore");
    if (keystoreFile == null) {
      throw new RuntimeException("You must provide a default keystore");
    }
    String keystoreType = System.getProperty("javax.net.ssl.keyStoreType",
                                             KeyStore.getDefaultType());

    PrivateKey privateKey;
    PublicKey publicKey;
    try {
      KeyStore ks = KeyStore.getInstance(keystoreType);
      InputStream ksis = new FileInputStream(keystoreFile);
      String keystorePasswordStr = System.getProperty(
          "javax.net.ssl.keyStorePassword");
      char[] keystorePassword = keystorePasswordStr == null ? null
          : keystorePasswordStr.toCharArray();
      try {
        ks.load(ksis, keystorePassword);
      } catch (NoSuchAlgorithmException ex) {
        throw new RuntimeException(ex);
      } catch (CertificateException ex) {
        throw new RuntimeException(ex);
      } finally {
        ksis.close();
      }
      Key key = null;
      try {
        key = ks.getKey(alias, keystorePassword);
      } catch (NoSuchAlgorithmException ex) {
        throw new RuntimeException(ex);
      } catch (UnrecoverableKeyException ex) {
        throw new RuntimeException(ex);
      }
      if (key == null) {
        throw new NullPointerException();
      }
      privateKey = (PrivateKey) key;
      publicKey = ks.getCertificate("adaptor").getPublicKey();
    } catch (KeyStoreException ex) {
      throw new RuntimeException(ex);
    }
    return SecurityHelper.getSimpleCredential(publicKey, privateKey);
  }
}
