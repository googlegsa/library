// Copyright 2014 Google Inc. All Rights Reserved.
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

package com.google.enterprise.adaptor.experimental;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdEncoder;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.StartupException;
import com.google.enterprise.adaptor.UserPrincipal;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * This is an experimental adaptor which reads State files created by 3.x
 * SharePoint Connector and generate DocIds to create similar site structure.
*/
public class SharePointStateFileAdaptor extends AbstractAdaptor {
  private static final Charset encoding = Charset.forName("UTF-8");
  private static final Logger log
      = Logger.getLogger(SharePointStateFileAdaptor.class.getName());
  private static final DocId statsDoc = new DocId("stats");

  private AdaptorContext context;
  private Map<String, SharePointUrl> urlToTypeMapping;
  private Map<String, Set<String>> parentChildMapping;
  private Set<String> rootCollection;
  private EnumMap<ObjectType, Integer> objectCount;
  private enum ObjectType {SITE_COLLECTION, SUB_SITE, LIST, FOLDER, DOCUMENT};

  public SharePointStateFileAdaptor() {
    urlToTypeMapping = new HashMap<String, SharePointUrl>();
    parentChildMapping = new HashMap<String, Set<String>>();
    rootCollection = new HashSet<String>();
    objectCount = new EnumMap<ObjectType, Integer>(ObjectType.class);
  }

  @Override
  public void initConfig(Config config) {
    //Specify input directory path where state files are saved
    config.addKey("state.input", null);
    config.addKey("list.loadCount", "5");
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    this.context = context;
    // Clear any data from previous failed init() call.
    urlToTypeMapping.clear();
    parentChildMapping.clear();
    rootCollection.clear();
    objectCount.put(ObjectType.SITE_COLLECTION, 0);
    objectCount.put(ObjectType.SUB_SITE, 0);
    objectCount.put(ObjectType.LIST, 0);
    objectCount.put(ObjectType.FOLDER, 0);
    objectCount.put(ObjectType.DOCUMENT, 0);

    Config config = context.getConfig();
    String inputDirectoryPath = config.getValue("state.input");
    int listLoadCount = Integer.parseInt(config.getValue("list.loadCount"));
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    File inputDirectory = new File(inputDirectoryPath);
    if (!inputDirectory.exists()) {
      throw new StartupException(
          String.format("Invalid directory path %s", inputDirectoryPath));
    }
    for (File stateFile : inputDirectory.listFiles()) {
      if (!stateFile.getName().endsWith(".xml")) {
        continue;
      }
      Document doc = builder.parse(stateFile);
      NodeList webStates = doc.getElementsByTagName("WebState");
      for (int i = 0; i < webStates.getLength(); i++) {
        Node webState = webStates.item(i);
        String webStateUrl = webState.getAttributes().getNamedItem("ID")
            .getNodeValue().toLowerCase();
        if (webStateUrl.endsWith("/")) {
          webStateUrl = webStateUrl.substring(0, webStateUrl.length() -1);
        }
        if (webStateUrl.contains("/_layouts/")) {
          webStateUrl
              = webStateUrl.substring(0, webStateUrl.indexOf("/_layouts/"));
        }
        String rootUrl = getRootUrl(spUrlToUri(webStateUrl));
        rootCollection.add(rootUrl);
        if (isSiteCollectionUrl(webStateUrl)) {
          addToUrlTypeMapping(webStateUrl, ObjectType.SITE_COLLECTION);
          addToParentMapping(rootUrl, webStateUrl);
        } else {
          addToUrlTypeMapping(webStateUrl, ObjectType.SUB_SITE);
          String parentUrl
              = webStateUrl.substring(0, webStateUrl.lastIndexOf("/"));
          addToParentMapping(parentUrl, webStateUrl);
        }
        addToParentMapping(webStateUrl, "");
        NodeList listStates = webState.getChildNodes();
        Random rand = new Random();
        for (int j = 0; j < listStates.getLength(); j++) {
          Node listState = listStates.item(j);
          if (listState.getNodeType() != Node.ELEMENT_NODE) {
            continue;
          }
          if (!"ListState".equals(listState.getNodeName())) {
            log.log(Level.WARNING, "Unexpected node type {0}",
                listState.getNodeName());
            continue;
          }
          String listUrl = listState.getAttributes().getNamedItem("URL")
              .getNodeValue().replace(":80/", "/");
          listUrl = listUrl.toLowerCase();
          if (listUrl.contains("/forms")) {
            listUrl = listUrl.substring(0, listUrl.lastIndexOf("/forms"));
          }
          if (listUrl.endsWith(".aspx")) {
            listUrl = listUrl.substring(0, listUrl.lastIndexOf("/"));
          }
          if (webStateUrl.equals(listUrl) || rootUrl.equals(listUrl)) {
            continue;
          }
          addToParentMapping(webStateUrl, listUrl);
          addToUrlTypeMapping(listUrl, ObjectType.LIST);
          addToParentMapping(listUrl, "");
          int docCount = rand.nextInt(listLoadCount);
          for (int d = 1; d <= docCount; d++) {
            String docUrl = String.format("%s/%d.txt", listUrl, d);
            addToParentMapping(listUrl, docUrl);
            addToUrlTypeMapping(docUrl, ObjectType.DOCUMENT);
          }
        }
      }
    }

    for (String url : urlToTypeMapping.keySet()) {
      if (urlToTypeMapping.get(url).type == ObjectType.SITE_COLLECTION) {
        String parentUrl = url.substring(0, url.lastIndexOf("/"));
        if (parentChildMapping.containsKey(parentUrl)) {
          log.log(Level.FINE, "Changing Object Type for {0} as sub site since"
              + " parent {1} is also a Site.", new Object[] {url, parentUrl});
          addToUrlTypeMapping(url, ObjectType.SUB_SITE);
          String rootUrl = getRootUrl(spUrlToUri(url));
          removeFromParentMapping(rootUrl, url);
          addToParentMapping(parentUrl, url);
        }
      }
    }
  }

