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

package adaptorlib;

import com.google.common.collect.ImmutableMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.print.DocFlavor;

/**
 * This class parses the adaptor data format into individual commands with
 * associated data. The data format is as follows:
 *
 * This format is used for communication between the adaptor library and
 * various command line adaptor components (lister, retriever, transformer,
 * authorizor, etc.). It supports responses coming back from the command
 * line adaptor implementation. The format support a mixture of
 * character and binary data. All character data is encoded in UTF-8.
 * Header Format
 *
 * Communications (via either file or stream) begin with the header:
 *
 * "GSA Adaptor Data Version 1 [<delimiter>]".
 *
 * The version number must be proceeded by a single space and followed
 * by a single space. The version number may increase in the future
 * should the format be enhanced.
 *
 * The string between the two square brackets will be used as the delimiter
 * for the remainder of the file being read or for the duration of the
 * communication session.
 *
 * Though the delimiter can be any string of characters, care must be taken
 * that this same character string can never occur in a document ID, metadata
 * name, metadata value, user name, or any other data that will be represented
 * using the format with the exception of document contents, which can contain
 * the delimiter string. The safest delimiter is likely to be the null
 * character (the character with a value of zero). This character is
 * unlikely to be present in existing names, paths, metadata, etc.
 * Another possible choice is the newline character, though in many systems
 * it is possible for this character to be present in document names and
 * document paths, etc. If in doubt, the null character is recommended.
 * A delimiter can be made up of more than one character so it is possible
 * to have a delimiter that is <CR><LF> or a highly unique string (such as a
 * GUID) that has an exceptionally low probability of occurring in the data.
 *
 * The following characters may not be used in the delimiter:
 * 'A'-'Z', 'a'-'z' and '0'-'9' the alphanumeric characters
 * ':'  colon
 * '/'  slash
 * '-'  hyphen
 * '_'  underscore
 * ' '  space
 * '=' equals
 * '+' plus
 * ‘[‘ left square bracket
 * ‘]’ right square bracket
 * Body Format
 *
 * Elements in the file start with one of the following commands. Commands
 * where data precedes the next delimiter include an equal sign. Commands
 * that are immediately followed by a delimiter do not include an equal sign.
 *
 * "id=" - specifies a document id
 * "last-crawled=" - specifies the last time the GSA crawled the associated
 *                 document in milliseconds from epoch.
 * "up-to-date=" - specifies (true or false) for whether a document is
 *               up-to-date based upon its last-crawled time.
 * "id-list" -- specified a list of document ids, separated by the
 *            delimiter character, the list is terminated by two
 *            consecutive delimiters or EOS (End-Of-Stream)
 * "meta-name=" -- specifies a metadata name associated with the most recent id
 * "meta-value=" -- specifies a metadata value associated with the
 *                previous metadata-name
 * "content-to-end" -- signals the beginning of binary content to the end
 *                   of the file or stream
 * "content-to-marker" -- signals the beginning of binary content that
 *                      continues until the following marker is encountered
 * 4387BDFA-C831-11E0-827B-48354824019B-7B19137E-0D3D-4447-8F55-44B52248A18B
 *
 * "content-bytes=" -- marks the beginning of binary content that runs for the
 *                   specified number of bytes.
 *
 *
 * Empty entries (Two consecutive delimiters are treated as one. Reaching
 *     end of file or end of stream terminates the data transmission.
 * Examples
 *
 * Example 1:
 *
 * GSA Adaptor Data Version 1
 * id-list
 * /home/repository/docs/file1
 * /home/repository/docs/file2
 * /home/repository/docs/file3
 * /home/repository/docs/file4
 * /home/repository/docs/file5
 * Example 2:
 *
 * GSA Adaptor Data Version 1
 * id=/home/repository/docs/file1
 * id=/home/repository/docs/file2
 * meta-name=GoogleAdaptor:CrawlImmediately
 * meta-content=true
 *
 * meta-name=GoogleAdaptor:LastModified
 * meta-content=20110803 16:07:23
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
 * Example 3:
 *
 * GSA Adaptor Data Version 1
 * id=/home/repository/docs/file2
 * meta-name=GoogleAdaptor:CrawlImmediately
 * meta-content=true
 * meta-name=GoogleAdaptor:LastModified
 * meta-content=20110803 16:07:23
 * meta-name=Department
 * meta-content=Engineering
 * Example 5:
 *
 *
 * meta-name=Creator
 * meta-content=howardhawks
 * content
 * <binary content to the end of the file>
 * Example 4:
 *
 * GSA Adaptor Data Version 1
 * id=/home/repository/docs/file1
 * content-bytes=32432
 * <binary content for 32432 bytes>
 * id=/home/repository/docs/file2
 * content-base64
 * <base64 content until next delimiter>
 * id=/home/repository/docs/file3
 * content-filepath=/home/source files/file3.docGSA Adaptor Data Version 1
 * id=/home/repository/docs/file3
 * content
 * <arbitrary block of binary content>
 * 4387BDFA-C831-11E0-827B-48354824019B-7B19137E-0D3D-4447-8F55-44B52248A18B
 *
 */
