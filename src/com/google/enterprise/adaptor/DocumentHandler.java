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

package com.google.enterprise.adaptor;

import static java.util.Map.Entry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.DateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;

class DocumentHandler extends AbstractHandler {
  private static final Logger log
      = Logger.getLogger(AbstractHandler.class.getName());

  private final DocIdDecoder docIdDecoder;
  private final DocIdEncoder docIdEncoder;
  private final Journal journal;
  private final Adaptor adaptor;
  /**
   * List of Common Names of Subjects that are provided full access when in
   * secure mode. All entries should be lower case.
   */
  private final Set<String> fullAccessCommonNames = new HashSet<String>();
  /**
   * List of IPs that are provided full access when not in secure mode.
   */
  private final Set<InetAddress> fullAccessAddresses
      = new HashSet<InetAddress>();
  private final HttpHandler authnHandler;
  private final SessionManager<HttpExchange> sessionManager;
  private final TransformPipeline transform;
  private final int transformMaxBytes;
  private final boolean transformRequired;
  private final boolean useCompression;

  /**
   * {@code authnHandler} and {@code transform} may be {@code null}.
   */
  public DocumentHandler(String defaultHostname, Charset defaultCharset,
                         DocIdDecoder docIdDecoder, DocIdEncoder docIdEncoder,
                         Journal journal, Adaptor adaptor,
                         String gsaHostname, String[] fullAccessHosts,
                         HttpHandler authnHandler,
                         SessionManager<HttpExchange> sessionManager,
                         TransformPipeline transform, int transformMaxBytes,
                         boolean transformRequired, boolean useCompression) {
    super(defaultHostname, defaultCharset);
    if (docIdDecoder == null || docIdEncoder == null || journal == null
        || adaptor == null || sessionManager == null) {
      throw new NullPointerException();
    }
    this.docIdDecoder = docIdDecoder;
    this.docIdEncoder = docIdEncoder;
    this.journal = journal;
    this.adaptor = adaptor;
    this.authnHandler = authnHandler;
    this.sessionManager = sessionManager;
    this.transform = transform;
    this.transformMaxBytes = transformMaxBytes;
    this.transformRequired = transformRequired;
    this.useCompression = useCompression;

    initFullAccess(gsaHostname, fullAccessHosts);
  }

  private void initFullAccess(String gsaHostname, String[] fullAccessHosts) {
    fullAccessCommonNames.add(gsaHostname.toLowerCase(Locale.ENGLISH));
    for (String hostname : fullAccessHosts) {
      hostname = hostname.trim();
      if ("".equals(hostname)) {
        continue;
      }
      fullAccessCommonNames.add(hostname.toLowerCase(Locale.ENGLISH));
    }
    log.log(Level.INFO, "When in secure mode, common names that are given full "
            + "access to content: {0}", new Object[] {fullAccessCommonNames});

    for (String hostname : fullAccessCommonNames) {
      try {
        InetAddress[] ips = InetAddress.getAllByName(hostname);
        fullAccessAddresses.addAll(Arrays.asList(ips));
      } catch (UnknownHostException ex) {
        log.log(Level.WARNING, "Could not resolve hostname. Not adding it to "
                + "full access list of IPs: " + hostname, ex);
      }
    }
    log.log(Level.INFO, "When not in secure mode, IPs that are given full "
            + "access to content: {0}", new Object[] {fullAccessAddresses});
  }

