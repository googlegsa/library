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
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.StartupException;
import com.google.enterprise.adaptor.UserPrincipal;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private static final String SITE_COLLECTION_ADMIN_FRAGMENT = "admin";
  private static final DocId rootDocId = new DocId("");
  private final Random randomNumberOfLines = new Random();

  private AdaptorContext context;
  private Map<String, SharePointUrl> urlToTypeMapping;
  private Map<String, Set<String>> parentChildMapping;
  private Set<String> rootCollection;  
  private EnumMap<ObjectType, Integer> objectCount;  
  Map<String, Set<String>> groupDefinations;
  private enum ObjectType {SITE_COLLECTION, SUB_SITE, LIST, FOLDER, DOCUMENT};
  private int breakInheritanceThreshold;
  private double inheritanceDepthFactor;
  private double averageDocAccessPercentage;
  private int averageNumberOfLinesInDocument;

  public SharePointStateFileAdaptor() {
    urlToTypeMapping = new HashMap<String, SharePointUrl>();
    parentChildMapping = new HashMap<String, Set<String>>();
    rootCollection = new HashSet<String>();
    objectCount = new EnumMap<ObjectType, Integer>(ObjectType.class);    
    groupDefinations = new HashMap<String, Set<String>>();   
  }

  @Override
  public void initConfig(Config config) {
    //Specify input directory path where state files are saved
    config.addKey("state.input", null);
    config.addKey("list.loadCount", "5");
    config.addKey("acl.breakInheritanceThresold", "20");
    config.addKey("acl.inheritanceDepthFactor", "0.9");
    config.addKey("acl.superGroupCount", "30");
    config.addKey("acl.averageDocAccessPercentage", "20");
    config.addKey("acl.searchUserCount", "1000");
    config.addKey("doc.averageSizeInKb", "120");
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    this.context = context;
    // Clear any data from previous failed init() call.
    urlToTypeMapping.clear();
    parentChildMapping.clear();
    rootCollection.clear();
    groupDefinations.clear();
    objectCount.put(ObjectType.SITE_COLLECTION, 0);
    objectCount.put(ObjectType.SUB_SITE, 0);
    objectCount.put(ObjectType.LIST, 0);
    objectCount.put(ObjectType.FOLDER, 0);
    objectCount.put(ObjectType.DOCUMENT, 0);

    Config config = context.getConfig();
    String inputDirectoryPath = config.getValue("state.input");
    int listLoadCount = Integer.parseInt(config.getValue("list.loadCount"));
    int searchUserCount 
        = Integer.parseInt(config.getValue("acl.searchUserCount"));
    breakInheritanceThreshold = Integer.parseInt(
        config.getValue("acl.breakInheritanceThresold"));
    inheritanceDepthFactor = Double.valueOf(
        config.getValue("acl.inheritanceDepthFactor"));
    int superGroupCount
        = Integer.parseInt(config.getValue("acl.superGroupCount"));
    averageDocAccessPercentage
        = Double.valueOf(config.getValue("acl.averageDocAccessPercentage"));
    // Each generated string e.g.<p>123456789</p> will roughly generate 16 bytes
    averageNumberOfLinesInDocument 
        = Integer.parseInt(config.getValue("doc.averageSizeInKb")) * 1000 / 16;
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    File inputDirectory = new File(inputDirectoryPath);
    if (!inputDirectory.exists()) {
      throw new StartupException(
          String.format("Invalid directory path %s", inputDirectoryPath));
    }
    // Read state file and create Site -> List structure.
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
        // Mark everything as site collection initially. This will allow
        // orphaned web states in state file to be reachable from root.
        addToUrlTypeMapping(webStateUrl, ObjectType.SITE_COLLECTION);
        addToParentMapping(rootUrl, webStateUrl);
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

    // Correct ObjectType for urls which are wrongly classified as
    // SiteCollection urls.
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

    // Break inheritance and assign super groups to ACLs.
    List<SharePointUrl> objectsWithUniquePermissions
        = new ArrayList<SharePointUrl>();
    for(String root : rootCollection) {
      calculateChildCountRecursively(root, 0);
      breakInheritanceForNodeRecursively(root, new Random(),
          breakInheritanceThreshold, "", objectsWithUniquePermissions);
      populateAclInheritanceCountRecursively(root);
      visitHierarchy(root);
    }
    int nonReachableNodes = 0;
    for (SharePointUrl url : urlToTypeMapping.values()) {
      if (!url.visited) {
        nonReachableNodes++;
      }
    }
    if (nonReachableNodes > 0) {
      log.log(Level.WARNING, "Non reachable node count = {0}",
          nonReachableNodes);
    }

    Collections.sort(objectsWithUniquePermissions, new AclChildCountCompare());
    Map<String, Integer> aclCountPerGroup = new HashMap<String, Integer>();
    for (int i = 1; i <= superGroupCount; i++) {
      aclCountPerGroup.put(String.format("google\\supergroup%d", i), 0);
    }

    Iterator<String> groupsIterator = aclCountPerGroup.keySet().iterator();
    for (SharePointUrl u : objectsWithUniquePermissions) {
      if (!groupsIterator.hasNext()) {
        groupsIterator = aclCountPerGroup.keySet().iterator();
      }
      u.superGroup = groupsIterator.next();
      aclCountPerGroup.put(u.superGroup, aclCountPerGroup.get(u.superGroup)+ 1 
          + u.aclInheritanceChildCount);
    }
    int total = 0;
    for (String s : aclCountPerGroup.keySet()) {
      log.log(Level.FINE, "Group {0} has access to {1} items",
          new Object[] { s, aclCountPerGroup.get(s)});
      total += aclCountPerGroup.get(s);
    }

    Map<String, Double> accessPercentage = new HashMap<String, Double>();
    for (String groupName : aclCountPerGroup.keySet()) {
      accessPercentage.put(groupName,
          (100 * (double) aclCountPerGroup.get(groupName) / (double) total));
      log.log(Level.FINE, "Group {0} has access to {1} % items",
          new Object[] { groupName, accessPercentage.get(groupName)});
    }    
    List<List<String>> groupCombinations 
        = generateGroupCombinations(accessPercentage);
    for (String groupName : accessPercentage.keySet()) {
      groupDefinations.put(groupName, new HashSet<String>());
    }
    Iterator<List<String>> groupsCombinationIterator 
        = groupCombinations.iterator();
    for(int i = 1; i <= searchUserCount; i++) {
      if (!groupsCombinationIterator.hasNext()) {
        groupsCombinationIterator = groupCombinations.iterator();
      }
      for (String groupName : groupsCombinationIterator.next()) {
        groupDefinations.get(groupName)
            .add(String.format("google\\SearchUser%d", i));
      }
    }
  }
  
  private void visitHierarchy(String url) {
    if (!urlToTypeMapping.containsKey(url)) {
      return;
    }
    urlToTypeMapping.get(url).visited = true;
    if (!parentChildMapping.containsKey(url)) {
      return;
    }
    for (String s : parentChildMapping.get(url)) {
      visitHierarchy(s);
    }
  }

  private List<List<String>> generateGroupCombinations(
      Map<String, Double> accessPercentage) {
    // There can be very large number of group combinations possible
    // which provides desired document access percentage. To control number
    // of combinations generated by particular group use 
    // combinationsPerGroupLimitingFactor as threshold.
    int combinationsPerGroupLimitingFactor = 100;
    List<List<String>> groupCombinations = new ArrayList<List<String>>();
    for (String groupName : accessPercentage.keySet()) {
      Map<String, Double> state = new HashMap<String, Double>();     
      state.put(groupName, accessPercentage.get(groupName));
      int groupCombinationCount = 0;
      for (String group : accessPercentage.keySet()) {
        if (group.equals(groupName)) {
          continue;
        }
        HashMap<String, Double> newState = new HashMap<String, Double>();
        for (String s : state.keySet()) {
          String combination = s + ";" + group;
          double percentage = state.get(s) + accessPercentage.get(group);
          if (percentage >= averageDocAccessPercentage - 2
              && percentage <= averageDocAccessPercentage + 2) {
            List<String> candidateCombination 
                = Arrays.asList(combination.split(";"));
            Collections.sort(candidateCombination);
            if (!groupCombinations.contains(candidateCombination)) {
              groupCombinations.add(candidateCombination);
              groupCombinationCount++;
            } else {
              log.log(Level.FINE, "Group Combination {0} is already available",
                  candidateCombination);
              continue;
            }
            if (groupCombinationCount == combinationsPerGroupLimitingFactor) {
              break;
            }
          } else if (percentage < averageDocAccessPercentage - 2) {
            newState.put(combination, percentage);
          }
        }
        state.putAll(newState);
        if (groupCombinationCount == combinationsPerGroupLimitingFactor) {
          break;
        }
      }
    }
    return groupCombinations;
  }

  @Override
  public void getDocIds(DocIdPusher pusher)
      throws IOException, InterruptedException {
    try {
      pusher.pushDocIds(Arrays.asList(rootDocId));
      int membershipCount = 0;
      Map<GroupPrincipal, Collection<Principal>> memberships 
          = new HashMap<GroupPrincipal, Collection<Principal>>();
      for (String groupName : groupDefinations.keySet()) {
        GroupPrincipal group = new GroupPrincipal(groupName);
        Collection<Principal> members = new ArrayList<Principal>();
        for(String user : groupDefinations.get(groupName)) {
          members.add(new UserPrincipal(user));
        }
        memberships.put(group, members);
        membershipCount = membershipCount + members.size();
        if (membershipCount > 5000) {
          pusher.pushGroupDefinitions(memberships, false);
          memberships.clear();
          membershipCount = 0;
        }
      }
      if (!memberships.isEmpty()) {
        pusher.pushGroupDefinitions(memberships, false);
      }
    } catch (Exception ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public void getDocContent(Request request, Response response)
      throws IOException, InterruptedException {
    String url = request.getDocId().getUniqueId();
    if (statsDoc.getUniqueId().equals(url)) {
      getStatsDocContent(request, response);
      return;
    } else if (rootDocId.getUniqueId().equals(url)) {
      getRootDocumentContent(request, response);
      return;
    }
    if (!urlToTypeMapping.containsKey(url)) {
      response.respondNotFound();
      return;
    }
    response.setCrawlOnce(true);
    SharePointUrl currentItem = urlToTypeMapping.get(url);   
    if (currentItem.type == ObjectType.SITE_COLLECTION) {
      Acl.Builder siteAdmin = new Acl.Builder().setEverythingCaseInsensitive()
          .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
          .setPermitUsers(getDummyUserPrincipals(
              "google\\siteAdminUser", 5, ""))
          .setPermitGroups(getDummyGroupPrincipals(
              "google\\siteAdminGroup", 5, "")).setInheritFrom(rootDocId);
      response.putNamedResource(SITE_COLLECTION_ADMIN_FRAGMENT,
          siteAdmin.build());
    }
    if (currentItem.breakInheritance) {
      response.setAcl(new Acl.Builder().setEverythingCaseInsensitive()
          .setPermitGroups(getDummyGroupPrincipals(
              "google\\dummyGroup", 5, currentItem.superGroup))
          .setPermitUsers(getDummyUserPrincipals(
              "google\\dummyUser", 10, ""))
          .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
          .setInheritFrom(new DocId(getSiteCollectionUrl(url)),
              SITE_COLLECTION_ADMIN_FRAGMENT).build());
    } else {
      response.setAcl(new Acl.Builder().setEverythingCaseInsensitive()
          .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
          .setInheritFrom(new DocId(currentItem.parent)).build());
    }
    if (currentItem.type == ObjectType.DOCUMENT) {
      response.setContentType("text/plain");
    }
    HtmlResponseWriter writer = new HtmlResponseWriter(
        new OutputStreamWriter(response.getOutputStream()),
        context.getDocIdEncoder(), Locale.ENGLISH);
    writer.start(request.getDocId(), url, currentItem.type.name());
    writer.addText(String.format("Break ACL Inheritance at current node %s",
        currentItem.breakInheritance));
    writer.addText(String.format("# Docs inheriting ACL from current node %d",
        currentItem.aclInheritanceChildCount));
    if (parentChildMapping.containsKey(url)) {
      for (String child : parentChildMapping.get(url)) {
        writer.addLink(new DocId(child), child);
      }
    }
    if (currentItem.type == ObjectType.DOCUMENT) {
      // Assuming generated random number gives even distribution
      // averageNumberOfLinesInDocument * 2 should give on average
      // averageNumberOfLinesInDocument lines per document.
      addRandomDocumentContent(10 + randomNumberOfLines.nextInt(
              averageNumberOfLinesInDocument * 2), writer);
    }
    writer.finish();
    writer.close();
  }
  
  private void getRootDocumentContent(Request request, Response response)
      throws IOException, InterruptedException {
    response.setCrawlOnce(true);
    response.setAcl(new Acl.Builder().setEverythingCaseInsensitive()
        .setPermitGroups(getDummyGroupPrincipals("google\\rootGroup", 5, ""))
        .setPermitUsers(getDummyUserPrincipals("google\\rootUser", 10, ""))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build());
    HtmlResponseWriter writer = new HtmlResponseWriter(
        new OutputStreamWriter(response.getOutputStream()),
        context.getDocIdEncoder(), Locale.ENGLISH);
    writer.start(request.getDocId(), "Root", "");
    for(String sc : rootCollection) {
      writer.addLink(new DocId(sc), sc);
    }
    writer.finish();
    writer.close();
  }

  private List<UserPrincipal> getDummyUserPrincipals(String userPrefix,
      int count, String principalToInclude) {

    List<UserPrincipal> users = new ArrayList<UserPrincipal>();
    Set<String> dummy = getDummyPrincipalNames(userPrefix, count);
    if (!"".equals(principalToInclude)) {
      dummy.add(principalToInclude);
    }
    for (String user : dummy) {
      users.add(new UserPrincipal(user));
    }
    return users;
  }

  private List<GroupPrincipal> getDummyGroupPrincipals(String groupPrefix,
      int count, String principalToInclude) {

    List<GroupPrincipal> groups = new ArrayList<GroupPrincipal>();
    Set<String> dummy = getDummyPrincipalNames(groupPrefix, count);
    if (!"".equals(principalToInclude)) {
      dummy.add(principalToInclude);
    }
    for (String group : dummy) {
      groups.add(new GroupPrincipal(group));
    }
    return groups;
  }

  private Set<String> getDummyPrincipalNames(String prefix, int count) {
    Set<String> dummy = new HashSet<String>();
    int i = 0;
    Random rand = new Random();
    while (i < count) {
      if(dummy.add(String.format("%s%d", prefix, rand.nextInt(1000)))) {
        i++;
      }      
    }
    return dummy;
  }
  
  private void addRandomDocumentContent(int numberOfLines,
      HtmlResponseWriter writer) throws IOException {
    writer.addText("Number of lines = " + numberOfLines);
    Random rand = new Random();
    for (int i = 0; i < numberOfLines; ++i) {
      // Each generated string <p>123456789</p> will roughly generate 16 bytes
      writer.addText(String.format("%09d", rand.nextInt(999999999)));
    }
  }

  public void getStatsDocContent(Request request, Response response)
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

      SharePointUrl rootUrl = urlToTypeMapping.get(root);
      output.append(String.format("Root %s has %d child items\n",
          root, rootUrl.childCount));
    }

    int totalCount = 0;
    for (ObjectType type : objectCount.keySet()) {
      totalCount = totalCount + objectCount.get(type);
      output.append(String.format("Object Type %s has %d count\n",
          type, objectCount.get(type)));
    }
    output.append(String.format("Total Document Count = %d\n", totalCount));

    OutputStream os = response.getOutputStream();
    os.write(output.toString().getBytes(encoding));
  }

  private void breakInheritanceForNodeRecursively(String parentUrl, Random rand,
      double threshold, String parent,
      List<SharePointUrl> objectsWithUniquePermissions) {
    if (!urlToTypeMapping.containsKey(parentUrl)) {
      return;
    }
    int factor = rand.nextInt(100);
    SharePointUrl currentNode = urlToTypeMapping.get(parentUrl);
    currentNode.breakInheritance = (factor <= threshold
        || currentNode.type == ObjectType.SITE_COLLECTION);
    if (!currentNode.breakInheritance) {
      currentNode.parent = parent;
    } else {
      objectsWithUniquePermissions.add(currentNode);
    }
    if (!parentChildMapping.containsKey(parentUrl)) {
      return;
    }
    for (String c : parentChildMapping.get(parentUrl)) {
      breakInheritanceForNodeRecursively(c, rand,
          inheritanceDepthFactor * threshold, parentUrl,
          objectsWithUniquePermissions);
    }
  }

  private int populateAclInheritanceCountRecursively(String url) {
    if (!urlToTypeMapping.containsKey(url)) {
      // If current node is not available, return -1 so that
      // current node is not counted as child inheriting ACL from parent.
      return -1;
    }
    SharePointUrl current = urlToTypeMapping.get(url);
    int aclInheritanceChildCount = 0;
    if (parentChildMapping.containsKey(url)) {
      for (String child : parentChildMapping.get(url)) {
        aclInheritanceChildCount = aclInheritanceChildCount + 1
            + populateAclInheritanceCountRecursively(child);
      }
    }
    current.aclInheritanceChildCount = aclInheritanceChildCount;
    if (current.breakInheritance) {
      // If current node is breaking ACL inheritance, return -1 so that
      // current node is not counted as child inheriting ACL from parent.
      return -1;
    } else {
      return current.aclInheritanceChildCount;
    }
  }

  private int calculateChildCountRecursively(String url, int depth) {
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
      childCount += calculateChildCountRecursively(child, depth + 1);
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
    boolean breakInheritance = false;
    int aclInheritanceChildCount = 0;
    String superGroup;
    boolean visited;

    public SharePointUrl(String url, ObjectType type) {
      this.url = url;
      this.type = type;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new Object[] {url, type, depth, parent, visited,
          childCount, breakInheritance, aclInheritanceChildCount, superGroup});
    }

    @Override
    public boolean equals(Object o) {
      if (null == o || !getClass().equals(o.getClass())) {
        return false;
      }
      SharePointUrl urlToCheck = (SharePointUrl) o;
      return this.url.equalsIgnoreCase(urlToCheck.url);
    }

    @Override
    public String toString() {
      return String.format("SharePointUrl (url = %s , breakInheritance = %s, "
          + "aclInheritanceChildCount = %d)", url, breakInheritance,
          aclInheritanceChildCount);
    }
  }

  private static class AclChildCountCompare
      implements Comparator<SharePointUrl> {

    @Override
    public int compare(SharePointUrl url1, SharePointUrl url2) {
      return Integer.compare(url1.aclInheritanceChildCount,
          url2.aclInheritanceChildCount);
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

  private String getSiteCollectionUrl(String url) throws IOException{
    try {
      if (!urlToTypeMapping.containsKey(url)) {
        return getRootUrl(spUrlToUri(url));
      }
      SharePointUrl current = urlToTypeMapping.get(url);
      if (current.type == ObjectType.SITE_COLLECTION) {
        return url;
      }

      String[] parts = url.split("/", 6);
      if (parts.length < 6) {
        return getRootUrl(spUrlToUri(url));
      }
      String possibleSiteCollectionUrl = parts[0] + "//" + parts[2] + "/"
          + parts[3] + "/" + parts[4];
      return getSiteCollectionUrl(possibleSiteCollectionUrl);
    } catch (URISyntaxException ex) {
      throw new IOException(ex);
    }
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

    public void addText(String comment) throws IOException {
      writer.write("<p>");
      writer.write(escapeContent(comment));
      writer.write("</p>");
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
