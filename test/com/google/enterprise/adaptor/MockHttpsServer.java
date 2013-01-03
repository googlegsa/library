// Copyright 2013 Google Inc. All Rights Reserved.
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

import com.sun.net.httpserver.*;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

/**
 * Mock {@link HttpsServer}.
 */
public class MockHttpsServer extends HttpsServer {
  @Override
  public void bind(InetSocketAddress addr, int backlog) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpContext createContext(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpContext createContext(String path, HttpHandler handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public InetSocketAddress getAddress() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Executor getExecutor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeContext(HttpContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeContext(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setExecutor(Executor executor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void start() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stop(int delay) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpsConfigurator getHttpsConfigurator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setHttpsConfigurator(HttpsConfigurator conf) {
    throw new UnsupportedOperationException();
  }

}
