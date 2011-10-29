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
import adaptorlib.CommandStreamParser;
import adaptorlib.DocId;
import adaptorlib.DocIdPusher;
import adaptorlib.Request;
import adaptorlib.Response;

import java.io.ByteArrayInputStream;
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
      log.finest("Command: ./list-doc-ids.sh");
      commandResult = command.exec(new String[] {"./list-doc-ids.sh"});
    } catch (InterruptedException e) {
      throw new IOException("Thread interrupted while waiting for external command.", e);
    } catch (IOException e) {
      throw new IOException("External command could not be executed.", e);
    }
    if (commandResult != 0) {
      throw new IOException("External command error. code = " + commandResult + ".");
    } else {

      String listerData = new String(command.getStdout(), encoding);

      CommandStreamParser parser = new CommandStreamParser(
          new ByteArrayInputStream(listerData.getBytes()));
      log.finest("Pushing Doc Info.");
      pusher.pushDocInfos(parser.readFromLister());
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

    String retrieverData = new String(command.getStdout(), encoding);

    CommandStreamParser parser = new CommandStreamParser(
        new ByteArrayInputStream(retrieverData.getBytes()));
    log.finest("Retrieving Doc Contents");
    resp.getOutputStream().write(parser.readFromRetriever().getContents());
  }

  protected Command newListerCommand() {
    return new Command();
  }

  protected Command newRetrieverCommand() {
    return new Command();
  }

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new CommandLineAdaptor(), args);
  }
}
