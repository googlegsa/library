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

import java.io.IOException;

/**
 * Interface for user-specific implementation details of an Adaptor.
 * Implementations must be thread-safe. Implementations are encouraged to not
 * keep any state or only soft-state like a connection cache.
 *
 * <p>Once configuration is prepared, {@link #init} will be called. This is
 * guaranteed to occur before any calls to {@link #getDocContent} or {@link
 * #getDocIds}. When the adaptor needs to shutdown, {@link #destroy} will be
 * called.
 *
 * <p>If the adaptor is using {@link AbstractAdaptor#main}, then {@link
 * #initConfig} will be called before {@link #init} to allow the adaptor an
 * opportunity to set and override default configuration values.
 *
 * @see com.google.enterprise.adaptor.examples.AdaptorTemplate
 * @see AbstractAdaptor
 * @see PollingIncrementalLister
 */
public interface Adaptor {
  /**
   * Provides contents and metadata of particular document. This method should
   * be highly parallelizable and support twenty or more concurrent calls. Two
   * to three concurrent calls may be average during initial GSA crawling, but
   * twenty or more concurrent calls is typical when the GSA is recrawling
   * unmodified content.
   *
   * <p>If you experience a fatal error, feel free to throw an {@link
   * IOException} or {@link RuntimeException}. In the case of an error, the GSA
   * will determine if and when to retry.
   * @param request info about document being sought
   * @param response place to put document data; this argument will always
   *     implement {@link Response2}
   * @throws IOException if getting data fails
   * @throws InterruptedException if an IO operation throws it
   */
  public void getDocContent(Request request, Response response)
      throws IOException, InterruptedException;

  /**
   * Pushes all the {@code DocId}s that are suppose to be indexed by the GSA.
   * This will frequently involve re-sending {@code DocId}s to the GSA, but this
   * allows healing previous errors and cache inconsistencies. Re-sending {@code
   * DocIds} is very fast and should be considered free on the GSA. This method
   * should determine a list of {@code DocId}s to push and call {@link
   * DocIdPusher#pushDocIds} one or more times and {@link
   * DocIdPusher#pushNamedResources} if using named resources.
   *
   * <p>{@code pusher} is provided as convenience and is the same object
   * provided to {@link #init} previously. This method may take a while and
   * implementations are free to call {@link Thread#sleep} occasionally to
   * reduce load.
   *
   * <p>If you experience a fatal error, feel free to throw an {@link
   * IOException} or {@link RuntimeException}. In the case of an error, the
   * {@link ExceptionHandler} in use in {@link AdaptorContext} will
   * determine if and when to retry.
   * @param pusher used to send doc ids to GSA
   * @throws IOException if getting data fails
   * @throws InterruptedException if an IO operations throws it
   */
  public void getDocIds(DocIdPusher pusher) throws IOException,
      InterruptedException;

  /**
   * Provides the opportunity for the Adaptor to create new configuration values
   * or override default values. Only {@link Config#addKey} should likely be
   * called. The user's configuration will override any values set in this way.
   * This method is called by {@link AbstractAdaptor#main} before {@link #init}
   * is called.
   * @param config to modify with additional keys
   */
  public void initConfig(Config config);

  /**
   * Initialize adaptor with the current context. This is the ideal time to
   * start any threads to do extra behind-the-scenes work. The {@code context}
   * points to other useful objects that can be used at any time. For example,
   * methods on {@link DocIdPusher} provided via {@link
   * AdaptorContext#getDocIdPusher} are allowed to be called whenever the
   * Adaptor wishes. This allows doing event-based incremental pushes at any
   * time.
   *
   * <p>The method is called at the end of {@link
   * GsaCommunicationHandler#start}.
   *
   * <p>If you experience a fatal error during initialization, feel free to
   * throw an {@link Exception} to cancel the startup process.
   * @param context for instance includes completed config
   * @throws Exception if things are not going well
   */
  public void init(AdaptorContext context) throws Exception;

  /**
   * Shutdown and release resources of adaptor.
   */
  public void destroy();
}
