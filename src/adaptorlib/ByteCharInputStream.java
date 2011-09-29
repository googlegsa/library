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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Arrays;

/**
 * An input stream than can be read as both a byte stream and a character stream.
 */
public class ByteCharInputStream {

  private InputStream inputStream;
  private CharsetDecoder charsetDecoder;

  private final int MAX_BYTES_IN_UTF8_CHAR = 6;
  private byte[] byteArray = new byte[MAX_BYTES_IN_UTF8_CHAR];

  public ByteCharInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
    this.charsetDecoder = Charset.availableCharsets().get("UTF-8").newDecoder( );

  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public int readFully(byte[] bytes, int off, int len) throws IOException {
    return IOHelper.readFully(inputStream, bytes, off, len);
  }

  /**
   * Reads a single character.
   *
   * @return a unicode codepoint (one char or a surrogate pair) or null if the end of the stream
   *         has been reached
   *
   * @exception java.io.IOException  If an I/O error occurs
   */
  public String readChar() throws IOException {
    int bytesRead = readFully(byteArray, 0, 1);
    if (bytesRead == -1) {
      return null;
    }
    int byteCount = bytesInUtf8Char(byteArray[0]);
    if (byteCount == 1) {
      return new String(byteArray, 0, 1, "UTF-8");
    } else {
      bytesRead = readFully(byteArray, 1, byteCount - 1);
      if (bytesRead != byteCount - 1) {
        throw new IOException("Invalid UTF-8 Character");
      }
      CharBuffer charBuffer = charsetDecoder.decode(ByteBuffer.wrap( byteArray, 0, byteCount));
      return new String(charBuffer.array(), 0, charBuffer.length());
    }
  }

  /**
   * Reads characters from the stream until {@code delimiter} or EOS (end of stream)
   * is encountered and returns them as a {@code String}.
   * The delimiter is not included in the returned string.
   *
   * @param delimiter - string to read until
   *
   * @return string of characters read before reaching the delimiter.
   *         Null if EOS is encountered before any characters are read
   *
   * @throws IOException 
   */
  public String readToDelimiter(String delimiter) throws IOException {
    if (delimiter == null || delimiter.isEmpty()) {
      throw new IllegalArgumentException("Delimiter may not be null or empty.");
    }
    String nextChar = readChar();
    // If EOS then return null
    if (nextChar == null) {
      return null;
    }

    StringBuilder stringBuilder = new StringBuilder();

    while (nextChar != null) {
      stringBuilder.append(nextChar);
      int delimiterPosition = stringBuilder.length() - delimiter.length();
      if ((delimiterPosition >= 0) &&
          (stringBuilder.substring(delimiterPosition).equals(delimiter))) {
        stringBuilder.delete(delimiterPosition, delimiterPosition + delimiter.length());
        nextChar = null;
      } else {
       nextChar = readChar();
      }
    }
    return stringBuilder.toString();
  }

  /**
   * Determines the number of bytes in a UTF-8 character given the first byte.
   *
   * @param firstByte the first byte of a UTF-8 character
   * @return the number of bytes used to represent the character, including the first byte that
   *         was passed.
   * @throws IOException thrown if firstByte is not a valid first byte in an UTF-8 character.
   */
  private int bytesInUtf8Char(byte firstByte) throws IOException {


    if ((firstByte & 0x80) == 0) {
      return 1; // ASCII - High order bit not set
    } else if ((firstByte & 0xE0) == 0xC0) {
      return 2;
    } else if ((firstByte & 0xF0) == 0xE0) {
      return 3;
    } else if ((firstByte & 0xF8) == 0xF0) {
      return 4;
    } else if ((firstByte & 0xFC) == 0xF8) {
      return 5;
    } else if ((firstByte & 0xFE) == 0xFC) {
      return 6;
    } else {
      throw new IOException("Invalid UTF-8 Character");
    }
  }
}
