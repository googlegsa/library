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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the adaptor data format into individual commands with associated data.
 *
 * This format is used for communication between the adaptor library and various command line
 * adaptor components (lister, retriever, transformer, authorizer, etc.). It supports responses
 * coming back from the command line adaptor implementation. The format supports a mixture of
 * character and binary data. All character data must be encoded in UTF-8.<p>
 *
 * Character data technically supports a 'modified UTF-8'. The modified UTF-8 encoding allows
 * newlines and the null character to be encoded as 2-bytes instead of one. Instead of byte 0x00,
 * the null character \0 can be encoded as 0xC0 0x80. Instead of byte 0x0a, the line feed character
 * \n can be encoded as 0xC0 0x8a.<br>
 *
 * <h3>Header Format</h3>
 *
 * Communications (via either file or stream) begin with the header:<p>
 *
 * {@code GSA Adaptor Data Version 1 [<delimiter>]}<p>
 *
 * The version number must be proceeded by a single space and followed by a single space. The
 * version number may increase in the future should the format be enhanced.<p>
 *
 * The string between the two square brackets will be used as the delimiter for the remainder of the
 * file being read or for the duration of the communication session.<p>
 *
 * Care must be taken that the delimiter character string can never occur in a document ID, metadata
 * name, metadata value, user name, or any other data that will be represented using the format with
 * the exception of document contents, which can contain the delimiter string. The safest delimiter
 * is likely to be the null character (the character with a value of zero). This character is
 * unlikely to be present in existing names, paths, metadata, etc. Another possible choice is the
 * newline character, though in many systems it is possible for this character to be present in
 * document names and document paths, etc. If in doubt, the null character is recommended. Because
 * modified UTF-8 is supported, newlines or null characters in document IDs, metadata, and the like
 * can be encoded in their 2-byte form which which will not be confused with the delimiter. A
 * delimiter can be made up of more than one character so it is possible to have a delimiter that is
 * &lt;CR&gt;&lt;LF&gt; or a highly unique string (such as a GUID) that has an exceptionally low
 * probability of occurring in the data.<p>
 *
 * The following characters may not be used in the delimiter:<p> 'A'-'Z', 'a'-'z' and '0'-'9' the
 * alphanumeric characters<br> ':'  colon<br> '/'  slash<br> '-'  hyphen<br> '_'  underscore<br> ' '
 * space<br> '=' equals<br> '+' plus<br> '[' left square bracket<br> ']' right square bracket<br>
 *
 * <h3>Body Format</h3> Elements in the file start with one of the following commands. Commands
 * where data precedes the next delimiter include an equal sign. Commands that are immediately
 * followed by a delimiter do not include an equal sign. The first command must specify a document
 * ID ("id=" or "id-list"). Command that don't specify a document ID are associated with the most
 * recent previously specified document ID.<br>
 *
 * <h3>Common Commands:</h3>
 *
 * "id=" -- specifies a document id<p>
 *
 * "id-list" -- Starts a list of document ids each separated by
 * the specified delimiter, the list is terminated by two consecutive delimiters or EOS
 * (End-Of-Stream). ids in an id-list cannot have any of the associated commands listed below.<p>
 *
 * "repository-unavailable=" -- the document repository is unavailable. The string following the "="
 * character includes additional information that will be logged with the error.
 *
 *
 * <h3>Lister Commands:</h3>
 *
 * "result-link=" -- specifies an alternative link to be displayed in the search results.
 * This must be a properly formed URL. A "result link" is sometimes referred to as a "display URL".
 * If no results-link is specified then the URL used for crawling is also used in the
 * search results.<p>
 *
 * "last-modified=" -- Specifies the last time the document or its metadata has changed.
 * The argument is a number representing the number of seconds since the standard base
 * time known as the epoch", namely January 1, 1970, 00:00:00 GMT. If last-modified is specified
 * and the document has never been crawled before or has been crawled prior to the last-modified
 * time then the ocument will be marked as "crawl-immediately" by the GSA.<p>
 *
 * "crawl-immediately" -- Increases the crawling priority of the document such
 * that the GSA will retrieve it sooner than normally crawled documents.<p>
 *
 * "crawl-once" -- specifies that the document will be crawled by the
 *                 GSA one time but then never re-crawled.<p>
 *
 * "lock" -- Causes the document to remain in the index unless explicitly removed.
 * Failure to retrieve the document during re-crawling will not result in
 * removal of the document. If every document in the GSA is
 * locked then locked document may be forced out when maximum capacity is
 * reached.<p>
 *
 * "delete" -- this document should be deleted from the GSA index.<br>
 *
 * <h3>Retriever Commands:</h3>
 *
 * "up-to-date" -- specifies that the document is up-to-date with respect to its last crawled
 * time.<p>
 *
 * "not-found" -- the document does not exists in the repository<p>
 *
 * "mime-type=" -- specifies the document's mime-type. If unspecified then the GSA will
 * automatically assign a type to the document. <p>
 *
 * "meta-name=" -- specifies a metadata key, to be followed by a metadata-value<p>
 *
 * "meta-value=" -- specifies a metadata value associated with
 * immediately preceding metadata-name<p>
 *
 * "param-name=" -- specifies a parameter key, to be followed by a parameter-value.
 * Parameters are supplied to {@link MetadataTransforms} for use when making
 * transforms or decisions.<p>
 *
 * "param-value=" -- specifies a parameter value associated with
 * immediately preceding parameter-name<p>
 *
 * "content" -- signals the beginning of binary content which
 * continues to the end of the file or stream<p>
 *
 * "last-modified=" -- specifies the last time the document or its metadata has changed.
 * The argument is a number representing the number of seconds since the standard base
 * time known as the epoch", namely January 1, 1970, 00:00:00 GMT.<p>
 *
 * "secure=" -- specifies whether the document is non-public. The argument is either 'true' or
 * 'false'.<p>
 *
 * "anchor-uri=" -- specifies an anchor URI, to be followed by anchor-text.<p>
 *
 * "anchor-text=" -- specifies the text associated with an anchor-uri.<p>
 *
 * "no-index=" -- specifies whether the document should be indexed by the GSA. The argument is
 * either 'true' or 'false'.<p>
 *
 * "no-follow=" -- specifies whether the document's links should be followed by the GSA. The
 * argument is either 'true' or 'false'.<p>
 *
 * "no-archive=" -- specifies whether GSA document will allow the user to see a cached version of
 * the document. The argument is either 'true' or 'false'.<p>
 *
 * "display-url=" -- specifies an alternative link to be displayed in the search results.
 * This must be a properly formed URL.<p>
 *
 * "crawl-once=" -- specifies that the document will be crawled by the
 * GSA one time but then never re-crawled. The argument should be 'true' or 'false'.<p>
 *
 * "lock=" -- Causes the document to remain in the index unless explicitly removed.
 * If every document in the GSA is locked then locked document may be forced out when maximum
 * capacity is reached.<p>
 *
 * "acl" -- when provided, an ACL is sent along with document. The ACL is made of 
 * values provided for other commands starting with "acl-" and "namespace"
 * command. If no acl command is provided then all other ACL commands are 
 * ignored. <p>
 *
 * "namespace=" -- namespace used on all user and group principals until another
 * another namespace is provided.  Defaults to the default namespace.<p>
 *
 * "acl-permit-user=" -- a user name, either with domain or without, that will
 * be permitted to view document being returned.<p>
 *
 * "acl-deny-user=" -- a user name, either with domain or without, that will
 * be denied access to document being returned.<p>
 *
 * "acl-permit-group=" -- a group name, either with domain or without, that 
 * will be permitted to view document being returned.<p>
 *
 * "acl-deny-group=" -- a group name, either with domain or without, that
 * will be denied to view document being returned.<p>
 *
 * "acl-inherit-from=" -- document id that this document inherits permissions
 * from. <p>
 *
 * "acl-inherit-fragment=" -- optional fragment supplementing acl-inherit-from.
 * Together acl-inherit-from and acl-inherit-fragment are what is being
 * inherited from.<p>
 * 
 * "acl-inheritance-type=" -- the type of inheritance {@code
 * com.google.enterprise.adaptor.Acl.InheritanceType}.  Valid values are:
 * and-both-permit, child-overrides, leaf-node, and parent-overrides<p>
 *
 * "acl-case-sensitive=" -- the principals of this document are case sensitive.
 * <p>
 *
 * "acl-case-insensitive=" -- the principals of this document are case
 * insensitive. <br>
 *
 * <h3>Authorizer Commands:</h3>
 *
 * "authz-status=" -- specifies whether a document is visible to a
 *     specified user. The argument must be PERMIT, DENY or INDETERMINATE<p>
 *
 *  "user=" -- specifies the user for whom the authorization check will be made<p>
 *
 *  "password=" -- specifies the password for the user. (optional)<p>
 *    
 *  "group=" -- specifies a security group to which the user belongs.<p>
 *
 * End-of-stream terminates the data transmission. Multiple consecutive delimiters are collapsed
 * into a single delimiter and terminates the current id-list should one exist.<p>
 *
 * Unrecognized commands generate a warning but are otherwise ignored.
 *
 * <h3>Examples</h3>
 *
 * Example 1:<br>
 *
 * <pre>
 * {@code
 * GSA Adaptor Data Version 1 [<delimiter>]
 * id-list
 * /home/repository/docs/file1
 * /home/repository/docs/file2
 * /home/repository/docs/file3
 * /home/repository/docs/file4
 * /home/repository/docs/file5
 * }</pre>
 *
 * Example 2:<br>
 *
 * <pre>
 * {@code
 * GSA Adaptor Data Version 1 [<delimiter>]
 * id=/home/repository/docs/file1
 * id=/home/repository/docs/file2
 * crawl-immediately
 * last-modified=20110803 16:07:23
 *
 * meta-name=Department
 * meta-content=Engineering
 *
 * meta-name=Creator
 * meta-content=howardhawks
 *
 * id=/home/repository/docs/file3
 * id=/home/repository/docs/file4
 * id=/home/repository/docs/file5
 * }</pre>
 *
 * Data passed to command line authorizer via stdin for authz check.
 * Entries will always occur in this order: user, password, group, id.
 * password and group information is optional. Any number of group and
 * id entries can exist. Each of the documents with a listed id should
 * be checked.
 * <pre>
 * {@code
 * GSA Adaptor Data Version 1 [<delimiter>]
 * user=tim_smith
 * password=abc123
 * group=managers
 * group=research
 * id=/home/repository/docs/file1
 * id=/home/repository/docs/file2
 * }</pre>
 *
 * AuthZ response passed from command line authorizer via stdout.
 * Each doc id must include an authz-status entry.
 * <pre>
 * {@code
 * GSA Adaptor Data Version 1 [<delimiter>]
 * id=/home/repository/docs/file1
 * authz-status=PERMIT
 * id=/home/repository/docs/file2
 * authz-status=DENY
 * }</pre>
 */
