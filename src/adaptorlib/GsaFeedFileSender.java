// Copyright 2011 Google Inc.
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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/** Takes an XML feed file for the GSA, sends it to GSA and
  then reads reply from GSA. */
class GsaFeedFileSender {
  private static final Logger log
      = Logger.getLogger(GsaFeedFileSender.class.getName());

  /** Indicates failure creating connection to GSA. */
  static class FailedToConnect extends Exception {
    private FailedToConnect(IOException e) {
      super(e);
    }
    // TODO(pjo): Add corrective tips.
  }
  /** Indicates failure to send XML feed file to GSA. */
  static class FailedWriting extends Exception {
    private FailedWriting(IOException e) {
      super(e);
    }
    // TODO(pjo): Add corrective tips.
  }
  /** Indicates failure to read response to sent XML feed file. */
  static class FailedReadingReply extends Exception {
    private FailedReadingReply(IOException e) {
      super(e);
    }
    // TODO(pjo): Add corrective tips.
  }

  // All communications are expected to be tailored to GSA.
  private final Charset encoding;

  // Feed file XML will not contain "<<".
  private static final String BOUNDARY = "<<";

  // Another frequently used constant of sent message.
  private static final String CRLF = "\r\n";

  public GsaFeedFileSender(Charset encoding) {
    this.encoding = encoding;
  }

  // Get bytes of string in communication's encoding.
  private byte[] toEncodedBytes(String s) {
    return s.getBytes(encoding);
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
  private HttpURLConnection setupConnection(String gsaHost, int len,
                                            boolean useCompression)
      throws MalformedURLException, IOException {
    URL feedUrl = new URL("http://" + gsaHost + ":19900/xmlfeed");
    HttpURLConnection uc = (HttpURLConnection) feedUrl.openConnection();
    uc.setDoInput(true);
    uc.setDoOutput(true);
    if (useCompression) {
      uc.setChunkedStreamingMode(0);
    } else {
      uc.setFixedLengthStreamingMode(len);
    }
    uc.setRequestProperty("Content-Type",
        "multipart/form-data; boundary=" + BOUNDARY);
    // GSA can handle gziped content, although there isn't a way to find out
    // other than just trying
    uc.setRequestProperty("Content-Encoding", "gzip");
    return uc;
  }

  /** Put bytes onto output stream. */
  private void writeToGsa(OutputStream outputStream, byte msgbytes[])
      throws IOException {
    try {
      outputStream.write(msgbytes);
      outputStream.flush();
    } finally {
      outputStream.close();
    }
  }

  /** Get GSA's response. */
  private String readGsaReply(HttpURLConnection uc) throws IOException {
    StringBuilder buf = new StringBuilder();
    BufferedReader br = null;
    try {
      InputStream inputStream = uc.getInputStream();
      br = new BufferedReader(new InputStreamReader(inputStream, encoding));
      String line;
      while ((line = br.readLine()) != null) {
        buf.append(line);
      }
    } finally {
      try {
        if (null != br) {
          br.close();
        }
      } catch (IOException e) {
        log.warning("failed to close buffered reader");
      }
      if (null != uc) {
        uc.disconnect();
      }
    }
    return buf.toString();
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
   * Sends XML with provided datasoruce name and feedtype "metadata-and-url".
   * Datasource name is limited to [a-zA-Z0-9_].
   */
  void sendMetadataAndUrl(String host, String datasource, String xmlString)
      throws FailedToConnect, FailedWriting, FailedReadingReply {
    // TODO(pjo): Check datasource characters for valid name.
    String feedtype = "metadata-and-url";
    byte msg[] = buildMessage(datasource, feedtype, xmlString);
    // GSA only allows request content up to 1 MB to be compressed
    boolean useCompression = msg.length < 1 * 1024 * 1024;

    HttpURLConnection uc;
    OutputStream outputStream;
    try {
      uc = setupConnection(host, msg.length, useCompression);
      outputStream = uc.getOutputStream();
    } catch (IOException ioe) {
      throw new FailedToConnect(ioe);
    }
    try {
      if (useCompression) {
        // setupConnection set Content-Encoding: gzip
        outputStream = new GZIPOutputStream(outputStream);
      }
      writeToGsa(outputStream, msg);
    } catch (IOException ioe) {
      throw new FailedWriting(ioe);
    } finally {
      try {
        String reply = readGsaReply(uc);
        handleGsaReply(reply);
      } catch (IOException ioe) {
        throw new FailedReadingReply(ioe);
      }
    }
  }
}