  @Override
  public void getDocIds(DocIdPusher pusher)
      throws IOException, InterruptedException {
    try {
      ArrayList<DocId> docIdsToPush =  new ArrayList<DocId>();
      for (String url : rootCollection) {
        docIdsToPush.add(new DocId(url));
      }
      pusher.pushDocIds(docIdsToPush);
    } catch (Exception ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public void getDocContent(Request request, Response response)
      throws IOException, InterruptedException {
    String url = request.getDocId().getUniqueId();
    if (statsDoc.getUniqueId().equals(url)) {
      getRootDocContent(request, response);
      return;
    }
    if (!urlToTypeMapping.containsKey(url)) {
      response.respondNotFound();
      return;
    }
    SharePointUrl currentItem = urlToTypeMapping.get(url);
    if (rootCollection.contains(url)) {
      response.setAcl(new Acl.Builder().setEverythingCaseInsensitive()
          .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
          .setPermitUsers(Collections.singletonList(
              new UserPrincipal("google\\superuser"))).build());
    }
    HtmlResponseWriter writer = new HtmlResponseWriter(
        new OutputStreamWriter(response.getOutputStream()),
        context.getDocIdEncoder(), Locale.ENGLISH);
    writer.start(request.getDocId(), url, currentItem.type.name());
    if (parentChildMapping.containsKey(url)) {
      for (String child : parentChildMapping.get(url)) {
        writer.addLink(new DocId(child), child);
      }
    }
    writer.finish();
  }

  public void getRootDocContent(Request request, Response response)
      throws IOException {
    StringBuilder output = new StringBuilder();
    for (String root : rootCollection) {
      Set<String> siteCollections
          = getChildOfType(ObjectType.SITE_COLLECTION, root);
      siteCollections.add(root);
      output.append(String.format("Root %s has %d site collections\n", root,
              siteCollections.size()));
      int sum = 0;
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (String sc : siteCollections) {
        if (!parentChildMapping.containsKey(sc)) {
          continue;
        }
        Set<String> subSites = getChildOfType(ObjectType.SUB_SITE, sc);
        sum = sum + subSites.size();
        if (min > subSites.size()) {
          min = subSites.size();
        }

        if (max < subSites.size()) {
          max =  subSites.size();
        }
      }
      output.append(String.format(
          "For Root %s Avg #sites %d Max #sites %d Min #sites %d\n",
          root, sum /(siteCollections.size()), max, min));

      output.append(String.format("Root %s has %d childs\n", root,
          calculateChildCount(root, 0)));
    }

    for (ObjectType type : objectCount.keySet()) {
      output.append(String.format("Object Type %s has %d count\n",
          type, objectCount.get(type)));
    }

    OutputStream os = response.getOutputStream();
    os.write(output.toString().getBytes(encoding));
  }

  private int calculateChildCount(String url, int depth) {
    if (!urlToTypeMapping.containsKey(url)) {
      return 0;
    }
    if (!parentChildMapping.containsKey(url)) {
      urlToTypeMapping.get(url).childCount = 0;
      urlToTypeMapping.get(url).depth = depth;
      return 0;
    }
    if (Integer.MIN_VALUE != urlToTypeMapping.get(url).childCount) {
      log.log(Level.FINER, "Url {0} was already processed", url);
      return urlToTypeMapping.get(url).childCount;
    }
    int childCount = parentChildMapping.get(url).size();
    for (String child : parentChildMapping.get(url)) {
      childCount += calculateChildCount(child, depth + 1);
    }
    urlToTypeMapping.get(url).childCount = childCount;
    urlToTypeMapping.get(url).depth = depth;
    return childCount;
  }

  private Set<String> getChildOfType(ObjectType type, String parent) {
    Set<String> childItems = new HashSet<String>();
    if (!parentChildMapping.containsKey(parent)) {
      return childItems;
    }
    for (String child : parentChildMapping.get(parent)) {
      if (!urlToTypeMapping.containsKey(child)) {
        continue;
      }
      if (type == urlToTypeMapping.get(child).type) {
        childItems.add(child);
      }
    }
    return childItems;
  }

  private synchronized void addToParentMapping(String parent, String child)
      throws IOException, URISyntaxException {
    if (!parentChildMapping.containsKey(parent)) {
      parentChildMapping.put(parent, new HashSet<String>());
    }
    String rootUrl = getRootUrl(spUrlToUri(parent));
    if (rootUrl.equalsIgnoreCase(child) && !rootUrl.equalsIgnoreCase(parent)) {
      log.log(Level.WARNING, "Why parent {0} has root {1} as child?",
          new Object[] {parent, rootUrl});
      return;
    }

    if (parent.length() >= child.length() && child.length() > 0) {
       log.log(Level.WARNING, "Why parent {0} is having {1} as child?",
          new Object[] {parent, child});
      return;
    }

    if (!"".equals(child)) {
      parentChildMapping.get(parent).add(child);
    }
  }

  private synchronized void removeFromParentMapping(String parent,
      String child) {
    if (!parentChildMapping.containsKey(parent)) {
      return;
    }
    parentChildMapping.get(parent).remove(child);
  }

  private synchronized void addToUrlTypeMapping(String url, ObjectType type) {
    if (urlToTypeMapping.containsKey(url)
        && type == urlToTypeMapping.get(url).type) {
      return;
    }
    if (urlToTypeMapping.containsKey(url)) {
      SharePointUrl existing = urlToTypeMapping.get(url);
      objectCount.put(existing.type, objectCount.get(existing.type) - 1);
    }
    urlToTypeMapping.put(url, new SharePointUrl(url, type));
    objectCount.put(type, objectCount.get(type) + 1);
  }

  private static class SharePointUrl {
    final String url;
    final ObjectType type;
    int depth = Integer.MIN_VALUE;
    String parent;
    int childCount = Integer.MIN_VALUE;

    public SharePointUrl(String url, ObjectType type) {
      this.url = url;
      this.type = type;
    }
  }

  private static URI spUrlToUri(String url) throws IOException {
    String[] parts = url.split("/", 4);
    if (parts.length < 3) {
      throw new IllegalArgumentException("Too few '/'s: " + url);
    }
    String host = parts[0] + "/" + parts[1] + "/" + parts[2];
    // Host must be properly-encoded already.
    URI hostUri = URI.create(host);
    if (parts.length == 3) {
      // There was no path.
      return hostUri;
    }
    URI pathUri;
    try {
      pathUri = new URI(null, null, "/" + parts[3], null);
    } catch (URISyntaxException ex) {
      throw new IOException(ex);
    }
    return hostUri.resolve(pathUri);
  }

  private static boolean isSiteCollectionUrl(String url)
      throws MalformedURLException, URISyntaxException, IOException {
    String rootUrl = getRootUrl(spUrlToUri(url));
    if (url.equals(rootUrl)) {
      return true;
    }
    return url.split("/").length == 5;
  }

  private static String getRootUrl(URI uri) throws URISyntaxException {
    return new URI(uri.getScheme(), uri.getAuthority(), null, null, null)
        .toString();
  }

  private static class HtmlResponseWriter implements Closeable {
    private static final Logger log
        = Logger.getLogger(HtmlResponseWriter.class.getName());

    private enum State {
      /** Initial state after construction. */
      INITIAL,
      /**
       * {@link #start} was just called, so the HTML header is in place, but no
       * other content.
       */
      STARTED,
      /** {@link #finish} has been called, so the HTML footer has been
       *  written. */
      FINISHED,
      /** The writer has been closed. */
      CLOSED,
    }

    private final Writer writer;
    private final DocIdEncoder docIdEncoder;
    private URI docUri;
    private State state = State.INITIAL;

    public HtmlResponseWriter(Writer writer, DocIdEncoder docIdEncoder,
        Locale locale) {
      if (writer == null) {
        throw new NullPointerException();
      }
      if (docIdEncoder == null) {
        throw new NullPointerException();
      }
      if (locale == null) {
        throw new NullPointerException();
      }
      this.writer = writer;
      this.docIdEncoder = docIdEncoder;
    }

    /**
     * Start writing HTML document.
     *
     * @param docId the DocId for the document being written out
     * @param label possibly-{@code null} title or name of {@code docId}
     */
    public void start(DocId docId, String label, String type)
        throws IOException {
      if (state != State.INITIAL) {
        throw new IllegalStateException("In unexpected state: " + state);
      }
      this.docUri = docIdEncoder.encodeDocId(docId);
      String header = MessageFormat.format("{0} {1}", type,
          computeLabel(label, docId));
      writer.write("<!DOCTYPE html>\n<html><head><title>");
      writer.write(escapeContent(header));
      writer.write("</title></head><body><h1>");
      writer.write(escapeContent(header));
      writer.write("</h1>");
      state = State.STARTED;
    }

    /**
     * @param docId docId to add as a link in the document
     * @param label possibly-{@code null} title or description of {@code docId}
     */
    public void addLink(DocId doc, String label) throws IOException {
      if (state != State.STARTED) {
        throw new IllegalStateException("In unexpected state: " + state);
      }
      if (doc == null) {
        throw new NullPointerException();
      }
      writer.write("<li><a href=\"");
      writer.write(escapeAttributeValue(encodeDocId(doc)));
      writer.write("\">");
      writer.write(escapeContent(computeLabel(label, doc)));
      writer.write("</a></li>");
    }

    /**
     * Complete HTML body and flush.
     */
    public void finish() throws IOException {
      log.entering("HtmlResponseWriter", "finish");
      if (state != State.STARTED) {
        throw new IllegalStateException("In unexpected state: " + state);
      }
      writer.write("</body></html>");
      writer.flush();
      state = State.FINISHED;
      log.exiting("HtmlResponseWriter", "finish");
    }

    /**
     * Close underlying writer. You will generally want to call {@link #finish}
     * first.
     */
    @Override
    public void close() throws IOException {
      log.entering("HtmlResponseWriter", "close");
      writer.close();
      state = State.CLOSED;
      log.exiting("HtmlResponseWriter", "close");
    }

    /**
     * Encodes a DocId into a URI formatted as a string.
     */
    private String encodeDocId(DocId doc) {
      log.entering("HtmlResponseWriter", "encodeDocId", doc);
      URI uri = docIdEncoder.encodeDocId(doc);
      uri = relativize(docUri, uri);
      String encoded = uri.toASCIIString();
      log.exiting("HtmlResponseWriter", "encodeDocId", encoded);
      return encoded;
    }

    /**
     * Produce a relative URI from {@code uri} relative to {@code base},
     * assuming both URIs are hierarchial. If possible, a relative URI will be
     * returned that can be resolved from {@code base}, otherwise {@code uri}
     * will be returned.
     *
     * <p>Necessary since {@link URI#relativize} is broken when considering
     * http://host/path vs http://host/path/ as the base URI. See
     * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6226081">
     * Bug 6226081</a> for more information. In addition, this version uses
     * {@code ..} when possible unlike {@link URI#relativize}.
     */

    private static URI relativize(URI base, URI uri) {
      if (base.getScheme() == null || !base.getScheme().equals(uri.getScheme())
          || base.getAuthority() == null
          || !base.getAuthority().equals(uri.getAuthority())) {
        return uri;
      }
      if (base.equals(uri)) {
        return URI.create("#");
      }
      // These paths are known to start with a / or be the empty string; since
      // the URIs have a scheme, we know they are absolute.
      String basePath = base.getPath();
      String uriPath = uri.getPath();

      String[] basePathParts = basePath.split("/", -1);
      String[] uriPathParts = uriPath.split("/", -1);
      int i = 0;
      // Remove common folders. Since we are looking at folders,
      // we don't compare the last elements in the array, because they were
      // after the last '/' in the URIs.
      for (; i < basePathParts.length - 1 && i < uriPathParts.length - 1; i++) {
        if (!basePathParts[i].equals(uriPathParts[i])) {
          break;
        }
      }
      StringBuilder pathBuilder = new StringBuilder();
      for (int j = i; j < basePathParts.length - 1; j++) {
        pathBuilder.append("../");
      }
      for (; i < uriPathParts.length; i++) {
        pathBuilder.append(uriPathParts[i]);
        pathBuilder.append("/");
      }
      String path = pathBuilder.substring(0, pathBuilder.length() - 1);
      int colonLocation = path.indexOf(":");
      int slashLocation = path.indexOf("/");
      if (colonLocation != -1
          && (slashLocation == -1 || colonLocation < slashLocation)) {
        // If there is a colon before the first slash, then it is easy to
        // confuse this relative URI for an absolute URI. Thus, we prepend
        // a ./ so that the beginning is obviously not a scheme.
        path = "./" + path;
      }
      try {
        return new URI(null, null, path, uri.getQuery(), uri.getFragment());
      } catch (URISyntaxException ex) {
        throw new AssertionError(ex);
      }
    }

    private String computeLabel(String label, DocId doc) {
      if ("".equals(label)) {
        // Use the last part of the URL if an item doesn't have a title.
        // The last part of the URL will generally be a filename in this case.
        String[] parts = doc.getUniqueId().split("/", 0);
        label = parts[parts.length - 1];
      }
      return label;
    }

    private String escapeContent(String raw) {
      return raw.replace("&", "&amp;").replace("<", "&lt;");
    }

    private String escapeAttributeValue(String raw) {
      return escapeContent(raw).replace("\"", "&quot;").replace("'", "&apos;");
    }
  }

  /** Call default main for adaptors. */
  public static void main(String[] args) {
    AbstractAdaptor.main(new SharePointStateFileAdaptor(), args);
  }
}