public class CommandStreamParser {

  private static enum Operation {
    ID("id"),
    RESULT_LINK("result-link"),
    LAST_MODIFIED("last-modified"),
    CRAWL_IMMEDIATELY("crawl-immediately"),
    CRAWL_ONCE("crawl-once"),
    LOCK("lock"),
    DELETE("delete"),
    UP_TO_DATE("up-to-date"),
    NOT_FOUND("not-found"),
    MIME_TYPE("mime-type"),
    META_NAME("meta-name"),
    META_VALUE("meta-value"),
    PARAM_NAME("param-name"),
    PARAM_VALUE("param-value"),
    CONTENT("content"),
    AUTHZ_STATUS("authz-status"),
    SECURE("secure"),
    ANCHOR_URI("anchor-uri"),
    ANCHOR_TEXT("anchor-text"),
    NO_INDEX("no-index"),
    NO_FOLLOW("no-follow"),
    NO_ARCHIVE("no-archive"),
    DISPLAY_URL("display-url"),
    ACL("acl"),
    NAMESPACE("namespace"),
    ACL_PERMIT_USER("acl-permit-user"),
    ACL_DENY_USER("acl-deny-user"),
    ACL_PERMIT_GROUP("acl-permit-group"),
    ACL_DENY_GROUP("acl-deny-group"),
    ACL_INHERIT_FROM("acl-inherit-from"),
    ACL_INHERIT_FRAGMENT("acl-inherit-fragment"),
    ACL_INHERITANCE_TYPE("acl-inheritance-type"),
    ACL_CASE_SENSITIVE("acl-case-sensitive"),
    ACL_CASE_INSENSITIVE("acl-case-insensitive"),
    ;

