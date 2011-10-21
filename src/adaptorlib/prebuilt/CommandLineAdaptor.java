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

package adaptorlib.prebuilt;

import adaptorlib.AbstractAdaptor;
import adaptorlib.Adaptor;
import adaptorlib.Config;
import adaptorlib.DocId;
import adaptorlib.GsaCommunicationHandler;
import adaptorlib.ScheduleOncePerDay;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Command Line Adaptor
 */
public class CommandLineAdaptor extends AbstractAdaptor {
  private static final Logger log = Logger.getLogger(CommandLineAdaptor.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  @Override
  public void getDocIds(DocIdPusher pusher) throws IOException,
         InterruptedException {
    int commandResult;
    Command command = newListerCommand();

    try {
      log.finest("Command: ./list-doc-ids-filesystem.sh");
      commandResult = command.exec(new String[] {"./list-doc-ids.sh"});
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
        log.finest("Pushing Doc ID: '" + docId + "'");
      }
      pusher.pushDocIds(docIds);
    }
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    int commandResult;
    Command command = newRetrieverCommand();

    try {
      log.finest("Command: ./get-doc-contents-filesystem.sh " + id.getUniqueId());
      commandResult = command.exec(
          new String[] {"./get-doc-contents.sh", id.getUniqueId()});
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
      log.finest("Returning document contents for ID '" + id.getUniqueId() + ".");
    }
    resp.getOutputStream().write(command.getStdout());
  }

  protected Command newListerCommand() {
    return new Command();
  }

  protected Command newRetrieverCommand() {
    return new Command();
  }

  /** An example main for an adaptor that enables serving. */
  public static void main(String a[]) throws InterruptedException {
    Config config = new Config();
    config.autoConfig(a);
    Adaptor adaptor = new CommandLineAdaptor();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content.
    try {
      gsa.beginListeningForContentRequests();
      log.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    // Push once at program start.
    gsa.pushDocIds();

    // Setup regular pushing of doc ids for once per day.
    gsa.beginPushingDocIds(
      new ScheduleOncePerDay(/*hour*/3, /*minute*/0, /*second*/0));
    log.info("doc id pushing has been put on schedule");
  }
}
