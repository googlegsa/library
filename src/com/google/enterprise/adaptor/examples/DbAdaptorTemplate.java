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

package com.google.enterprise.adaptor.examples;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;

import java.io.*;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

/**
 * Demonstrates what code is necessary for putting DB
 * content onto a GSA.
 */
public class DbAdaptorTemplate extends AbstractAdaptor {
  private static final Logger log
      = Logger.getLogger(DbAdaptorTemplate.class.getName());
  private Charset encoding = Charset.forName("UTF-8");

  private int maxIdsPerFeedFile;
  private String dbname, tablename;
 
  @Override
  public void initConfig(Config config) {
    config.addKey("db.name", null);
    config.addKey("db.tablename", null);
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    Class.forName("org.gjt.mm.mysql.Driver");
    log.info("loaded driver");
    maxIdsPerFeedFile = context.getConfig().getFeedMaxUrls();
    dbname = context.getConfig().getValue("db.name");
    tablename = context.getConfig().getValue("db.tablename");
  }

  /** Get all doc ids from database. */
  @Override
  public void getDocIds(DocIdPusher pusher) throws IOException,
         InterruptedException {
    BufferingPusher outstream = new BufferingPusher(pusher);
    Connection conn = null;
    try {
      conn = makeNewConnection();
      ResultSet rs = getStreamFromDb(conn, "select id from " + tablename);
      while (rs.next()) {
        DocId id = new DocId("" + rs.getInt("id"));
        outstream.add(id);
      }
    } catch (SQLException problem) {
      log.log(Level.SEVERE, "failed getting ids", problem);
      throw new IOException(problem);
    } finally {
      //tryClosingResultSet();
      //tryClosingStatement();
      tryClosingConnection(conn);
    }
    outstream.forcePush();
  }

  /** Gives the bytes of a document referenced with id. */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    Connection conn = null;
    try {
      conn = makeNewConnection();
      int primaryKey;
      try {
        primaryKey = Integer.parseInt(id.getUniqueId());
      } catch (NumberFormatException nfe) {
        resp.respondNotFound();
        return;
      }
      String query = "select * from " + tablename + " where id = " + primaryKey;
      ResultSet rs = getFromDb(conn, query);

      // First handle cases with no data to return.
      boolean hasResult = rs.next();
      if (!hasResult) {
        resp.respondNotFound();
        return;
      }
      ResultSetMetaData rsMetaData = rs.getMetaData();
      int numberOfColumns = rsMetaData.getColumnCount();
      if (0 == numberOfColumns) {
        log.warning("no columns in results");
        // TODO(pjo): Cause some sort of error code.
        resp.getOutputStream().write(
            "no columns in database result".getBytes(encoding));
        return;
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
      resp.getOutputStream().write(document.getBytes(encoding));
    } catch (SQLException problem) {
      log.log(Level.SEVERE, "failed getting content", problem);
      throw new IOException("retrieval error", problem);
    } finally {
      //tryClosingResultSet();
      //tryClosingStatement();
      tryClosingConnection(conn);
    }
  }

  public static void main(String[] args) {
    AbstractAdaptor.main(new DbAdaptorTemplate(), args);
  }


  private Connection makeNewConnection() throws SQLException {
    // TODO(pjo): DB connection pooling.
    String url = "jdbc:mysql://127.0.0.1/" + dbname;
    log.fine("about to connect");
    Connection conn = DriverManager.getConnection(url, "root", "test");
    log.fine("connected");
    return conn;
  }

  private static ResultSet getFromDb(Connection conn, String query)
      throws SQLException {
    Statement st = conn.createStatement();
    log.fine("about to query: " + query);
    ResultSet rs = st.executeQuery(query);
    log.fine("queried");
    return rs;
  }

  private static ResultSet getStreamFromDb(Connection conn, String query)
      throws SQLException {
    Statement st = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY,
        java.sql.ResultSet.CONCUR_READ_ONLY);
    st.setFetchSize(Integer.MIN_VALUE);
    log.fine("about to query for stream: " + query);
    ResultSet rs = st.executeQuery(query);
    log.fine("queried for stream");
    return rs;
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

  /**
   * Mechanism that accepts stream of DocId instances, bufferes them,
   * and sends them when it has accumulated maximum allowed amount per
   * feed file.
   */
  private class BufferingPusher {
    DocIdPusher wrapped;
    ArrayList<DocId> saved;
    BufferingPusher(DocIdPusher underlying) {
      wrapped = underlying;
      saved = new ArrayList<DocId>(maxIdsPerFeedFile);
    }
    void add(DocId id) throws InterruptedException {
      saved.add(id);
      if (saved.size() >= maxIdsPerFeedFile) {
        forcePush();
      }
    }
    void forcePush() throws InterruptedException {
      wrapped.pushDocIds(saved);
      log.fine("sent " + saved.size() + " doc ids to pusher");
      saved.clear();
    }
    protected void finalize() throws Throwable {
      if (0 != saved.size()) {
        log.severe("still have saved ids that weren't sent");
      }
    }
  }
}
