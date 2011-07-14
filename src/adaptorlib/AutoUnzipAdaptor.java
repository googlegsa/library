package adaptorlib;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;

/**
 * Wrapping Adaptor that auto-unzips zips within the nested Adaptor.
 */
public class AutoUnzipAdaptor extends WrapperAdaptor {
  private static final Logger log
      = Logger.getLogger(AutoUnzipAdaptor.class.getName());
  /**
   * Delimiter to separate a file from the ZIP it is contained within for use in
   * DocIds.
   */
  private static final String DELIMITER = "!";
  // Escape normal '!' using '\\!' (single backslash, bang)
  private static final String ESCAPE_DELIMITER = "\\\\!";
  // Matches strings that contain a '!' with no preceding backslash
  private static final String DELIMITER_MATCHER = "(?<!\\\\)!";

  /**
   * Wrap {@code adaptor} with auto-unzip functionality.
   */
  public AutoUnzipAdaptor(Adaptor adaptor) {
    super(adaptor);
  }

  /**
   * Auto-expand all ZIPs listed by the wrapped adaptor in addition to the
   * normal contents the wrapped adaptor provides.
   */
  @Override
  public List<DocId> getDocIds() throws IOException {
    List<DocId> handles = super.getDocIds();
    List<DocId> expanded = new ArrayList<DocId>(handles.size());
    for (DocId docId : handles) {
      String uniqueId = escape(docId.getUniqueId());
      expanded.add(new DocId(uniqueId, docId.getDocReadPermissions()));
    }

    for (DocId docId : handles) {
      if (!docId.getUniqueId().endsWith(".zip")) {
        continue;
      }
      byte[] content;
      try {
        content = super.getDocContent(docId);
      } catch (IOException ex) {
        // We don't throw this exception because we want to remain as
        // transparent as possible. This should be a additional feature that
        // doesn't bring down the world when things go wrong.
        log.log(Level.FINE, "Exception trying to auto-expand a zip", ex);
        continue;
      }
      DocId escaped = new DocId(escape(docId.getUniqueId()),
                                docId.getDocReadPermissions());
      InputStream is = new ByteArrayInputStream(content);
      listZip(escaped, is, expanded);
    }
    return expanded;
  }

  /**
   * Get list of files within zip. Recursive method to handle listing ZIPs in
   * ZIPs.
   */
  private static void listZip(DocId docId, InputStream is,
                              List<DocId> expanded) {
    File rawZip;
    try {
      rawZip = IOHelper.writeToTempFile(is);
    } catch (IOException ex) {
      log.log(Level.WARNING, "Could not save zip to temporary file.", ex);
      // Bail for this entry
      return;
    }
    ZipFile zip = null;
    try {
      try {
        zip = new ZipFile(rawZip);
      } catch (IOException ex) {
        log.log(Level.WARNING, "Could not open zip.", ex);
        // Bail for this entry
        return;
      }
      for (Enumeration<? extends ZipEntry> en = zip.entries();
           en.hasMoreElements(); ) {
        ZipEntry e = en.nextElement();
        if (e.isDirectory()) {
          continue;
        }
        String uniqId = docId.getUniqueId() + DELIMITER + escape(e.getName());
        DocId nestedId = new DocId(uniqId, docId.getDocReadPermissions());
        expanded.add(nestedId);
        if (uniqId.endsWith(".zip")) {
          InputStream nestedIs;
          try {
            nestedIs = zip.getInputStream(e);
          } catch (IOException ex) {
            log.log(Level.WARNING,
                    "Could not get recursive zip file contents within zip", ex);
            // Bail for this entry
            continue;
          }
          listZip(nestedId, nestedIs, expanded);
        }
      }
    } finally {
      if (zip != null) {
        try {
          zip.close();
        } catch (IOException ex) {
          // ignore
        }
      }
      rawZip.delete();
    }
  }

  /**
   * Provide content of file that exists within a ZIP provided by the wrapped
   * adaptor or simply call wrapped adaptor.
   */
  @Override
  public byte[] getDocContent(DocId docId) throws IOException {
    String[] parts = docId.getUniqueId().split(DELIMITER_MATCHER, 2);
    String filename = unescape(parts[0]);
    byte[] content = super.getDocContent(new DocId(filename));
    if (content == null) {
      throw new IOException("Nested adaptor did not provide content.");
    }
    if (parts.length == 1) {
      // This was just a normal file, without any of our escapes
      return content;
    }
    try {
      return extractDocFromZip(new DocId(parts[1]),
                               new ByteArrayInputStream(content));
    } catch (FileNotFoundException ex) {
      throw new FileNotFoundException(
          "Could not find file within zip for docId '" + docId.getUniqueId()
          + "': " + ex.getMessage());
    }
  }

  /**
   * Recursively provide content of file within ZIP file {@code is}. Recursion
   * allows providing a file within a ZIP within a ZIP.
   */
  private static byte[] extractDocFromZip(DocId docId, InputStream is)
      throws IOException {
    File file = IOHelper.writeToTempFile(is);
    ZipFile zip = null;
    try {
      zip = new ZipFile(file);
      String[] parts = docId.getUniqueId().split(DELIMITER_MATCHER, 2);
      String filename = unescape(parts[0]);
      ZipEntry entry = zip.getEntry(filename);
      if (entry == null || entry.isDirectory()) {
        throw new FileNotFoundException("Could not find file '" + filename);
      }
      InputStream nestedIs = zip.getInputStream(entry);
      if (parts.length == 1) {
        return IOHelper.readInputStreamToByteArray(nestedIs);
      } else {
        return extractDocFromZip(new DocId(parts[1]), nestedIs);
      }
    } finally {
      if (zip != null) {
        try {
          zip.close();
        } catch (IOException ex) {
          // ignore
        }
      }
      file.delete();
    }
  }

  /**
   * Escape used characters within a DocId provided by wrapped adaptor or file
   * names within a zip file.
   */
  private static String escape(String string) {
    return string.replaceAll(DELIMITER, ESCAPE_DELIMITER);
  }

  /**
   * Remove escapes added by {@link #escape}.
   */
  private static String unescape(String string) {
    return string.replaceAll(ESCAPE_DELIMITER, DELIMITER);
  }
}