    private final String commandName;

    private Operation(String commandName) {
      this.commandName = commandName;
    }

    public String getCommandName() {
      return commandName;
    }
  }

  private static final Logger log = Logger.getLogger(CommandStreamParser.class.getName());
  private static final String HEADER_PREFIX = "GSA Adaptor Data Version";
  private static final String DISALLOWED_DELIMITER_CHARS_REGEX = "[a-zA-Z0-9:/\\-_ =\\+\\[\\]]";
  private static final Charset CHARSET = Charset.forName("UTF-8");

  private static final Map<String, Operation> STRING_TO_OPERATION;
  static {
    Map<String, Operation> stringToOperation = new HashMap<String, Operation>();
    for (Operation operation : Operation.values()) {
      stringToOperation.put(operation.getCommandName(), operation);
    }
    STRING_TO_OPERATION = Collections.unmodifiableMap(stringToOperation);
  }

  private static final Map<String, Acl.InheritanceType> STRING_TO_INHERITANCE_TYPE;
  static {
    Map<String, Acl.InheritanceType> stringToType
        = new HashMap<String, Acl.InheritanceType>();
    for (Acl.InheritanceType type : Acl.InheritanceType.values()) {
      stringToType.put(type.getCommonForm(), type);
    }
    STRING_TO_INHERITANCE_TYPE = Collections.unmodifiableMap(stringToType);
  }

