package dbadaptortemplate;

import adaptorlib.*;
import java.io.*;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

/**
 * Demonstrates what code is necessary for putting DB
 * content onto a GSA.
 */
class DbAdaptorTemplate extends Adaptor {
  private static final Logger log
      = Logger.getLogger(DbAdaptorTemplate.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  private static Connection makeNewConnection() throws SQLException {
    // TODO(pjo): DB connection pooling.
    String url = "jdbc:mysql://127.0.0.1/adaptor1";
    log.fine("about to connect");
    Connection conn = DriverManager.getConnection(url, "root", "test");
    log.fine("connected");
    return conn;
  }

  private static ResultSet getFromDb(Connection conn, String query)
      throws SQLException {
    Statement st = conn.createStatement();
    log.fine("about to query");
    ResultSet rs = st.executeQuery(query);
    log.fine("queried");
    return rs;
  }

  /** Get all doc ids from database. */
  public List<DocId> getDocIds() throws IOException {
    ArrayList<DocId> primaryKeys = new ArrayList<DocId>();
    Connection conn = null;
    try {
      conn = makeNewConnection();
      ResultSet rs = getFromDb(conn, "select id from backlog");
      while (rs.next()) {
        DocId id = new DocId("" + rs.getInt("id"));
        primaryKeys.add(id);
      }
    } catch (SQLException problem) {
      log.log(Level.SEVERE, "failed getting ids", problem);
      throw new IOException(problem);
    } finally {
      tryClosingConnection(conn);
    }
    if (log.isLoggable(Level.FINEST)) {
      log.finest("primary keys: " + primaryKeys);
    }
    return primaryKeys;
  }

  /** Gives the bytes of a document referenced with id. */
  public byte[] getDocContent(DocId id) throws IOException {
    Connection conn = null;
    try {
      conn = makeNewConnection();
      ResultSet rs = getFromDb(conn, "select * from backlog where id = " + id.getUniqueId());

      // First handle cases with no data to return.
      boolean hasResult = rs.next();
      if (!hasResult) {
        throw new FileNotFoundException("no document with id: " + id);
      }
      ResultSetMetaData rsMetaData = rs.getMetaData();
      int numberOfColumns = rsMetaData.getColumnCount();
      if (0 == numberOfColumns) {
        log.warning("no columns in results");
        // TODO(pjo): Cause some sort of error code.
        return "no columns in database result".getBytes(encoding);
      }

      // If we have data then create lines of resulting document.
      StringBuilder line1 = new StringBuilder();
      StringBuilder line2 = new StringBuilder();
      StringBuilder line3 = new StringBuilder();
      for (int i = 1; i < (numberOfColumns + 1); i++) {
        String tableName = rsMetaData.getTableName(i);
        String columnName = rsMetaData.getColumnName(i);
        Object value = rs.getObject(i);
        line1.append(",");
        line1.append(makeIntoCsvField(tableName));
        line2.append(",");
        line2.append(makeIntoCsvField(columnName));
        line3.append(",");
        line3.append(makeIntoCsvField("" + value));
      }
      String document = line1.substring(1) + "\n"
          + line2.substring(1) + "\n" + line3.substring(1) + "\n";
      return document.getBytes(encoding);
    } catch (SQLException problem) {
      log.log(Level.SEVERE, "failed getting content", problem);
      throw new IOException("retrieval error", problem);
    } finally {
      tryClosingConnection(conn);
    }
  }

  /** An example main for an adaptor. */
  public static void main(String a[]) throws Exception {
    Class.forName("org.gjt.mm.mysql.Driver");
    log.info("loaded driver");
    Config config = new Config();
    config.autoConfig(a);
    Adaptor adaptor = new DbAdaptorTemplate();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor, config);

    // Setup providing content.
    try {
      gsa.beginListeningForContentRequests();
      log.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    // Push once at program start.
    gsa.pushDocIds();

    // Setup scheduled pushing of doc ids for once a day.
    gsa.beginPushingDocIds(
        new ScheduleOncePerDay(/*hour*/3, /*minute*/0, /*second*/0));
    log.info("doc id pushing has been put on schedule");
  }

  private static void tryClosingConnection(Connection conn) {
    if (null != conn) {
      try {
        conn.close();
      } catch (SQLException e) {
        log.log(Level.WARNING, "close failed", e);
      }
    }
  }

  private static String makeIntoCsvField(String s) {
    /*
     * Fields that contain a special character (comma, newline,
     * or double quote), must be enclosed in double quotes.
     * <...> If a field's value contains a double quote character
     * it is escaped by placing another double quote character next to it.
     */
    String doubleQuote = "\"";
    boolean containsSpecialChar = s.contains(",")
        || s.contains("\n") || s.contains(doubleQuote);
    if (containsSpecialChar) {
      s = s.replace(doubleQuote, doubleQuote + doubleQuote);
      s = doubleQuote + s + doubleQuote;
    }
    return s;
  }
}
