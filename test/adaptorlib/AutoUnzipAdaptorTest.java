package adaptorlib;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.*;

/**
 * Tests for {@link AutoUnzipAdaptor}.
 */
public class AutoUnzipAdaptorTest {
  @Test
  public void testEscape() throws IOException {
    final Charset charset = Charset.forName("US-ASCII");
    final List<DocId> original = Arrays.asList(new DocId[] {
          new DocId("test1"),
          new DocId("test2!"),
          new DocId("!test3"),
          new DocId("!test4!"),
          new DocId("test!test5!test"),
          new DocId("test6!!test"),
        });
    AutoUnzipAdaptor adaptor = new AutoUnzipAdaptor(new Adaptor() {
      public List<DocId> getDocIds() {
        return original;
      }

      public byte[] getDocContent(DocId docId) {
        return docId.getUniqueId().getBytes(charset);
      }
    });

    // Check encoding
    List<DocId> expected = Arrays.asList(new DocId[] {
        new DocId("test1"),
        new DocId("test2\\!"),
        new DocId("\\!test3"),
        new DocId("\\!test4\\!"),
        new DocId("test\\!test5\\!test"),
        new DocId("test6\\!\\!test"),
      });
    List<DocId> retrieved = adaptor.getDocIds();
    assertEquals("encoding", expected, retrieved);

    // Check decoding
    for (int i = 0; i < original.size(); i++) {
      assertArrayEquals("decoding",
                        original.get(i).getUniqueId().getBytes(charset),
                        adaptor.getDocContent(retrieved.get(i)));
    }
  }

  @Test
  public void testUnzipException() throws IOException {
    AutoUnzipAdaptor adaptor = new AutoUnzipAdaptor(new Adaptor() {
      public List<DocId> getDocIds() {
        return Arrays.asList(new DocId[] {new DocId("test.zip")});
      }

      public byte[] getDocContent(DocId docId) throws IOException {
        throw new IOException();
      }
    });
    List<DocId> expected = Arrays.asList(new DocId[] {
        new DocId("test.zip"),
      });
    assertEquals(expected, adaptor.getDocIds());
  }

  @Test
  public void testUnzip() throws IOException {
    final Charset charset = Charset.forName("US-ASCII");
    final List<String> original = Arrays.asList(new String[] {
          "test1",
          "test2!",
          "!test3",
          "!test4!",
          "test!test5!test",
          "test6!!test",
        });
    AutoUnzipAdaptor adaptor = new AutoUnzipAdaptor(new Adaptor() {
      public List<DocId> getDocIds() {
        return Arrays.asList(new DocId[] {new DocId("!test.zip")});
      }

      public byte[] getDocContent(DocId docId) throws IOException {
        if (!"!test.zip".equals(docId.getUniqueId())) {
          throw new FileNotFoundException(docId.getUniqueId());
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        for (String entry : original) {
          zos.putNextEntry(new ZipEntry(entry));
          zos.write(entry.getBytes(charset));
        }
        zos.close();
        return baos.toByteArray();
      }
    });

    // Check encoding
    List<DocId> expected = Arrays.asList(new DocId[] {
        new DocId("\\!test.zip"),
        new DocId("\\!test.zip!test1"),
        new DocId("\\!test.zip!test2\\!"),
        new DocId("\\!test.zip!\\!test3"),
        new DocId("\\!test.zip!\\!test4\\!"),
        new DocId("\\!test.zip!test\\!test5\\!test"),
        new DocId("\\!test.zip!test6\\!\\!test"),
      });
    List<DocId> retrieved = adaptor.getDocIds();
    assertEquals(expected, retrieved);

    // Check decoding
    for (int i = 0; i < original.size(); i++) {
      assertArrayEquals(original.get(i).getBytes(charset),
                        adaptor.getDocContent(retrieved.get(i + 1)));
    }
  }
}
