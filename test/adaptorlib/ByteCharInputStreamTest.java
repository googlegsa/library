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

import java.util.Arrays;


/**
 * Tests for {@link ByteCharInputStream}.
 */
public class ByteCharInputStreamTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testUTF8ReadEntireStream() throws IOException {
    String source = "ABC123 %^& ĀÁÂḀ Ⓐ Μονάχη Laȝamon пустынных ტყაოსანი ಸಂಭವಿ";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    ByteCharInputStream byteCharStream = new ByteCharInputStream(inputStream);

    // Delimiter does not exist in source string. Should read entire stream.
    String result = byteCharStream.readToDelimiter("\n");

    assertEquals(source, result);

    // Read past end of stream
    result = byteCharStream.readToDelimiter("\n");

    assertNull(result);
  }

  @Test
  public void testDelimiterSeparation() throws IOException {
    String source = "ABC123 %^&\nĀÁÂḀ Ⓐ\r\nΜονάχη Laȝamon пустынных ტყაოსანი ಸಂಭವಿ";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    ByteCharInputStream byteCharStream = new ByteCharInputStream(inputStream);

    String resultString = byteCharStream.readToDelimiter("\n");
    assertEquals("ABC123 %^&", resultString);
    resultString = byteCharStream.readToDelimiter("\r\n");
    assertEquals("ĀÁÂḀ Ⓐ", resultString);
    resultString = byteCharStream.readToDelimiter("\n");
    assertEquals("Μονάχη Laȝamon пустынных ტყაოსანი ಸಂಭವಿ", resultString);
    resultString = byteCharStream.readToDelimiter("\n");
    assertNull(resultString);

  }

  @Test
  public void testByteRead() throws IOException {
    byte[] source = {-128, -127, -126, -125, -3, -2, -1, 0, 1, 2, 3, 125, 126, 127};

    InputStream inputStream = new ByteArrayInputStream(source);
    ByteCharInputStream byteCharStream = new ByteCharInputStream(inputStream);

    byte[] result = new byte[source.length];

    int length = byteCharStream.readFully(result, 0, 5);
    assertEquals(5, length);
    assertArrayEquals(Arrays.copyOfRange(source, 0, 5), Arrays.copyOfRange(result, 0, 5));

    length = byteCharStream.readFully(result, 0, 8);
    assertEquals(8, length);
    assertArrayEquals(Arrays.copyOfRange(source, 5, 13), Arrays.copyOfRange(result, 0, 8));
  }

  @Test
  public void testByteReadPastEndOfStream() throws IOException {
    byte[] source = {-128, -127, -126, -125, -3, -2, -1, 0, 1, 2, 3, 125, 126, 127};

    InputStream inputStream = new ByteArrayInputStream(source);
    ByteCharInputStream byteCharStream = new ByteCharInputStream(inputStream);

    byte[] result = new byte[100];
    int length = byteCharStream.readFully(result, 0, 100);
    assertEquals(14, length);
    // read past end of stream
    length = byteCharStream.readFully(result, 0, 1);
    assertEquals(-1, length);
  }

  @Test
  public void testMixedUtf8AndByteRead() throws IOException {

    String text1 = "ABC123 %^&\nĀÁÂḀ Ⓐ";
    String surrogatePair = "\uD803\uDC22"; // 'OLD TURKIC LETTER ORKHON EM' (U+10C22)
    byte[] utf8ForSurrogatePair = {(byte) 0xF0, (byte) 0x90, (byte) 0xB0, (byte) 0xA2};
    String text1Delimiter = "\n";
    String text2 = "Μονάχη Laȝamon пустынных ტყაოსანი ಸಂಭವಿ\n";

    byte[] dataBytes1 = {-2, -1, 0, 1, 2};
    byte[] dataBytes2 = {-128, 0, 127};

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byteArrayOutputStream.write(text1.getBytes("UTF-8"));
    byteArrayOutputStream.write(utf8ForSurrogatePair);
    byteArrayOutputStream.write(text1Delimiter.getBytes("UTF-8"));
    byteArrayOutputStream.write(dataBytes1);
    byteArrayOutputStream.write(text2.getBytes("UTF-8"));
    byteArrayOutputStream.write(dataBytes2);
    byte[] source = byteArrayOutputStream.toByteArray();

    InputStream inputStream = new ByteArrayInputStream(source);
    ByteCharInputStream byteCharStream = new ByteCharInputStream(inputStream);

    byte[] result = new byte[100];

    String resultString = byteCharStream.readToDelimiter(("\n"));
    assertEquals("ABC123 %^&", resultString);
    resultString = byteCharStream.readToDelimiter(("\n"));
    assertEquals("ĀÁÂḀ Ⓐ" + surrogatePair, resultString);

    int length = byteCharStream.readFully(result, 0, 5);
    assertEquals(5, length);
    assertArrayEquals(dataBytes1, Arrays.copyOfRange(result, 0, 5));

    resultString = byteCharStream.readToDelimiter(("\n"));
    assertEquals("Μονάχη Laȝamon пустынных ტყაოსანი ಸಂಭವಿ", resultString);

    // Read to end of stream
    length = byteCharStream.readFully(result, 0, 100);
    assertEquals(3, length);
    assertArrayEquals(dataBytes2, Arrays.copyOfRange(result, 0, 3));
  }

  @Test
  public void testNullDelimiter() throws IOException {
    String source = "ABC123";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    ByteCharInputStream byteCharStream = new ByteCharInputStream(inputStream);

    thrown.expect(IllegalArgumentException.class);
    byteCharStream.readToDelimiter(null);
  }
  
  @Test
  public void testEmptyDelimiter() throws IOException {
    String source = "ABC123";

    InputStream inputStream = new ByteArrayInputStream(source.getBytes("UTF-8"));
    ByteCharInputStream byteCharStream = new ByteCharInputStream(inputStream);

    thrown.expect(IllegalArgumentException.class);
    byteCharStream.readToDelimiter("");
  }

}
