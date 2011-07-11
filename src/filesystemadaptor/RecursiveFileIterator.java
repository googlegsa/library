package filesystemadaptor;

import adaptorlib.*;
import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterate over all files within a folder, including files in subdirectories.
 */
class RecursiveFileIterator implements Iterator<File>, Iterable<File> {
  /**
   * The recursive call stack of directory content we have encountered. As we
   * recursively descend into directories, we add one item in the outer list for
   * each directory. The contents of the inner List are the files and folders
   * within that directory. The stack is pushed and poped from the front, so the
   * top-most directory contents are at the end. Things are removed from the
   * inner list as they are consumed by next().
   */
  private List<List<File>> traversalStateStack = new LinkedList<List<File>>();

  public RecursiveFileIterator(File rootFile) {
    List<File> roots = new LinkedList<File>();
    roots.add(rootFile);
    traversalStateStack.add(roots);
  }

  public Iterator<File> iterator() {
    return this;
  }

  /**
   * Make {@code traversalStateStack.get(0).get(0)} be the file that would be
   * returned by {@link #next}, or have tranversalStateStack be empty. If things
   * are already in the right place, then no action is performed.
   */
  private void setPositionToNextFile() {
    while (traversalStateStack.size() > 0) {
      List<File> l = traversalStateStack.get(0);

      if (l.isEmpty()) {
        traversalStateStack.remove(0);
      } else {
        File f = l.get(0);
        if (f.isDirectory()) {
          l.remove(0);
          List<File> child = Arrays.asList(f.listFiles());
          traversalStateStack.add(0, new LinkedList<File>(child));
        } else {
          // Everything looks good
          return;
        }
      }
    }
  }

  public boolean hasNext() {
    setPositionToNextFile();
    return !traversalStateStack.isEmpty();
  }

  public File next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    File next = traversalStateStack.get(0).remove(0);
    return next;
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }
}
