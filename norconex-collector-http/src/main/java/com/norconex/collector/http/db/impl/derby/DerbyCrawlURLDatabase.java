/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.db.impl.derby;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ArrayListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.CrawlURL;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.CrawlURLDatabaseException;
import com.norconex.collector.http.db.ICrawlURLDatabase;

public class DerbyCrawlURLDatabase  implements ICrawlURLDatabase {

    private static final Logger LOG = 
            LogManager.getLogger(DerbyCrawlURLDatabase.class);
    
    private static final String TABLE_QUEUE = "queue";
    private static final String TABLE_ACTIVE = "active";
    private static final String TABLE_CACHE = "cache";
    private static final String TABLE_PROCESSED_VALID = "valid";
    private static final String TABLE_PROCESSED_INVALID = "invalid";
    private static final String TABLE_SITEMAP = "sitemap";
    
    private static final int NUMBER_OF_TABLES = 6;
    private static final int COLCOUNT_SITEMAP = 2;
    private static final int COLCOUNT_ALL = 5;
    private static final int SQL_ERROR_ALREADY_EXISTS = 30000;
    private final ResourceBundle sqls = ResourceBundle.getBundle(
            DerbyCrawlURLDatabase.class.getName() + "SQLs");
    
    private final String dbDir;
    private final DataSource datasource;

    
    public DerbyCrawlURLDatabase(HttpCrawlerConfig config, boolean resume) {
        super();
        LOG.info("Initializing crawl database...");
        this.dbDir = config.getWorkDir().getPath() + "/crawldb/db";
        
        System.setProperty("derby.system.home", 
                config.getWorkDir().getPath() + "/crawldb/log");
        
        this.datasource = createDataSource();
        boolean incrementalRun;
        try {
            incrementalRun = ensureTablesExist();
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem creating crawl database.", e);
        }
        if (!resume) {
            if (incrementalRun) {
                LOG.info("Caching processed URL from last run (if any)...");
                LOG.debug("Rename processed table to cache...");
                sqlUpdate("DROP TABLE " + TABLE_CACHE);
                sqlUpdate("RENAME TABLE " + TABLE_PROCESSED_VALID 
                        + " TO " + TABLE_CACHE);
                LOG.debug("Cleaning queue table...");
                sqlClearTable(TABLE_QUEUE);
                LOG.debug("Cleaning invalid URLS table...");
                sqlClearTable(TABLE_PROCESSED_INVALID);
                LOG.debug("Cleaning active table...");
                sqlClearTable(TABLE_ACTIVE);
                LOG.debug("Re-creating processed table...");
                sqlCreateTable(TABLE_PROCESSED_VALID);
                LOG.debug("Cleaning sitemap table...");
                sqlClearTable(TABLE_SITEMAP);
            }
        } else {
            LOG.debug("Resuming: putting active URLs back in the queue...");
            copyCrawlURLsToQueue(TABLE_ACTIVE);
            LOG.debug("Cleaning active database...");
            sqlClearTable(TABLE_ACTIVE);
        }
        LOG.info("Done initializing databases.");
    }

    @Override
    public final synchronized void queue(CrawlURL crawlURL) {
        sqlInsertLightCrawlURL(TABLE_QUEUE, crawlURL);
    }

    @Override
    public final synchronized boolean isQueueEmpty() {
        return getQueueSize()  == 0;
    }

    @Override
    public final synchronized int getQueueSize() {
        return sqlRecordCount(TABLE_QUEUE);
    }

    @Override
    public final synchronized boolean isQueued(String url) {
        return sqlURLExists(TABLE_QUEUE, url);
    }

    @Override
    public final synchronized CrawlURL nextQueued() {
        CrawlURL crawlURL = sqlQueryCrawlURL(false, TABLE_QUEUE, null, "depth");
        if (crawlURL != null) {
            sqlInsertLightCrawlURL(TABLE_ACTIVE, crawlURL);
            sqlDeleteCrawlURL(TABLE_QUEUE, crawlURL);
        }
        return crawlURL;
    }

    @Override
    public final synchronized boolean isActive(String url) {
        return sqlURLExists(TABLE_ACTIVE, url);
    }
    
    @Override
    public final synchronized int getActiveCount() {
        return sqlRecordCount(TABLE_ACTIVE);
    }

    @Override
    public synchronized CrawlURL getCached(String url) {
        return sqlQueryCrawlURL(true, TABLE_CACHE, "url = ?", null, url);
    }

    @Override
    public final synchronized boolean isCacheEmpty() {
        return sqlRecordCount(TABLE_CACHE) == 0;
    }

    @Override
    public final synchronized void processed(CrawlURL crawlURL) {
        String table;
        if (isValidStatus(crawlURL)) {
            table = TABLE_PROCESSED_VALID;
        } else {
            table = TABLE_PROCESSED_INVALID;
        }
        sqlInsertFullCrawlURL(table, crawlURL);
        sqlDeleteCrawlURL(TABLE_ACTIVE, crawlURL);
        sqlDeleteCrawlURL(TABLE_CACHE, crawlURL);
    }
    
