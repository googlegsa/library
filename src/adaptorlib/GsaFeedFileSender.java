package adaptorlib;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Logger;

/** Takes an XML feed file for the GSA, sends it to GSA and
  then reads reply from GSA. */
class GsaFeedFileSender {
  private static String CLASS_NAME = GsaFeedFileSender.class.getName();
  private static Logger LOG = Logger.getLogger(CLASS_NAME);

  /** Indicates failure creating connection to GSA. */
  static class FailedToConnect extends Exception {
    private FailedToConnect(IOException e) {
      super(e);
    }
    // TODO: Add corrective tips.
  }
  /** Indicates failure to send XML feed file to GSA. */
  static class FailedWriting extends Exception {
    private FailedWriting(IOException e) {
      super(e);
    }
    // TODO: Add corrective tips.
  }
  /** Indicates failure to read response to sent XML feed file. */
  static class FailedReadingReply extends Exception {
    private FailedReadingReply(IOException e) {
      super(e);
    }
    // TODO: Add corrective tips.
  }

  // All communications are expected to be tailored to GSA.
  private static final Charset ENCODING
      = Config.getGsaCharacterEncoding();

  // Feed file XML will not contain "<<".
  private static final String BOUNDARY = "<<";

  // Another frequently used constant of sent message.
  private static final String CRLF = "\r\n";

  // Get bytes of string in communication's encoding.
  private static byte []toEncodedBytes(String s) {
    return s.getBytes(ENCODING);
  }

  /** Helper method for creating a multipart/form-data HTTP post.
    Creates a post parameter made of a name and value. */
  private static void buildPostParameter(StringBuilder sb, String name,
      String mimetype, String value) {
    sb.append("--").append(BOUNDARY).append(CRLF);
    sb.append("Content-Disposition: form-data;");
    sb.append(" name=\"").append(name).append("\"").append(CRLF);
    sb.append("Content-Type: ").append(mimetype).append(CRLF);
    sb.append(CRLF).append(value).append(CRLF);
  }

  private static byte []buildMessage(String datasource, String feedtype,
      String xmlDocument) {
    StringBuilder sb = new StringBuilder();
    buildPostParameter(sb, "datasource", "text/plain", datasource);
    buildPostParameter(sb, "feedtype", "text/plain", feedtype);
    buildPostParameter(sb, "data", "text/xml", xmlDocument);
    sb.append("--").append(BOUNDARY).append("--").append(CRLF);
    return toEncodedBytes("" + sb);
  }

  /** Tries to get in touch with our GSA. */
  private static HttpURLConnection setupConnection(int len) 
      throws MalformedURLException, IOException {
    String gsaHost = Config.getGsaHostname();
    URL feedUrl = new URL("http://" + gsaHost + ":19900/xmlfeed");
    HttpURLConnection uc = (HttpURLConnection) feedUrl.openConnection();
    uc.setDoInput(true);
    uc.setDoOutput(true);
    uc.setFixedLengthStreamingMode(len);
    uc.setRequestProperty("Content-Type",
        "multipart/form-data; boundary=" + BOUNDARY);
    return uc;
  }

  /** Put bytes onto output stream. */
  private static void writeToGsa(OutputStream outputStream,
      byte msgbytes[]) throws IOException {
    try {
      // TODO: Remove System.out .
      //System.out.write(msgbytes);
      outputStream.write(msgbytes);
      outputStream.flush();
    } finally {
      try {
        outputStream.close();
      } catch (IOException e) {
        LOG.warning("failed to close output stream");
      }
    }
  }

  /** Get GSA's response. */
  private static String readGsaReply(HttpURLConnection uc) throws IOException {
    StringBuilder buf = new StringBuilder();
    BufferedReader br = null;
    try {
      InputStream inputStream = uc.getInputStream();
      br = new BufferedReader(new InputStreamReader(inputStream, ENCODING));
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
        LOG.warning("failed to close buffered reader");
      }
      if (null != uc) {
        uc.disconnect();
      }
    }
    return buf.toString();
  }

  private static void handleGsaReply(String reply) {
    if ("Success".equals(reply)) {
      LOG.info("success message received");
    } else {
      throw new IllegalStateException("GSA reply: " + reply);
    }

    /* TODO: Recognize additional replies.
    if ("Error - Unauthorized Request".equals(reply)) {
        // TODO: Improve message with Admin Console details.
        throw new IllegalStateException("GSA is not configured "
            + "to accept feeds from this IP.  Please add <IP> "
            + "to permitted machines.");
      }
    if ("Internal Error".equals(reply))
    if ("".equals(reply))
    */
  }

  /** Sends XML with provided datasoruce name
    and feedtype "metadata-and-url".  Datasource name 
    is limited to [a-zA-Z0-9_]. */
  static void sendMetadataAndUrl(String datasource, String xmlString) 
      throws FailedToConnect, FailedWriting, FailedReadingReply {
    // TODO: Check datasource characters for valid name.
    String feedtype = "metadata-and-url";
    byte msg[] = buildMessage(datasource, feedtype, xmlString);

    HttpURLConnection uc;
    OutputStream outputStream;
    try {
      uc = setupConnection(msg.length);
      outputStream = uc.getOutputStream();
    } catch (IOException ioe) {
      throw new FailedToConnect(ioe);
    }
    try {
      writeToGsa(outputStream, msg);
    } catch (IOException ioe) {
      throw new FailedWriting(ioe);
    } finally {
      try {
        String reply = readGsaReply(uc);
        // TODO: Remove System.out .
        // System.out.println("REPLY:\n\n" + reply);
        handleGsaReply(reply);
      } catch (IOException ioe) {
        throw new FailedReadingReply(ioe);
      }
    }
  }
}