public class CommandStreamParser {

  private static final String HEADER_PREFIX = "GSA Adaptor Data Version";
  private static final String DISALLOWED_DELIMITER_CHARS_REGEX = "[a-zA-Z0-9:/\\-_ =\\+\\[\\]]";
  private static final byte[] END_BINARY_MARKER =
      "4387BDFA-C831-11E0-827B-48354824019B-7B19137E-0D3D-4447-8F55-44B52248A18B".getBytes();

  private static final Map<String, CommandWithArgCount> STRING_TO_COMMAND;

  static {
    Map<String, CommandWithArgCount> stringToCommand = new HashMap<String, CommandWithArgCount>();
    stringToCommand.put("id", new CommandWithArgCount(CommandType.ID, 1));
    stringToCommand.put("last-crawled", new CommandWithArgCount(CommandType.LAST_CRAWLED, 1));
    stringToCommand.put("up-to-date", new CommandWithArgCount(CommandType.UP_TO_DATE, 1));
    stringToCommand.put("meta-name", new CommandWithArgCount(CommandType.META_NAME, 1));
    stringToCommand.put("meta-value", new CommandWithArgCount(CommandType.META_VALUE, 1));
    stringToCommand.put("content-to-end", new CommandWithArgCount(CommandType.CONTENT, 0));
    stringToCommand.put("content-to-marker", new CommandWithArgCount(CommandType.CONTENT, 0));
    stringToCommand.put("content-bytes", new CommandWithArgCount(CommandType.CONTENT, 1));
    STRING_TO_COMMAND = Collections.unmodifiableMap(stringToCommand);
  }

  public static enum CommandType {ID, META_NAME, META_VALUE, CONTENT, LAST_CRAWLED, UP_TO_DATE}

  CommandStreamParser(InputStream inputStream) {
    hybridStream = new ByteCharInputStream(inputStream);
    inIdList = false;
  }

  public static class Command {
    CommandType commandType;
    String argument;
    byte[] contents;

    public CommandType getCommandType() {
      return commandType;
    }

    public String getArgument() {
      return argument;
    }

    public byte[] getContents() {
      return contents;
    }

    Command(CommandType commandType, String argument, byte[] contents) {
      this.commandType = commandType;
      this.argument = argument;
      this.contents = contents;
    }
  }

  private ByteCharInputStream hybridStream;
  private String delimiter;
  private boolean inIdList;

  private static class CommandWithArgCount {
    CommandType commandType;
    int argumentCount;

    public CommandType getCommandType() {
      return commandType;
    }

    public int getArgumentCount() {
      return argumentCount;
    }

    CommandWithArgCount(CommandType commandType, int argumentCount) {
      this.commandType = commandType;
      this.argumentCount = argumentCount;
    }
  }

