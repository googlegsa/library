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

import static com.google.enterprise.adaptor.DocIdPusher.FeedType.FULL;
import static com.google.enterprise.adaptor.DocIdPusher.FeedType.INCREMENTAL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All logic for sending DocIds to the GSA from an adaptor.
 */
class DocIdSender extends AbstractDocIdPusher
    implements AsyncDocIdSender.ItemPusher {
  private static final Logger log
      = Logger.getLogger(DocIdSender.class.getName());

  private final GsaFeedFileMaker fileMaker;
  private final GsaFeedFileSender fileSender;
  private final FeedArchiver fileArchiver;
  private final Journal journal;
  private final Config config;
  private final Adaptor adaptor;
  private final ExceptionHandler defaultErrorHandler
      = ExceptionHandlers.defaultHandler();

  public DocIdSender(GsaFeedFileMaker fileMaker, GsaFeedFileSender fileSender,
      FeedArchiver fileArchiver, Journal journal, Config config,
      Adaptor adaptor) {
    this.fileMaker = fileMaker;
    this.fileSender = fileSender;
    this.fileArchiver = fileArchiver;
    this.journal = journal;
    this.config = config;
    this.adaptor = adaptor;
  }

  /**
   * Calls {@link Adaptor#getDocIds}. This method blocks until all DocIds are
   * sent or retrying failed.
   */
  public void pushFullDocIdsFromAdaptor(ExceptionHandler handler)
      throws InterruptedException {
    if (handler == null) {
      throw new NullPointerException();
    }
    log.info("Beginning getDocIds");
    journal.recordFullPushStarted();
    for (int ntries = 1;; ntries++) {
      boolean keepGoing = true;
      try {
        adaptor.getDocIds(this);
        break; // Success
      } catch (InterruptedException ex) {
        // Stop early.
        journal.recordFullPushInterrupted();
        log.info("Interrupted. Aborted getDocIds");
        throw ex;
      } catch (Exception ex) {
        log.log(Level.WARNING, "Exception during getDocIds", ex);
        keepGoing = handler.handleException(ex, ntries);
      } catch (Error t) {
        // Stop early in case of Error
        journal.recordFullPushFailed();
        throw t;
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attempts: {0}", ntries);
      } else {
        journal.recordFullPushFailed();
        log.warning("Gave up. Failed getDocIds");
        return; // Bail
      }
    }
    journal.recordFullPushSuccessful();
    log.info("Completed getDocIds");
  }

  /**
   * Calls {@link Adaptor#getModifiedDocIds}. This method blocks until all
   * DocIds are sent or retrying failed.
   */
  public void pushIncrementalDocIdsFromAdaptor(PollingIncrementalLister lister,
      ExceptionHandler handler) throws InterruptedException {
    if (handler == null) {
      throw new NullPointerException();
    }
    log.info("Beginning getModifiedDocIds");
    journal.recordIncrementalPushStarted();
    for (int ntries = 1;; ntries++) {
      boolean keepGoing = true;
      try {
        lister.getModifiedDocIds(this);
        break; // Success
      } catch (InterruptedException ex) {
        // Stop early.
        journal.recordIncrementalPushInterrupted();
        log.info("Interrupted. Aborted getModifiedDocIds");
        throw ex;
      } catch (Exception ex) {
        log.log(Level.WARNING, "Exception during getModifiedDocIds", ex);
        keepGoing = handler.handleException(ex, ntries);
      } catch (Error t) {
        // Stop early in case of Error
        journal.recordIncrementalPushFailed();
        throw t;
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attempts: {0}", ntries);
      } else {
        journal.recordIncrementalPushFailed();
        log.warning("Gave up. Failed getModifiedDocIds");
        return; // Bail
      }
    }
    journal.recordIncrementalPushSuccessful();
    log.info("Completed getModifiedDocIds");
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. Generally, you should
   * use {@link #pushDocIds()} instead of this method. However, if you want to
   * push just a few DocIds to the GSA manually, this is the method to use.
   * This method blocks until all DocIds are sent or retrying failed.
   */
  @Override
  public Record pushRecords(Iterable<Record> items, ExceptionHandler handler)
      throws InterruptedException {
    return pushItems(items.iterator(), handler);
  }

  @Override
  public DocId pushNamedResources(Map<DocId, Acl> resources,
                                  ExceptionHandler handler)
      throws InterruptedException {
    if (config.markAllDocsAsPublic()) {
      log.finest("Ignoring attempt to send ACLs to the GSA because "
                 + "markAllDocsAsPublic is true.");
      return null;
    }
    List<AclItem> acls = new ArrayList<AclItem>(resources.size());
    for (Map.Entry<DocId, Acl> me : resources.entrySet()) {
      acls.add(new AclItem(me.getKey(), me.getValue()));
    }
    log.log(Level.FINE, "about to push named resources: {0}", acls);
    AclItem acl = pushItems(acls.iterator(), handler);
    DocId result = (acl == null) ? null : acl.getDocId();
    log.log(Level.FINE, "return value: {0}", result);
    return result;
  }

  @Override
  public <T extends Item> T pushItems(Iterator<T> items,
      ExceptionHandler handler) throws InterruptedException {
    log.log(Level.INFO, "Pushing items");
    if (handler == null) {
      handler = defaultErrorHandler;
    }
    boolean firstBatch = true;
    final int max = config.getFeedMaxUrls();
    while (items.hasNext()) {
      List<T> batch = new ArrayList<T>();
      for (int j = 0; j < max; j++) {
        if (!items.hasNext()) {
          break;
        }
        batch.add(items.next());
      }
      log.log(Level.INFO, "Pushing group of {0} items", batch.size());
      T failedId;
      try {
        failedId = pushSizedBatchOfItems(batch, handler);
      } catch (InterruptedException ex) {
        if (firstBatch) {
          throw ex;
        } else {
          // If this is not the first batch, then some items have already been
          // sent. Thus, return gracefully instead of throwing an exception so
          // that the caller can discover what was sent.
          log.log(Level.INFO, "Pushing items interrupted");
          Thread.currentThread().interrupt();
          return batch.get(0);
        }
      }
      if (failedId != null) {
        log.log(Level.INFO, "Failed to push all items. Failed on: {0}",
            failedId);
        return failedId;
      }
      firstBatch = false;
      journal.recordDocIdPush(batch);
    }
    log.info("Pushed items");
    return null;
  }

  @Override
  public GroupPrincipal pushGroupDefinitions(
      Map<GroupPrincipal, ? extends Collection<Principal>> defs,
      boolean caseSensitive, FeedType feedType, String groupSource,
      ExceptionHandler handler) throws InterruptedException {
    if (config.markAllDocsAsPublic()) {
      log.finest("Ignoring attempt to send groups to the GSA because "
                 + "markAllDocsAsPublic is true.");
      return null;
    }
    return pushGroupDefinitionsInternal(
        defs, caseSensitive, feedType, groupSource, handler);
  }

  /**
   * Issue: Full vs. Incremental Feeds for Groups.
   *
   * The problem with supplying all the group definitions in a single full
   * feed is the GSA limit to the size of a feed file (1GB). Although this
   * is a large limit, several of our customers would easily exceed that.
   * One customer has 100,000 groups averaging 10,000 members each. Another
   * has 400,000 groups.
   *
   * A novel solution to this is to double-buffer the group definitions for
   * each data source. We maintain two pseudo data sources for each actual
   * data source (*-FULL1 and *-FULL2). Each "full" upload alternates between
   * these two, using batches of incremental feeds. As each batch is processed,
   * groups defined in that batch are re-assigned from the older data source
   * to the newer one. Once all batches have been uploaded, the only groups
   * remaining in the older data source are the ones that were missing from
   * the newer feeds (in other words, deleted from the repository). We then
   * "delete" these stale entries by sending an empty, but true full feed
   * for the older data source. After this, the GSA should only have group
   * definitions for the newer data source.
   *
   * If there is a failure mid-push, the GSA groups database may have entries
   * for both data sources. Since the adaptors do not maintain persistent
   * state, on a restart it will not know which is "older" vs. "newer".
   * This is not fatal however, as the only downside might be some deleted
   * groups persisting through one or two "full" pushes before they get
   * cleaned up.
   *
   * This maps the connector-supplied group source to the last used pseudonym,
   * e.g. {@code foo -> foo-FULL1}.
   */
  private Map<String, String> groupSources = new HashMap<String, String>();

  /**
   * Returns the previous group source pushed to. If there was no previous
   * group source, returns the supplied source. If a connector only ever does
   * INCREMENTAL feeds, then that group source will always be used. This is
   * also backward compatible. If a connector is upgraded to a new version that
   * supports FULL feeds, then the older incremental group source will
   * eventually get deleted from the GSA.
   */
  private String previousGroupSource(String source) {
    String prevSource = groupSources.get(source);
    return (prevSource == null) ? source : prevSource;
  }

  /** Returns the alternate to the previous group source. */
  private String alternateGroupSource(String source) {
    return (source + "-FULL1").equals(groupSources.get(source))
        ? (source + "-FULL2") : (source + "-FULL1");
  }

  /*
   * Internal version of pushGroupDefinitions() to add the parameterized generic
   * T. We need the parameter to be able to create a List and add Map.Entries to
   * that list.
   *
   * Unfortunately, due to a limitation in Java (which is still present in
   * Java 7), the generics in the methods this one calls are forced to be
   * parameterized even though it should be unnecessary. Fortunately, our API
   * does not trigger this issue; it is only internal code that suffers.
   *
   * As an example test, these generics work fine:
   *   private void method1(List<?> args) {
   *     method2(args);
   *   }
   *
   *   private <T> void method2(List<T> args) {
   *     method1(args);
   *   }
   *
   * Whereas these both fail to find the other method:
   *   private void method3(List<List<?>> args) {
   *     method4(args);
   *   }
   *
   *  private <T> void method4(List<List<T>> args) {
   *    method3(args);
   *  }
   *
   * And so having any container of Map.Entry breaks mixing of parameterized
   * and wildcard generic types.
   *
   * Luckily when mixed with concrete types it is only half broken:
   *   List<List<Object>> l = null;
   *   method3(l); // Fails
   *   method4(l); // Compiles
   */
  private <T extends Collection<Principal>> GroupPrincipal
      pushGroupDefinitionsInternal(
      Map<GroupPrincipal, T> defs,
      boolean caseSensitive, FeedType feedType, String groupSource,
      ExceptionHandler handler) throws InterruptedException {
    int numGroups = 0;
    int numMembers = 0;
    if (defs.isEmpty()) {
      log.log(Level.FINE,
          "called pushGroupDefinitions() with no groups to push");
      return null;
    }
    journal.recordGroupPushStarted();
    String gsaVerString = config.getGsaVersion();
    GsaVersion gsaVersion = new GsaVersion(gsaVerString);
    if (!gsaVersion.isAtLeast("7.2.0-0")) {
      log.log(Level.WARNING,
          "GSA ver {0} does not accept group definitions", gsaVerString);
      journal.recordGroupPushFailed();
      return defs.entrySet().iterator().next().getKey();
    }
    if (feedType == FULL && !gsaVersion.isAtLeast("7.4.0-0")) {
      log.log(Level.WARNING,
          "GSA ver {0} does not support per-source replacement of all groups",
          gsaVerString);
      feedType = INCREMENTAL;
    }
    if (null == handler) {
      handler = defaultErrorHandler;
    }
    if (groupSource == null) {
      groupSource = config.getFeedName();
    }
    log.log(Level.INFO, "Starting {0} push of {1} groups from source {2}",
        new Object[] { feedType, defs.size(), groupSource });
    String feedSourceName;
    if (feedType == INCREMENTAL) {
      feedSourceName = previousGroupSource(groupSource);
    } else {
      feedSourceName = alternateGroupSource(groupSource);
      log.log(Level.FINE, "Writing to alternate group source {0}",
          feedSourceName);
    }
    boolean firstBatch = true;
    final int max = config.getFeedMaxUrls();
    Iterator<Map.Entry<GroupPrincipal, T>> defsIterator
        = defs.entrySet().iterator();
    List<Map.Entry<GroupPrincipal, T>> batch
        = new ArrayList<Map.Entry<GroupPrincipal, T>>();
    int batchMemberCount;
    while (defsIterator.hasNext()) {
      batch.clear();
      batchMemberCount = 0;
      for (int j = 0; j < max; j++) {
        if (!defsIterator.hasNext()) {
          break;
        }
        Map.Entry<GroupPrincipal, T> nextGroup = defsIterator.next();
        batchMemberCount += nextGroup.getValue().size();
        batch.add(nextGroup);
      }
      log.log(Level.INFO, "Pushing batch of {0} groups", batch.size());
      GroupPrincipal failedId;
      try {
        failedId = pushSizedBatchOfGroups(batch, caseSensitive,
            INCREMENTAL, feedSourceName, handler);
        if (failedId == null) {
          // TODO(myk): determine if it makes sense to count the include the
          // counts from a partial batch in our totals (if the batch fails).
          numGroups += batch.size();
          numMembers += batchMemberCount;
        }
      } catch (InterruptedException ex) {
        journal.recordGroupPushInterrupted();
        if (firstBatch) {
          throw ex;
        } else {
          // If this is not the first batch, then some items have already been
          // sent. Thus, return gracefully instead of throwing an exception so
          // that the caller can discover what was sent.
          log.log(Level.INFO, "Pushing groups interrupted");
          Thread.currentThread().interrupt();
          return batch.get(0).getKey();
        }
      }
      if (failedId != null) {
        log.log(Level.INFO, "Failed to push all groups. Failed on: {0}",
            failedId);
        journal.recordGroupPushFailed();
        return failedId;
      }
      firstBatch = false;
    }
    log.log(Level.INFO, "Pushed {0} groups containing {1} memberships",
        new Object[] { numGroups, numMembers });
    if (0 != numGroups) {
      double mean = ((double) numMembers) / numGroups;
      log.finer("mean size of groups: " + mean);
    }

    if (feedType == FULL) {
      String oldGroupSource;
      synchronized (groupSources) {
        // Remember the latest target group source fed.
        oldGroupSource = previousGroupSource(groupSource);
        groupSources.put(groupSource, feedSourceName);
      }
      // Delete any entries from the previous group source by pushing an
      // empty full feed.
      log.log(Level.FINE, "Deleting entries from previous group source {0}",
          oldGroupSource);
      List<Map.Entry<GroupPrincipal, T>> emptyList = Collections.emptyList();
      pushSizedBatchOfGroups(emptyList, caseSensitive, FULL, oldGroupSource,
          handler);
    }
    journal.recordGroupPushSuccessful();
    return null;
  }

  private <T extends Collection<Principal>> GroupPrincipal
      pushSizedBatchOfGroups(
      List<Map.Entry<GroupPrincipal, T>> defs,
      boolean caseSensitive, FeedType feedType, String feedSourceName,
      ExceptionHandler handler) throws InterruptedException {
    String groupsDefXml
        = fileMaker.makeGroupDefinitionsXml(defs, caseSensitive);
    boolean keepGoing = true;
    boolean success = false;
    log.log(Level.INFO, "pushing groups");
    for (int ntries = 1; keepGoing; ntries++) {
      try {
        log.info("sending groups to GSA host name: " + config.getGsaHostname());
        fileSender.sendGroups(feedSourceName, feedType.toString(),
            groupsDefXml, config.isServerToUseCompression());
        keepGoing = false;  // Sent.
        success = true;
      } catch (IOException ex) {
        log.log(Level.WARNING, "failed to send groups", ex);
        keepGoing = handler.handleException(ex, ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "trying again... number of attempts: {0}", ntries);
      }
    }
    GroupPrincipal last = null;
    if (success) {
      log.info("pushing groups batch succeeded");
      fileArchiver.saveFeed(feedSourceName, groupsDefXml);
      journal.recordGroupPush(defs);
    } else {
      last = defs.get(0).getKey();  // checked in pushGroupDefinitionsInternal()
      log.log(Level.WARNING, "gave up pushing groups. First item: {0}", last);
      fileArchiver.saveFailedFeed(feedSourceName, groupsDefXml);
    }
    log.info("finished pushing batch of groups");
    return last;
  }


  private <T extends Item> T pushSizedBatchOfItems(List<T> items,
                                         ExceptionHandler handler)
      throws InterruptedException {
    String feedSourceName = config.getFeedName();
    String xmlFeedFile = fileMaker.makeMetadataAndUrlXml(feedSourceName, items);
    boolean keepGoing = true;
    boolean success = false;
    log.log(Level.INFO, "Pushing batch of {0} items to GSA", items.size());
    for (int ntries = 1; keepGoing; ntries++) {
      try {
        log.info("Sending items to GSA host: " + config.getGsaHostname());
        fileSender.sendMetadataAndUrl(feedSourceName, xmlFeedFile,
                                      config.isServerToUseCompression());
        keepGoing = false;  // Sent.
        success = true;
      } catch (IOException ex) {
        log.log(Level.WARNING, "Failed to send items", ex);
        keepGoing = handler.handleException(ex, ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attempts: {0}", ntries);
      }
    }
    if (success) {
      log.info("Pushing batch succeeded");
      fileArchiver.saveFeed(feedSourceName, xmlFeedFile);
    } else {
      log.log(Level.WARNING, "Gave up. First item in list: {0}", items.get(0));
      fileArchiver.saveFailedFeed(feedSourceName, xmlFeedFile);
    }
    log.info("Finished pushing batch of items");
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

    @Override
    public boolean equals(Object o) {
      boolean same = false;
      if (null != o && this.getClass().equals(o.getClass())) {
        AclItem other = (AclItem) o;
        same = id.equals(other.id) && acl.equals(other.acl)
            && (docIdFragment == null
                    ? other.docIdFragment == null
                    : docIdFragment.equals(other.docIdFragment));
      }
      return same;
    }

    @Override
    public int hashCode() {
      Object members[] = new Object[] { id, acl, docIdFragment };
      return Arrays.hashCode(members);
    }

    @Override 
    public String toString() {
      return "AclItem(" + id + "," + docIdFragment + "," + acl + ")";
    }
  }
}
