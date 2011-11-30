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

  private DocIdPusher pusher;
  private final DocIdPusher innerPusher = new InnerDocIdPusher();

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
    super.getDocIds(innerPusher);
  }

  private DocIdPusher.Record pushRecords(
      Iterable<DocIdPusher.Record> records, PushErrorHandler handler)
      throws InterruptedException {
    List<DocIdPusher.Record> expanded = new ArrayList<DocIdPusher.Record>();
    // Used for returning the original DocIdPusher.Record instead of an
    // equivalent DocIdPusher.Record when an error occurs.
    Map<DocIdPusher.Record, DocIdPusher.Record> newOrigMap
        = new HashMap<DocIdPusher.Record, DocIdPusher.Record>();
    for (DocIdPusher.Record docRecord : records) {
      DocId docId = docRecord.getDocId();
      DocIdPusher.Record newRecord = createDerivativeRecord(docRecord,
          escape(docId.getUniqueId()));
      expanded.add(newRecord);
      newOrigMap.put(newRecord, docRecord);

      if (!docId.getUniqueId().endsWith(".zip")) {
        continue;
      }
      // Add the children documents of a zip immediately after the zip, so when
      // an error occurs we can correctly inform the client which document the
      // failure was on.
      if (docRecord.getPushAttributes().isToBeDeleted()) {
        // Not a great case since we don't remember what files were in each zip.
        // The GSA will have to figure out the files are gone via 404s later.
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
        DocIdPusher.Record escaped = createDerivativeRecord(docRecord,
            escape(docId.getUniqueId()));
        listZip(escaped, tmpFile, expanded, newOrigMap);
      } finally {
        tmpFile.delete();
      }
    }

    DocIdPusher.Record failedRecord = pusher.pushRecords(expanded, handler);
    if (failedRecord == null) {
      // Success.
      return null;
    }
    failedRecord = newOrigMap.get(failedRecord);
    if (failedRecord == null) {
      throw new IllegalStateException("Bug triggered");
    }
    return failedRecord;
  }

  /**
   * Creates a new {@code DocIdPusher.Record} that is a copy
   * of {@code docRecord}, but with a different uniqueId of its {@link DocId}.
   */
  private static DocIdPusher.Record createDerivativeRecord(
      DocIdPusher.Record docRecord, String uniqueId) {
    PushAttributes attrs = docRecord.getPushAttributes();
    return new DocIdPusher.Record(new DocId(uniqueId), attrs);
  }

  /**
   * Get list of files within zip. Recursive method to handle listing ZIPs in
   * ZIPs.
   */
  private static void listZip(DocIdPusher.Record docRecord, File rawZip,
      List<DocIdPusher.Record> expanded,
      Map<DocIdPusher.Record, DocIdPusher.Record> newOrigMap) {
    DocId docId = docRecord.getDocId();
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
        DocIdPusher.Record nestedRecord
            = createDerivativeRecord(docRecord, uniqId);
        expanded.add(nestedRecord);
        newOrigMap.put(nestedRecord, docRecord);
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
            listZip(nestedRecord, innerRawZip, expanded, newOrigMap);
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
    File tmpFile = File.createTempFile("adaptorlib", ".tmp");
    try {
      OutputStream tmpFileOs = new FileOutputStream(tmpFile);
      AutoUnzipResponse auResponse;
      try {
        auResponse = new AutoUnzipResponse(resp, tmpFileOs);
        super.getDocContent(auRequest, auResponse);
        tmpFileOs.flush();
      } finally {
        tmpFileOs.close();
      }
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
  public void init(AdaptorContext context) throws Exception {
    this.pusher = context.getDocIdPusher();
    super.init(new InnerAdaptorContext(context, innerPusher));
  }

  @Override
  public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
      Set<String> groups, Collection<DocId> ids) throws IOException {
    List<DocId> unescapedIds = new ArrayList<DocId>(ids.size());
    for (DocId id : ids) {
      unescapedIds.add(getDocIdParts(id)[0]);
    }
    return super.isUserAuthorized(userIdentifier, groups,
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

  private class InnerDocIdPusher extends AbstractDocIdPusher {
    @Override
    public DocIdPusher.Record pushRecords(
        Iterable<DocIdPusher.Record> records, PushErrorHandler handler)
        throws InterruptedException {
      return AutoUnzipAdaptor.this.pushRecords(records, handler);
    }
  }

  private static class InnerAdaptorContext extends WrapperAdaptorContext {
    private DocIdPusher pusher;

    public InnerAdaptorContext(AdaptorContext context, DocIdPusher pusher) {
      super(context);
      this.pusher = pusher;
    }

    @Override
    public DocIdPusher getDocIdPusher() {
      return pusher;
    }
  }
}