  private InputStream inputStream;
  private int versionNumber = 0;
  private String delimiter;
  private boolean inIdList;

  /** */
  private static class Command {

    private Operation operation;
    private String argument;

    Command(Operation operation, String argument) {
      this.operation = operation;
      this.argument = argument;
    }

    public Operation getOperation() {
      return operation;
    }

    public String getArgument() {
      return argument;
    }

    public boolean hasArgument() {
      return argument != null;
    }
  }

  public CommandStreamParser(InputStream inputStream) {
    this.inputStream = inputStream;
    inIdList = false;
  }

  public int getVersionNumber() throws IOException {
    checkHeader();
    return versionNumber;
  }

  public Map<DocId, AuthzStatus> readFromAuthorizer() throws IOException {
    Map<DocId, AuthzStatus> result = new HashMap<DocId, AuthzStatus>();
    String docId = null;
    AuthzStatus authzStatus = null;
    Command command = readCommand();

    // Starting out at end-of-stream so return an empty list.
    if (command == null) {
      return result;
    }

    // The first operation must be a doc ID.
    if (command.getOperation() != Operation.ID) {
      throw new IOException("Authorizer Error: the first operator must be a document ID. "
          + " Instead encountered '" + command.getOperation() + "'.");
    }
    while (command != null) {
      switch (command.getOperation()) {
        case ID:
          if (docId != null) {
            result.put(new DocId(docId), authzStatus);
          }
          docId = command.getArgument();
          authzStatus = null;
          break;
        case AUTHZ_STATUS:
          String authzStatusString = command.getArgument();
          try {
            authzStatus = AuthzStatus.valueOf(authzStatusString);
          } catch (IllegalArgumentException ex) {
            log.warning("Unrecognized authz-status of '" + authzStatusString + "' for document: '"
                + docId + "'");
          }
          break;
        default:
          throw new IOException("Authorizer Error: invalid operation: '" + command.getOperation()
              + (command.hasArgument() ? "' with argument: '"  + command.getArgument() : "")
              + "'");
      }
      command = readCommand();
    }
    result.put(new DocId(docId), authzStatus);

    return Collections.unmodifiableMap(result);
  }

