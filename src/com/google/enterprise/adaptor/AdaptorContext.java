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

/**
 * Methods for an Adaptor to communicate with the adaptor library.
 * Implementations of this class must be thread-safe.
 */
public interface AdaptorContext {
  /**
   * Configuration instance for the adaptor and all things within the adaptor
   * library.
   */
  public Config getConfig();

  /**
   * Callback object for pushing {@code DocId}s to the GSA at any time.
   */
  public DocIdPusher getDocIdPusher();

  /**
   * A way to construct URIs from DocIds.
   */
  public DocIdEncoder getDocIdEncoder();

  /**
   * Add a status source to the dashboard.
   */
  public void addStatusSource(StatusSource source);

  /**
   * Remove a previously added status source to the dashboard.
   */
  public void removeStatusSource(StatusSource source);

  /**
   * Override the default {@link GetDocIdsErrorHandler}.
   */
  public void setGetDocIdsErrorHandler(GetDocIdsErrorHandler handler);

  /**
   * Retrieve the current {@link GetDocIdsErrorHandler}.
   */
  public GetDocIdsErrorHandler getGetDocIdsErrorHandler();

  /**
   * Retrieve decoder for sensitive values, like passwords. To protect sensitive
   * values, the user should have previously encoded them using the Dashboard.
   * However, a user is still allowed to choose to keep sensitive values in
   * plain text.
   */
  public SensitiveValueDecoder getSensitiveValueDecoder();
}
