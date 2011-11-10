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

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Interface for user-specific implementation details of an Adaptor.
 * Implementations must be thread-safe. Implementations are encouraged to not
 * keep any state or only soft-state like a connection cache.
 *
 * <p>Once configuration is prepared, {@link #init} will be called. This is
 * guaranteed to occur before any calls to {@link #getDocContent}, {@link
 * #getDocIds}, or {@link #isUserAuthorized}. When the adaptor needs to
 * shutdown, {@link #destroy} will be called.
 *
 * <p>If the adaptor is using {@link AbstractAdaptor#main}, then {@link
 * #initConfig} will be called before {@link #init} to allow the adaptor an
 * opportunity to set and override default configuration values.
 *
 * @see adaptorlib.examples.AdaptorTemplate
 * @see AbstractAdaptor
 * @see PollingIncrementalAdaptor
 */
public interface Adaptor {
  /**
   * Provides contents and metadata of particular document. This method should
   * be highly parallelizable and support twenty or more concurrent calls. Two
   * to three concurrent calls may be average during initial GSA crawling, but
   * twenty or more concurrent calls is typical when the GSA is recrawling
   * unmodified content.
   *
   * @throws java.io.FileNotFoundException when requested document doesn't exist
   */
  public void getDocContent(Request request, Response response)
      throws IOException;

  /**
   * Pushes all the {@code DocId}s that are suppose to be indexed by the GSA.
   * This will frequently involve re-sending {@code DocId}s to the GSA, but this
   * allows healing previous errors and cache inconsistencies. Re-sending {@code
   * DocIds} is very fast and should be considered free on the GSA. This method
   * should determine a list of {@code DocId}s to push and call {@link
   * DocIdPusher#pushDocIds} one or more times.
   *
   * <p>{@code pusher} is provided as convenience and is the same object
   * provided to {@link #init} previously. This method may take a while and
   * implementations are free to call {@link Thread#sleep} occasionally to
   * reduce load.
   */
  public void getDocIds(DocIdPusher pusher) throws IOException,
      InterruptedException;

  /**
   * Determines whether the user identified is allowed to access the {@code
   * DocId}s. The user is either anonymous or assumed to be previously
   * authenticated. If an anonymous user is denied access to a document, then
   * the caller may prompt the user to go through an authentication process and
   * then try again.
   *
   * <p>Returns {@link AuthzStatus#PERMIT} for {@link DocId}s the user is
   * allowed to access. Retutrns {@link AuthzStatus#DENY} for {@code DocId}s the
   * user is not allowed to access. If the document exists, {@link
   * AuthzStatus#INDETERMINATE} will not be returned for that {@code DocId}.
   *
   * <p>If the document doesn't exist, then there are several possibilities. If
   * the repository is fully-public then it will return {@code PERMIT}. This
   * will allow the caller to provide a cached version of the file to the user
   * or call {@link #getDocContent} which should throw a {@link java.io.
   * FileNotFoundException}. If the adaptor is not sensitive to users knowing
   * that certain documents do not exist, then it will return {@code
   * INDETERMINATE}. This will be interpreted as the document does not exist; no
   * cached copy will be provided to the user but the user may be informed the
   * document doesn't exist. Highly sensitive repositories may return {@code
   * DENY}.
   *
   * @param userIdentifier User to authorize, or {@code null} for anonymous
   *        users
   * @param groups never-{@code null} set of groups the user belongs to
   * @param ids Collection of {@code DocId}s that need to be checked
   * @return an {@code AuthzStatus} for each {@code DocId} provided in {@code
   *         ids}
   */
  public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
      Set<String> groups, Collection<DocId> ids) throws IOException;

  /**
   * Provides the opportunity for the Adaptor to create new configuration values
   * or override default values. Only {@link Config#addKey} should likely be
   * called. The user's configuration will override any values set in this way.
   * This method is called by {@link AbstractAdaptor#main} before {@link #init}
   * is called.
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
   */
  public void init(AdaptorContext context) throws Exception;

  /**
   * Shutdown and release resources of adaptor.
   */
  public void destroy();
}