  public void readFromRetriever(DocId docId, Response response) throws IOException {
    Command command = readCommand();

    if (command == null) {
      throw new IOException("Invalid or missing retriever data.");
    } else if (command.getOperation() != Operation.ID) {
      throw new IOException("Retriever Error: the first operator must be a document ID. "
          + " Instead encountered '" + command.getOperation() + "'.");
    }

    DocId foundDocId = new DocId(command.getArgument());
    if (!docId.equals(foundDocId)) {
      throw new IOException("requested document "  + docId + " does not match retrieved "
          + "document " + foundDocId + ".");
    }

    boolean sendAclWithDocument = false;
    Acl.Builder aclBuilder = new Acl.Builder();
    DocId inheritFrom = null;  // saves inherit-from in case fragment comes
    Set<Principal> permits = new TreeSet<Principal>();  // accumulates acl state
    Set<Principal> denies = new TreeSet<Principal>();  // accumulates acl state
    String namespace = Principal.DEFAULT_NAMESPACE;  // last given namespace

    command = readCommand();
    while (command != null) {
      switch (command.getOperation()) {
        case ID:
          throw new IOException("Only one document ID can be specified in a retriever message");
        case CONTENT:
          IOHelper.copyStream(inputStream, response.getOutputStream());
          break;
        case META_NAME:
          String metaName = command.getArgument();
          command = readCommand();
          if (command == null || command.getOperation() != Operation.META_VALUE) {
            throw new IOException("meta-name must be immediately followed by meta-value");
          }
          log.log(Level.FINEST, "Retriever: {0} has metadata {1}={2}",
              new Object[] {docId.getUniqueId(), metaName,
                command.getArgument()});
          response.addMetadata(metaName, command.getArgument());
          break;
        case PARAM_NAME:
          if (!(response instanceof Response2)) {
            throw new IOException(
                "param-name is not supported by " + response.getClass());
          }
          String paramName = command.getArgument();
          command = readCommand();
          if (command == null || command.getOperation() != Operation.PARAM_VALUE) {
            throw new IOException("param-name must be immediately followed by param-value");
          }
          log.log(Level.FINEST, "Retriever: {0} has parameter {1}={2}",
              new Object[] {docId.getUniqueId(), paramName,
                command.getArgument()});
          ((Response2) response).addParam(paramName, command.getArgument());
          break;
        case UP_TO_DATE:
          log.log(Level.FINEST, "Retriever: {0} is up to date.", docId.getUniqueId());
          response.respondNotModified();
          break;
        case NOT_FOUND:
          response.respondNotFound();
          break;
        case MIME_TYPE:
          log.log(Level.FINEST, "Retriever: {0} has mime-type {1}",
              new Object[] {docId.getUniqueId(), command.getArgument()});
          response.setContentType(command.getArgument());
          break;
        case LAST_MODIFIED:
          // Convert seconds to milliseconds for Date constructor.
          response.setLastModified(new Date(Long.parseLong(command.getArgument()) * 1000));
          break;
        case SECURE:
          response.setSecure(Boolean.parseBoolean(command.getArgument()));
          break;
        case ANCHOR_URI:
          URI anchorUri = URI.create(command.getArgument());
          command = readCommand();
          if (command == null || command.getOperation() != Operation.ANCHOR_TEXT) {
            throw new IOException("anchor-uri must be immediately followed by anchor-text");
          }
          response.addAnchor(anchorUri, command.getArgument());
          break;
        case NO_INDEX:
          response.setNoIndex(Boolean.parseBoolean(command.getArgument()));
          break;
        case NO_FOLLOW:
          response.setNoFollow(Boolean.parseBoolean(command.getArgument()));
          break;
        case NO_ARCHIVE:
          response.setNoArchive(Boolean.parseBoolean(command.getArgument()));
          break;
        case DISPLAY_URL:
          response.setDisplayUrl(URI.create(command.getArgument()));
          break;
        case CRAWL_ONCE:
          response.setCrawlOnce(Boolean.parseBoolean(command.getArgument()));
          break;
        case LOCK:
          response.setLock(Boolean.parseBoolean(command.getArgument()));
          break;
        case ACL:
          sendAclWithDocument = true;
          break;
        case NAMESPACE:
          namespace = command.getArgument();
          break;
        case ACL_PERMIT_USER:
          permits.add(new UserPrincipal(command.getArgument(), namespace));
          break;
        case ACL_DENY_USER:
          denies.add(new UserPrincipal(command.getArgument(), namespace));
          break;
        case ACL_PERMIT_GROUP:
          permits.add(new GroupPrincipal(command.getArgument(), namespace));
          break;
        case ACL_DENY_GROUP:
          denies.add(new GroupPrincipal(command.getArgument(), namespace));
          break;
        case ACL_INHERIT_FROM:
          inheritFrom = new DocId(command.getArgument());
          aclBuilder.setInheritFrom(inheritFrom);
          break;
        case ACL_INHERIT_FRAGMENT:
          if (null == inheritFrom) {
            throw new IOException("acl-inherit-fragment cannot preceed acl-inherit-from");
          }
          aclBuilder.setInheritFrom(inheritFrom, command.getArgument());
          break;
        case ACL_INHERITANCE_TYPE:
          Acl.InheritanceType type = STRING_TO_INHERITANCE_TYPE.get(command.getArgument());
          if (null == type) {
            throw new IOException("invalid acl-inheritance-type: " + command.getArgument());
          }
          aclBuilder.setInheritanceType(type);
          break;
        case ACL_CASE_SENSITIVE:
          aclBuilder.setEverythingCaseSensitive();
          break;
        case ACL_CASE_INSENSITIVE:
          aclBuilder.setEverythingCaseInsensitive();
          break;
        default:
          throw new IOException("Retriever Error: invalid operation: '" + command.getOperation()
              + (command.hasArgument() ? "' with argument: '"  + command.getArgument() : "")
              + "'");
      }
      command = readCommand();
    }
    // Finish by putting accumulated ACL into response.
    if (sendAclWithDocument) {
      aclBuilder.setPermits(permits);
      aclBuilder.setDenies(denies);
      response.setAcl(aclBuilder.build());
    }
  }

