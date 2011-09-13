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

package com.google.enterprise.secmgr.http;

import com.google.common.collect.ListMultimap;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An abstraction to hide HttpClient behind.
 * This allows HTTP transport to be mocked for testing.
 */
public interface HttpClientInterface {
  /**
   * Create a new HTTP HEAD exchange object.
   *
   * @param url The URL to send the request to.
   * @return A new HTTP exchange object.
   */
  public HttpExchange headExchange(@Nonnull URL url);

  /**
   * Create a new HTTP GET exchange object.
   *
   * @param url The URL to send the request to.
   * @return A new HTTP exchange object.
   */
  public HttpExchange getExchange(@Nonnull URL url);

  /**
   * Create a new HTTP POST exchange object.
   *
   * @param url The URL to send the request to.
   * @param parameters The POST parameters.
   * @return A new HTTP exchange object.
   */
  public HttpExchange postExchange(@Nonnull URL url,
      @Nullable ListMultimap<String, String> parameters);

  /**
   * Create a new HTTP GET or HEAD exchange object.
   * The method (GET or HEAD) is determined by the configuration for the URL.
   *
   * @param url The URL to send the request to.
   * @return A new HTTP exchange object.
   */
  public HttpExchange newHttpExchange(@Nonnull URL url);

  /**
   * A marker type for an object representing an HTTP connection.
   */
  public interface Connection {

    /**
     * Close the connection.  After calling this method, the connection can't
     * be used for further communication.
     */
    public void close() throws IOException;
  }

  /**
   * Get a persistent connection for a given URL.
   * The returned connection may be used multiple times.
   * Calling this twice returns two different connections.
   *
   * @param url A URL specifying where to connect to.
   * @return A new connection to the specified host.
   */
  public Connection getConnection(@Nonnull URL url) throws IOException;

  /**
   * Create a new HTTP HEAD exchange object.
   *
   * @param connection The connection to send the request over.
   * @param url The URL to send the request to.
   * @return A new HTTP exchange object.
   */
  public HttpExchange headExchange(@Nullable Connection connection, @Nonnull URL url);

  /**
   * Create a new HTTP GET exchange object.
   *
   * @param connection The connection to send the request over.
   * @param url The URL to send the request to.
   * @return A new HTTP exchange object.
   */
  public HttpExchange getExchange(@Nullable Connection connection, @Nonnull URL url);

  /**
   * Create a new HTTP POST exchange object.
   *
   * @param connection The connection to send the request over.
   * @param url The URL to send the request to.
   * @param parameters The POST parameters.
   * @return A new HTTP exchange object.
   */
  public HttpExchange postExchange(@Nullable Connection connection, @Nonnull URL url,
      @Nullable ListMultimap<String, String> parameters);

  /**
   * Create a new HTTP GET or HEAD exchange object.
   * The method (GET or HEAD) is determined by the configuration for the URL.
   *
   * @param connection The connection to send the request over.
   * @param url The URL to send the request to.
   * @return A new HTTP exchange object.
   */
  public HttpExchange newHttpExchange(@Nullable Connection connection, @Nonnull URL url);

  /**
   * Sets connection timeout and socket timeout.
   */
  public void setRequestTimeoutMillis(int millisec);
}
