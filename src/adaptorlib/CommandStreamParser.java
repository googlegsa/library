// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;

import com.google.common.collect.ImmutableMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author johnfelton@google.com (John Felton)
 */


public class CommandStreamParser {

  private static final String HEADER_PREFIX = "GSA Adaptor Data Version";
  private static final String DISALLOWED_DELIMITER_CHARS = "[a-zA-Z0-9:/\\-_ =\\+\\[\\]]";
  private static final byte[] END_BINARY_MARKER =
      "4387BDFA-C831-11E0-827B-48354824019B-7B19137E-0D3D-4447-8F55-44B52248A18B".getBytes();

  private static final Map<String, CommandWithArgCount> STRING_TO_COMMAND =
      new ImmutableMap.Builder<String, CommandWithArgCount>()
      .put("end-message", new CommandWithArgCount(CommandType.END_MESSAGE, 0))
      .put("id", new CommandWithArgCount(CommandType.ID, 1))
      .put("last-crawled", new CommandWithArgCount(CommandType.LAST_CRAWLED, 1))
      .put("up-to-date", new CommandWithArgCount(CommandType.UP_TO_DATE, 1))
      .put("meta-name", new CommandWithArgCount(CommandType.META_NAME, 1))
      .put("meta-value", new CommandWithArgCount(CommandType.META_VALUE, 1))
      .put("content-to-end", new CommandWithArgCount(CommandType.CONTENT, 0))
      .put("content-to-marker", new CommandWithArgCount(CommandType.CONTENT, 0))
      .put("content-bytes", new CommandWithArgCount(CommandType.CONTENT, 1))
      .build();

  public static enum CommandType {
    ID, META_NAME, META_VALUE, CONTENT, LAST_CRAWLED, UP_TO_DATE, END_MESSAGE
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

  CommandStreamParser(InputStream inputStream) {
    hybridStream = new ByteCharInputStream(inputStream);
    inIdList = false;
  }

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
      delimiter = ReadHeader();
    }
    String line = hybridStream.readToDelimiter(delimiter);

    // On End-Of-Stream return the end-message command
    if (line == null) {
      return new Command(CommandType.END_MESSAGE, null, null);
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

    if (commandWithArgCount.getArgumentCount() == 0) {
      if (tokens.length != 1) {
        throw new IOException("Invalid Command '" + line +"'");
      }

      if (tokens[0].equals("content-to-end")) {
          content = readBytesUntilEnd();
      } else if (tokens[0].equals("content-to-marker")) {
          content = readBytesUntilMarker();
      }
    } else { // argument count == 1
      if (tokens.length != 2) {
        throw new IOException("Invalid Command '" + line +"'");
      }

      if (tokens[0].equals("content-bytes")) {
        int byteCount = Integer.parseInt(tokens[1]);
        content = readBytes(byteCount);
      } else {
        argument = tokens[1];
      }
    }
    return new Command(commandWithArgCount.getCommandType(), argument, content);
  }

  private String ReadHeader() throws IOException {

    String line = hybridStream.readToDelimiter("[");
    if ((line.length() < HEADER_PREFIX.length()) ||
        !line.substring(0, HEADER_PREFIX.length()).equals(HEADER_PREFIX)) {
      throw new IOException("Adaptor data must begin with '" + HEADER_PREFIX + "'");
    }

    String versionNumber = line.substring(HEADER_PREFIX.length());
    if (!versionNumber.trim().equals("1")) {
      throw new IOException("Adaptor format version '" + versionNumber + "' is not supported.");
    }

    String delimiter = hybridStream.readToDelimiter("]");
    if (delimiter.length() < 1) {
      throw new IOException("Delimiter must be at least one character long.");
    }

      Pattern pattern = Pattern.compile(DISALLOWED_DELIMITER_CHARS);
      Matcher matcher = pattern.matcher(delimiter);
    
      if(matcher.find()){
         throw new IOException("Invalid character in delimiter.");
      }

    return delimiter;
  }

  private int shiftDistance(byte[] buffer) {
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
      byteArrayOutputStream.write(buffer,0, shiftDistance);
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