  /**
   * Parse a listing response, sending results to {@code pusher}. If {@code handler} is {@code
   * null}, then {@code pusher}'s default handler will be used. In case of failure sending in
   * {@code pusher}, the rest of the input stream may not be read.
   *
   * @param pusher doc id pusher
   * @param handler exception handler
   * @return {@code null} on success, otherwise the first Record to fail
   * @throws IOException ioe
   * @throws InterruptedException if interrupted
   */
  public DocIdPusher.Record readFromLister(DocIdPusher pusher, ExceptionHandler handler)
      throws IOException, InterruptedException {
    ArrayList<DocIdPusher.Record> result = new ArrayList<DocIdPusher.Record>();
    DocIdPusher.Record.Builder builder = null;
    Command command = readCommand();

    // Starting out at end-of-stream so don't send anything.
    if (command == null) {
      return null;
    }

    // The first operation must be a doc ID.
    if (command.getOperation() != Operation.ID) {
      throw new IOException("Lister Error: the first operator must be a document ID. "
          + " Instead encountered '" + command.getOperation() + "'.");
    }
    while (command != null) {
      switch (command.getOperation()) {
        case ID:
          if (builder != null) {
            result.add(builder.build());
            // TODO(ejona): make threshold smarter.
            if (result.size() >= 10000) {
              DocIdPusher.Record errorRecord = pusher.pushRecords(result, handler);
              if (errorRecord != null) {
                return errorRecord;
              }
              result.clear();
            }
          }
          builder = new DocIdPusher.Record.Builder(new DocId(command.getArgument()));
          break;
        case LAST_MODIFIED:
          // Convert seconds to milliseconds for Date constructor.
          builder.setLastModified(new Date(Long.parseLong(command.getArgument()) * 1000));
          break;
        case RESULT_LINK:
          try {
            builder.setResultLink(new URI(command.getArgument()));
          } catch (URISyntaxException e) {
            throw new IOException("Lister Error: invalid URL: '" + command.getOperation()
                + (command.hasArgument() ? "' with argument: '"
                + command.getArgument() + "'" : "'"), e);
          }
          break;
        case CRAWL_IMMEDIATELY:
          builder.setCrawlImmediately(true);
          break;
        case CRAWL_ONCE:
          builder.setCrawlOnce(true);
          break;
        case LOCK:
          builder.setLock(true);
          break;
        case DELETE:
          builder.setDeleteFromIndex(true);
          break;
        default:
          throw new IOException("Lister Error: invalid operation: '" + command.getOperation()
              + (command.hasArgument() ? "' with argument: '"  + command.getArgument() : "'")
              + "'");
      }
      command = readCommand();
    }
    result.add(builder.build());
    return pusher.pushRecords(result, handler);
  }

