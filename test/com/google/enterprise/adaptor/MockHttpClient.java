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
import com.google.enterprise.adaptor.secmgr.http.HttpClientInterface;
import com.google.enterprise.adaptor.secmgr.http.HttpExchange;

import com.sun.net.httpserver.Headers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock {@link HttpClientInterface}.
 */
public abstract class MockHttpClient implements HttpClientInterface {
  private static final String POST_ENCODING = "UTF-8";

  protected abstract void handleExchange(ClientExchange ex);

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

  /** Mocked exchange that calls {@link #handleExchange}. */
  protected class ClientExchange implements HttpExchange {
    private final URL url;
    private final String method;
    private byte[] requestBody;
    /** POST parameters */
    // Alternates between key and value
    private List<String> parameters = new ArrayList<String>();
    private boolean connected = false;
    private Headers requestHeaders = new Headers();
    private int responseCode = -1;
    private Headers responseHeaders = new Headers();
    private InputStream responseStream;

    public ClientExchange(URL url, String method) {
      this.url = url;
      this.method = method;
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
    }

    @Override
    public void setTimeout(int timeout) {
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
      requestHeaders.add(name, value);
    }

    @Override
    public void setRequestHeader(String name, String value) {
      requestHeaders.set(name, value);
    }

    @Override
    public List<String> getRequestHeaderValues(String headerName) {
      return requestHeaders.get(headerName);
    }

    @Override
    public String getRequestHeaderValue(String headerName) {
      return requestHeaders.getFirst(headerName);
    }

    /** Does not copy provided byte array. */
    @Override
    public void setRequestBody(byte[] byteArrayRequestEntity) {
      requestBody = byteArrayRequestEntity;
    }

    public byte[] getRequestBody() {
      return requestBody;
    }

    @Override
    public int exchange() throws IOException {
      if (parameters.size() > 0) {
        if (requestBody != null) {
          // Thus, having parameters is equivalent to having a requestBody, and
          // we can't have both.
          throw new IllegalStateException();
        }
      }
      handleExchange(this);
      if (responseCode == -1) {
        throw new IllegalStateException("handleExchange must set statusCode");
      }
      connected = true;
      return getStatusCode();
    }

    @Override
    public String getResponseEntityAsString() throws IOException {
      Charset charset = Charset.forName("UTF-8");
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
      return responseStream;
    }

    public void setResponseStream(InputStream responseStream) {
      this.responseStream = responseStream;
    }

    public void setResponseStream(byte[] bytes) {
      this.responseStream = new ByteArrayInputStream(bytes);
    }

    @Override
    public List<String> getResponseHeaderValues(String headerName) {
      return responseHeaders.get(headerName);
    }

    @Override
    public String getResponseHeaderValue(String headerName) {
      return responseHeaders.getFirst(headerName);
    }

    @Override
    public ListMultimap<String, String> getResponseHeaders() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getStatusCode() {
      return responseCode;
    }

    public void setStatusCode(int responseCode) {
      this.responseCode = responseCode;
    }

    @Override
    public void close() {
      if (connected) {
        try {
          responseStream.close();
        } catch (IOException ex) {
          // Ignore.
        }
      }
    }
  }
}
