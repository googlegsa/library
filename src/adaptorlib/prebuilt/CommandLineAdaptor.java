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
import adaptorlib.AdaptorContext;
import adaptorlib.CommandStreamParser;
import adaptorlib.Config;
import adaptorlib.DocId;
import adaptorlib.DocIdPusher;
import adaptorlib.Request;
import adaptorlib.Response;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Command Line Adaptor
 */
public class CommandLineAdaptor extends AbstractAdaptor {
  private static final Logger log = Logger.getLogger(CommandLineAdaptor.class.getName());
  private Charset encoding = Charset.forName("UTF-8");
  private List<String> listerCommand;
  private List<String> retrieverCommand;

  @Override
  public void initConfig(Config config) {
    // Setup default configuration values. The user is allowed to override them.

    // Create a new configuration key for letting the user configure this
    // adaptor.
    config.addKey("commandline.lister.cmd", "./lister.sh");
    config.addKey("commandline.retriever.cmd", "./retriever.sh");
    // Change the default to automatically provide unzipped zip contents to the
    // GSA.
    config.overrideKey("adaptor.autoUnzip", "true");
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    // Process lister configuration.
    Map<String, String> listerConfig = context.getConfig().getValuesWithPrefix(
        "commandline.lister.");
    listerCommand = new ArrayList<String>();

    String listerCommandString = listerConfig.get("cmd");
    if (listerCommandString == null) {
      throw new RuntimeException("commandline.lister.cmd configuration property"
          + " must be set");
    }
    listerCommand.add(listerCommandString);

    for (int i = 1;; i++) {
      String argument = listerConfig.get("arg" + i);
      if (argument == null) {
        break;
      }
      listerCommand.add(argument);
    }

    // Process retriever configuration.
    Map<String, String> retrieverConfig = context.getConfig().getValuesWithPrefix(
        "commandline.retriever.");
    retrieverCommand = new ArrayList<String>();

    String retrieverCommandString = retrieverConfig.get("cmd");
    if (retrieverCommandString == null) {
      throw new RuntimeException("commandline.retriever.cmd configuration property"
          + " must be set");
    }
    retrieverCommand.add(retrieverCommandString);

    for (int i = 1;; i++) {
      String value = retrieverConfig.get("arg" + i);
      if (value == null) {
        break;
      }
      retrieverCommand.add(value);
    }
  }

  public void setListerCommand(List<String> commandWithArgs) {
    this.listerCommand = new ArrayList<String>(commandWithArgs);
  }

  public List<String> getListerCommand() {
    return Collections.unmodifiableList(listerCommand);
  }

  public void setRetrieverCommand(List<String> commandWithArgs) {
    this.retrieverCommand = new ArrayList<String>(commandWithArgs);
  }

  public List<String> geRetrieverCommand() {
    return Collections.unmodifiableList(retrieverCommand);
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws IOException,
         InterruptedException {
    int commandResult;
    Command command = newListerCommand();

    try {
       String[] commandLine = listerCommand.toArray(new String[0]);
      log.finest("Command: " + commandLine);
      commandResult = command.exec(commandLine);
    } catch (InterruptedException e) {
      throw new IOException("Thread interrupted while waiting for external command.", e);
    } catch (IOException e) {
      throw new IOException("External command could not be executed.", e);
    }
    if (commandResult != 0) {
      throw new IOException("External command error. code = " + commandResult + ".");
    }

    CommandStreamParser parser = new CommandStreamParser(
        new ByteArrayInputStream(command.getStdout()));
    log.finest("Pushing Document IDs.");
    pusher.pushRecords(parser.readFromLister());
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    int commandResult;
    Command command = newRetrieverCommand();

    try {
      Date lastCrawled = req.getLastAccessTime();
      long lastCrawledMillis = 0;
      if (lastCrawled != null) {
        lastCrawledMillis = lastCrawled.getTime();
      }
      String[] commandLine = new String[retrieverCommand.size() + 2];
      retrieverCommand.toArray(commandLine);
      commandLine[retrieverCommand.size()] = id.getUniqueId();
      commandLine[retrieverCommand.size() + 1] = Long.toString(lastCrawledMillis);

      log.finest("Command: " + commandLine);
      commandResult = command.exec(commandLine);
    } catch (InterruptedException e) {
      throw new IOException("Thread intrupted while waiting for external command.", e);
    } catch (IOException e) {
      throw new IOException("External command could not be executed.", e);
    }
    if (commandResult != 0) {
      throw new IOException("External command error. code=" + commandResult + ".");
    }
    // TODO(johnfelton) log any output sent to stderr

    CommandStreamParser parser = new CommandStreamParser(
        new ByteArrayInputStream(command.getStdout()));
    CommandStreamParser.RetrieverInfo retrieverInfo = parser.readFromRetriever();

    if (!req.getDocId().equals(retrieverInfo.getDocId())) {
      throw new IOException("requested document "  + req.getDocId() + " does not match retrieved "
          + "document  " + retrieverInfo.getDocId() + ".");
    }
    if (retrieverInfo.notFound()) {
      throw new FileNotFoundException("Could not find file '" + retrieverInfo.getDocId());
    }
    else if (retrieverInfo.isUpToDate()) {
      log.finest("Retriever: " + id.getUniqueId() + " is up to date.");
      resp.respondNotModified();

    } else {
      if (retrieverInfo.getMimeType() != null) {
        log.finest("Retriever: " + id.getUniqueId() + " has mime-type "
            + retrieverInfo.getMimeType());
        resp.setContentType(retrieverInfo.getMimeType());
      };
      if (retrieverInfo.getMetadata() != null) {
        log.finest("Retriever: " + id.getUniqueId() + " has metadata "
            + retrieverInfo.getMetadata());
        resp.setMetadata(retrieverInfo.getMetadata());
      };
      if (retrieverInfo.getContents() != null) {
        resp.getOutputStream().write(retrieverInfo.getContents());
      } else {
        throw new IOException("No content returned by retriever for "  + req.getDocId() + ".");
      }
    }
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
