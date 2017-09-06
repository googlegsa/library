// Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor.testing;

import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.AsyncDocIdPusher;
import com.google.enterprise.adaptor.AuthnAuthority;
import com.google.enterprise.adaptor.AuthzAuthority;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocIdEncoder;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.ExceptionHandler;
import com.google.enterprise.adaptor.PollingIncrementalLister;
import com.google.enterprise.adaptor.SensitiveValueDecoder;
import com.google.enterprise.adaptor.Session;
import com.google.enterprise.adaptor.StatusSource;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * An implementation of {@link AdaptorContext} that throws an
 * {@code UnsupportedOperationException} if any method is called.
 *
 * <p>This class is intended to be extended for unit testing, rather
 * than implementing the {@link AdaptorContext} interface directly.
 */
public class UnsupportedAdaptorContext implements AdaptorContext {
  /** @throws UnsupportedOperationException always */
  @Override
  public Config getConfig() {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public DocIdPusher getDocIdPusher() {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public AsyncDocIdPusher getAsyncDocIdPusher() {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public DocIdEncoder getDocIdEncoder() {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void addStatusSource(StatusSource source) {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setGetDocIdsFullErrorHandler(ExceptionHandler handler) {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public ExceptionHandler getGetDocIdsFullErrorHandler() {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setGetDocIdsIncrementalErrorHandler(ExceptionHandler handler) {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public ExceptionHandler getGetDocIdsIncrementalErrorHandler() {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public SensitiveValueDecoder getSensitiveValueDecoder() {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public HttpContext createHttpContext(String path, HttpHandler handler) {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public Session getUserSession(HttpExchange ex, boolean create) {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setPollingIncrementalLister(PollingIncrementalLister lister) {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setAuthnAuthority(AuthnAuthority authnAuthority) {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public void setAuthzAuthority(AuthzAuthority authzAuthority) {
    throw new UnsupportedOperationException(
        "UnsupportedAdaptorContext was called");
  }
}
