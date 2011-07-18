package adaptorlib;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
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

  public GsaCommunicationHandler(Adaptor adaptor, Config config) {
    // TODO(ejona): allow the adaptor to choose whether it wants this feature
    this.adaptor = new AutoUnzipAdaptor(adaptor);
    this.config = config;
    this.fileSender = new GsaFeedFileSender(config.getGsaCharacterEncoding());
    this.fileMaker = new GsaFeedFileMaker(this);
  }

  /** Starts listening for communications from GSA. */
  public void beginListeningForContentRequests() throws IOException {
    int port = config.getServerPort();
    InetSocketAddress addr = new InetSocketAddress(port);
    HttpServer server = HttpServer.create(addr, 0);
    server.createContext("/dashboard", new DashboardHandler(config));
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

  public void beginPushingDocIds(Iterator<Date> schedule) {
    Scheduler pushScheduler = new Scheduler();
    pushScheduler.schedule(new Scheduler.Task() {
      public void run() {
        try {
          pushDocIds();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
    }, schedule);
  }

  private void pushSizedBatchOfDocIds(List<DocId> handles)
      throws InterruptedException {
    String feedSourceName = config.getFeedName();
    String xmlFeedFile = fileMaker.makeMetadataAndUrlXml(
        feedSourceName, handles);
    boolean keepGoing = true;
    for (int ntries = 1; keepGoing; ntries++) {
      try {
        log.info("Sending feed to GSA host name: " + config.getGsaHostname());
        fileSender.sendMetadataAndUrl(config.getGsaHostname(), feedSourceName,
                                  xmlFeedFile);
        keepGoing = false;  // Sent.
      } catch (GsaFeedFileSender.FailedToConnect ftc) {
        log.log(Level.WARNING, "Unable to connect to the GSA", ftc);
        keepGoing = adaptor.handleFailedToConnect(
            (Exception) ftc.getCause(), ntries);
      } catch (GsaFeedFileSender.FailedWriting fw) {
        log.log(Level.WARNING, "Unable to write request to the GSA", fw);
        keepGoing = adaptor.handleFailedWriting(
            (Exception) fw.getCause(), ntries);
      } catch (GsaFeedFileSender.FailedReadingReply fr) {
        log.log(Level.WARNING, "Unable to read reply from GSA", fr);
        keepGoing = adaptor.handleFailedReadingReply(
            (Exception) fr.getCause(), ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      }
    }
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. This method blocks
   * until all DocIds are sent or retrying failed.
   */
  public void pushDocIds() throws InterruptedException {
    log.info("Getting list of DocIds");
    List<DocId> handles;
    for (int ntries = 1;; ntries++) {
      boolean keepGoing = true;
      try {
        handles = adaptor.getDocIds();
        break; // Success
      } catch (Exception ex) {
        log.log(Level.WARNING, "Unable to retrieve DocIds from adaptor", ex);
        keepGoing = adaptor.handleFailedToGetDocIds(ex, ntries);
      }
      if (keepGoing) {
        log.log(Level.INFO, "Trying again... Number of attemps: {0}", ntries);
      } else {
        return; // Bail
      }
    }
    pushDocIds(handles);
    Journal.recordDocIdPush(handles);
  }

  /**
   * Makes and sends metadata-and-url feed files to GSA. Generally, you should
   * use {@link #pushDocIds()} instead of this method. However, if you want to
   * push just a few DocIds to the GSA manually, this is the method to use.
   * This method blocks until all DocIds are sent or retrying failed.
   */
  public void pushDocIds(List<DocId> handles) throws InterruptedException {
    log.log(Level.INFO, "Pushing {0} DocIds", handles.size());
    final int max = config.getFeedMaxUrls();
    int totalPushed = 0;
    for (int i = 0; i < handles.size(); i += max) {
      int endIndex = i + max;
      if (endIndex > handles.size()) {
        endIndex = handles.size();
      }
      List<DocId> batch = handles.subList(i, endIndex);
      pushSizedBatchOfDocIds(batch);
      totalPushed += batch.size();
    }
    if (handles.size() != totalPushed) {
      throw new IllegalStateException();
    }
    log.info("Pushed DocIds");
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
}
