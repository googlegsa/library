// Copyright 2012 Google Inc. All Rights Reserved.
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

import com.google.enterprise.adaptor.HttpClientAdapter;
import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

/**
 * Test cases for {@link HttpClientAdapter}.
 */
public class HttpClientAdapterTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private HttpClientAdapter httpClient = new HttpClientAdapter();

  @Test
  public void testHeadExchange() {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.headExchange(null);
  }

  @Test
  public void testGetExchange() {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.getExchange(null);
  }

  @Test
  public void testNewHttpExchange() {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.newHttpExchange(null);
  }

  @Test
  public void testGetConnection() throws Exception {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.getConnection(null);
  }

  @Test
  public void testHeadExchange2() {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.headExchange(null, null);
  }

  @Test
  public void testGetExchange2() {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.getExchange(null, null);
  }

  @Test
  public void testPostExchange3() {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.postExchange(null, null, null);
  }

  @Test
  public void testNewHttpExchange2() {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.newHttpExchange(null, null);
  }

  @Test
  public void testSetRequestTimeoutMillis() {
    thrown.expect(UnsupportedOperationException.class);
    httpClient.setRequestTimeoutMillis(-1);
  }
}