  private boolean requestIsFromFullyTrustedClient(HttpExchange ex) {
    boolean trust;
    if (ex instanceof HttpsExchange) {
      Principal principal;
      try {
        principal = ((HttpsExchange) ex).getSSLSession().getPeerPrincipal();
      } catch (SSLPeerUnverifiedException e) {
        log.log(Level.FINE, "Client is not trusted. It does not have a verified"
                + " client certificate", e);
        return false;
      }
      if (!(principal instanceof X500Principal)) {
        log.fine("Client is not trusted. It does not have a X500 principal");
        return false;
      }
      LdapName dn;
      try {
        // getName() provides RFC2253-encoded data.
        dn = new LdapName(principal.getName());
      } catch (InvalidNameException e) {
        // Getting here may represent a bug in the standard libraries.
        log.log(Level.FINE, "Client is not trusted. The X500 principal could "
                + "not be parsed", e);
        return false;
      }
      String commonName = null;
      for (Rdn rdn : dn.getRdns()) {
        if ("CN".equalsIgnoreCase(rdn.getType())
            && (rdn.getValue() instanceof String)) {
          commonName = (String) rdn.getValue();
          break;
        }
      }
      if (commonName == null) {
        log.log(Level.FINE, "Client is not trusted. Could not find Common "
                + "Name");
        return false;
      }
      commonName = commonName.toLowerCase(Locale.ENGLISH);
      trust = fullAccessCommonNames.contains(commonName);
      if (trust) {
        log.log(Level.FINE, "Client is trusted in secure mode: {0}",
                commonName);
      } else {
        log.log(Level.FINE, "Client is not trusted in secure mode: {0}",
                commonName);
      }
    } else {
      InetAddress addr = ex.getRemoteAddress().getAddress();
      trust = fullAccessAddresses.contains(addr);
      if (trust) {
        log.log(Level.FINE, "Client is trusted in non-secure mode: {0}", addr);
      } else {
        log.log(Level.FINE, "Client is not trusted in non-secure mode: {0}",
                addr);
      }
    }

    return trust;
  }

  @Override
  public void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
      /* Call into adaptor developer code to get document bytes. */
      DocId docId = docIdDecoder.decodeDocId(getRequestUri(ex));
      log.fine("id: " + docId.getUniqueId());

      if (!authzed(ex, docId)) {
        return;
      }

      DocumentRequest request = new DocumentRequest(ex, docId,
                                                    dateFormat.get());
      DocumentResponse response = new DocumentResponse(ex, docId);
      journal.recordRequestProcessingStart();
      try {
        adaptor.getDocContent(request, response);
      } catch (RuntimeException e) {
        journal.recordRequestProcessingFailure();
        throw e;
      } catch (IOException e) {
        journal.recordRequestProcessingFailure();
        throw e;
      }
      journal.recordRequestProcessingEnd(response.getWrittenContentSize());

