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

package com.google.enterprise.adaptor.prebuilt;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.AuthnIdentity;
import com.google.enterprise.adaptor.AuthzStatus;
import com.google.enterprise.adaptor.CommandStreamParser;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
  private List<String> authorizerCommand;
  private String authzDelimiter;

  @Override
  public void initConfig(Config config) {
    // Setup default configuration values. The user is allowed to override them.

    // Create a new configuration key for letting the user configure this
    // adaptor.
    config.addKey("commandline.lister.cmd", null);
    config.addKey("commandline.retriever.cmd", null);
    config.addKey("commandline.authorizer.delimeter", "\0");
    // Change the default to automatically provide unzipped zip contents to the
    // GSA.
    config.overrideKey("adaptor.autoUnzip", "true");
  }


  private List<String> readCommandLineConfig(AdaptorContext context, String prefix) {
    Map<String, String> config = context.getConfig().getValuesWithPrefix(prefix);
    String commandString = config.get("cmd");
    List<String> command = null;
    if (commandString != null) {
      command = new ArrayList<String>();
      command.add(commandString);
      for (int i = 1;; i++) {
        String argument = config.get("arg" + i);
        if (argument == null) {
          break;
        }
        command.add(argument);
      }
    }
    return command;
  }

  @Override
  public void init(AdaptorContext context) throws Exception {

    listerCommand = readCommandLineConfig(context, "commandline.lister.");
    if (listerCommand == null) {
      throw new RuntimeException("commandline.lister.cmd configuration property must be set.");
    }

    retrieverCommand = readCommandLineConfig(context, "commandline.retriever.");
    if (retrieverCommand == null) {
      throw new RuntimeException("commandline.retriever.cmd configuration property must be set.");
    }

    authorizerCommand = readCommandLineConfig(context, "commandline.authorizer.");

    authzDelimiter = context.getConfig().getValue("commandline.authorizer.delimeter");
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

  public List<String> getRetrieverCommand() {
    return Collections.unmodifiableList(retrieverCommand);
  }

  @Override
  public void getDocIds(final DocIdPusher pusher) throws IOException,
         InterruptedException {
    int commandResult;
    StreamingCommand command = newListerCommand();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try {
      log.finest("Command: " + listerCommand);
      String[] commandLine = listerCommand.toArray(new String[0]);
      StreamingCommand.OutputSink stdout = new StreamingCommand.OutputSink() {
        @Override
        public void sink(InputStream in) throws IOException {
          try {
            new CommandStreamParser(in).readFromLister(pusher, null);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
      };
      StreamingCommand.OutputSink stderr = new StreamingCommand.StreamOutputSink(baos);
      commandResult = command.exec(commandLine, null, stdout, stderr);
    } catch (InterruptedException e) {
      throw new IOException("Thread interrupted while waiting for external command.", e);
    } catch (IOException e) {
      throw new IOException("External command could not be executed.", e);
    }
    if (commandResult != 0) {
      String errorOutput = new String(baos.toByteArray(), encoding);
      throw new IOException("External command error. code = " + commandResult + ". Stderr: "
                            + errorOutput);
    }
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, final Response resp) throws IOException {
    final DocId id = req.getDocId();
    int commandResult;
    StreamingCommand command = newRetrieverCommand();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

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
      StreamingCommand.OutputSink stdin = new StreamingCommand.OutputSink() {
        @Override
        public void sink(InputStream in) throws IOException {
          new CommandStreamParser(in).readFromRetriever(id, resp);
        }
      };
      StreamingCommand.OutputSink stderr = new StreamingCommand.StreamOutputSink(baos);

      log.finest("Command: " + Arrays.asList(commandLine));
      commandResult = command.exec(commandLine, null, stdin, stderr);
    } catch (InterruptedException e) {
      throw new IOException("Thread intrupted while waiting for external command.", e);
    } catch (IOException e) {
      throw new IOException("External command could not be executed.", e);
    }
    if (commandResult != 0) {
      String errorOutput = new String(baos.toByteArray(), encoding);
      throw new IOException("External command error. code=" + commandResult + ". Stderr: "
                            + errorOutput);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation provides access permissions for the {@code DocId}s in an \
   * unmodifiable map based upon data returned by a command line authorizer.
   * Permissions can have one of three values:
   * {@link com.google.enterprise.adaptor.AuthzStatus#PERMIT},
   * {@link com.google.enterprise.adaptor.AuthzStatus#DENY},
   * {@link com.google.enterprise.adaptor.AuthzStatus#INDETERMINATE}
   * If an authorizerCommand is not set then AbstractAdaptor.isUserAuthorized
   * is used.
   */
  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(AuthnIdentity userIdentity,
      Collection<DocId> ids) throws IOException {

    if (authorizerCommand == null) {
      return super.isUserAuthorized(userIdentity, ids);
    }

    StringBuilder stdinStringBuilder = new StringBuilder();

    // Write out the user name
    if (userIdentity.getUser().getName().contains(authzDelimiter)) {
      throw new IllegalArgumentException("Error - User '" + userIdentity.getUser().getName()
          + "' contains the delimiter '" + authzDelimiter + "'");
    }
    stdinStringBuilder.append("GSA Adaptor Data Version 1 [" + authzDelimiter + "]"
        + authzDelimiter);

    stdinStringBuilder.append("username=").append(userIdentity.getUser().getName())
        .append(authzDelimiter);

    // Write out the user password
    if (userIdentity.getPassword() != null) {
      if (userIdentity.getPassword().contains(authzDelimiter)) {
        throw new IllegalArgumentException("Error - Password contains the delimiter '"
            + authzDelimiter + "'");
      }
      stdinStringBuilder.append("password=").append(userIdentity.getPassword())
          .append(authzDelimiter);
    }

    // Write out the list of groups that this user belongs to
    if (userIdentity.getGroups() != null) {
      for (GroupPrincipal group : userIdentity.getGroups()) {
        String name = group.getName();
        if (name.contains(authzDelimiter)) {
          throw new IllegalArgumentException("Group cannot contain the delimiter: "
              + authzDelimiter);
        }
        stdinStringBuilder.append("group=").append(name).append(authzDelimiter);
      }
    }

    // Write out the list of document ids that are to be checked
    for (DocId id : ids) {
      if (id.getUniqueId().contains(authzDelimiter)) {
        throw new IllegalArgumentException("Document ID cannot contain the delimiter: "
            + authzDelimiter);
      }
      stdinStringBuilder.append("id=").append(id.getUniqueId()).append(authzDelimiter);
    }
    String stdin = stdinStringBuilder.toString();

    int commandResult;
    Command command = newAuthorizerCommand();

    try {

      String[] commandLine = new String[authorizerCommand.size()];
      authorizerCommand.toArray(commandLine);

      log.finest("Command: " + Arrays.asList(commandLine));
      commandResult = command.exec(commandLine, stdin.getBytes(encoding));
    } catch (InterruptedException e) {
      throw new IOException("Thread interrupted while waiting for external command.", e);
    } catch (IOException e) {
      throw new IOException("External command could not be executed.", e);
    }
    if (commandResult != 0) {
      String errorOutput = new String(command.getStderr(), encoding);
      throw new IOException("External command error. code = " + commandResult + ". Stderr: "
                            + errorOutput);
    }

    CommandStreamParser parser = new CommandStreamParser(
        new ByteArrayInputStream(command.getStdout()));
    log.finest("Pushing Document IDs.");
    return parser.readFromAuthorizer();
  }

  protected StreamingCommand newListerCommand() {
    return new StreamingCommand();
  }

  protected StreamingCommand newRetrieverCommand() {
    return new StreamingCommand();
  }

  protected Command newAuthorizerCommand() {
    return new Command();
  }

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new CommandLineAdaptor(), args);
  }

}
