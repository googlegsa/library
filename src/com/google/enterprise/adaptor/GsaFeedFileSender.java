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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/** Takes an XML feed file for the GSA, sends it to GSA and
  then reads reply from GSA. */
class GsaFeedFileSender {
  private static final Logger log
      = Logger.getLogger(GsaFeedFileSender.class.getName());
  private static final Pattern DATASOURCE_FORMAT
      = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_-]*");

  /** Indicates failure creating connection to GSA. */
  static class FailedToConnect extends Exception {
    public FailedToConnect(IOException e) {
      super(e);
    }
    // TODO(pjo): Add corrective tips.
  }
  /** Indicates failure to send XML feed file to GSA. */
  static class FailedWriting extends Exception {
    public FailedWriting(IOException e) {
      super(e);
    }
    // TODO(pjo): Add corrective tips.
  }
  /** Indicates failure to read response to sent XML feed file. */
  static class FailedReadingReply extends Exception {
    public FailedReadingReply(IOException e) {
      super(e);
    }
    // TODO(pjo): Add corrective tips.
  }

  /** Configuration for GSA's encoding and whether to use HTTPS. */
  private final Config config;

  // Feed file XML will not contain "<<".
  private static final String BOUNDARY = "<<";

  // Another frequently used constant of sent message.
  private static final String CRLF = "\r\n";

  public GsaFeedFileSender(Config config) {
    this.config = config;
  }

  // Get bytes of string in communication's encoding.
  private byte[] toEncodedBytes(String s) {
    return s.getBytes(config.getGsaCharacterEncoding());
  }

  /** Helper method for creating a multipart/form-data HTTP post.
    Creates a post parameter made of a name and value. */
  private void buildPostParameter(StringBuilder sb, String name,
      String mimetype, String value) {
    sb.append("--").append(BOUNDARY).append(CRLF);
    sb.append("Content-Disposition: form-data;");
    sb.append(" name=\"").append(name).append("\"").append(CRLF);
    sb.append("Content-Type: ").append(mimetype).append(CRLF);
    sb.append(CRLF).append(value).append(CRLF);
  }

  private byte[] buildMessage(String datasource, String feedtype,
      String xmlDocument) {
    StringBuilder sb = new StringBuilder();
    buildPostParameter(sb, "datasource", "text/plain", datasource);
    buildPostParameter(sb, "feedtype", "text/plain", feedtype);
    buildPostParameter(sb, "data", "text/xml", xmlDocument);
    sb.append("--").append(BOUNDARY).append("--").append(CRLF);
    return toEncodedBytes("" + sb);
  }

  /** Tries to get in touch with our GSA. */
  private HttpURLConnection setupConnection(URL feedUrl, int len,
                                            boolean useCompression)
      throws IOException {
    HttpURLConnection uc = (HttpURLConnection) feedUrl.openConnection();
    uc.setDoInput(true);
    uc.setDoOutput(true);
    if (useCompression) {
      uc.setChunkedStreamingMode(0);
      // GSA can handle gziped content, although there isn't a way to find out
      // other than just trying
      uc.setRequestProperty("Content-Encoding", "gzip");
    } else {
      uc.setFixedLengthStreamingMode(len);
    }
    uc.setRequestProperty("Content-Type",
        "multipart/form-data; boundary=" + BOUNDARY);
    return uc;
  }

  /** Put bytes onto output stream. */
  private void writeToGsa(HttpURLConnection uc, byte msgbytes[],
                          boolean useCompression)
      throws IOException {
    OutputStream outputStream = uc.getOutputStream();
    try {
      if (useCompression) {
        // setupConnection set Content-Encoding: gzip
        outputStream = new GZIPOutputStream(outputStream);
      }
      // Use copyStream(), because using a single write() prevents errors from
      // propagating during writing and causes them to be discovered at read
      // time. Using copyStream() isn't perfect either though, in that if
      // buffered data eventually causes an error, then that will still be
      // discovered at read time.
      IOHelper.copyStream(new ByteArrayInputStream(msgbytes), outputStream);
      outputStream.flush();
    } finally {
      outputStream.close();
    }
  }

  /** Get GSA's response. */
  private String readGsaReply(HttpURLConnection uc) throws IOException {
    InputStream inputStream = uc.getInputStream();
    String reply;
    try {
      reply = IOHelper.readInputStreamToString(inputStream,
          config.getGsaCharacterEncoding());
    } finally {
      inputStream.close();
    }
    return reply;
  }

  private void handleGsaReply(String reply) {
    if ("Success".equals(reply)) {
      log.info("success message received");
    } else {
      throw new IllegalStateException("GSA reply: " + reply);
    }

    /* TODO(pjo): Recognize additional replies.
    if ("Error - Unauthorized Request".equals(reply)) {
        // TODO(pjo): Improve message with Admin Console details.
        throw new IllegalStateException("GSA is not configured "
            + "to accept feeds from this IP.  Please add <IP> "
            + "to permitted machines.");
      }
    if ("Internal Error".equals(reply))
    if ("".equals(reply))
    */
  }

  /**
   * Sends XML with provided datasource name and feedtype "metadata-and-url".
   * Datasource name is limited to [a-zA-Z0-9_].
   */
  void sendMetadataAndUrl(String host, String datasource,
                          String xmlString, boolean useCompression)
      throws FailedToConnect, FailedWriting, FailedReadingReply {
    URL feedUrl;
    try {
      if (config.isServerSecure()) {
        feedUrl = new URL("https://" + host + ":19902/xmlfeed");
      } else {
        feedUrl = new URL("http://" + host + ":19900/xmlfeed");
      }
    } catch (MalformedURLException ex) {
      throw new FailedToConnect(ex);
    }
    sendMetadataAndUrl(feedUrl, datasource, xmlString, useCompression);
  }

  /**
   * Sends XML with provided datasource name and feedtype "metadata-and-url".
   * Datasource name is limited to [a-zA-Z_][a-zA-Z0-9_-]*.
   */
  void sendMetadataAndUrl(URL feedUrl, String datasource,
                          String xmlString, boolean useCompression)
      throws FailedToConnect, FailedWriting, FailedReadingReply {
    if (!DATASOURCE_FORMAT.matcher(datasource).matches()) {
      throw new IllegalArgumentException("Data source contains illegal "
          + "characters: " + datasource);
    }
    String feedtype = "metadata-and-url";
    byte msg[] = buildMessage(datasource, feedtype, xmlString);
    // GSA only allows request content up to 1 MB to be compressed
    if (msg.length >= 1 * 1024 * 1024) {
      useCompression = false;
    }

    HttpURLConnection uc;
    try {
      uc = setupConnection(feedUrl, msg.length, useCompression);
      uc.connect();
    } catch (IOException ioe) {
      throw new FailedToConnect(ioe);
    }
    try {
      writeToGsa(uc, msg, useCompression);
    } catch (IOException ioe) {
      uc.disconnect();
      throw new FailedWriting(ioe);
    }
    try {
      String reply = readGsaReply(uc);
      handleGsaReply(reply);
    } catch (IOException ioe) {
      uc.disconnect();
      throw new FailedReadingReply(ioe);
    }
  }
}
