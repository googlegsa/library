package adaptorlib;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;

/**
 * Wrapping Adaptor that auto-unzips zips within the nested Adaptor.
 */
public class AutoUnzipAdaptor extends WrapperAdaptor
      implements Adaptor.DocIdPusher {
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
  private DocIdPusher pusher;

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
  public void getDocIds(DocIdPusher pusher) throws IOException,
         InterruptedException {
    super.getDocIds(this);
  }

  @Override
  public DocId pushDocIds(Iterable<DocId> docIds)
      throws InterruptedException {
    return pushDocIds(docIds, null);
  }

  @Override
  public DocId pushDocIds(Iterable<DocId> docIds,
                          Adaptor.PushErrorHandler handler)
      throws InterruptedException {
    List<DocId> expanded = new ArrayList<DocId>();
    for (DocId docId : docIds) {
      expanded.add(new DocId(escape(docId.getUniqueId())));
    }

    for (DocId docId : docIds) {
      if (!docId.getUniqueId().endsWith(".zip")) {
        continue;
      }
      File tmpFile;
      try {
        tmpFile = File.createTempFile("adaptorlib", ".tmp");
      } catch (IOException ex) {
        log.log(Level.WARNING, "Could not create temporary file", ex);
        // Bail for this entry
        continue;
      }
      try {
        try {
          OutputStream os = new FileOutputStream(tmpFile);
          try {
            Response resp = new GetContentsResponse(os);
            super.getDocContent(new GetContentsRequest(docId), resp);
          } finally {
            os.close();
          }
        } catch (IOException ex) {
          // We don't throw this exception because we want to remain as
          // transparent as possible. This should be a additional feature that
          // doesn't bring down the world when things go wrong.
          log.log(Level.FINE, "Exception trying to auto-expand a zip", ex);
          continue;
        }
        DocId escaped = new DocId(escape(docId.getUniqueId()));
        listZip(escaped, tmpFile, expanded);
      } finally {
        tmpFile.delete();
      }
    }

    DocId failedId = pusher.pushDocIds(expanded, handler);
    if (failedId == null) {
      // Success
      return null;
    }
    return getDocIdParts(failedId)[0];
  }

  /**
   * Get list of files within zip. Recursive method to handle listing ZIPs in
   * ZIPs.
   */
  private static void listZip(DocId docId, File rawZip,
                              List<DocId> expanded) {
    ZipFile zip;
    try {
      zip = new ZipFile(rawZip);
    } catch (IOException ex) {
      log.log(Level.WARNING, "Could not open zip.", ex);
      // Bail for this entry
      return;
    }
    try {
      for (Enumeration<? extends ZipEntry> en = zip.entries();
           en.hasMoreElements(); ) {
        ZipEntry e = en.nextElement();
        if (e.isDirectory()) {
          continue;
        }
        String uniqId = docId.getUniqueId() + DELIMITER + escape(e.getName());
        DocId nestedId = new DocId(uniqId);
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
          File innerRawZip;
          try {
            innerRawZip = IOHelper.writeToTempFile(nestedIs);
          } catch (IOException ex) {
            log.log(Level.WARNING, "Could write out to temporary file", ex);
            // Bail for this entry
            continue;
          }
          try {
            listZip(nestedId, innerRawZip, expanded);
          } finally {
            innerRawZip.delete();
          }
        }
      }
    } finally {
      try {
        zip.close();
      } catch (IOException ex) {
        log.log(Level.WARNING, "Could not close zip.", ex);
      }
    }
  }

  /**
   * Splits an encoded DocId into an unencoded DocId portion, and possibly an
   * additional still-encoded portion.
   */
  private static DocId[] getDocIdParts(DocId internalDocId) {
    String[] parts = internalDocId.getUniqueId().split(DELIMITER_MATCHER, 2);
    parts[0] = unescape(parts[0]);
    DocId[] docParts = new DocId[parts.length];
    for (int i = 0; i < parts.length; i++) {
      docParts[i] = new DocId(parts[i]);
    }
    return docParts;
  }

  /**
   * Provide content of file that exists within a ZIP provided by the wrapped
   * adaptor or simply call wrapped adaptor.
   */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId docId = req.getDocId();
    DocId[] parts = getDocIdParts(docId);
    Request auRequest = new AutoUnzipRequest(req, parts[0]);
    if (parts.length == 1) {
      // This was just a normal file, without any of our escapes
      super.getDocContent(auRequest, resp);
      return;
    }
    if (!req.needDocumentContent()) {
      // No need to perform any real content magic. Everything but contentType
      // applies to both the zip and the file it contains.
      // TODO(ejona): revisit once we add other metadata support
      // TODO(ejona): set content type on response here
      super.getDocContent(auRequest, new NoContentsResponse(resp));
      return;
    }
    File tmpFile = File.createTempFile("adaptorlib", ".tmp");
    try {
      AutoUnzipResponse auResponse = new AutoUnzipResponse(resp,
          new FileOutputStream(tmpFile));
      super.getDocContent(auRequest, auResponse);
      switch (auResponse.getState()) {
        case NORESPONSE:
          // This is an error, but we will let a higher level complain about it
          return;

        case NOTMODIFIED:
          // No content needed, we are done here
          return;

        case CONTENT:
          break;
      }
      try {
        extractDocFromZip(parts[1], tmpFile, new LazyOutputStream(resp));
      } catch (FileNotFoundException e) {
        throw new FileNotFoundException(
            "Could not find file within zip for docId '" + docId.getUniqueId()
            + "': " + e.getMessage());
      }
    } finally {
      tmpFile.delete();
    }
  }

  /**
   * Recursively provide content of file within ZIP file {@code is}. Recursion
   * allows providing a file within a ZIP within a ZIP.
   */
  private static void extractDocFromZip(DocId docId, File file,
                                        OutputStream os) throws IOException {
    ZipFile zip = new ZipFile(file);
    try {
      DocId[] parts = getDocIdParts(docId);
      String filename = parts[0].getUniqueId();
      ZipEntry entry = zip.getEntry(filename);
      if (entry == null || entry.isDirectory()) {
        throw new FileNotFoundException("Could not find file '" + filename);
      }
      InputStream nestedIs = zip.getInputStream(entry);
      if (parts.length == 1) {
        IOHelper.copyStream(nestedIs, os);
      } else {
        File innerFile = IOHelper.writeToTempFile(nestedIs);
        try {
          extractDocFromZip(parts[1], innerFile, os);
        } finally {
          innerFile.delete();
        }
      }
    } finally {
      try {
        zip.close();
      } catch (IOException ex) {
        log.log(Level.WARNING, "Failed to close zip.", ex);
      }
    }
  }

  @Override
  public void setDocIdPusher(DocIdPusher pusher) {
    this.pusher = pusher;
    super.setDocIdPusher(this);
  }

  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
      Collection<DocId> ids) throws IOException {
    List<DocId> unescapedIds = new ArrayList<DocId>(ids.size());
    for (DocId id : ids) {
      unescapedIds.add(getDocIdParts(id)[0]);
    }
    return super.isUserAuthorized(userIdentifier,
                                  Collections.unmodifiableList(unescapedIds));
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

  private static class AutoUnzipRequest extends WrapperRequest {
    private DocId docId;

    public AutoUnzipRequest(Request request, DocId docId) {
      super(request);
      this.docId = docId;
    }

    @Override
    public DocId getDocId() {
      return docId;
    }
  }

  /**
   * Handles the GET request case.
   */
  private static class AutoUnzipResponse extends WrapperResponse {
    private OutputStream os;
    private State state = State.NORESPONSE;

    public AutoUnzipResponse(Response response, OutputStream os) {
      super(response);
      this.os = os;
    }

    @Override
    public void respondNotModified() {
      state = State.NOTMODIFIED;
    }

    @Override
    public OutputStream getOutputStream() {
      // This case is the only one that requires we auto-unzip anything
      state = State.CONTENT;
      return os;
    }

    State getState() {
      return state;
    }

    static enum State {NORESPONSE, NOTMODIFIED, CONTENT};
  }

  /**
   * Handles the HEAD request case.
   */
  private static class NoContentsResponse extends WrapperResponse {
    public NoContentsResponse(Response response) {
      super(response);
    }

    @Override
    public void setContentType(String contentType) {}
  }

  /**
   * OutputStream that passes all calls to the {@code OutputStream} provided by
   * {@link Response#getOutputStream}, but calls {@code getOutputStream} only
   * once needed. This allows for code to be provided an OutputStream that
   * writes directly to the {@code Response}, but also allows the code to
   * throwing a {@link FileNotFoundException} before writing to the stream.
   */
  private static class LazyOutputStream extends OutputStream {
    private Response resp;
    private OutputStream os;

    public LazyOutputStream(Response resp) {
      this.resp = resp;
    }

    public void close() throws IOException {
      loadOs();
      os.close();
    }

    public void flush() throws IOException {
      loadOs();
      os.flush();
    }

    public void write(byte[] b, int off, int len) throws IOException {
      loadOs();
      os.write(b, off, len);
    }

    public void write(int b) throws IOException {
      loadOs();
      os.write(b);
    }

    private void loadOs() {
      os = resp.getOutputStream();
    }
  }
}
