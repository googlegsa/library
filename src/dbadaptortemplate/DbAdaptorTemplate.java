package dbadaptortemplate;
import adaptorlib.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;
/**
 * Demonstrates what code is necessary for putting DB
 * content onto a GSA.
 */
class DbAdaptorTemplate extends Adaptor {
  private final static Logger LOG
      = Logger.getLogger(DbAdaptorTemplate.class.getName());

  private static Connection makeNewConnection() throws SQLException {
    // TODO: DB connection pooling.
    String url = "jdbc:mysql://127.0.0.1/adaptor1";
    LOG.fine("about to connect");
    Connection conn = DriverManager.getConnection(url, "root", "test");
    LOG.fine("connected");
    return conn;
  }

  private static ResultSet getFromDb(Connection conn, String query)
      throws SQLException {
    Statement st = conn.createStatement();
    LOG.info("about to query");
    ResultSet rs = st.executeQuery(query);
    LOG.info("queried");
    return rs;
  }

  public List<DocId> getDocIds() {
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
      LOG.log(Level.SEVERE, "failed getting ids", problem);
    } finally {
      tryClosingConnection(conn);
    }
    LOG.info("primary keys: " + primaryKeys);
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
        return "no columns in database result".getBytes(Config.getGsaCharacterEncoding());
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
        line1.append(tableName);
        line2.append(",");
        line2.append(columnName);
        line3.append(",");
        line3.append("" + value);
      }
      String document = line1.substring(1) + "\n"
          + line2.substring(1) + "\n" + line3.substring(1) + "\n";
      return document.getBytes(Config.getGsaCharacterEncoding());
    } catch (SQLException problem) {
      LOG.log(Level.SEVERE, "failed getting ids", problem);
      throw new IOException("retrieval error", problem);
    } finally {
      tryClosingConnection(conn);
    }
  }

  /** An example main for an adaptor. */
  public static void main(String a[]) throws Exception {
    Class.forName("org.gjt.mm.mysql.Driver").newInstance();
    LOG.info("loaded driver");
    Adaptor adaptor = new DbAdaptorTemplate();
    GsaCommunicationHandler gsa = new GsaCommunicationHandler(adaptor);

    // Setup providing content.
    try {
      gsa.beginListeningForContentRequests();
      LOG.info("doc content serving started");
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    List<DocId> handles = adaptor.getDocIds();
    GsaCommunicationHandler.pushDocIds("testfeed", handles);

    // Setup scheduled pushing of doc ids.
    ScheduleIterator everyNite = new ScheduleOncePerDay(/*hour*/3,
        /*minute*/0, /*second*/0);
    gsa.beginPushingDocIds(everyNite);
    LOG.info("doc id pushing has been put on schedule");
  }

  private static void tryClosingConnection(Connection conn) {
    if (null != conn) {
      try {
        conn.close();
      } catch(SQLException e) {
        LOG.log(Level.WARNING, "close failed", e);
      }
    }
  }
}
