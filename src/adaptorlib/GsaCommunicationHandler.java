package adaptorlib;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** This class handles the communications with GSA. */
public class GsaCommunicationHandler {
  private static final Logger log
      = Logger.getLogger(GsaCommunicationHandler.class.getName());

  private final Adaptor adaptor;
  private final Config config;
  private final GsaFeedFileSender fileSender;
  private final GsaFeedFileMaker fileMaker;
  private final Journal journal = new Journal();
  private final Adaptor.DocIdPusher pusher = new InnerDocIdPusher();
  private final Adaptor.GetDocIdsErrorHandler defaultErrorHandler
      = new DefaultGetDocIdsErrorHandler();

  public GsaCommunicationHandler(Adaptor adaptor, Config config) {
    // TODO(ejona): allow the adaptor to choose whether it wants this feature
    this.adaptor = new AutoUnzipAdaptor(adaptor);
    this.adaptor.setDocIdPusher(pusher);

    this.config = config;
    this.fileSender = new GsaFeedFileSender(config.getGsaCharacterEncoding());
    this.fileMaker = new GsaFeedFileMaker(this);
  }

  /** Starts listening for communications from GSA. */
  public void beginListeningForContentRequests() throws IOException {
    int port = config.getServerPort();
    InetSocketAddress addr = new InetSocketAddress(port);
    HttpServer server = HttpServer.create(addr, 0);
    server.createContext("/dashboard", new DashboardHandler(config, journal));
    server.createContext("/sso", new SsoHandler(config.getServerHostname(),
        config.getGsaCharacterEncoding()));
    // Disable SecurityHandler until it can query adapter for configuration
    server.createContext(config.getServerBaseUri().getPath()
        + config.getServerDocIdPath(),
        new DocumentHandler(config.getServerHostname(),
                            config.getGsaCharacterEncoding(), this, adaptor));
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    log.info("GSA host name: " + config.getGsaHostname());
    log.info("server is listening on port #" + port);
  }

  /**
   * Schedule {@link Adaptor#getDocIds} to be called when defined by the {@code
   * schedule}. Equivalent to {@code beginPushingDocIds(schedule, null)}.
   *
   * @see #beginPushingDocIds(Iterator, Adaptor.GetDocIdsErrorHandler)
   */
  public void beginPushingDocIds(Iterator<Date> schedule) {
    beginPushingDocIds(schedule, null);
  }

  /**
   * Schedule {@link Adaptor#getDocIds} to be called when defined by the {@code
   * schedule}. If {@code handler} is {@code null}, then a default error handler
   * will be used.
   */
  public void beginPushingDocIds(Iterator<Date> schedule,
                                 final Adaptor.GetDocIdsErrorHandler handler) {
    Scheduler pushScheduler = new Scheduler();
    pushScheduler.schedule(new Scheduler.Task() {
      public void run() {
        try {
          pushDocIds(handler);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
    }, schedule);
  }

  private DocId pushSizedBatchOfDocIds(List<DocId> docIds,
                                       Adaptor.PushErrorHandler handler)
      throws InterruptedException {
    String feedSourceName = config.getFeedName();
    String xmlFeedFile = fileMaker.makeMetadataAndUrlXml(
        feedSourceName, docIds);
    boolean keepGoing = true;
    boolean success = false;
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
    return success ? null : docIds.get(0);
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. This method blocks
   * until all DocIds are sent or retrying failed. Equivalent to {@code
   * pushDocIds(null)}.
   */
  public void pushDocIds() throws InterruptedException {
    pushDocIds(null);
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. This method blocks
   * until all DocIds are sent or retrying failed. If {@code handler} is {@code
   * null}, then a default error handler is used.
   */
  public void pushDocIds(Adaptor.GetDocIdsErrorHandler handler)
      throws InterruptedException {
    if (handler == null) {
      handler = defaultErrorHandler;
    }
    log.info("Getting list of DocIds");
    for (int ntries = 1;; ntries++) {
      boolean keepGoing = true;
      try {
        adaptor.getDocIds(pusher);
        break; // Success
      } catch (Exception ex) {
        log.log(Level.WARNING, "Unable to retrieve DocIds from adaptor", ex);
        keepGoing = handler.handleFailedToGetDocIds(ex, ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      } else {
        return; // Bail
      }
    }
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. Generally, you should
   * use {@link #pushDocIds()} instead of this method. However, if you want to
   * push just a few DocIds to the GSA manually, this is the method to use.
   * This method blocks until all DocIds are sent or retrying failed.
   */
  private DocId pushDocIds(Iterator<DocId> docIds,
                           Adaptor.PushErrorHandler handler)
      throws InterruptedException {
    log.log(Level.INFO, "Pushing DocIds");
    final int max = config.getFeedMaxUrls();
    while (docIds.hasNext()) {
      List<DocId> batch = new ArrayList<DocId>();
      for (int j = 0; j < max; j++) {
        if (!docIds.hasNext()) {
          break;
        }
        batch.add(docIds.next());
      }
      log.log(Level.INFO, "Pushing group of {0} DocIds", batch.size());
      DocId failedId = pushSizedBatchOfDocIds(batch, handler);
      if (failedId != null) {
        log.info("Failed to push all ids. Failed on docId: " + failedId);
        return failedId;
      }
      journal.recordDocIdPush(batch);
    }
    log.info("Pushed DocIds");
    return null;
  }

  URI encodeDocId(DocId docId) {
    if (config.isDocIdUrl()) {
      return URI.create(docId.getUniqueId());
    } else {
      URI base = config.getServerBaseUri(docId);
      URI resource;
      String uniqueId = docId.getUniqueId();
      // Add two dots to any sequence of only dots. This is to allow "/../" and
      // "/./" within DocIds.
      uniqueId = uniqueId.replaceAll("(^|/)(\\.+)(?=$|/)", "$1$2..");
      try {
        resource = new URI(null, null, base.getPath()
                           + config.getServerDocIdPath() + uniqueId, null);
      } catch (URISyntaxException ex) {
        throw new IllegalStateException(ex);
      }
      return base.resolve(resource);
    }
  }

  /** Given a URI that was used in feed file, convert back to doc id. */
  DocId decodeDocId(URI uri) {
    if (config.isDocIdUrl()) {
      return new DocId(uri.toString());
    } else {
      String basePath = config.getServerBaseUri().getPath();
      String id = uri.getPath().substring(basePath.length()
          + config.getServerDocIdPath().length());
      // Remove two dots from any sequence of only dots. This is to remove the
      // addition we did in {@link #encodeDocId}.
      id = id.replaceAll("(^|/)(\\.+)\\.\\.(?=$|/)", "$1$2");
      return new DocId(id);
    }
  }

  URI formNamespacedUri(String namespace) {
    URI uri;
    try {
      uri = new URI(null, null, config.getServerBaseUri().getPath() + namespace,
                    null);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
    return config.getServerBaseUri().resolve(uri);
  }

  Journal getJournal() {
    return journal;
  }

  private class InnerDocIdPusher implements Adaptor.DocIdPusher {
    private Adaptor.PushErrorHandler defaultErrorHandler
        = new DefaultPushErrorHandler();

    public DocId pushDocIds(Iterable<DocId> docIds)
        throws InterruptedException {
      return pushDocIds(docIds, null);
    }

    public DocId pushDocIds(Iterable<DocId> docIds,
                            Adaptor.PushErrorHandler handler)
        throws InterruptedException {
      if (handler == null) {
        handler = defaultErrorHandler;
      }
      return GsaCommunicationHandler.this.pushDocIds(docIds.iterator(),
                                                     handler);
    }
  }
}
