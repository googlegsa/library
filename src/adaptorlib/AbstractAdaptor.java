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

import java.util.*;
import java.util.logging.Logger;

/**
 * Provides a reasonable default implementation for most {@link Adaptor}
 * methods. Extending classes only need to implement {@link Adaptor#getDocIds}
 * and {@link Adaptor#getDocContent}.
 */
public abstract class AbstractAdaptor implements Adaptor {
  private static final Logger log
      = Logger.getLogger(AbstractAdaptor.class.getName());

  /**
   * {@inheritDoc}
   *
   * <p>This implementation provides {@link AuthzStatus#PERMIT} for all {@code
   * DocId}s in an unmodifiable map.
   */
  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
                                                  Set<String> groups,
                                                  Collection<DocId> ids) {
    Map<DocId, AuthzStatus> result
        = new HashMap<DocId, AuthzStatus>(ids.size() * 2);
    for (DocId id : ids) {
      result.put(id, AuthzStatus.PERMIT);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation does nothing.
   */
  @Override
  public void initConfig(Config config) {}

  /**
   * {@inheritDoc}
   *
   * <p>This implementation does nothing.
   */
  @Override
  public void init(Config config, DocIdPusher pusher) throws Exception {}

  /**
   * {@inheritDoc}
   *
   * <p>This implementation does nothing.
   */
  @Override
  public void destroy() {}

  /**
   * Standard main for all adaptors (including those not extending
   * AbstractAdaptor).
   *
   * <p>This method starts the HTTP server for serving doc contents, schedules
   * sending {@code DocId}s on a schedule, and optionally sends {@code DocId}s
   * on startup.
   */
  public static GsaCommunicationHandler main(Adaptor adaptor, String[] args) {
    return main(adaptor, args, null);
  }

  /**
   * Standard main for all adaptors (including those not extending
   * AbstractAdaptor).
   *
   * <p>This method starts the HTTP server for serving doc contents, schedules
   * sending {@code DocId}s on a schedule, and optionally sends {@code DocId}s
   * on startup.
   */
  public static GsaCommunicationHandler main(Adaptor adaptor, String[] args,
                                             GetDocIdsErrorHandler handler) {
    Config config = new Config();
    adaptor.initConfig(config);
    config.autoConfig(args);

    if (config.useAdaptorAutoUnzip()) {
      adaptor = new AutoUnzipAdaptor(adaptor);
    }
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    if (handler != null) {
      // Override default handler.
      gsa.setGetDocIdsErrorHandler(handler);
    }

    // Setup providing content.
    try {
      gsa.start();
      log.info("doc content serving started");
    } catch (Exception e) {
      throw new RuntimeException("could not start serving", e);
    }

    if (config.isAdaptorPushDocIdsOnStartup()) {
      log.info("Pushing once at program start");
      gsa.checkAndScheduleImmediatePushOfDocIds();
    }

    return gsa;
  }
}
