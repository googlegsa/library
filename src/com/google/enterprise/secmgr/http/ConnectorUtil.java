/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.enterprise.secmgr.http;

import static com.google.enterprise.secmgr.common.XmlUtil.findChildElement;
import static com.google.enterprise.secmgr.common.XmlUtil.getChildElementText;
import static com.google.enterprise.secmgr.common.XmlUtil.getChildElements;
import static com.google.enterprise.secmgr.common.XmlUtil.isElementWithQname;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.secmgr.common.XmlUtil;
import com.google.enterprise.secmgr.config.ConfigSingleton;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSSerializer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.xml.namespace.QName;

public final class ConnectorUtil {

  // Don't instantiate.
  private ConnectorUtil() {
    throw new UnsupportedOperationException();
  }

  private static final Logger LOGGER = Logger.getLogger(ConnectorUtil.class.getName());

  private static QName cmname(String localPart) {
    return new QName(localPart);
  }

  // All of these QName constants are exposed for testing.
  public static final QName XML_TAG_ANSWER = cmname("Answer");
  public static final QName XML_TAG_AUTHN_CREDENTIAL = cmname("Credentials");
  public static final QName XML_TAG_AUTHN_DOMAIN = cmname("Domain");
  public static final QName XML_TAG_AUTHN_PASSWORD = cmname("Password");
  public static final QName XML_TAG_AUTHN_REQUEST = cmname("AuthnRequest");
  public static final QName XML_TAG_AUTHN_RESPONSE = cmname("AuthnResponse");
  public static final QName XML_TAG_AUTHN_USERNAME = cmname("Username");
  public static final QName XML_TAG_AUTHZ_QUERY = cmname("AuthorizationQuery");
  public static final QName XML_TAG_AUTHZ_RESPONSE = cmname("AuthorizationResponse");
  public static final QName XML_TAG_CONNECTORS = cmname("Connectors");
  public static final QName XML_TAG_CONNECTOR_INSTANCE = cmname("ConnectorInstance");
  public static final QName XML_TAG_CONNECTOR_INSTANCES = cmname("ConnectorInstances");
  public static final QName XML_TAG_CONNECTOR_NAME = cmname("ConnectorName");
  public static final QName XML_TAG_CONNECTOR_QUERY = cmname("ConnectorQuery");
  public static final QName XML_TAG_CONNECTOR_TYPE = cmname("ConnectorType");
  public static final QName XML_TAG_DECISION = cmname("Decision");
  public static final QName XML_TAG_FAILURE = cmname("Failure");
  public static final QName XML_TAG_GROUP = cmname("Group");
  public static final QName XML_TAG_IDENTITY = cmname("Identity");
  public static final QName XML_TAG_INFO = cmname("Info");
  public static final QName XML_TAG_RESOURCE = cmname("Resource");
  public static final QName XML_TAG_RESPONSE_ROOT = cmname("CmResponse");
  public static final QName XML_TAG_STATUS_ID = cmname("StatusId");
  public static final QName XML_TAG_SUCCESS = cmname("Success");

  // The connector name attribute should be lower case, however the
  // connector manager used this form so we have to change to the current.
  public static final QName XML_ATTR_CONNECTOR_NAME = cmname("ConnectorName");

  public static final QName XML_ATTR_DOMAIN = cmname("domain");
  public static final QName XML_ATTR_PASSWORD = cmname("password");

  public static final String CONFIG_XML_DECLARATION = "xml-declaration";

  public static final String DECISION_TEXT_PERMIT = "permit";
  public static final String DECISION_TEXT_DENY = "deny";
  public static final String DECISION_TEXT_INDETERMINATE = "indeterminate";

  public static final String CM_AUTHENTICATE_SERVLET_PATH = "/authenticate";
  public static final String CM_AUTHORIZATION_SERVLET_PATH = "/authorization";
  public static final String CM_INSTANCE_LIST_SERVLET_PATH = "/getConnectorInstanceList";

  public static final String LOG_RESPONSE_EMPTY_NODE = "Empty node";

  public static final int SUCCESS = 0;
  public static final int RESPONSE_EMPTY_NODE = 5213;
  public static final int RESPONSE_NULL_CONNECTOR = 5215;
  public static final int ERROR_PARSING_XML_REQUEST = 5300;

  @GuardedBy("class") private static Map<String, String> urlMap;
  @GuardedBy("class") private static boolean isInitialized;

  public static synchronized void initialize() {
    if (!isInitialized) {
      ConfigSingleton.getObservable().addObserver(LOCAL_OBSERVER);
      isInitialized = true;
    }
  }

