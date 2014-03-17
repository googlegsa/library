// Copyright 2008 Google Inc.
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

package com.google.enterprise.adaptor.secmgr.http;

import com.google.common.collect.ListMultimap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * An abstraction for an HTTP exchange.  @see HttpClientInterface
 */
@ParametersAreNonnullByDefault
public interface HttpExchange {

  /**
   * Sets the proxy to use for the exchange.
   *
   * @param proxy The proxy host and port.
   */
  public void setProxy(String proxy);

  /**
   * Set credentials to use for HTTP Basic authentication.
   *
   * @param username The username to use, never null or empty.
   * @param password The password to use, never null or empty.
   */
  public void setBasicAuthCredentials(String username, String password);

  /**
   * Tells this exchange whether to follow redirects.  Default is to not follow
   * them.
   *
   * @param followRedirects {@code true} means follow them.
   */
  public void setFollowRedirects(boolean followRedirects);

  /**
   * Sets the timeout.
   */
  public void setTimeout(int timeout);

  /**
   * Gets the HTTP method name.
   *
   * @return The HTTP method string: e.g. {@code "GET"} or {@code "POST"}.
   */
  @Nonnull
  public String getHttpMethod();

  /**
   * Gets the request URL for this exchange.
   *
   * @return The URL for this exchange.
   */
  public URL getUrl();

  /**
   * Adds a parameter to the exchange.  Works only for POST methods.
   *
   * @param name The parameter's name.
   * @param value The parameter's value.
   */
  public void addParameter(String name, String value);

  /**
   * Adds an HTTP request header field.  Must not be used to set cookies (use
   * {@link #addCookies}).
   *
   * @param name The header's name (case insensitive).
   * @param value The header's value.
   */
  public void addRequestHeader(String name, String value);

  /**
   * Sets an HTTP request header field.  Overrides any previous header of that
   * name.  Must not be used to set cookies (use {@link #addCookies}).
   *
   * @param name The header's name (case insensitive).
   * @param value The header's value.
   */
  public void setRequestHeader(String name, String value);

  /**
   * Gets the request header values for a specified header name.
   *
   * @param headerName The header name (case insensitive).
   * @return The header values for that name.
   */
  @Nonnull
  public List<String> getRequestHeaderValues(String headerName);

  /**
   * Gets the request header value for a specified header name.  If there are
   * one or more headers with that name, returns the first one; otherwise
   * returns {@code null}.
   *
   * @param headerName The header's name (case insensitive).
   * @return The specified request header value.
   */
  @Nullable
  public String getRequestHeaderValue(String headerName);

  /**
   * Sets the entity of the request.
   *
   * @param byteArrayRequestEntity The bytes to use as an entity.
   */
  public void setRequestBody(byte[] byteArrayRequestEntity);

  /**
   * Performs the HTTP exchange.
   *
   * @return The status code from the exchange.
   * @throws IOException if there's a transport error.
   */
  public int exchange() throws IOException;

  /**
   * Gets the response entity (body) as a string.
   *
   * @return The entity.
   * @throws IOException if there's a transport error.
   */
  @Nonnull
  public String getResponseEntityAsString() throws IOException;

  /**
   * Gets the response entity (body) as an input stream.
   *
   * @return The entity input stream.
   * @throws IOException if there's a transport error.
   */
  @Nonnull
  public InputStream getResponseEntityAsStream() throws IOException;

  /**
   * Gets the response header values for a given header name.
   *
   * @param headerName The header name to use.
   * @return The values of all the response headers with that name.
   */
  @Nonnull
  public List<String> getResponseHeaderValues(String headerName);

  /**
   * Gets the response header value for a given header name.  If there are one
   * or more such headers, returns the first one; otherwise returns
   * {@code null}.
   *
   * @param headerName The header name to use.
   * @return The value of the specified response header.
   */
  @Nullable
  public String getResponseHeaderValue(String headerName);

  /**
   * Gets all of the headers in the response as a multimap.  The map keys will
   * always be lower case; convert a key argument to lower case before doing
   * lookup.
   *
   * @return An immutable multimap of all the received headers.
   */
  @Nonnull
  public ListMultimap<String, String> getResponseHeaders();

  /**
   * Gets the status code from the exchange.
   *
   * @return The status code.
   */
  public int getStatusCode();

  /**
   * Closes the exchange and reclaim its resources.
   */
  public void close();
}
