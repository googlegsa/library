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

package com.google.enterprise.adaptor;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Exposes an AdaptorContext that wraps all methods of the provided
 * AdaptorContext to allow modification of behavior via chaining.
 */
class WrapperAdaptor {
  public static class WrapperAdaptorContext implements AdaptorContext {
    private AdaptorContext context;

    public WrapperAdaptorContext(AdaptorContext context) {
      this.context = context;
    }

    @Override
    public Config getConfig() {
      return context.getConfig();
    }

    @Override
    public DocIdPusher getDocIdPusher() {
      return context.getDocIdPusher();
    }

    @Override
    public AsyncDocIdPusher getAsyncDocIdPusher() {
      return context.getAsyncDocIdPusher();
    }

    @Override
    public DocIdEncoder getDocIdEncoder() {
      return context.getDocIdEncoder();
    }

    @Override
    public void addStatusSource(StatusSource source) {
      context.addStatusSource(source);
    }

    @Override
    public void setGetDocIdsFullErrorHandler(ExceptionHandler handler) {
      context.setGetDocIdsFullErrorHandler(handler);
    }

    @Override
    public ExceptionHandler getGetDocIdsFullErrorHandler() {
      return context.getGetDocIdsFullErrorHandler();
    }

    @Override
    public void setGetDocIdsIncrementalErrorHandler(
        ExceptionHandler handler) {
      context.setGetDocIdsIncrementalErrorHandler(handler);
    }

    @Override
    public ExceptionHandler getGetDocIdsIncrementalErrorHandler() {
      return context.getGetDocIdsIncrementalErrorHandler();
    }

    @Override
    public SensitiveValueDecoder getSensitiveValueDecoder() {
      return context.getSensitiveValueDecoder();
    }

    @Override
    public HttpContext createHttpContext(String path, HttpHandler handler) {
      return context.createHttpContext(path, handler);
    }

    @Override
    public Session getUserSession(HttpExchange ex, boolean create) {
      return context.getUserSession(ex, create);
    }

    @Override
    public void setPollingIncrementalLister(PollingIncrementalLister lister) {
      context.setPollingIncrementalLister(lister);
    }

    @Override
    public void setAuthnAuthority(AuthnAuthority authnAuthority) {
      context.setAuthnAuthority(authnAuthority);
    }

    @Override
    public void setAuthzAuthority(AuthzAuthority authzAuthority) {
      context.setAuthzAuthority(authzAuthority);
    }
  }
}