    @Override
    public final synchronized boolean isProcessed(String url) {
        return sqlURLExists(TABLE_PROCESSED_VALID, url)
                || sqlURLExists(TABLE_PROCESSED_INVALID, url);
    }

    @Override
    public final synchronized int getProcessedCount() {
        return sqlRecordCount(TABLE_PROCESSED_VALID)
                + sqlRecordCount(TABLE_PROCESSED_INVALID);
    }

    @Override
    public Iterator<CrawlURL> getCacheIterator() {
        try {
            final Connection conn = datasource.getConnection(); 
            final Statement stmt = conn.createStatement(
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            final ResultSet rs = stmt.executeQuery(
                    "SELECT url, depth, smLastMod, smChangeFreq, smPriority "
                    + "FROM " + TABLE_CACHE);
            if (rs == null || !rs.first()) {
                return null;
            }
            rs.beforeFirst();
            return new CrawlURLIterator(rs, conn, stmt);
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem getting database cache iterator.", e);            
        }
    }
    
    
    @Override
    public final synchronized boolean isVanished(CrawlURL crawlURL) {
        CrawlURL cachedURL = getCached(crawlURL.getUrl());
        if (cachedURL == null) {
            return false;
        }
        CrawlStatus cur = crawlURL.getStatus();
        CrawlStatus last = cachedURL.getStatus();
        return cur != CrawlStatus.OK && cur != CrawlStatus.UNMODIFIED
              && (last == CrawlStatus.OK ||  last == CrawlStatus.UNMODIFIED);
    }

    @Override
    public synchronized void sitemapResolved(String urlRoot) {
        sqlUpdate("INSERT INTO " + TABLE_SITEMAP 
                + " (urlroot) VALUES (?) ", urlRoot);
    }

    @Override
    public synchronized boolean isSitemapResolved(String urlRoot) {
        return sqlQueryInteger(
                "SELECT 1 FROM " + TABLE_SITEMAP
                + " where urlroot = ?", urlRoot) > 0;
    }
    @Override
    public void close() {
        //do nothing
    }
    
    private boolean sqlURLExists(String table, String url) {
        return sqlQueryInteger("SELECT 1 FROM " + table
                + " WHERE url = ?", url) > 0;
    }
    
    private void sqlClearTable(String table) {
        sqlUpdate("DELETE FROM " + table);
    }
    
    private void sqlDeleteCrawlURL(String table, CrawlURL crawlURL) {
        sqlUpdate("DELETE FROM " + table 
                + " WHERE url = ?", crawlURL.getUrl());
    }
    
    private int sqlRecordCount(String table) {
        return sqlQueryInteger("SELECT count(*) FROM " + table);
    }

    private void sqlInsertFullCrawlURL(String table, CrawlURL crawlURL) {
        sqlUpdate("INSERT INTO " + table
                + " (url, depth, smLastMod, smChangeFreq, smPriority, "
                + "  docchecksum, headchecksum, status) "
                + " values (?,?,?,?,?,?,?,?)",
                crawlURL.getUrl(),
                crawlURL.getDepth(),  
                crawlURL.getSitemapLastMod(),
                crawlURL.getSitemapChangeFreq(),
                crawlURL.getSitemapPriority(),
                crawlURL.getDocChecksum(),
                crawlURL.getHeadChecksum(),
                crawlURL.getStatus().toString());
    }
    private void sqlInsertLightCrawlURL(String table, CrawlURL crawlURL) {
        sqlUpdate("INSERT INTO " + table
                + " (url, depth, smLastMod, smChangeFreq, smPriority) "
                + " values (?,?,?,?,?)",
                crawlURL.getUrl(),
                crawlURL.getDepth(),  
                crawlURL.getSitemapLastMod(),
                crawlURL.getSitemapChangeFreq(),
                crawlURL.getSitemapPriority());
    }
    
    private void copyCrawlURLsToQueue(String sourceTable) {
        ResultSetHandler<Void> h = new ResultSetHandler<Void>() {
            @Override
            public Void handle(ResultSet rs) throws SQLException {
                while(rs.next()) {
                    CrawlURL crawlURL = toCrawlURL(rs);
                    if (crawlURL != null) {
                        queue(crawlURL);
                    }
                }
                return null;
            }
        };
        try {
            new QueryRunner(datasource).query(
                    "SELECT url, depth, smLastMod, smChangeFreq, smPriority "
                  + "FROM " + sourceTable, h);
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem loading crawl URL from database.", e);            
        }
    }
    
    private CrawlURL sqlQueryCrawlURL(boolean selectAll, String table, 
            String where, String order, Object... params) {
      try {
          ResultSetHandler<CrawlURL> h = new ResultSetHandler<CrawlURL>() {
              @Override
              public CrawlURL handle(ResultSet rs) throws SQLException {
                  if (rs.next()) {
                      return toCrawlURL(rs);
                  }
                  return null;
              }
          };
          String sql = "SELECT url, depth, smLastMod, smChangeFreq, "
                     + "smPriority ";
          if (selectAll) {
              sql += ", docchecksum, headchecksum, status ";
          }
          sql += "FROM " + table;
          if (StringUtils.isNotBlank(where)) {
              sql += " WHERE " + where;
          }
          if (StringUtils.isNotBlank(order)) {
              sql += " ORDER BY " + order;
          }
          if (LOG.isDebugEnabled()) {
              LOG.debug("SQL: " + sql);
          }
          return new QueryRunner(datasource).query(sql, h, params);
      } catch (SQLException e) {
          throw new CrawlURLDatabaseException(
                  "Problem running database query.", e);            
      }
    }
    
    private CrawlURL toCrawlURL(ResultSet rs) throws SQLException {
        if (rs == null) {
            return null;
        }
        int colCount = rs.getMetaData().getColumnCount();
        CrawlURL crawlURL = new CrawlURL(
                rs.getString("url"), rs.getInt("depth"));
        if (colCount > COLCOUNT_SITEMAP) {
            crawlURL.setSitemapChangeFreq(
                    rs.getString("smChangeFreq"));
            BigDecimal bigP = rs.getBigDecimal("smPriority");
            if (bigP != null) {
                crawlURL.setSitemapPriority(bigP.floatValue());
            }
            BigDecimal bigLM = rs.getBigDecimal("smLastMod");
            if (bigLM != null) {
                crawlURL.setSitemapLastMod(bigLM.longValue());
            }
            if (colCount > COLCOUNT_ALL) {
                crawlURL.setDocChecksum(rs.getString("docchecksum"));
                crawlURL.setHeadChecksum(rs.getString("headchecksum"));
                crawlURL.setStatus(
                        CrawlStatus.valueOf(rs.getString("status")));
            }
        }
        return crawlURL;
    }
    
    
    private int sqlQueryInteger(String sql, Object... params) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("SQL: " + sql);
        }
        try {
            Integer value = new QueryRunner(datasource).query(
                    sql, new ScalarHandler<Integer>(), params);
            if (value == null) {
                return 0;
            }
            return value;
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem getting database scalar value.", e);            
        }
    }
    private void sqlUpdate(String sql, Object... params) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("SQL: " + sql);
        }
        try {
            new QueryRunner(datasource).update(sql, params);
        } catch (SQLException e) {
            if (e.getErrorCode() == SQL_ERROR_ALREADY_EXISTS) {
                LOG.debug("Already exists in table. SQL Error:" 
                        + e.getMessage());
            } else {
                throw new CrawlURLDatabaseException(
                        "Problem updating database.", e);            
            }
        }
    }

    private DataSource createDataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("org.apache.derby.jdbc.EmbeddedDriver");
        ds.setUrl("jdbc:derby:" + dbDir + ";create=true");
        ds.setDefaultAutoCommit(true);
        return ds;
    }

    private boolean ensureTablesExist() throws SQLException {
        ArrayListHandler arrayListHandler = new ArrayListHandler();
        Connection conn = datasource.getConnection();
        List<Object[]> tables = arrayListHandler.handle(
                conn.getMetaData().getTables(
                        null, null, null, new String[]{"TABLE"}));
        conn.close();
        if (tables.size() == NUMBER_OF_TABLES) {
            LOG.debug("    Re-using existing tables.");
            return true;
        }
        LOG.debug("    Creating new crawl tables...");
        sqlCreateTable(TABLE_QUEUE);
        sqlCreateTable(TABLE_ACTIVE);
        sqlCreateTable(TABLE_PROCESSED_VALID);
        sqlCreateTable(TABLE_PROCESSED_INVALID);
        sqlCreateTable(TABLE_CACHE);
        sqlCreateTable(TABLE_SITEMAP);
        return false;
    }

    private void sqlCreateTable(String table) {
        sqlUpdate(sqls.getString("create." + table));
        if (TABLE_QUEUE.equals(table)) {
            sqlUpdate(sqls.getString("create." + table + ".index"));
        }
    }
    

    private boolean isValidStatus(CrawlURL crawlURL) {
        return crawlURL.getStatus() == CrawlStatus.OK
                || crawlURL.getStatus() == CrawlStatus.UNMODIFIED;
    }
    
    private final class CrawlURLIterator implements Iterator<CrawlURL> {
        private final ResultSet rs;
        private final Connection conn;
        private final Statement stmt;

        private CrawlURLIterator(ResultSet rs, Connection conn, Statement stmt) {
            this.rs = rs;
            this.conn = conn;
            this.stmt = stmt;
        }

        @Override
        public boolean hasNext() {
            try {
                if (conn.isClosed()) {
                    return false;
                }
                if (!rs.isLast()) {
                    return true;
                } else {
                    DbUtils.closeQuietly(conn, stmt, rs);
                    return false;
                }
            } catch (SQLException e) {
                LOG.error("Database problem.", e);
                return false;
            }
        }

        @Override
        public CrawlURL next() {
            try {
                if (rs.next()) {
                    return toCrawlURL(rs);
                }
            } catch (SQLException e) {
                LOG.error("Database problem.", e);
            }
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