  /**
   * Read a command from the command stream
   *
   * @return The next command from the command stream. for end-of-steam null is returned.
   * @throws IOException on stream read error
   */
  private Command readCommand() throws IOException {

    Command result = null;

    while (result == null) {
      String commandTokens[] = parseNextLine();
      if (commandTokens == null) {
        return null;
      } else if ((commandTokens[0].equals("repository-unavailable"))) {
        throw new IOException("Error: repository unavailable. "
            + (commandTokens.length > 1 ? commandTokens[1] : ""));
      }

      Operation operation = STRING_TO_OPERATION.get(commandTokens[0]);
      // Skip over unrecognized commands
      if (operation == null) {
        log.warning("Unrecognized command: " + commandTokens[0]);
        continue;
      }

      String argument = null;

      if (commandTokens.length > 1) {
        argument = commandTokens[1];
      }

      result = new Command(operation, argument);
    }
    return result;
  }

  private String[] parseNextLine() throws IOException {
    checkHeader();
    String line = "";
    while (line.length() == 0) {
      line = readCharsUntilMarker(delimiter);
      // On End-Of-Stream return the end-message command
      if (line == null) {
        return null;
      }
      // If nothing is between the last delimiter and this one then exit ID list mode
      if (inIdList && line.length() == 0) {
        inIdList = false;
      } else if (!inIdList && line.equals("id-list")) {
        inIdList = true;
        line = ""; // loop again
      }
    }
    if (inIdList) {
      return new String[]{"id", line};
    }
    return line.split("=", 2);
  }

