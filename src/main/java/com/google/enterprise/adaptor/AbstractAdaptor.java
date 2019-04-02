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
  public void init(AdaptorContext context) throws Exception {}

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
   * @param adaptor to be initialized
   * @param args command line params passed to config
   * @return the application instance in use
   */
  public static Application main(Adaptor adaptor, String[] args) {
    return Application.main(adaptor, args);
  }
}
