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

import com.google.common.collect.ListMultimap;
import com.google.enterprise.secmgr.common.CookieStore;
import com.google.enterprise.secmgr.common.GCookie;
import com.google.enterprise.secmgr.http.HttpClientInterface;
import com.google.enterprise.secmgr.http.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows communicating with HTTP servers.
 */
class HttpClientAdapter implements HttpClientInterface {
  private static final String POST_ENCODING = "UTF-8";

  @Override
  public HttpExchange headExchange(URL url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpExchange getExchange(URL url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpExchange postExchange(URL url,
                                   ListMultimap<String, String> parameters) {
    HttpExchange exchange = new ClientExchange(url, "POST");
    if (parameters != null) {
      exchange.setRequestHeader("Content-Type",
          "application/x-www-form-urlencoded; charset=" + POST_ENCODING);
      for (String name : parameters.keySet()) {
        for (String value : parameters.get(name)) {
          exchange.addParameter(name, value);
        }
      }
    }
    return exchange;
  }

  @Override
  public HttpExchange newHttpExchange(URL url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Connection getConnection(URL url) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpExchange headExchange(Connection connection, URL url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpExchange getExchange(Connection connection, URL url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpExchange postExchange(Connection connection, URL url,
                                   ListMultimap<String, String> parameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpExchange newHttpExchange(Connection connection, URL url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRequestTimeoutMillis(int millisec) {
    throw new UnsupportedOperationException();
  }

  private static class ClientExchange implements HttpExchange {
    private final URL url;
    private final String method;
    private final HttpURLConnection conn;
    private byte[] requestBody;
    /** POST parameters */
    // Alternates between key and value
    private List<String> parameters = new ArrayList<String>();
    private boolean connected = false;

    public ClientExchange(URL url, String method) {
      this.url = url;
      this.method = method;
      try {
        conn = (HttpURLConnection) url.openConnection();
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      conn.setDoOutput(true);
      conn.setDoInput(true);
    }

    @Override
    public void setProxy(String proxy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setBasicAuthCredentials(String username, String password) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setFollowRedirects(boolean followRedirects) {
      conn.setFollowRedirects(followRedirects);
    }

    @Override
    public void setTimeout(int timeout) {
      conn.setConnectTimeout(timeout);
      conn.setReadTimeout(timeout);
    }

    @Override
    public String getHttpMethod() {
      return method;
    }

    @Override
    public URL getUrl() {
      return url;
    }

    @Override
    public void addParameter(String name, String value) {
      if (!"POST".equals(method)) {
        throw new IllegalStateException();
      }
      parameters.add(name);
      parameters.add(value);
    }

    @Override
    public void addRequestHeader(String name, String value) {
      conn.addRequestProperty(name, value);
    }

    @Override
    public void setRequestHeader(String name, String value) {
      conn.setRequestProperty(name, value);
    }

    @Override
    public List<String> getRequestHeaderValues(String headerName) {
      return conn.getRequestProperties().get(headerName);
    }

    @Override
    public String getRequestHeaderValue(String headerName) {
      return conn.getRequestProperty(headerName);
    }

    @Override
    public void addCookies(Iterable<GCookie> cookies) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CookieStore getCookies() {
      throw new UnsupportedOperationException();
    }

    /** Does not copy provided byte array. */
    @Override
    public void setRequestBody(byte[] byteArrayRequestEntity) {
      requestBody = byteArrayRequestEntity;
    }

    @Override
    public int exchange() throws IOException {
      if (parameters.size() > 0) {
        if (requestBody != null) {
          // We are about to encode the parameters into a byte[] for the
          // requestBody. Thus, having parameters is equivalent to having a
          // requestBody, and we can't have both.
          throw new IllegalStateException();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameters.size(); i += 2) {
          String name = parameters.get(i);
          String value = parameters.get(i + 1);
          sb.append(URLEncoder.encode(name, POST_ENCODING));
          sb.append("=");
          sb.append(URLEncoder.encode(value, POST_ENCODING));
          sb.append("&");
        }
        String formData = sb.substring(0, sb.length() - 1);
        setRequestBody(formData.getBytes(Charset.forName("UTF-8")));
      }
      conn.connect();
      connected = true;
      OutputStream os = conn.getOutputStream();
      try {
        if (requestBody != null) {
          os.write(requestBody);
        }
      } finally {
        os.close();
      }
      return conn.getResponseCode();
    }

    @Override
    public String getResponseEntityAsString() throws IOException {
      Charset charset = Charset.forName(conn.getContentEncoding());
      if (charset == null) {
        throw new RuntimeException("Unknown charset: " + charset);
      }
      InputStream is = getResponseEntityAsStream();
      StringBuilder sb = new StringBuilder();
      try {
        Reader reader = new InputStreamReader(is, charset);
        char[] buf = new char[1024];
        int read;
        while ((read = reader.read(buf)) != -1) {
          sb.append(buf, 0, read);
        }
      } finally {
        is.close();
      }
      return sb.toString();
    }

    @Override
    public InputStream getResponseEntityAsStream() throws IOException {
      return conn.getInputStream();
    }

    @Override
    public List<String> getResponseHeaderValues(String headerName) {
      return conn.getHeaderFields().get(headerName);
    }

    @Override
    public String getResponseHeaderValue(String headerName) {
      // Returns last instead of first header if there are multiple headers with
      // same name.
      return conn.getHeaderField(headerName);
    }

    @Override
    public ListMultimap<String, String> getResponseHeaders() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getStatusCode() {
      try {
        return conn.getResponseCode();
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public void close() {
      if (connected) {
        try {
          conn.getInputStream().close();
        } catch (IOException ex) {
          // Ignore.
        }
      }
    }
  }
}