  public Command readCommand() throws IOException {
    if (delimiter == null) {
      readHeader();
    }
    String line = hybridStream.readToDelimiter(delimiter);

    // On End-Of-Stream return the end-message command
    if (line == null) {
      return null;
    }

    if (line.length() == 0) {
      // If nothing is between the last delimiter and this one
      // then exit ID list mode
      if (inIdList) {
        inIdList = false;
      }
      return readCommand();
    }

    if (inIdList) {
      return new Command(CommandType.ID, line, null);
    }

    if (line.equals("id-list")) {
      inIdList = true;
      return readCommand();
    }

    String[] tokens = line.split("=", 2);

    CommandWithArgCount commandWithArgCount = STRING_TO_COMMAND.get(tokens[0]);
    if (commandWithArgCount == null) {
      throw new IOException("Invalid Command '" + line +"'");
    }

    byte[] content = null;
    String argument = null;

    if (tokens.length != commandWithArgCount.getArgumentCount() + 1) {
        throw new IOException("Invalid Command '" + line +"'");
    }

    if (commandWithArgCount.getArgumentCount() == 1) {
        argument = tokens[1];
    }

    if (tokens[0].equals("content-to-end")) {
        content = readBytesUntilEnd();
    } else if (tokens[0].equals("content-to-marker")) {
        content = readBytesUntilMarker();
    } else if (tokens[0].equals("content-bytes")) {
        int byteCount = Integer.parseInt(tokens[1]);
        content = readBytes(byteCount);
    }

    return new Command(commandWithArgCount.getCommandType(), argument, content);
  }

  /**
   * Read and verify the data format header
   *
   * @return The format version
   * @throws IOException
   */
  public int readHeader() throws IOException {
    String line = hybridStream.readToDelimiter("[");
    if ((line.length() < HEADER_PREFIX.length()) ||
        !line.substring(0, HEADER_PREFIX.length()).equals(HEADER_PREFIX)) {
      throw new IOException("Adaptor data must begin with '" + HEADER_PREFIX + "'");
    }

    String versionNumber = line.substring(HEADER_PREFIX.length());
    if (!versionNumber.equals(" 1 ")) {
      throw new IOException("Adaptor format version '" + versionNumber + "' is not supported. " +
          "(Is there a single space preceeding and following the version number?");
    }

    delimiter = hybridStream.readToDelimiter("]");
    if (delimiter.length() < 1) {
      throw new IOException("Delimiter must be at least one character long.");
    }

    Pattern pattern = Pattern.compile(DISALLOWED_DELIMITER_CHARS_REGEX);
    Matcher matcher = pattern.matcher(delimiter);

    if(matcher.find()){
       throw new IOException("Invalid character in delimiter.");
    }

  return Integer.parseInt(versionNumber.substring(1,2));
  }

  /**
   * Starting with the second byte in the buffer,
   * search for the first occurrence of the first byte
   * in the data end marker.
   *
   * @param buffer bytes to searched.
   * @return The number of places to shift the data in
   *         order to move the first end marker byte
   *         to the beginning of the buffer. If this
   *         byte is found then shift
   *         the entire buffer.
   */
  private int shiftDistance(byte[] buffer) {
    // Starting with the second byte in the buffer,
    // Look for the first occurrence of the first byte
    // in the data end marker.
    // return the
    for (int i = 1; i < buffer.length; i++) {
      if (buffer[i] == END_BINARY_MARKER[0]) {
        return i;
      }
    }
    return buffer.length;
  }

  private byte[] readBytesUntilMarker() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[END_BINARY_MARKER.length];

    int bytesRead = hybridStream.readFully(buffer, 0, buffer.length);

    while ((bytesRead != -1) && !Arrays.equals(buffer, END_BINARY_MARKER)) {
      int shiftDistance = shiftDistance(buffer);
      int bytesToShift = buffer.length - shiftDistance;
      byteArrayOutputStream.write(buffer, 0, shiftDistance);
      System.arraycopy(buffer, shiftDistance, buffer, 0, bytesToShift);
      bytesRead = hybridStream.readFully(buffer, bytesToShift, shiftDistance);
    }
    if (bytesRead == -1) {
      return null;
    } else {
      return byteArrayOutputStream.toByteArray();
    }
  }

  private byte[] readBytesUntilEnd() throws IOException {
    return IOHelper.readInputStreamToByteArray(hybridStream.getInputStream());
  }


  private byte[] readBytes(int byteCount) throws IOException {
    byte[] result = new byte[byteCount];
    int bytesRead =  hybridStream.readFully(result, 0, byteCount);
    if (bytesRead != byteCount) {
      return null;
    } else {
      return result;
    }
  }


}
