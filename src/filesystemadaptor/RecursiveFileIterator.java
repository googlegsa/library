package filesystemadaptor;

import adaptorlib.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

class RecursiveFileIterator implements Iterator<File>, Iterable<File> {
  private List<List<File>> traversalStateStack
      = new ArrayList<List<File>>();

  public RecursiveFileIterator(File rootFile) {
    ArrayList<File> roots = new ArrayList<File>();
    roots.add(rootFile);
    traversalStateStack.add(roots);
  }

  public Iterator<File> iterator() {
    return this;
  }

  private void setPositionToNextFile() {
    while (traversalStateStack.size() > 0) {
      int last = traversalStateStack.size() - 1;
      List<File> l = traversalStateStack.get(last);

      if (l.isEmpty()) {
        traversalStateStack.remove(traversalStateStack.size() - 1);
      } else {
        File f = l.get(0);
        if (f.isDirectory()) {
          l.remove(0);
          List<File> child = Arrays.asList(f.listFiles());
          traversalStateStack.add(new ArrayList<File>(child));
        } else if (!isQualifiyingFile(f)) {
          l.remove(0);
        } else {
          return;
        }
      }
    }
  }

  private boolean isQualifiyingFile(File f) {
    return true;
  }

  public boolean hasNext() {
    setPositionToNextFile();
    return !traversalStateStack.isEmpty();
  }

  public File next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    File next = traversalStateStack.get(
        traversalStateStack.size() - 1).remove(0);
    return next;
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }

  public static void main(String a[]) {
    File d = new File("/usr/local/google/home/pjo/smbdup/800k/");
    Iterator<File> itr = new RecursiveFileIterator(d);
    for (int count = 0; itr.hasNext(); count++) {
      File f = itr.next();
      System.out.println("" + f);
    }
  }
}