      response.complete();
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
                    Translation.HTTP_BAD_METHOD);
    }
  }

  /**
   * Check authz of user to access document. If the user is not authzed, the
   * method handles responding to the HttpExchange.
   *
   * @return {@code true} if user authzed
   */
  private boolean authzed(HttpExchange ex, DocId docId) throws IOException {
    if ("SecMgr".equals(ex.getRequestHeaders().getFirst("User-Agent"))) {
      // Assume that the SecMgr is performing a "HEAD" request to check authz.
      // We don't support this, so we always issue deny.
      cannedRespond(ex, HttpURLConnection.HTTP_FORBIDDEN,
                    Translation.HTTP_FORBIDDEN_SECMGR);
      return false;
    }

    if (requestIsFromFullyTrustedClient(ex)) {
      journal.recordGsaContentRequest(docId);
    } else {
      journal.recordNonGsaContentRequest(docId);
      // Default to anonymous.
      AuthnIdentity identity = null;

      Session session = sessionManager.getSession(ex, false);
      if (session != null) {
        AuthnState authnState
            = (AuthnState) session.getAttribute(AuthnState.SESSION_ATTR_NAME);
        if (authnState != null && authnState.isAuthenticated()) {
          identity = authnState.getIdentity();
        }
      }

      Map<DocId, AuthzStatus> authzMap = adaptor.isUserAuthorized(identity,
          Collections.singletonList(docId));

      AuthzStatus status = authzMap != null ? authzMap.get(docId) : null;
      if (status == null) {
        status = AuthzStatus.DENY;
        log.log(Level.WARNING, "Adaptor did not provide an authorization "
                + "result for the requested DocId ''{0}''. Instead provided: "
                + "{1}", new Object[] {docId, authzMap});
      }

      if (status == AuthzStatus.INDETERMINATE) {
        cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
                      Translation.HTTP_NOT_FOUND);
        return false;
      } else if (status == AuthzStatus.DENY) {
        if (identity == null && authnHandler != null) {
          // User was anonymous and document is not public, so try to authn
          // user.
          authnHandler.handle(ex);
          return false;
        } else {
          cannedRespond(ex, HttpURLConnection.HTTP_FORBIDDEN,
                        Translation.HTTP_FORBIDDEN);
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Format the GSA-specific metadata header value for crawl-time metadata.
   */
  static String formMetadataHeader(Metadata metadata) {
    StringBuilder sb = new StringBuilder();
    for (Entry<String, String> item : metadata) {
      percentEncodeMapEntryPair(sb, item.getKey(), item.getValue());
    }
    return (sb.length() == 0) ? "" : sb.substring(0, sb.length() - 1);
  }

  static String formAclHeader(Acl acl, DocIdEncoder docIdEncoder) {
    StringBuilder sb = new StringBuilder();
    for (String permitUser : acl.getPermitUsers()) {
      percentEncodeMapEntryPair(sb, "google:aclusers", permitUser);
    }
    for (String permitGroup : acl.getPermitGroups()) {
      percentEncodeMapEntryPair(sb, "google:aclgroups", permitGroup);
    }
    for (String denyUser : acl.getDenyUsers()) {
      percentEncodeMapEntryPair(sb, "google:acldenyusers", denyUser);
    }
    for (String denyGroup : acl.getDenyGroups()) {
      percentEncodeMapEntryPair(sb, "google:acldenygroups", denyGroup);
    }
    if (acl.getInheritFrom() != null) {
      percentEncodeMapEntryPair(sb, "google:aclinheritfrom",
          docIdEncoder.encodeDocId(acl.getInheritFrom()).toString());
    }
    if (acl.getInheritanceType() != Acl.InheritanceType.LEAF_NODE) {
      percentEncodeMapEntryPair(sb, "google:aclinheritancetype",
          acl.getInheritanceType().getCommonForm());
    }
    return (sb.length() == 0) ? "" : sb.substring(0, sb.length() - 1);
  }

  /**
   * Format the GSA-specific anchor header value for extra crawl-time anchors.
   */
  static String formAnchorHeader(List<URI> uris, List<String> texts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < uris.size(); i++) {
      URI uri = uris.get(i);
      String text = texts.get(i);
      if (text == null) {
        sb.append(percentEncode(uri.toString()));
        sb.append(",");
      } else {
        percentEncodeMapEntryPair(sb, text, uri.toString());
      }
    }
    return (sb.length() == 0) ? "" : sb.substring(0, sb.length() - 1);
  }

  private static void percentEncodeMapEntryPair(StringBuilder sb, String key,
                                                String value) {
    sb.append(percentEncode(key));
    sb.append("=");
    sb.append(percentEncode(value));
    sb.append(",");
  }

  /**
   * Percent-encode {@code value} as described in
   * <a href="http://tools.ietf.org/html/rfc3986#section-2">RFC 3986</a> and
   * using UTF-8. This is the most common form of percent encoding. The
   * characters A-Z, a-z, 0-9, '-', '_', '.', and '~' are left as-is; the rest
   * are percent encoded.
   */
  static String percentEncode(String value) {
    final Charset encoding = Charset.forName("UTF-8");
    StringBuilder sb = new StringBuilder();
    byte[] bytes = value.getBytes(encoding);
    for (byte b : bytes) {
      if ((b >= 'a' && b <= 'z')
          || (b >= 'A' && b <= 'Z')
          || (b >= '0' && b <= '9')
          || b == '-' || b == '_' || b == '.' || b == '~') {
        sb.append((char) b);
      } else {
        // Make sure it is positive
        int i = b & 0xff;
        String hex = Integer.toHexString(i).toUpperCase();
        if (hex.length() > 2) {
          throw new AssertionError();
        }
        while (hex.length() != 2) {
          hex = "0" + hex;
        }
        sb.append('%').append(hex);
      }
    }
    return sb.toString();
  }

  private static class DocumentRequest implements Request {
    // DateFormats are relatively expensive to create, and cannot be used from
    // multiple threads
    private final DateFormat dateFormat;
    private final HttpExchange ex;
    private final DocId docId;

    private DocumentRequest(HttpExchange ex, DocId docId,
                            DateFormat dateFormat) {
      this.ex = ex;
      this.docId = docId;
      this.dateFormat = dateFormat;
    }

    @Override
    public boolean hasChangedSinceLastAccess(Date lastModified) {
      Date date = getLastAccessTime();
      if (date == null) {
        return true;
      }
      return date.before(lastModified);
    }

    @Override
    public Date getLastAccessTime() {
      return getIfModifiedSince(ex);
    }

    @Override
    public DocId getDocId() {
      return docId;
    }

    @Override
    public String toString() {
      return "Request(docId=" + docId
          + ",lastAccessTime=" + getLastAccessTime() + ")";
    }
  }

  /**
   * The state of the response. The state begins in SETUP mode, after which it
   * should transition to another state and become fixed at that state.
   */
  private enum State {
    /**
     * The class has not been informed how to respond, so we can still make
     * changes to what will be provided in headers.
     */
    SETUP,
    /** No content to send, but we do need a different response code. */
    NOT_MODIFIED,
    /** No content to send, but we do need a different response code. */
    NOT_FOUND,
    /** Must not respond with content, but otherwise act like normal. */
    HEAD,
    /** No need to buffer contents before sending. */
    NO_TRANSFORM,
    /**
     * Buffer "small" contents. Large file contents will be written without
     * transformation or cause an exception (depending on transformRequired).
     */
    TRANSFORM,
  }

  /**
   * Handles incoming data from adaptor and sending it to the client. There are
   * unfortunately many possible response cases. In short they are: document is
   * Not Modified, document contents are ignored because we are responding to a
   * HEAD request, transform pipeline is in use and document is small, transform
   * pipeline is in use and document is large, and transform pipeline is not in
   * use.
   *
   * <p>{@link #getOutputStream} and {@link #complete} are the main methods that
   * need to be very aware of all the different possibilities.
   */
  private class DocumentResponse implements Response {
    private State state = State.SETUP;
    private HttpExchange ex;
    private OutputStream os;
    private CountingOutputStream countingOs;
    private String contentType;
    private Metadata metadata = new Metadata();
    private Acl acl = Acl.EMPTY;
    private boolean secure;
    private List<URI> anchorUris = new ArrayList<URI>();
    private List<String> anchorTexts = new ArrayList<String>();
    private final DocId docId;
    private boolean noIndex;
    private boolean noFollow;
    private boolean noArchive;

    public DocumentResponse(HttpExchange ex, DocId docId) {
      this.ex = ex;
      this.docId = docId;
    }

    @Override
    public void respondNotModified() throws IOException {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      state = State.NOT_MODIFIED;
    }

    @Override
    public void respondNotFound() throws IOException {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      state = State.NOT_FOUND;
    }

    @Override
    public OutputStream getOutputStream() {
      switch (state) {
        case SETUP:
          // We will need to make an OutputStream.
          break;
        case HEAD:
        case NO_TRANSFORM:
        case TRANSFORM:
          // Already called before. Provide saved OutputStream.
          return os;
        case NOT_MODIFIED:
          throw new IllegalStateException("respondNotModified already called");
        case NOT_FOUND:
          throw new IllegalStateException("respondNotFound already called");
        default:
          throw new IllegalStateException("Already responded");
      }
      if ("HEAD".equals(ex.getRequestMethod())) {
        state = State.HEAD;
        os = new SinkOutputStream();
      } else {
        if (transform != null) {
          state = State.TRANSFORM;
          OutputStream innerOs = transformRequired
              ? new CantUseOutputStream() : new LazyContentOutputStream();
          countingOs = new CountingOutputStream(innerOs);
          os = new MaxBufferOutputStream(countingOs, transformMaxBytes);
        } else {
          state = State.NO_TRANSFORM;
          countingOs = new CountingOutputStream(new LazyContentOutputStream());
          os = countingOs;
        }
      }
      return os;
    }

    @Override
    public void setContentType(String contentType) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.contentType = contentType;
    }

    @Override
    public void addMetadata(String key, String value) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      metadata.add(key, value);
    }

    @Override
    public void setAcl(Acl acl) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.acl = acl;
    }

    @Override
    public void setSecure(boolean secure) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.secure = secure;
    }

    @Override
    public void addAnchor(URI uri, String text) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      if (uri == null) {
        throw new NullPointerException();
      }
      anchorUris.add(uri);
      anchorTexts.add(text);
    }

    @Override
    public void setNoIndex(boolean noIndex) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.noIndex = noIndex;
    }

    @Override
    public void setNoFollow(boolean noFollow) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.noFollow = noFollow;
    }

    @Override
    public void setNoArchive(boolean noArchive) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.noArchive = noArchive;
    }

    private long getWrittenContentSize() {
      return countingOs == null ? 0 : countingOs.getBytesWritten();
    }

    private void complete() throws IOException {
      switch (state) {
        case SETUP:
          throw new IOException("No response sent from adaptor");

        case NOT_MODIFIED:
          respond(ex, HttpURLConnection.HTTP_NOT_MODIFIED, null, null);
          break;

        case NOT_FOUND:
          cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
                        Translation.HTTP_NOT_FOUND);
          break;

        case TRANSFORM:
          MaxBufferOutputStream mbos = (MaxBufferOutputStream) os;
          byte[] buffer = mbos.getBufferedContent();
          if (buffer == null) {
            log.info("Not transforming document because document is too large");
          } else {
            ByteArrayOutputStream baos = transform(buffer);
            buffer = null;
            startSending(true);
            baos.writeTo(ex.getResponseBody());
          }
          ex.getResponseBody().flush();
          ex.getResponseBody().close();
          break;

        case NO_TRANSFORM:
          // The adaptor called getOutputStream, but that doesn't mean they
          // wrote out to it (consider an empty document). Thus, we force a
          // usage of the output stream now.
          os.flush();
          ex.getResponseBody().flush();
          ex.getResponseBody().close();
          break;

        case HEAD:
          startSending(false);
          break;

        default:
          throw new IllegalStateException();
      }
      ex.close();
    }

    private void startSending(boolean hasContent) throws IOException {
      if (requestIsFromFullyTrustedClient(ex)) {
        // Always specify metadata and ACLs, even when empty, to replace
        // previous values.
        ex.getResponseHeaders().add("X-Gsa-External-Metadata",
                                    formMetadataHeader(metadata));
        ex.getResponseHeaders().add("X-Gsa-External-Metadata",
                                    formAclHeader(acl, docIdEncoder));
        if (!anchorUris.isEmpty()) {
          ex.getResponseHeaders().add("X-Gsa-External-Anchor",
              formAnchorHeader(anchorUris, anchorTexts));
        }
        // Specify the security, even if public, because the default varies.
        // For instance, requesting the client certificate of the GSA can mark
        // documents secure, but it can also leave them as public, depending on
        // a GSA configuration setting.
        ex.getResponseHeaders().add("X-Gsa-Serve-Security",
            secure ? "secure" : "public");
        if (noIndex) {
          ex.getResponseHeaders().add("X-Robots-Tag", "noindex");
        }
        if (noFollow) {
          ex.getResponseHeaders().add("X-Robots-Tag", "nofollow");
        }
        if (noArchive) {
          ex.getResponseHeaders().add("X-Robots-Tag", "noarchive");
        }
      }
      if (useCompression) {
        // TODO(ejona): decide when to use compression based on mime-type
        enableCompressionIfSupported(ex);
      }
      startResponse(ex, HttpURLConnection.HTTP_OK, contentType, hasContent);
    }

    private ByteArrayOutputStream transform(byte[] content) throws IOException {
      ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
      Map<String, String> params = new HashMap<String, String>();
      params.put("DocId", docId.getUniqueId());
      params.put("Content-Type", contentType);
      try {
        transform.transform(content, contentOut, metadata, params);
      } catch (TransformException e) {
        throw new IOException(e);
      }
      contentType = params.get("Content-Type");
      return contentOut;
    }

    /**
     * Used when transform pipeline is circumvented.
     */
    private class LazyContentOutputStream extends AbstractLazyOutputStream {
      protected OutputStream retrieveOs() throws IOException {
        startSending(true);
        return ex.getResponseBody();
      }
    }
    
    /**
     * Used when transform pipeline is circumvented, but the pipeline is
     * required.
     */
    private class CantUseOutputStream extends AbstractLazyOutputStream {
      protected OutputStream retrieveOs() throws IOException {
        throw new IOException("Transform pipeline is required, but document is "
                              + "too large");
      }
    }
  }

  /**
   * OutputStream that forgets all input. It is equivalent to using /dev/null.
   */
  private static class SinkOutputStream extends OutputStream {
    @Override
    public void write(byte[] b, int off, int len) throws IOException {}

    @Override
    public void write(int b) throws IOException {}
  }

  private static class CountingOutputStream extends FastFilterOutputStream {
    private long count;

    public CountingOutputStream(OutputStream out) {
      super(out);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      super.write(b, off, len);
      // Increment after write so that 'len' is known valid. If an exception is
      // thrown then this is likely the better behavior as well.
      count += len;
    }

    public long getBytesWritten() {
      return count;
    }
  }

  /**
   * {@link ByteArrayOutputStream} that allows inquiring the current number of
   * bytes written.
   */
  private static class CountByteArrayOutputStream
      extends ByteArrayOutputStream {
    public int getCount() {
      return count;
    }
  }

  /**
   * Stream that buffers all content up to a maximum size, at which point it
   * stops buffering altogether.
   */
  private static class MaxBufferOutputStream extends FastFilterOutputStream {
    private static final Logger log
        = Logger.getLogger(MaxBufferOutputStream.class.getName());

    private CountByteArrayOutputStream buffer
        = new CountByteArrayOutputStream();
    private final int maxBytes;

    public MaxBufferOutputStream(OutputStream out, int maxBytes) {
      super(out);
      this.maxBytes = maxBytes;
    }

    @Override
    public void close() throws IOException {
      if (buffer == null) {
        super.close();
      }
    }

    @Override
    public void flush() throws IOException {
      if (buffer == null) {
        super.flush();
      }
    }

    /**
     * Returns the buffered content, or {@code null} when too much content was
     * written an the provided {@code OutputStream} was used.
     */
    public byte[] getBufferedContent() {
      if (buffer == null) {
        return null;
      }
      return buffer.toByteArray();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (buffer != null && buffer.getCount() + len > maxBytes) {
        // Buffer begins overflowing. Flush buffer and stop using it.
        log.fine("Buffer was exhausted. Stopping buffering.");
        buffer.writeTo(out);
        buffer = null;
      }
      if (buffer == null) {
        // Buffer was exhausted. Write out directly.
        super.write(b, off, len);
        return;
      }
      // Write to buffer.
      buffer.write(b, off, len);
    }
  }

  /**
   * {@link FilterOutputStream} replacement that uses {@link
   * #write(byte[],int,int)} for all writes.
   */
  private static class FastFilterOutputStream extends OutputStream {
    private byte[] singleByte = new byte[1];
    // Protected to mimic FilterOutputStream.
    protected OutputStream out;

    public FastFilterOutputStream(OutputStream out) {
      this.out = out;
    }

    @Override
    public void close() throws IOException {
      out.close();
    }

    @Override
    public void flush() throws IOException {
      out.flush();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
      write(b, 0, b.length);
    }

    @Override
    public void write(int b) throws IOException {
      singleByte[0] = (byte) b;
      write(singleByte);
    }
  }
}
