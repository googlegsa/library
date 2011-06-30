package adaptorlib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;

/** This class handles the communications with GSA. */
public class GsaCommunicationHandler {
  private static final Logger LOG
      = Logger.getLogger(GsaCommunicationHandler.class.getName());

  private final int port;
  private final Adaptor adaptor;

  public GsaCommunicationHandler(Adaptor adaptor) {
    this.port = Config.getLocalPort();
    this.adaptor = adaptor;
  }

  /** Starts listening for communications from GSA. */
  public void beginListeningForContentRequests() throws IOException {
    InetSocketAddress addr = new InetSocketAddress(port);
    HttpServer server = HttpServer.create(addr, 0);
    server.createContext("/sso", new SsoHandler());
    // Disable SecurityHandler until it can query adapter for configuration
    server.createContext(Config.getBaseUri().getPath() + Config.getDocIdPath(),
        /*new SecurityHandler(*/new DocumentHandler(adaptor)/*)*/);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    LOG.info("server is listening on port #" + port);
  }

  public void beginPushingDocIds(ScheduleIterator schedule) {
    Scheduler pushScheduler = new Scheduler();
    pushScheduler.schedule(new Scheduler.Task() {
      public void run() {
        // TODO: Prevent two simultenous calls.
        LOG.info("about to get doc ids");
        List<DocId> handles = adaptor.getDocIds();
        LOG.info("about to push " + handles.size() + " doc ids");
        GsaCommunicationHandler.pushDocIds("testfeed", handles);
        LOG.info("done pushing doc ids");
      }
    }, schedule);
  }

  private static void pushSizedBatchOfDocIds(String feedSourceName,
      List<DocId> handles) {
    String xmlFeedFile = GsaFeedFileMaker.makeMetadataAndUrlXml(
        feedSourceName, handles);
    boolean keepGoing = true;
    for (int ntries = 0; keepGoing; ntries++) {
      try {
        GsaFeedFileSender.sendMetadataAndUrl(feedSourceName, xmlFeedFile);
        keepGoing = false;  // Sent.
      } catch (GsaFeedFileSender.FailedToConnect ftc) {
        LOG.warning("" + ftc);
        keepGoing = Config.handleFailedToConnect(ftc, ntries);
      } catch (GsaFeedFileSender.FailedWriting fw) {
        LOG.warning("" + fw);
        keepGoing = Config.handleFailedToConnect(fw, ntries);
      } catch (GsaFeedFileSender.FailedReadingReply fr) {
        LOG.warning("" + fr);
        keepGoing = Config.handleFailedToConnect(fr, ntries);
      }
    }
  }

  /** Makes and sends metadata-and-url feed files to GSA. */
  public static void pushDocIds(String feedSourceName, List<DocId> handles) {
    final int MAX = Config.getUrlsPerFeedFile();
    int totalPushed = 0;
    for (int i = 0; i < handles.size(); i += MAX) {
      int endIndex = i + MAX;
      if (endIndex > handles.size()) {
        endIndex = handles.size();
      }
      List<DocId> batch = handles.subList(i, endIndex);
      pushSizedBatchOfDocIds(feedSourceName, batch);
      totalPushed += batch.size();
    }
    if (handles.size() != totalPushed) {
      throw new IllegalStateException();
    }
  }
}
