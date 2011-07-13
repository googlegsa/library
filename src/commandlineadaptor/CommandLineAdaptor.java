package commandlineadaptor;
import adaptorlib.Adaptor;
import adaptorlib.Config;
import adaptorlib.DocId;
import adaptorlib.GsaCommunicationHandler;
import adaptorlib.ScheduleIterator;
import adaptorlib.ScheduleOncePerDay;

import adaptorlib.Command;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Command Line Adaptor
 */
class CommandLineAdaptor extends Adaptor {
  private final static Logger LOG = Logger.getLogger(CommandLineAdaptor.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  @Override
  public List<DocId> getDocIds() throws IOException {
    int commandResult;
    Command command = new Command();
    
    try {
      LOG.finest("Command: ./list-doc-ids-filesystem.sh");
      commandResult = command.exec(new String[] {"./list-doc-ids-filesystem.sh"});
    } catch (InterruptedException e) {
      throw new IOException("Thread interrupted while waiting for external command.", e);
    } catch (IOException e) {
      throw new IOException("External command could not be executed.", e);
    }
    if (commandResult != 0) {
      throw new IOException("External command error. code = " + commandResult + ".");
    } else {
      String docIdString = new String(command.getStdout(), encoding);
      ArrayList<DocId> docIds = new ArrayList<DocId>();
      for (String docId : docIdString.split("\n")) {
        docIds.add(new DocId(docId));
        LOG.finest("Pushing Doc ID: '" + docId + "'");
      }
      return docIds;
    }
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public byte[] getDocContent(DocId id) throws IOException {
    int commandResult;
    Command command = new Command();
    
    try {
      LOG.finest("Command: ./get-doc-contents-filesystem.sh " + id.getUniqueId());
      commandResult = command.exec(new String[] {"./get-doc-contents-filesystem.sh", id.getUniqueId()});
    } catch (InterruptedException e) {
      throw new IOException("Thread intrupted while waiting for external command.", e);
    } catch (IOException e) {
      throw new IOException("External command could not be executed.", e);
    }
    if (commandResult == 2) {
      throw new FileNotFoundException("Document not found.");
    } else if (commandResult != 0) {
      throw new IOException("External command error. code=" + commandResult + ".");
    } else {
      LOG.finest("Returning document contents for ID '" + id.getUniqueId() + ".");
      return command.getStdout();
    }
  }

  /** An example main for an adaptor that enables serving. */
  public static void main(String a[]) {
    Config config = new Config();
    config.autoConfig(a);
    Adaptor adaptor = new CommandLineAdaptor();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content.
    try {
      gsa.beginListeningForContentRequests();
      LOG.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    try {
      gsa.pushDocIds("commandLineAdaptor", adaptor.getDocIds());
    } catch (IOException e) {
      // TODO(johnfelton): Improve error recording when "journal" is available.
      LOG.severe(e.getMessage());
    }

    // Setup scheduled pushing of doc ids.
    ScheduleIterator everyNight = new ScheduleOncePerDay(/*hour*/3,
        /*minute*/0, /*second*/0);
    gsa.beginPushingDocIds(everyNight);
    LOG.info("doc id pushing has been put on schedule");
  }
}
