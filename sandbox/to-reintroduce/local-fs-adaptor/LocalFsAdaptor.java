package localfs;
import adaptorlib.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
class LocalFsAdaptor implements DocContentRetriever {

private static byte[] toByteArray(File f) {
    if (f.length() > Integer.MAX_VALUE) {
        // TODO: Think and modify.
        throw new IllegalArgumentException(f + " is too large!");
    }
    int length = (int) f.length();
    byte[] content = new byte[length];
    int off = 0;
    int read = 0;
    InputStream in = null;
    try {
        in = new FileInputStream(f);
        while (read != -1 && off < length) {
            read = in.read(content, off, (length - off));
            off += read;
        }
        if (off != length) {
            // file size has shrunken since check, handle appropriately
        } else if (in.read() != -1) {
            // file size has grown since check, handle appropriately
        }
        return content;
    } catch (IOException e) {
      System.out.println("Mashed by IOE: " + f.getAbsolutePath()); 
      return null;
    } finally {
      try {
        if (null != in)
          in.close();
      } catch (Exception eee) {}
    }
}



  String p = "/usr/local/google/home/pjo/smbdup/";
  File d = new File(p);

  public List<DocId> getDocIds() {
    Iterator<File> itr = new FileIterator(d);
    ArrayList<DocId> docIds = new ArrayList<DocId>();
    while (itr.hasNext()) {
      File f = itr.next();
      String rel = f.getAbsolutePath().substring(p.length());
      docIds.add(new DocId(rel));
    }
    return docIds;
  }

  /** Gives the bytes of a document referenced with id. Returns
    null if such a document doesn't exist.  */
  public byte []getDocContent(DocId id) {
    File f = new File(d, id.getUniqueId());
    if (f.exists()) {
      return toByteArray(f);
    } else {
      System.out.println("Mashed by: " + f.getAbsolutePath()); 
      return null;
    }
  }

  /** Answers whether particular user is allowed access to 
    referenced document. */
  boolean isAllowedAccess(DocId id, String username) {
    return true;
  }

  public static void main(String a[]) {
    LocalFsAdaptor adapter
        = new LocalFsAdaptor();

    int port = Config.getLocalPort();
    try {
      new GsaCommunicationHandler(port, adapter).beginListeningForConnections();
    } catch (IOException e) {
      throw new RuntimeException("could not listen on " + port, e);
    }
    for (int ntimes=0;ntimes<1;ntimes++) {
      System.out.println("PJO b4 get docids " + ntimes + ":" + new Date());
      List<DocId> handles = adapter.getDocIds();
      System.out.println("PJO at get docids: " + ntimes + ":" + new Date());
      GsaCommunicationHandler.pushDocIds("FUF", handles);
      System.out.println("PJO pushed: " + ntimes + ":" + new Date());
      try {
        Thread.sleep(1000 * 60 * 60);
      } catch (InterruptedException eii) {
        System.out.println("interrupted?"+eii); 
      }
    }
  }
}
