package filesystemadaptor;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.util.Iterator;

public class RecursiveFileIteratorTest {
  @Test
  public void testIteration() {
    File dir = new MockFile("parent", new File[] {
      new MockFile("file2"),
      new MockFile("dir1", new File[] {
        new MockFile("dir2", new File[0]),
        new MockFile("file3"),
      }),
      new MockFile("file1"),
      new MockFile("dir3", new File[] {
        new MockFile("file4"),
      }),
    });
    Iterator<File> iter = new RecursiveFileIterator(dir);
    assertTrue(iter.hasNext());
    assertEquals("file2", iter.next().getName());
    assertTrue(iter.hasNext());
    assertEquals("file3", iter.next().getName());
    assertEquals("file1", iter.next().getName());
    assertEquals("file4", iter.next().getName());
    assertFalse(iter.hasNext());
  }

  @Test(expected=RecursiveFileIterator.WrappedIOException.class)
  public void testIOExceptionNext() {
    Iterator<File> iter = new RecursiveFileIterator(new File("trash") {
      @Override
      public File[] listFiles() {
        // Indicates error
        return null;
      }

      @Override
      public boolean isDirectory() {
        return true;
      }
    });
    iter.next();
  }

  @Test(expected=RecursiveFileIterator.WrappedIOException.class)
  public void testIOExceptionHasNext() {
    Iterator<File> iter = new RecursiveFileIterator(new File("trash") {
      @Override
      public File[] listFiles() {
        // Indicates error
        return null;
      }

      @Override
      public boolean isDirectory() {
        return true;
      }
    });
    iter.hasNext();
  }

  private static class MockFile extends File {
    private boolean isDirectory;
    private String name;
    private File[] children;

    /** Constructor for mock files */
    public MockFile(String name) {
      super("trash");
      isDirectory = false;
      this.name = name;
    }

    /** Constructor for mock directories */
    public MockFile(String name, File[] children) {
      super("trash");
      isDirectory = true;
      this.name = name;
      this.children = children;
    }

    @Override
    public boolean isDirectory() {
      return isDirectory;
    }

    @Override
    public File[] listFiles() {
      return children;
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
