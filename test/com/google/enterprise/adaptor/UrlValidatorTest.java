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

package com.google.enterprise.adaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Unit tests for {@link UrlValidator}. */
public class UrlValidatorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  public UrlValidator urlValidator = new UrlValidator();

  @Test
  public void testNullUrl() throws Exception {
    thrown.expect(MalformedURLException.class);
    urlValidator.validate(null);
  }

  @Test
  public void testEmptyUrl() throws Exception {
    thrown.expect(MalformedURLException.class);
    urlValidator.validate("");
  }

  @Test
  public void testNoProtocol() throws Exception {
    thrown.expect(MalformedURLException.class);
    urlValidator.validate("//foo/bar");
  }

  @Test
  public void testUnknownProtocol() throws Exception {
    thrown.expect(MalformedURLException.class);
    urlValidator.validate("unknown://foo/bar");
  }

  @Test
  public void testBadProtocol() throws Exception {
    thrown.expect(MalformedURLException.class);
    urlValidator.validate("https//foo/bar");
  }

  @Test
  public void testNoHost() throws Exception {
    thrown.expect(MalformedURLException.class);
    urlValidator.validate("http://");
  }

  @Test
  public void testHost() throws Exception {
    thrown.expect(MalformedURLException.class);
   assertEquals(false, urlValidator.validate("http://"));
  }

  @Test
  public void testReachableHost() throws Exception {
   assertEquals(true, urlValidator.validate("http://127.0.0.1/foo/bar"));
  }

  @Test
  public void testUnreachableHost() throws Exception {
   assertEquals(false, urlValidator.validate("http://unknown_host/foo/bar"));
  }
}