  @VisibleForTesting
  public static synchronized void initializeForTesting(Map<String, String> urlMap) {
    ConfigSingleton.getObservable().deleteObserver(LOCAL_OBSERVER);
    ConnectorUtil.urlMap = urlMap;
    isInitialized = true;
  }

  private static final Observer LOCAL_OBSERVER
      = new Observer() {
          @Override
          public void update(Observable observable, Object object) {
            reinitializeUrlMap();
          }
        };

  private static void reinitializeUrlMap() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    Iterable<String> urls;
    try {
      urls = ConfigSingleton.getConnectorManagerUrls();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Unable to get list of connector manager URLs: ", e);
      return;
    }
    for (String url : urls) {
      Set<String> instances;
      try {
        instances = getInstances(url);
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Unable to read connector instances from " + url + ": ", e);
        continue;
      }
      for (String instance : instances) {
        builder.put(instance, url);
      }
    }
    synchronized (ConnectorUtil.class) {
      urlMap = builder.build();
    }
  }

  /**
   * @return An immutable map from connector-instance name to connector-manager URL.
   */
  public static synchronized Map<String, String> getUrlMap() {
    if (urlMap == null) {
      reinitializeUrlMap();
    }
    return urlMap;
  }

  /**
   * Gets the connector-manager URL associated with a given connector-instance name.
   *
   * @param instanceName The connector instance name to look up.
   * @return The corresponding connector-manager URL, or null if none.
   */
  public static String getInstanceManagerUrl(String instanceName) {
    return getUrlMap().get(instanceName);
  }

  /**
   * Gets the connector-manager URL associated with a given connector-instance name.
   *
   * @param instanceName The connector instance name to look up.
   * @return The corresponding connector-manager URL.
   * @throws IllegalArgumentException if the URL is unknown.
   */
  public static String requireInstanceManagerUrl(String instanceName) {
    String url = getInstanceManagerUrl(instanceName);
    Preconditions.checkArgument(url != null);
    return url;
  }

  /**
   * Get a list of connector instances from a connector manager.
   *
   * @param managerUrl The URL for the connector manager.
   * @return An immutable set of the instance names from the connector manager.
   */
  private static Set<String> getInstances(String managerUrl)
      throws IOException {
    Preconditions.checkNotNull(managerUrl);
    return parseInstanceListResponse(doExchange(null, managerUrl + CM_INSTANCE_LIST_SERVLET_PATH,
        -1));
  }

  private static Set<String> parseInstanceListResponse(Document document) {
    Element root = document.getDocumentElement();
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    if (!isElementWithQname(root, XML_TAG_RESPONSE_ROOT)) {
      LOGGER.warning("Unexpected response from connector manager:" + root);
      return builder.build();
    }
    Element instances = findChildElement(root, XML_TAG_CONNECTOR_INSTANCES, false);
    if (instances != null) {
      for (Element instance : getChildElements(instances, XML_TAG_CONNECTOR_INSTANCE)) {
        builder.add(getChildElementText(instance, XML_TAG_CONNECTOR_NAME, true));
      }
    }
    return builder.build();
  }

  /**
   */
  public static Document doExchange(Document request, String cmUrlString, int timeout)
      throws IOException {
    HttpExchange exchange = sendRequest(request, cmUrlString, timeout);
    Document response;
    try {
      response = XmlUtil.getInstance().readXmlDocument(
          new InputStreamReader(exchange.getResponseEntityAsStream(), "UTF-8"));
    } finally {
      exchange.close();
    }
    return response;
  }

  private static HttpExchange sendRequest(Document request, String cmUrlString, int timeout)
      throws IOException {
    HttpClientInterface httpClient = HttpClientUtil.getHttpClient();
    HttpExchange exchange = httpClient.postExchange(new URL(cmUrlString), null);
    if (timeout >= 0) {
      exchange.setTimeout(timeout);
    }
    try {
      exchange.setRequestHeader("Content-Type", "text/xml");
      exchange.setRequestBody(documentToString(request).getBytes("UTF-8"));
      int status = exchange.exchange();
      if (status > 300) {
        throw new IOException("Message exchange returned status: " + status);
      }
    } catch (IOException e) {
      exchange.close();
      throw e;
    }
    return exchange;
  }

  private static String documentToString(Document document)
      throws IOException {
    if (document == null) {
      return "";
    }
    XmlUtil xmlUtil = XmlUtil.getInstance();
    LSSerializer serializer = xmlUtil.makeSerializer();
    // Don't generate <?xml ... ?> line.
    serializer.getDomConfig().setParameter(CONFIG_XML_DECLARATION, false);
    StringWriter output = new StringWriter();
    try {
      XmlUtil.writeXmlDocument(document, serializer, xmlUtil.getLsOutput(output));
    } catch (LSException e) {
      throw new IOException(e);
    }
    return output.toString();
  }
}
