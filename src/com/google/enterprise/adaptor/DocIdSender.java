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

import java.util.*;
import java.util.logging.*;

/**
 * All logic for sending DocIds to the GSA from an adaptor.
 */
class DocIdSender extends AbstractDocIdPusher {
  private static final Logger log
      = Logger.getLogger(DocIdSender.class.getName());

  private final GsaFeedFileMaker fileMaker;
  private final GsaFeedFileSender fileSender;
  private final Journal journal;
  private final Config config;
  private final Adaptor adaptor;
  private final PushErrorHandler defaultErrorHandler
      = new DefaultPushErrorHandler();

  public DocIdSender(GsaFeedFileMaker fileMaker, GsaFeedFileSender fileSender,
                     Journal journal, Config config, Adaptor adaptor) {
    this.fileMaker = fileMaker;
    this.fileSender = fileSender;
    this.journal = journal;
    this.config = config;
    this.adaptor = adaptor;
  }

  /**
   * Calls {@link Adaptor#getDocIds}. This method blocks until all DocIds are
   * sent or retrying failed.
   */
  public void pushFullDocIdsFromAdaptor(GetDocIdsErrorHandler handler)
      throws InterruptedException {
    if (handler == null) {
      throw new NullPointerException();
    }
    log.info("Beginning full push of DocIds");
    journal.recordFullPushStarted();
    for (int ntries = 1;; ntries++) {
      boolean keepGoing = true;
      try {
        adaptor.getDocIds(this);
        break; // Success
      } catch (InterruptedException ex) {
        // Stop early.
        journal.recordFullPushInterrupted();
        log.info("Interrupted. Aborted full push of DocIds");
        throw ex;
      } catch (Exception ex) {
        log.log(Level.WARNING, "Unable to retrieve DocIds from adaptor", ex);
        keepGoing = handler.handleFailedToGetDocIds(ex, ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      } else {
        journal.recordFullPushFailed();
        log.warning("Gave up. Failed full push of DocIds");
        return; // Bail
      }
    }
    journal.recordFullPushSuccessful();
    log.info("Completed full pushing DocIds");
  }

  /**
   * Calls {@link Adaptor#getModifiedDocIds}. This method blocks until all
   * DocIds are sent or retrying failed.
   */
  public void pushIncrementalDocIdsFromAdaptor(GetDocIdsErrorHandler handler)
      throws InterruptedException {
    if (handler == null) {
      throw new NullPointerException();
    }
    log.info("Beginning incremental push of DocIds");
    journal.recordIncrementalPushStarted();
    for (int ntries = 1;; ntries++) {
      boolean keepGoing = true;
      try {
        ((PollingIncrementalAdaptor) adaptor).getModifiedDocIds(this);
        break; // Success
      } catch (InterruptedException ex) {
        // Stop early.
        journal.recordIncrementalPushInterrupted();
        log.info("Interrupted. Aborted incremental push of DocIds");
        throw ex;
      } catch (Exception ex) {
        log.log(Level.WARNING, "Unable to retrieve DocIds from adaptor", ex);
        keepGoing = handler.handleFailedToGetDocIds(ex, ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      } else {
        journal.recordIncrementalPushFailed();
        log.warning("Gave up. Failed incremental push of DocIds");
        return; // Bail
      }
    }
    journal.recordIncrementalPushSuccessful();
    log.info("Completed incremental pushing DocIds");
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. Generally, you should
   * use {@link #pushDocIds()} instead of this method. However, if you want to
   * push just a few DocIds to the GSA manually, this is the method to use.
   * This method blocks until all DocIds are sent or retrying failed.
   */
  @Override
  public Record pushRecords(Iterable<Record> items, PushErrorHandler handler)
      throws InterruptedException {
    return pushItems(items.iterator(), handler);
  }

  @Override
  public DocId pushNamedResources(Map<DocId, Acl> resources,
                                  PushErrorHandler handler)
      throws InterruptedException {
    List<AclItem> acls = new ArrayList<AclItem>(resources.size());
    for (Map.Entry<DocId, Acl> me : resources.entrySet()) {
      acls.add(new AclItem(me.getKey(), me.getValue()));
    }
    AclItem acl = pushItems(acls.iterator(), handler);
    return acl == null ? null : acl.getDocId();
  }

  <T extends Item> T pushItems(Iterator<T> items,
      PushErrorHandler handler) throws InterruptedException {
    log.log(Level.INFO, "Pushing DocIds");
    if (handler == null) {
      handler = defaultErrorHandler;
    }
    final int max = config.getFeedMaxUrls();
    while (items.hasNext()) {
      List<T> batch = new ArrayList<T>();
      for (int j = 0; j < max; j++) {
        if (!items.hasNext()) {
          break;
        }
        batch.add(items.next());
      }
      log.log(Level.INFO, "Pushing group of {0} DocIds", batch.size());
      T failedId = pushSizedBatchOfRecords(batch, handler);
      if (failedId != null) {
        log.info("Failed to push all ids. Failed on docId: " + failedId);
        return failedId;
      }
      journal.recordDocIdPush(batch);
    }
    log.info("Pushed DocIds");
    return null;
  }

  private <T extends Item> T pushSizedBatchOfRecords(List<T> items,
                                         PushErrorHandler handler)
      throws InterruptedException {
    String feedSourceName = config.getFeedName();
    String xmlFeedFile = fileMaker.makeMetadataAndUrlXml(feedSourceName, items);
    boolean keepGoing = true;
    boolean success = false;
    log.log(Level.INFO, "Pushing batch of {0} DocIds to GSA", items.size());
    for (int ntries = 1; keepGoing; ntries++) {
      try {
        log.info("Sending feed to GSA host name: " + config.getGsaHostname());
        fileSender.sendMetadataAndUrl(config.getGsaHostname(), feedSourceName,
                                      xmlFeedFile,
                                      config.isServerToUseCompression());
        keepGoing = false;  // Sent.
        success = true;
      } catch (GsaFeedFileSender.FailedToConnect ftc) {
        log.log(Level.WARNING, "Unable to connect to the GSA", ftc);
        keepGoing = handler.handleFailedToConnect(
            (Exception) ftc.getCause(), ntries);
      } catch (GsaFeedFileSender.FailedWriting fw) {
        log.log(Level.WARNING, "Unable to write request to the GSA", fw);
        keepGoing = handler.handleFailedWriting(
            (Exception) fw.getCause(), ntries);
      } catch (GsaFeedFileSender.FailedReadingReply fr) {
        log.log(Level.WARNING, "Unable to read reply from GSA", fr);
        keepGoing = handler.handleFailedReadingReply(
            (Exception) fr.getCause(), ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      }
    }
    if (success) {
      log.info("Pushing batch succeeded");
    } else {
      log.log(Level.WARNING, "Gave up. First item in list: {0}", items.get(0));
    }
    log.info("Finished pushing batch");
    return success ? null : items.get(0);
  }

  /** Marker interface for an item that can exist in a feed. */
  interface Item {}

  /**
   * Represents the ACL tag sent in feeds.
   */
  static final class AclItem implements Item {
    private DocId id;
    private final String docIdFragment;
    private Acl acl;

    public AclItem(DocId id, Acl acl) {
      this(id, null, acl);
    }

    public AclItem(DocId id, String docIdFragment, Acl acl) {
      if (id == null || acl == null) {
        throw new NullPointerException("DocId and Acl must not be null");
      }
      this.id = id;
      this.docIdFragment = docIdFragment;
      this.acl = acl;
    }

    public DocId getDocId() {
      return id;
    }

    public String getDocIdFragment() {
      return docIdFragment;
    }

    public Acl getAcl() {
      return acl;
    }
  }
}
