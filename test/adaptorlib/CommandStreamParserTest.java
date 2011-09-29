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

import adaptorlib.CommandStreamParser.CommandType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Tests for {@link CommandStreamParser}.
 */
public class CommandStreamParserTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  void checkCommand(CommandType expectedCommandType, String expectedArgument,
      byte[] expectedContents, CommandStreamParser.Command command) {
    assertEquals(expectedCommandType, command.getCommandType());
    if (expectedArgument != null) {
      assertEquals(expectedArgument, command.getArgument());
    } else {
      assertNull(command.getArgument());
    }
    if (expectedContents != null) {
      assertArrayEquals(expectedContents, command.getContents());
    } else {
      assertNull(command.getContents());
    }
  }

  @Test
  public void testReadDocIds() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\nid=123\nid=456\n" +
        "id-list\n10\n20\n30\n\nid=789\n";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    checkCommand(CommandType.ID, "123", null, parser.readCommand());
    checkCommand(CommandType.ID, "456", null, parser.readCommand());
    checkCommand(CommandType.ID, "10", null, parser.readCommand());
    checkCommand(CommandType.ID, "20", null, parser.readCommand());
    checkCommand(CommandType.ID, "30", null, parser.readCommand());
    checkCommand(CommandType.ID, "789", null, parser.readCommand());
    assertNull(parser.readCommand());
  }

  @Test
  public void testInvalidHeaderString() throws IOException {
    String source = "GSA Adaptor Data Ver 1 [\n]\nid=123\nid=456\nid-list\n10\n20\n30\n\nid=789" +
        "\nend-message";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    thrown.expect(IOException.class);
    parser.readCommand();

  }

  @Test
  public void testInvalidVersion() throws IOException {
    String source = "GSA Adaptor Data Version 1a [\n]\nid=123\nid=456\nid-list\n10\n20\n30\n\n" +
        "id=789\nend-message";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    thrown.expect(IOException.class);
    parser.readCommand();

  }

  @Test
  public void testUnsupportedVersion() throws IOException {
    String source = "GSA Adaptor Data Version 2 [\n]\nid=123\nid=456\nid-list\n10\n20\n30\n\n" +
        "id=789\nend-message";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    thrown.expect(IOException.class);
    parser.readCommand();

  }

  @Test
  public void testEmptyDelimiter() throws IOException {
    String source = "GSA Adaptor Data Version 1 []\nid=123\nid=456\nid-list\n10\n20\n30\n\nid=789" +
        "\nend-message";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    thrown.expect(IOException.class);
    parser.readCommand();

  }

  void checkDelimiter(String delimiter, boolean isValid) throws IOException {
    String source = "GSA Adaptor Data Version 1 [" + delimiter + "]" + delimiter + "id=123" +
        delimiter + "end-message";
    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    if (!isValid) {
      thrown.expect(IOException.class);
      parser.readCommand();
    } else {
      checkCommand(CommandType.ID, "123", null, parser.readCommand());
    }

  }

  @Test
  public void testUnsupportedDelimiterCharacters() throws IOException {
    checkDelimiter("A", false);
    checkDelimiter("K", false);
    checkDelimiter("Z", false);

    checkDelimiter("a", false);
    checkDelimiter("k", false);
    checkDelimiter("z", false);

    checkDelimiter("0", false);
    checkDelimiter("5", false);
    checkDelimiter("9", false);

    checkDelimiter(":", false);
    checkDelimiter("/", false);
    checkDelimiter("-", false);
    checkDelimiter("_", false);
    checkDelimiter(" ", false);
    checkDelimiter("=", false);
    checkDelimiter("+", false);
    checkDelimiter("[", false);
    checkDelimiter("]", false);

    checkDelimiter("<+>", false);
    checkDelimiter("/n /n", false);
  }

  @Test
  public void testSupportedDelimiterCharacters() throws IOException {
    checkDelimiter("\0", true);
    checkDelimiter("~!#$%^&*(){}", true);
    checkDelimiter("ĀÁÂḀⒶ", true);
    checkDelimiter("ტყაოსანი", true);
  }

  @Test
  public void testAllValidCommands() throws IOException {
    String source = "GSA Adaptor Data Version 1 [\n]\n" +
        "id=123\nid-list\n10\n\n" +
        "last-crawled=1234567\nup-to-date=false\n" +
        "meta-name=project\nmeta-value=plexi\ncontent-to-marker\nabcdefg" +
        "4387BDFA-C831-11E0-827B-48354824019B-7B19137E-0D3D-4447-8F55-44B52248A18B" +
        "content-bytes=5\n12345content-to-end\n2468";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    CommandStreamParser parser = new CommandStreamParser(inputStream);
    int version = parser.readHeader();
    assertEquals(1, version);

    checkCommand(CommandType.ID, "123", null, parser.readCommand());
    checkCommand(CommandType.ID, "10", null, parser.readCommand());
    checkCommand(CommandType.LAST_CRAWLED, "1234567", null, parser.readCommand());
    checkCommand(CommandType.UP_TO_DATE, "false", null, parser.readCommand());
    checkCommand(CommandType.META_NAME, "project", null, parser.readCommand());
    checkCommand(CommandType.META_VALUE, "plexi", null, parser.readCommand());
    checkCommand(CommandType.CONTENT, null, "abcdefg".getBytes(), parser.readCommand());
    checkCommand(CommandType.CONTENT, null, "12345".getBytes(), parser.readCommand());
    checkCommand(CommandType.CONTENT, null, "2468".getBytes(), parser.readCommand());
  }

  @Test
  public void testReadContentAllBytes() throws IOException {
    String commandSource = "GSA Adaptor Data Version 1 [\n]\ncontent-to-end\n";

    byte[] byteSource = new byte[256];

    byte value = -128;
    for (int i = 0; i <= 255; i++) {
      byteSource[i] = value++;
    }

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byteArrayOutputStream.write(commandSource.getBytes("UTF-8"));
    byteArrayOutputStream.write(byteSource);
    byte[] source = byteArrayOutputStream.toByteArray();

    InputStream inputStream = new ByteArrayInputStream(source);
    CommandStreamParser parser = new CommandStreamParser(inputStream);

    checkCommand(CommandType.CONTENT, null, byteSource, parser.readCommand());
  }

}
