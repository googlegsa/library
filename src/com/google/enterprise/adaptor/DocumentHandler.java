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

import com.google.common.annotations.VisibleForTesting;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;

import org.json.simple.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;

class DocumentHandler implements HttpHandler {
  private static final Logger log
      = Logger.getLogger(DocumentHandler.class.getName());

  private final DocIdDecoder docIdDecoder;
  private final DocIdEncoder docIdEncoder;
  private final Journal journal;
  private final Adaptor adaptor;
  private final AuthzAuthority authzAuthority;
  private final Watchdog watchdog;
  private final AsyncPusher pusher;
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
  private final SamlServiceProvider samlServiceProvider;
  private final TransformPipeline transform;
  private final AclTransform aclTransform;
  private final boolean useCompression;
  private final boolean sendDocControls;
  private final boolean markDocsPublic;
  private final long headerTimeoutMillis;
  private final long contentTimeoutMillis;
  private final String scoring;
  private final boolean alwaysGiveAcl;
  private final GsaVersion gsaVersion;

  /**
   * {@code samlServiceProvider} and {@code transform} may be {@code null}.
   */
  public DocumentHandler(DocIdDecoder docIdDecoder, DocIdEncoder docIdEncoder,
                         Journal journal, Adaptor adaptor,
                         AuthzAuthority authzAuthority,
                         String gsaHostname, String[] fullAccessHosts,
                         SamlServiceProvider samlServiceProvider,
                         TransformPipeline transform, AclTransform aclTransform,
                         boolean useCompression,
                         Watchdog watchdog, AsyncPusher pusher,
                         boolean sendDocControls, boolean markDocsPublic,
                         long headerTimeoutMillis,
                         long contentTimeoutMillis, String scoringType,
                         boolean provideAclsAndMetadata,
                         GsaVersion gsaVersion) {
    if (docIdDecoder == null || docIdEncoder == null || journal == null
        || adaptor == null || aclTransform == null || watchdog == null
        || pusher == null || scoringType == null || gsaVersion == null) {
      throw new NullPointerException();
    }
    this.docIdDecoder = docIdDecoder;
    this.docIdEncoder = docIdEncoder;
    this.journal = journal;
    this.adaptor = adaptor;
    this.authzAuthority = authzAuthority;
    this.samlServiceProvider = samlServiceProvider;
    this.transform = transform;
    this.aclTransform = aclTransform;
    this.useCompression = useCompression;
    this.watchdog = watchdog;
    this.pusher = pusher;
    this.sendDocControls = sendDocControls;
    this.markDocsPublic = markDocsPublic;
    this.headerTimeoutMillis = headerTimeoutMillis;
    this.contentTimeoutMillis = contentTimeoutMillis;
    this.scoring = scoringType;
    this.alwaysGiveAcl = provideAclsAndMetadata;
    this.gsaVersion = gsaVersion;
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
      java.security.Principal principal;
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
  public void handle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
      /* Call into adaptor developer code to get document bytes. */
      DocId docId = docIdDecoder.decodeDocId(HttpExchanges.getRequestUri(ex));
      log.log(Level.FINE, "DocId: {0}", docId.getUniqueId());

      if (!authzed(ex, docId)) {
        return;
      }

      DocumentRequest request = new DocumentRequest(ex, docId);
      DocumentResponse response
          = new DocumentResponse(ex, docId, Thread.currentThread());
      journal.recordRequestProcessingStart();
      watchdog.processingStarting(headerTimeoutMillis);
      try {
        adaptor.getDocContent(request, response);
      } catch (InterruptedException e) {
        journal.recordRequestProcessingFailure();
        throw new RuntimeException("Retriever interrupted: " + docId, e);
      } catch (RuntimeException e) {
        journal.recordRequestProcessingFailure();
        throw new RuntimeException("Exception in retriever: " + docId, e);
      } catch (IOException e) {
        journal.recordRequestProcessingFailure();
        throw new IOException("Exception in retriever: " + docId, e);
      } finally {
        watchdog.processingCompleted();
      }
      journal.recordRequestProcessingEnd(response.getWrittenContentSize());

      response.complete();
    } else {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD,
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
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_FORBIDDEN,
          Translation.HTTP_FORBIDDEN_SECMGR);
      return false;
    }

    if (requestIsFromFullyTrustedClient(ex)) {
      journal.recordGsaContentRequest(docId);
    } else if (authzAuthority == null) {
      HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_FORBIDDEN,
          Translation.HTTP_FORBIDDEN);
      return false;
    } else {
      journal.recordNonGsaContentRequest(docId);
      // Default to anonymous.
      AuthnIdentity identity = null;

      if (samlServiceProvider != null) {
        identity = samlServiceProvider.getUserIdentity(ex);
      }

      Map<DocId, AuthzStatus> authzMap = authzAuthority.isUserAuthorized(
          identity, Collections.singletonList(docId));

      AuthzStatus status = authzMap != null ? authzMap.get(docId) : null;
      if (status == null) {
        status = AuthzStatus.DENY;
        log.log(Level.WARNING, "Adaptor did not provide an authorization "
                + "result for the requested DocId ''{0}''. Instead provided: "
                + "{1}", new Object[] {docId, authzMap});
      }

      if (status == AuthzStatus.INDETERMINATE) {
        HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
            Translation.HTTP_NOT_FOUND);
        return false;
      } else if (status == AuthzStatus.DENY) {
        if (identity == null && samlServiceProvider != null) {
          // User was anonymous and document is not public, so try to authn
          // user.
          samlServiceProvider.handleAuthentication(ex);
          return false;
        } else {
          HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_FORBIDDEN,
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

  @VisibleForTesting
  static String formUnqualifiedAclHeader(Acl acl, DocIdEncoder docIdEncoder) {
    if (acl == null) {
      return "";
    }
    if (Acl.EMPTY.equals(acl)) {
      acl = Acl.FAKE_EMPTY;
    }
    StringBuilder sb = new StringBuilder();
    for (UserPrincipal permitUser : acl.getPermitUsers()) {
      String name = permitUser.getName();
      percentEncodeMapEntryPair(sb, "google:aclusers", name);
    }
    for (GroupPrincipal permitGroup : acl.getPermitGroups()) {
      String name = permitGroup.getName();
      percentEncodeMapEntryPair(sb, "google:aclgroups", name);
    }
    for (UserPrincipal denyUser : acl.getDenyUsers()) {
      String name = denyUser.getName();
      percentEncodeMapEntryPair(sb, "google:acldenyusers", name);
    }
    for (GroupPrincipal denyGroup : acl.getDenyGroups()) {
      String name = denyGroup.getName();
      percentEncodeMapEntryPair(sb, "google:acldenygroups", name);
    }
    if (acl.getInheritFrom() != null) {
      URI uri = docIdEncoder.encodeDocId(acl.getInheritFrom());
      try {
        // Although it is named "fragment", we use a query parameter because the
        // GSA "normalizes" away fragments.
        uri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
            acl.getInheritFromFragment(), null);
      } catch (URISyntaxException ex) {
        throw new AssertionError(ex);
      }
      percentEncodeMapEntryPair(sb, "google:aclinheritfrom", uri.toString());
    }
    if (acl.getInheritanceType() != Acl.InheritanceType.LEAF_NODE) {
      percentEncodeMapEntryPair(sb, "google:aclinheritancetype",
          acl.getInheritanceType().getCommonForm());
    }
    return sb.substring(0, sb.length() - 1);
  }

  @VisibleForTesting
  static String formNamespacedAclHeader(Acl acl, DocIdEncoder enc) {
    if (null == acl) {
      return "";
    }
    if (Acl.EMPTY.equals(acl)) {
      acl = Acl.FAKE_EMPTY;
    }
    Map<String, Object> gsaAcl = new TreeMap<String, Object>();
    List<Map<String, String>> gsaAclEntries = makeGsaAclEntries(acl);    
    if (!gsaAclEntries.isEmpty()) {
      gsaAcl.put("entries", gsaAclEntries);
    }
    if (null != acl.getInheritFrom()) {
      URI from = enc.encodeDocId(acl.getInheritFrom());
      try {
        // Although it is named "fragment", we use a query parameter because the
        // GSA "normalizes" away fragments.
        from = new URI(from.getScheme(), from.getAuthority(), from.getPath(),
            acl.getInheritFromFragment(), null);
      } catch (URISyntaxException ex) {
        throw new AssertionError(ex);
      }
      gsaAcl.put("inherit_from", "" + from);
    }
    if (acl.getInheritanceType() != Acl.InheritanceType.LEAF_NODE) {
      String type = "" + acl.getInheritanceType();
      gsaAcl.put("inheritance_type", "" + type);
    }
    return JSONObject.toJSONString(gsaAcl);
  }

  private static List<Map<String, String>> makeGsaAclEntries(Acl acl) {
    List<Map<String, String>> princ = new ArrayList<Map<String, String>>();
    for (Principal p : acl.getPermitGroups()) {
      princ.add(makeGsaAclEntry("permit", acl, p));
    }
    for (Principal p : acl.getDenyGroups()) {
      princ.add(makeGsaAclEntry("deny", acl, p));
    }
    for (Principal p : acl.getPermitUsers()) {
      princ.add(makeGsaAclEntry("permit", acl, p));
    }
    for (Principal p : acl.getDenyUsers()) {
      princ.add(makeGsaAclEntry("deny", acl, p));
    }
    return princ;
  }

  private static Map<String, String> makeGsaAclEntry(String access,
      Acl acl, Principal p) {
    Map<String, String> gsaEntry = new TreeMap<String, String>();
    gsaEntry.put("access", access);
    gsaEntry.put("scope", p.isUser() ? "user" : "group");
    gsaEntry.put("name", p.getName());
    if (!Principal.DEFAULT_NAMESPACE.equals(p.getNamespace())) {
      gsaEntry.put("namespace", p.getNamespace());
    }
    if (!acl.isEverythingCaseSensitive()) {
      gsaEntry.put("case_sensitivity_type", "everything_case_insensitive");
    }
    return gsaEntry;
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
    private final HttpExchange ex;
    private final DocId docId;

    private DocumentRequest(HttpExchange ex, DocId docId) {
      this.ex = ex;
      this.docId = docId;
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
      return HttpExchanges.getIfModifiedSince(ex);
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
    SEND_BODY,
    /** No file content to send but we can send updated metadata and acls */
    NO_CONTENT,
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
    private Thread workingThread;
    private State state = State.SETUP;
    private HttpExchange ex;
    // Whether ex.getResponseBody().close() has been called while we are in the
    // SEND_BODY state. This isn't used for much internal code that calls
    // close on the stream since it is obvious in those states that we won't
    // ever attempt to flush or close the stream a second time.
    private boolean responseBodyClosed;
    private OutputStream os;
    private CountingOutputStream countingOs;
    private String contentType;
    private Date lastModified;
    private Metadata metadata = new Metadata();
    private Acl acl;
    private boolean secure;
    private List<URI> anchorUris = new ArrayList<URI>();
    private List<String> anchorTexts = new ArrayList<String>();
    private final DocId docId;
    private boolean noIndex;
    private boolean noFollow;
    private boolean noArchive;
    private URI displayUrl;
    private boolean crawlOnce;
    private boolean lock;
    private Map<String, Acl> fragments = new TreeMap<String, Acl>();

    public DocumentResponse(HttpExchange ex, DocId docId, Thread thread) {
      this.ex = ex;
      this.docId = docId;
      this.workingThread = thread;
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
   
/* 
    @Override
    public void respondNoContent() throws IOException{
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }

      if (!gsaVersion.isAtLeast("7.4.0-0")) {
        log.log(Level.WARNING,
            "GSA ver {0} doesn't support respondNoContent.", gsaVersion);
      }

      state = State.NO_CONTENT;
      startSending(false);
    }
*/

    @Override
    public OutputStream getOutputStream() throws IOException {
      switch (state) {
        case SETUP:
          // We will need to make an OutputStream.
          break;
        case HEAD:
        case SEND_BODY:
          // Already called before. Provide saved OutputStream.
          return os;
        case NOT_MODIFIED:
          throw new IllegalStateException("respondNotModified already called");
        case NOT_FOUND:
          throw new IllegalStateException("respondNotFound already called");
        case NO_CONTENT:
          throw new IllegalStateException("respondNoContent already called");
        default:
          throw new IllegalStateException("Already responded");
      }
      if ("HEAD".equals(ex.getRequestMethod())) {
        // Unfortunately, we won't be able to report any errors after this
        // point. We don't delay sending the headers, however, because of the
        // watchdog.
        state = State.HEAD;
        startSending(false);
        os = new SinkOutputStream();
      } else {
        state = State.SEND_BODY;
        startSending(true);
        countingOs = new CountingOutputStream(new CloseNotifyOutputStream(
            ex.getResponseBody()));
        os = countingOs;
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
    public void setLastModified(Date lastModified) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.lastModified = lastModified;
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
    public void putNamedResource(String fragment, Acl acl) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      // TODO(pjo): verify fragment string is valid
      this.fragments.put(fragment, acl);
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

    @Override
    public void setDisplayUrl(URI displayUrl) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.displayUrl = displayUrl;
    }

    @Override
    public void setCrawlOnce(boolean crawlOnce) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.crawlOnce = crawlOnce;
    }

    @Override
    public void setLock(boolean lock) {
      if (state != State.SETUP) {
        throw new IllegalStateException("Already responded");
      }
      this.lock = lock;
    }

    private long getWrittenContentSize() {
      return countingOs == null ? 0 : countingOs.getBytesWritten();
    }

    private void complete() throws IOException {
      switch (state) {
        case SETUP:
          throw new IOException("No response sent from adaptor");

        case NOT_MODIFIED:
          HttpExchanges.respond(
              ex, HttpURLConnection.HTTP_NOT_MODIFIED, null, null);
          break;

        case NOT_FOUND:
          HttpExchanges.cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND,
              Translation.HTTP_NOT_FOUND);
          break;

        case NO_CONTENT:
          break;

        case SEND_BODY:
          if (!responseBodyClosed) {
            // The Adaptor didn't close the stream, so close it for them, making
            // sure to flush any existing contents. We choose to use the same
            // OutputStream as the Adaptor in order to prevent bugs due to
            // different codepaths.
            //
            // In particular, it is possible the adaptor called getOutputStream,
            // but didn't write out to the stream (consider an empty document
            // and some code choosing to never call write because all the bytes
            // were written).
            os.flush();
            os.close();
          }
          if (!responseBodyClosed) {
            throw new AssertionError();
          }
          // At this point we are guaranteed that ex.getResponseBody().close()
          // has been called.
          break;

        case HEAD:
          break;

        default:
          throw new IllegalStateException();
      }
      ex.close();
    }

    private void startSending(boolean hasContent) throws IOException {
      if (transform != null) {
        transform();  
      } 
      if (markDocsPublic) {
        acl = null;
        secure = false;
      } else {
        acl = aclTransform.transform(acl);
      }
      if (requestIsFromFullyTrustedClient(ex) || alwaysGiveAcl) {
        // Always specify metadata and ACLs, even when empty, to replace
        // previous values.
        ex.getResponseHeaders().add("X-Gsa-External-Metadata",
             formMetadataHeader(metadata));
        if (sendDocControls) {
          ex.getResponseHeaders().add("X-Gsa-Doc-Controls", "acl="
              + percentEncode(formNamespacedAclHeader(acl, docIdEncoder)));
          if (null != displayUrl) {
            String link = "display_url=" + percentEncode("" + displayUrl);
            ex.getResponseHeaders().add("X-Gsa-Doc-Controls", link);
          }
          ex.getResponseHeaders().add("X-Gsa-Doc-Controls",
              "crawl_once=" + crawlOnce);
          ex.getResponseHeaders().add("X-Gsa-Doc-Controls", "lock=" + lock);
          ex.getResponseHeaders().add("X-Gsa-Doc-Controls",
              "scoring=" + scoring);
        } else {
          acl = checkAndWorkaroundGsa70Acl(acl);
          ex.getResponseHeaders().add("X-Gsa-External-Metadata",
              formUnqualifiedAclHeader(acl, docIdEncoder));
          if (displayUrl != null || crawlOnce || lock) {
            // Emulate these crawl-time values by sending them in feeds
            // since they aren't supported at crawl-time on GSA 7.0.
            pusher.asyncPushItem(new DocIdPusher.Record.Builder(docId)
                .setResultLink(displayUrl).setCrawlOnce(crawlOnce).setLock(lock)
                .build());
            // TODO(ejona): figure out how to notice that a true went false
          }
        }
        if (!anchorUris.isEmpty()) {
          ex.getResponseHeaders().add("X-Gsa-External-Anchor",
              formAnchorHeader(anchorUris, anchorTexts));
        }
        // (1) Always specify the security, either secure or public, because
        // the default varies. For instance, requesting the client certificate
        // of the GSA can mark documents secure, but it can also leave them as
        // public, depending on a GSA configuration setting.
        // (2) If document has ACL, then send secure. That helps the GSA
        // and prevents confusion of having ACLs and public label juxtaposed.
        ex.getResponseHeaders().add("X-Gsa-Serve-Security",
            (secure || (null != acl)) ? "secure" : "public");
        if (noIndex) {
          ex.getResponseHeaders().add("X-Robots-Tag", "noindex");
        }
        if (noFollow) {
          ex.getResponseHeaders().add("X-Robots-Tag", "nofollow");
        }
        if (noArchive) {
          ex.getResponseHeaders().add("X-Robots-Tag", "noarchive");
        }
        
        if (state == State.NO_CONTENT) {
          ex.getResponseHeaders().add("X-Gsa-Skip-Updating-Content", "true");
        }
      }
      if (useCompression) {
        // TODO(ejona): decide when to use compression based on mime-type
        HttpExchanges.enableCompressionIfSupported(ex);
      }
      if (lastModified != null) {
        HttpExchanges.setLastModified(ex, lastModified);
      }
      // There are separate timeouts for sending headers and sending content.
      // Here we stop the headers timer and start the content timer.     
      watchdog.processingCompleted(workingThread);
      watchdog.processingStarting(workingThread, contentTimeoutMillis);
      int responseCode = state == State.NO_CONTENT 
          ? HttpURLConnection.HTTP_NO_CONTENT : HttpURLConnection.HTTP_OK;
      HttpExchanges.startResponse(ex, responseCode, contentType, hasContent);
      for (Map.Entry<String, Acl> fragment : fragments.entrySet()) {
        pusher.asyncPushItem(new DocIdSender.AclItem(docId,
            fragment.getKey(), fragment.getValue()));
      }
    }

    private Acl checkAndWorkaroundGsa70Acl(Acl acl) {
      if (acl == null) {
        return acl;
      }
      // Check to see if the ACL can be used as-is with X-Gsa-External-Metadata
      if (acl.isEverythingCaseSensitive()
          && allDefaultNamespace(acl.getPermitUsers())
          && allDefaultNamespace(acl.getPermitGroups())
          && allDefaultNamespace(acl.getDenyUsers())
          && allDefaultNamespace(acl.getDenyGroups())) {
        return acl;
      }

      // Workaround for GSA 7.0 support. Since GSA 7.0 supports namespaces and
      // case insensitivity in feeds, we create a named resource with all the
      // "real" ACL data and put a noop ACL on the document itself.
      // Unfortunately, to do this trick with AND_BOTH_PERMIT requires using the
      // 'everyone' group, which would require namespace support on the
      // document's ACLs.

      Acl.Builder namedResourceAcl = new Acl.Builder(acl);
      if (Acl.InheritanceType.LEAF_NODE.equals(acl.getInheritanceType())) {
        namedResourceAcl.setInheritanceType(
            Acl.InheritanceType.PARENT_OVERRIDES);
      } else if (Acl.InheritanceType.AND_BOTH_PERMIT.equals(
          acl.getInheritanceType())) {
        throw new RuntimeException("Unable to use AND_BOTH_PERMIT with "
            + "advanced acls and GSA 7.0");
      } else {
        // CHILD_OVERRIDES and PARENT_OVERRIDES are fine as-is.
      }
      final String fragment = "generated";
      pusher.asyncPushItem(
          new DocIdSender.AclItem(docId, fragment, namedResourceAcl.build()));
      return new Acl.Builder()
          .setInheritanceType(acl.getInheritanceType())
          .setInheritFrom(docId, fragment).build();
    }

    private boolean allDefaultNamespace(Iterable<? extends Principal> i) {
      for (Principal p : i) {
        if (!Principal.DEFAULT_NAMESPACE.equals(p.getNamespace())) {
          return false;
        }
      }
      return true;
    }

    private void transform() {
      Map<String, String> params = new HashMap<String, String>();
      params.put("DocId", docId.getUniqueId());
      params.put("Content-Type", contentType);
      transform.transform(metadata, params);
      contentType = params.get("Content-Type");
    }

    private class CloseNotifyOutputStream extends FastFilterOutputStream {
      public CloseNotifyOutputStream(OutputStream os) {
        super(os);
      }

      @Override
      public void close() throws IOException {
        responseBodyClosed = true;
        super.close();
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

  interface AsyncPusher {
    public void asyncPushItem(DocIdSender.Item item);
  }
}
