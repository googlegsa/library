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
  public void pushDocIdsFromAdaptor(GetDocIdsErrorHandler handler)
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
   * Makes and sends metadata-and-url feed files to GSA. Generally, you should
   * use {@link #pushDocIds()} instead of this method. However, if you want to
   * push just a few DocIds to the GSA manually, this is the method to use.
   * This method blocks until all DocIds are sent or retrying failed.
   */
  @Override
  public Record pushRecords(Iterable<Record> records,
                              PushErrorHandler handler)
      throws InterruptedException {
    if (handler == null) {
      handler = defaultErrorHandler;
    }
    return pushRecords(records.iterator(), handler);
  }

  private Record pushRecords(Iterator<Record> records,
                               PushErrorHandler handler)
      throws InterruptedException {
    log.log(Level.INFO, "Pushing DocIds");
    final int max = config.getFeedMaxUrls();
    while (records.hasNext()) {
      List<Record> batch = new ArrayList<Record>();
      for (int j = 0; j < max; j++) {
        if (!records.hasNext()) {
          break;
        }
        batch.add(records.next());
      }
      log.log(Level.INFO, "Pushing group of {0} DocIds", batch.size());
      Record failedId = pushSizedBatchOfRecords(batch, handler);
      if (failedId != null) {
        log.info("Failed to push all ids. Failed on docId: " + failedId);
        return failedId;
      }
      journal.recordDocIdPush(batch);
    }
    log.info("Pushed DocIds");
    return null;
  }

  private Record pushSizedBatchOfRecords(List<Record> records,
                                           PushErrorHandler handler)
      throws InterruptedException {
    String feedSourceName = config.getFeedName();
    String xmlFeedFile = fileMaker.makeMetadataAndUrlXml(feedSourceName,
        records);
    boolean keepGoing = true;
    boolean success = false;
    log.log(Level.INFO, "Pushing batch of {0} DocIds to GSA", records.size());
    for (int ntries = 1; keepGoing; ntries++) {
      try {
        log.info("Sending feed to GSA host name: " + config.getGsaHostname());
        fileSender.sendMetadataAndUrl(config.getGsaHostname(), feedSourceName,
                                      xmlFeedFile);
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
      log.log(Level.WARNING, "Gave up. First item in list: {0}",
              records.get(0));
    }
    log.info("Finished pushing batch");
    return success ? null : records.get(0);
  }
}