  /**
   * Read and verify the data format header if needed.
   */
  private void checkHeader() throws IOException {
    if (this.delimiter != null) {
      return;
    }

    String line = readCharsUntilMarker("[");
    if ((line == null) || (line.length() < HEADER_PREFIX.length())
        || !line.substring(0, HEADER_PREFIX.length()).equals(HEADER_PREFIX)) {
      throw new IOException("Adaptor data must begin with '" + HEADER_PREFIX + "'");
    }

    String versionNumberString = line.substring(HEADER_PREFIX.length());
    if (versionNumberString.length() < 3) {
      throw new IOException("Format version '" + versionNumberString + "' is invalid. "
          + "The version must be at least one digit with one leading space"
          + " and one trailing space.");
    }

    delimiter = readCharsUntilMarker("]");
    if ((delimiter == null) || (delimiter.length() < 1)) {
      throw new IOException("Delimiter must be at least one character long.");
    }

    Pattern pattern = Pattern.compile(DISALLOWED_DELIMITER_CHARS_REGEX);
    Matcher matcher = pattern.matcher(delimiter);

    if (matcher.find()) {
      throw new IOException("Invalid character in delimiter.");
    }

    try {
      versionNumber = Integer.parseInt(versionNumberString.trim());
    } catch (NumberFormatException e) {
      throw new IOException("Format version '" + versionNumberString + "' is invalid.");
    }
  }


  private byte[] readBytesUntilMarker(byte[] marker) throws IOException {

    if (marker.length == 0) {
      throw new IOException("Internal Error: Marker length must be greater than zero.");
    }
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    int matchPosition = 0;
    int nextByte = 0;

    while (matchPosition < marker.length) {
      nextByte = inputStream.read();
      if (nextByte == ((int) marker[matchPosition] & 0xff)) {
        matchPosition += 1;
      } else {
        if (matchPosition > 0) {
          byteArrayOutputStream.write(marker, 0, matchPosition);
          matchPosition = 0;
        }
        if (nextByte == -1) {
          break;
        } else {
          byteArrayOutputStream.write(nextByte);
        }
      }
    }
    byte[] result = byteArrayOutputStream.toByteArray();
    if (nextByte == -1 && result.length == 0) {
      return null;
    } else {
      return result;
    }
  }

  private String readCharsUntilMarker(String marker) throws IOException {
    byte[] byteMarker = marker.getBytes(CHARSET);
    byte[] bytes = readBytesUntilMarker(byteMarker);
    if (bytes == null) {
      return null;
    }
    bytes = convertModifiedUtf8ToStandardUtf8(bytes);
    return CHARSET.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
  }

  /**
   * Converts modified UTF-8 that supports 2-byte \n and \0 to standard UTF-8. It replaces
   * 0xC0 0x80 with 0x00 and 0xC0 0x8a with 0x0a.
   */
  private static byte[] convertModifiedUtf8ToStandardUtf8(byte[] bytes) throws IOException {
    // Byte 0xC0 is always invalid in standard UTF-8, so its presence implies modified UTF-8.
    int numberOfByteC0 = 0;
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == (byte) 0xC0) {
        numberOfByteC0++;
      }
    }
    if (numberOfByteC0 == 0) {
      return bytes;
    }
    // In UTF-8 if byte 0xC0 was valid, it would mean the code point is stored in two bytes.
    // In modified UTF-8, it means that we have stored \0 or \n in two bytes.
    byte[] newBytes = new byte[bytes.length - numberOfByteC0];
    boolean lastByteWasC0 = false;
    for (int i = 0, j = 0; i < bytes.length; i++) {
      if (!lastByteWasC0) {
        if (bytes[i] == (byte) 0xC0) {
          lastByteWasC0 = true;
          // Don't copy C0, because we will encode the character as one byte.
        } else {
          newBytes[j++] = bytes[i];
        }
      } else {
        lastByteWasC0 = false;
        if (bytes[i] == (byte) 0x80) {
          // Null character
          newBytes[j++] = 0x00;
        } else if (bytes[i] == (byte) 0x8a) {
          // Newline
          newBytes[j++] = 0x0a;
        } else {
          throw new IOException(
              "Invalid modified UTF-8 byte sequence: 192 " + (bytes[i] & 0xff));
        }
      }
    }
    if (lastByteWasC0) {
      throw new IOException("Invalid modified UTF-8 byte sequence: trailing 192");
    }
    return newBytes;
  }
}
